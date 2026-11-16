# Events and public discovery

Owner: Yasmine. FR-10 / FR-11, with shared FR-03 isolation and FR-12 event fields.
Implemented locally, 16 November 2026, under Yasmine's instruction to complete her
workflow. Choices below are recorded local defaults, not teammate approvals.

## Authorization and ownership

Reuse Gabriel's bearer sessions, AuthenticatedUser, AssociationAccess and global
code/message/fieldErrors responses. Project events require Raul's live
ProjectAccess.requireManager for exactly the stored association/project. Association
ADMIN alone does not grant project-manager permissions. Standalone events require
current association ADMIN. Department managers and ordinary members cannot edit.
All private reads use the same organizer predicate; collection queries exclude
inaccessible events instead of leaking drafts through client filtering.
Private responses use Cache-Control: no-store. Public responses never contain
project IDs, account identities, memberships, session information or rosters.

Mutation lock order is association -> project, if any -> event. Membership removal
and manager changes serialize with these association/project locks, followed by live
permission checks. Event ownership is fixed after creation. Each project can have
many events; project ID null means standalone. Scoped composite FK enforces that a
project and its event belong to the same association.

## Implemented API

| Method/path | Response / behavior |
| --- | --- |
| GET /api/public/associations | [{id,name,slug}], sorted lowercase name then slug |
| GET /api/public/associations/{slug} | {id,name,slug,description,website}; unknown slug 404 |
| GET /api/public/events | {items,total,page,size}; published events only |
| GET /api/public/events/{id} | Public event; drafts and unknown IDs return 404; cancelled/completed notices remain accessible |
| GET /api/associations/{associationId}/events | Organizer-only projections of manageable events, including drafts |
| GET /api/associations/{associationId}/events/{id} | Authorized organizer event |
| POST /api/associations/{associationId}/events | Create DRAFT, 201 |
| PUT /api/associations/{associationId}/events/{id} | Replace editable fields, 200 |
| POST /api/associations/{associationId}/events/{id}/publish | Immediate publication without approval |
| POST /api/associations/{associationId}/events/{id}/cancel | Terminal cancellation of published event |
| POST /api/associations/{associationId}/events/{id}/complete | Complete published event only after its end |
| PUT /api/associations/{associationId}/public-profile | ADMIN updates description/website only, 204 |

Name/slug/identity/role administration remains in Gabriel's existing module. Public
presentation editing is a single panel in the event workspace, not a second account
or membership administration module. This local ownership split needs Gabriel's
review before merging; no external coordination is claimed.

## Event payloads

Create/replace body:

```json
{
  "projectId": null,
  "title": "Community workshop",
  "description": "A student-led workshop.",
  "location": "Campus hall",
  "startsAt": "2026-11-20T16:00:00Z",
  "endsAt": "2026-11-20T18:00:00Z",
  "timeZone": "Europe/Bucharest",
  "registrationDeadline": "2026-11-20T15:00:00Z",
  "capacity": null
}
```

Public event: id, association {id,name,slug}, title, description, location, startsAt,
endsAt, timeZone, registrationDeadline, capacity, status. Organizer response wraps
this as {event,projectId,updatedAt}. Event ID remains stable across edits/publication.
Titles max 150, description 5000, location 500; trimmed nonblank. Schedule/deadline
required; end strictly after start, deadline no later than start; new events start
in the future. Timezone must be a valid IANA ID. Capacity null is unlimited;
otherwise positive signed 32-bit integer. API times are ISO instants stored as
TIMESTAMPTZ. Editor inputs explicitly use UTC with a selected-timezone preview.

Profile update: {description,website}. Description max 5000; empty allowed. Website
nullable, max 500; only full http/https URLs with host and without credentials.
Text is rendered as React text, never injected HTML; external links use noopener.

## Discovery and agenda semantics

GET /api/public/events supports optional associationId, from and to (ISO instants),
page (0..100000, default 0), size (1..100, default 20). Range is [from,to): an event
matches when endsAt > from and startsAt < to. Both bounds supplied must increase.
Order startsAt then event UUID; total counts all matching published events.
No implicit future-only restriction. List and agenda share the exact same query,
pagination and records; no second calendar model or capped fetch-all behavior.
Association-page feeds lock associationId and use the same query.

UI filters label UTC calendar days. The displayed Through day is inclusive and
converted to the next UTC midnight as the exclusive API bound. Event times display
their own named timezone, including DST. Filters/page/view are URL search params;
changing a filter resets page; switching List/Agenda preserves the query and page.

## Recorded lifecycle/capacity defaults

- DRAFT -> PUBLISHED requires future start and future registration deadline.
- PUBLISHED -> CANCELLED is terminal; confirmation in UI. No reopening/unpublishing.
- PUBLISHED -> COMPLETED only once endsAt <= current server time.
- DRAFT and PUBLISHED are editable; terminal states are read-only.
- A valid future registration deadline reopens the event's registration window;
  event cancellation never reopens. Deadline equality/cutoff must be enforced by
  Denisa as server now < registrationDeadline, not trusted UI state.
- Published capacity can increase or become unlimited. Finite reductions and
  unlimited -> finite changes return 409 CAPACITY_REDUCTION_UNAVAILABLE until
  Denisa's active-seat ledger is integrated. Draft capacity remains freely editable.

Errors: 400 validation/filter/date/deadline/timezone/website; 401 missing session;
403 association/project rights; 404 scoped event/project/association absent;
409 invalid transition, terminal edit or published capacity reduction. Codes include
EVENT_NOT_FOUND, INVALID_EVENT_DATE, INVALID_DEADLINE, INVALID_TIME_ZONE,
EVENT_WINDOW_CLOSED, EVENT_OWNERSHIP_FIXED, INVALID_EVENT_TRANSITION, EVENT_READ_ONLY,
INVALID_EVENT_FILTER, INVALID_WEBSITE and CAPACITY_REDUCTION_UNAVAILABLE.

## Denisa handoff

Registration/attendance are not implemented here. Public detail contains the
`#event-registration` region with data-event-id and all event fields available.
Replace the current unavailable/closed/cancelled notice with Denisa's real component;
do not add a second registration API or promise a place from capacity alone.
Her backend must re-read persisted status/deadline/capacity and lock the event row
in the same transaction as active-seat changes. Only PUBLISHED events and times
strictly before the deadline are eligible. Cancellation disables new registrations;
existing registration/refund/cancellation notifications remain her/team policy.
No roster or attendance data is added to public event responses.

## Schema / coordination

`V6__events_public_profiles.sql` (originally V3; renumbered because main already had `V3__research_responses.sql`, `V4__tasks.sql` and `V5__messaging.sql`) adds profile description/website, events and indexes,
and a composite unique key on projects(id,association_id) for scoped event FKs.
V1–V5 are unchanged. V6 is the next free number on main; confirm it with Raul
before shared application. No production seeds or passwords are added.
Tests include fresh migrations, V2->V6 retention and direct cross-association FK
rejection. The existing project upgrade test now targets V2 explicitly.

## UI routes

/home (landing with discovery links), /events, /events/{eventId}, /associations,
/associations/{slug}, /workspaces/{slug}/events. Existing Vercel fallback supports
direct routes in configuration. Hosted routes have not been deployed or verified.
