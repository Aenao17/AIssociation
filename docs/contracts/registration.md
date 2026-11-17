# Registration, cancellation and attendance

Owner: Denisa. FR-12, FR-13, FR-14, with FR-03 isolation. Consumes
[events](events-and-public-discovery.md) (event status, deadline, capacity),
[association access](association-workspaces.md) and Raul's
[project access](projects-and-reporting.md). Migration: `V7__registrations_attendance.sql`.

## Rules (all enforced server-side, re-read from the database on every request)

- Any signed-in account may register; **no association membership is needed and none is granted**.
- Only `PUBLISHED` events accept registration. Drafts are invisible (404 `EVENT_NOT_FOUND`);
  cancelled/completed events return 409 `EVENT_NOT_OPEN`.
- Registration and participant cancellation are open while `now < registrationDeadline`
  (strict: at the exact deadline instant they are closed). A deadline extension by an organizer
  reopens the window immediately.
- Confirmation is immediate, no approval. `capacity = null` means unlimited.
- Seats: `COUNT(active registrations)`. Every seat change locks the event row first
  (`SELECT … FOR UPDATE`), so the last seat can be won by exactly one request.
- One row per (event, user). Cancelling sets `CANCELLED`; registering again while open
  reactivates the same row. So duplicates are impossible and attendance can never be duplicated.
- Participant cancel after the deadline: 403 `CANCELLATION_CLOSED`. An organizer may cancel any
  active registration of a published event (before or after the deadline).
- A registration cancelled **by an organizer** cannot be self-reactivated
  (409 `REGISTRATION_CANCELLED_BY_ORGANIZER`).
- Organizer = association `ADMIN` (any event of their association) or the `PROJECT_MANAGER` of the
  event's project. A project manager has no access to standalone events or other projects' events.
- Attendance is manual, idempotent, correctable, and only for `ACTIVE` registrations of
  `PUBLISHED`/`COMPLETED` events. Cancelling a registration clears its check-in.
- No public roster: the public endpoint returns counts only.

## Routes

| Method / path | Who | Behavior |
| --- | --- | --- |
| GET `/api/public/events/{eventId}/availability` | anyone | `Availability` (counts only); 404 for drafts |
| GET `/api/events/{eventId}/registration` | signed in | own `MyRegistration` |
| POST `/api/events/{eventId}/registration` | signed in | register; 201 `MyRegistration` |
| DELETE `/api/events/{eventId}/registration` | signed in | cancel own; 200 `MyRegistration` |
| GET `/api/associations/{associationId}/events/{eventId}/registrations` | organizer | `Roster` |
| DELETE `…/registrations/{registrationId}` | organizer | cancel; 200 `RosterEntry` |
| PUT `…/registrations/{registrationId}/attendance` | organizer | body `{ "attended": true\|false }`; 200 `RosterEntry` |

All responses are `Cache-Control: no-store`; errors are `{code, message, fieldErrors}`.

## Shapes

~~~json
// Availability
{ "eventId": "…", "eventStatus": "PUBLISHED", "capacity": 30, "activeRegistrations": 12,
  "seatsRemaining": 18, "registrationOpen": true, "registrationDeadline": "2026-11-20T15:00:00Z" }
// MyRegistration   (status: NOT_REGISTERED | ACTIVE | CANCELLED; canRegister/canCancel are UI hints)
{ "eventId": "…", "status": "ACTIVE", "registeredAt": "…", "cancelledAt": null,
  "cancelledByOrganizer": false, "canRegister": false, "canCancel": true, "availability": { /* Availability */ } }
// RosterEntry   (attendance: NOT_CHECKED_IN | CHECKED_IN)
{ "registrationId": "…", "userId": "…", "displayName": "…", "email": "…", "status": "ACTIVE",
  "registeredAt": "…", "cancelledAt": null, "cancelledByOrganizer": false,
  "attendance": "CHECKED_IN", "checkedInAt": "…" }
// Roster
{ "eventId": "…", "eventTitle": "…", "eventStatus": "PUBLISHED", "capacity": null,
  "activeRegistrations": 12, "checkedIn": 5, "items": [ /* RosterEntry, ACTIVE first */ ] }
~~~

## Errors

| Status | Code | When |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | attendance body without `attended` |
| 401 | — | no/expired session |
| 403 | `MEMBERSHIP_REQUIRED` | organizer route, caller not in this association |
| 403 | `PROJECT_MANAGER_REQUIRED` / `ORGANIZER_REQUIRED` | member, but not an organizer of this event |
| 403 | `CANCELLATION_CLOSED` | participant cancels at/after the deadline |
| 404 | `EVENT_NOT_FOUND` | unknown event, draft, or wrong association in the path |
| 404 | `REGISTRATION_NOT_FOUND` | not registered / id belongs to another event |
| 409 | `ALREADY_REGISTERED`, `ALREADY_CANCELLED` | repeated action |
| 409 | `EVENT_FULL` | no seat left |
| 409 | `REGISTRATION_CLOSED` | at/after the deadline |
| 409 | `EVENT_NOT_OPEN` | event not published (or not published/completed for attendance) |
| 409 | `REGISTRATION_CANCELLED_BY_ORGANIZER` | self re-registration blocked |
| 409 | `REGISTRATION_NOT_ACTIVE` | check-in on a cancelled registration |

## Decisions recorded as defaults (confirm with Yasmine / Gabriel)

- Re-registration after self-cancellation is allowed while the window is open.
- Organizers may cancel before the deadline too (plan only requires "after").
- Association admins see rosters for project events as well (plan's attendance-visibility default);
  Yasmine's event *editing* still needs the project manager.
- Roster includes participant e-mail for organizers only.
- Yasmine's rule stays as is: a published event's capacity can only grow or become unlimited
  (409 `CAPACITY_REDUCTION_UNAVAILABLE`). That is safe (never overbooks). If the team wants to allow
  lowering down to the active count, Yasmine can replace that check with
  `SELECT COUNT(*) FROM event_registrations WHERE event_id=? AND status='ACTIVE'` (event row is already locked there).
- Deleting an event that has registrations is refused (409 `EVENT_IN_USE`); cancel it instead.

## Tests

`Backend/src/test/java/ro/stucanii/aissociation/registration/RegistrationWorkflowTests.java`
(immediate confirmation, duplicates, capacity/unlimited, drafts, deadline equality, cancel/release,
organizer cancel, deadline extension, roster privacy per role, cross-association and
foreign-id access, idempotent attendance, concurrent last seat, double click, DB constraints).

## Frontend (Denisa)

New code lives only in `Frontend/src/registration/`:

| File | Purpose |
| --- | --- |
| `types.ts` | TypeScript mirror of the DTOs above + one readable message per error code |
| `RegistrationPanel.tsx` | `<RegistrationPanel eventId status />` — seats, register/cancel, sign-in prompt |
| `RosterPage.tsx` | Organizer roster, check-in/undo, cancel registration (route below) |
| `Registration.css` | Styles, brand tokens only |

### Integration points in other people's files (not changed by this work; each is a few lines)

1. **Yasmine — `Frontend/src/events/EventPages.tsx`**: inside `<div id="event-registration">` replace the
   cancelled/ended/closed/"not available yet" conditional with
   `<RegistrationPanel eventId={event.id} status={event.status} />` (import from `../registration/RegistrationPanel`).
   The panel prints the same texts for cancelled/ended events, so her existing test still passes, and the
   page's own 30-second `now` clock is no longer needed.
2. **Yasmine — `Frontend/src/events/EventWorkspace.tsx`**: next to "View public event" add a link
   `/workspaces/${slug}/events/${item.event.id}/roster` labelled "Registrations".
3. **Shared — `Frontend/src/App.tsx`**: add
   `<Route path="/workspaces/:slug/events/:eventId/roster" element={<RosterPage />} />`.

Tried together on a scratch copy: typecheck, ESLint and all 72 unit tests pass.
## Integrated entry points

Public event details at `/events/{eventId}` render RegistrationPanel. Organizers
open **Participants & attendance** from an event in their workspace; the roster
route is `/workspaces/{slug}/events/{eventId}/registrations`. All roster and
attendance permissions are enforced by the backend.
