# Messaging API

Owner: Tudor (backend). Consumer: Denisa (messaging UI). Implemented locally,
16 November 2026. Requirement: FR-15, with FR-03 association isolation. Uses
[association access](association-workspaces.md) and Raul's
[project audiences](projects-and-reporting.md). Plain text and periodic refresh
only: no attachments, read receipts, typing indicators or push.

## Shared conventions

Live bearer session; the actor is always the authenticated account. UUIDs,
ISO-8601 instants, `Cache-Control: no-store`, errors as `{code, message, fieldErrors}`.
Every conversation belongs to exactly one association. A conversation the actor
cannot open is reported as **404 `CONVERSATION_NOT_FOUND`** (existence is not leaked).

## Conversation types and who can use them

| Type | Members (checked live on every request) | Created |
| --- | --- | --- |
| `ASSOCIATION` | every current association member | automatically, first listing |
| `PROJECT` | active project team (`projectAudienceUserIds`) | automatically for team members |
| `DEPARTMENT` | that department + the project's active project managers (`departmentAudienceUserIds`) | automatically for its members/managers |
| `GROUP` | explicit members; the creator manages them | `POST /groups` |
| `DIRECT` | the two participants, same association | `POST /direct` (one per pair) |

- An association ADMIN is **not** automatically in project/department chats.
- GROUP/DIRECT membership requires the person's *current* association membership.
  Leaving the association revokes access at once; rejoining does not restore old
  groups (the creator can add them again). Reopening a direct chat restores it.
- Chat membership grants nothing on tasks or any other module.

## Routes

Prefix: `/api/associations/{associationId}/conversations`.

| Method / path | Behavior |
| --- | --- |
| GET `` | Conversations the actor can open, newest activity first; ensures built-in ones exist |
| GET `/contacts` | Other current association members `{userId, displayName}` (no emails), for group/direct pickers |
| POST `/groups` | Create a custom group; 201 |
| POST `/direct` | Get or create the direct conversation with a member; 200 |
| GET `/{id}` | One conversation |
| GET `/{id}/members` | Current participants `{userId, displayName}` |
| PUT `/{id}/members/{userId}` | GROUP creator adds a current association member; returns participants |
| DELETE `/{id}/members/{userId}` | GROUP creator removes someone, or a member removes themself (leave); 204 |
| GET `/{id}/messages` | Messages, oldest first; see paging |
| POST `/{id}/messages` | Send; 201 with the message |

### Requests

~~~json
// POST /groups — memberUserIds may be empty; the creator is always added. Max 100.
{ "name": "Banner crew", "memberUserIds": ["account-uuid"] }
// POST /direct
{ "userId": "account-uuid" }
// POST /{id}/messages — trimmed, 1–4000 characters
{ "body": "Fair kickoff tomorrow at 10" }
~~~

### Responses

Conversation (one shape for all types):

~~~json
{ "id": "…", "associationId": "…", "type": "DEPARTMENT",
  "title": "Spring Fair · Logistics",
  "projectId": "…", "projectName": "Spring Fair",
  "departmentId": "…", "departmentName": "Logistics",
  "createdByUserId": null, "canManageMembers": false,
  "lastMessage": { /* Message or null */ } }
~~~

`title` is ready to display: association name, project name, `Project · Department`,
group name, or, for DIRECT, the *other* person's name. `canManageMembers` is true only
for a GROUP's creator (UI hint; the server rechecks).

Message: `id, seq, conversationId, senderUserId, senderName, body, createdAt, mine`.
`seq` is a strictly increasing number used as a cursor. `mine` is true for the actor's own messages.

### Paging and refresh

`GET /{id}/messages` returns `{ "messages": [...], "hasMore": boolean }`, always oldest first.

| Query | Returns | `hasMore` means |
| --- | --- | --- |
| *(none)* | newest `limit` messages | older messages exist |
| `before=<seq>` | up to `limit` messages older than `seq` | even older messages exist |
| `after=<seq>` | up to `limit` messages newer than `seq` | more new messages; ask again with the last seq |

`limit` defaults to 50, max 200. `after` and `before` together → 400.
Suggested UI loop: load without cursors, then poll every 3–5 s with
`after=<last seq shown>`; refresh the conversation list less often (e.g. 15–30 s).

## Errors

| Status | Code | When |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | blank/oversized body or name, both cursors |
| 400 | `INVALID_JSON` | malformed body |
| 400 | `INVALID_PARTICIPANT` | group/direct participant is not a current member of this association, or direct with self |
| 401 | — | no/expired session |
| 403 | `MEMBERSHIP_REQUIRED` | actor is not a member of this association |
| 403 | `GROUP_MANAGER_REQUIRED` | non-creator adds/removes someone else |
| 404 | `CONVERSATION_NOT_FOUND` | wrong association, or the actor is not a participant |
| 409 | `NOT_A_GROUP` | member changes on a non-GROUP conversation |
| 409 | `CREATOR_CANNOT_LEAVE` | the group creator tries to leave |

## Decisions (proposals from the plan; confirm with Gabriel and Denisa)

- Plain text, polling with a sequence cursor; no edit/delete of messages.
- Group creator manages membership; the creator cannot leave. If the creator leaves
  the association, the group keeps working but nobody can change its members.
- Built-in conversations are created on first listing; the unique `scope_key`
  plus `INSERT … ON CONFLICT DO NOTHING` makes concurrent creation safe.

## Persistence

Migration `V5__messaging.sql` (renumbered from V4 after main received V3 research responses): `conversations`
(type, association, optional project/department, unique `scope_key`, group name,
creator), `conversation_members` (GROUP/DIRECT only, with the member's association
membership id, nulled on removal), `messages` (identity `seq`, sender, body, time).

## Tests

`Backend/src/test/java/ro/stucanii/aissociation/messaging/MessagingApiTests.java`:
visibility per role, send/poll, outsiders and ADMIN denied team chats, department
separation, chat does not grant task access, group management, direct uniqueness
and cross-association isolation, removal/rejoin revocation, cursor paging.

## Interface entry point

Association members open **Messages** from their workspace. The screen route is
`/workspaces/{slug}/messages`; it resolves the workspace before loading the
association-scoped conversation API. Shared backend audience checks still control
which conversations and messages are available.
