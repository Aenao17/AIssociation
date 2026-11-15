# Association workspaces and membership administration

Owner: Gabriel. Builds on the identity/current-user and platform creation contracts.
No new database migration or default account is required.

## Permissions and endpoints

All endpoints require a valid bearer session. Association membership is evaluated
from the database on each operation; it is not embedded in session tokens.
Platform-admin configuration does not bypass association membership checks.

| Method/path | Access | Behavior |
| --- | --- | --- |
| GET `/api/workspaces/{slug}` | Association member | Workspace ID, name, slug and current association role |
| GET `/api/associations/{associationId}/members` | Association ADMIN | Member roster with account email/role |
| POST `/api/associations/{associationId}/members` | Association ADMIN | Add an existing account, returning 201 |
| PATCH `/api/associations/{associationId}/members/{membershipId}` | Association ADMIN | Change membership role |
| DELETE `/api/associations/{associationId}/members/{membershipId}` | Association ADMIN | Remove membership, returning 204 |

Workspace response:

```json
{"associationId":"uuid","name":"Example Association","slug":"example","role":"MEMBER"}
```

Add body: `{"email":"existing@example.com","role":"MEMBER"}`. Email is trimmed,
lowercased and validated. Role is required and must be `MEMBER` or `ADMIN`.
Role-change body: `{"role":"ADMIN"}`. Project roles are separate.

Member shape: `id` (membership UUID), `userId`, `displayName`, `email`, `role`,
`createdAt`. Use the membership UUID in PATCH/DELETE paths, not the account UUID.
The member lookup includes both association and membership IDs; an ID belonging
to another association cannot be changed through the current association path.

Ordinary members cannot read the administrative roster, add users, or promote
anyone, including themselves. A user's ADMIN role in one association does not
grant privileges in another association. The roster is not a public directory.

## Last-administrator invariant and changes

An association must retain at least one administrator. Removing or demoting the
last administrator returns 409 `LAST_ADMIN_REQUIRED`. Keeping the same ADMIN role
is allowed. Self-demotion/removal is allowed only when another admin remains.

All membership mutations lock the parent association row with `SELECT ... FOR
UPDATE` inside one transaction. The actor's current role is checked after that
lock is acquired; admin count and target updates occur in the same transaction.
This serializes competing membership operations for that association and avoids
two simultaneous self-demotions leaving no administrator.

Do not bypass this service with direct membership mutations in other application
modules: doing so would bypass the lock/invariant. Platform creation supplies the
first admin when a new association is created. Direct operator SQL is outside
this application-level guarantee.

Removal deletes the membership, not the account. The same bearer session stays
valid for participant use, but workspace/member APIs immediately reject the
removed association access. `/api/me` reflects role and membership changes
without a new login. Project/team history and rejoining policies must be agreed
with those module owners; these APIs do not delete unrelated domain data.

## Shared helper for teammates

Inject `AssociationAccess` and obtain the trusted user via
`@AuthenticationPrincipal AuthenticatedUser user`:

```java
AssociationRole role = associationAccess.requireMember(associationId, user.id());
associationAccess.requireAdmin(associationId, user.id());
```

`requireMember` returns the current association role or throws 403
`MEMBERSHIP_REQUIRED`. `requireAdmin` additionally requires ADMIN, otherwise 403
`ASSOCIATION_ADMIN_REQUIRED`. Apply these checks before private project, task,
roster or chat operations, then enforce that module's project/reporting/audience
permissions too. Association membership alone is not a grant to every project.
Never substitute a client-provided role or user ID for the authenticated identity.

The shared `ApiErrors` advice now handles validation and `ApiException` globally.
Feature services translate expected conflicts into their own codes; duplicate
account email remains `EMAIL_EXISTS`, not a generic mapping for all SQL conflicts.

Other errors: 400 invalid identifiers/body/role, 401 invalid session, 404
`ASSOCIATION_NOT_FOUND`, `ACCOUNT_NOT_FOUND` or `MEMBER_NOT_FOUND`, and 409
`MEMBER_EXISTS`. Member IDs are scoped to the association before use. Private
responses use `Cache-Control: no-store`.

## Frontend and handoff

`/account` links each membership to `/workspaces/{slug}`. The workspace resolves
its current role from the backend, not a browser-supplied role. Only ADMIN sees
membership controls. Add, role change and removal refresh current-user/workspace
state; removing/demoting oneself removes the corresponding interface access.

Removal needs explicit confirmation. Last-admin controls are disabled with an
explanation, while server checks protect against bypass and concurrent changes.
Switching user/workspace resets the scoped view so old responses do not replace
another workspace's state. Refresh actions support detecting externally changed
membership/permissions.

Future project/task/chat screens can extend this workspace without adding another
account system. The association roster here is admin-only; any member-facing
contact directory needs its own intentional response/permissions contract.
