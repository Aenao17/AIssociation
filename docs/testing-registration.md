# Registration, attendance and messaging UI: workflow scenarios

Owner: Denisa. Requirements: FR-12 (registration), FR-13 (cancellation), FR-14
(attendance), FR-15 (messaging UI; Tudor owns the API), with FR-03 isolation.
Contracts: [registration-attendance](contracts/registration-attendance.md),
[events](contracts/events-and-public-discovery.md), [messaging](contracts/messaging.md).

**Status: scenarios only.** The registration/attendance backend and the registration
and messaging screens are not implemented yet, so nothing below has been run. Fill
the *Result* column only with commands actually executed. Error codes marked
*(proposed)* are not final until the registration contract is written.

Levels: **API** = `RegistrationWorkflowTests`, **UI** = Vitest component tests
(`RegistrationPanel.test.tsx`, `ChatPage.test.tsx`), **E2E** = Playwright
(`e2e/registration-workflow.spec.ts`).

## Fixtures

Two associations (Alpha, Beta). In Alpha: an ADMIN, a project manager of project P,
a department manager, an ordinary member. Participants: Ana and Bob (plain accounts,
no membership), Cara (member of Beta only). Events: E1 project event (capacity 1),
E2 standalone (capacity null), E3 published with a deadline already passed, E4 draft,
E5 cancelled. Synthetic data only.

## A. Registration (FR-12)

| ID | Scenario | Expected | Level | Result |
|----|----------|----------|-------|--------|
| REG-01 | Signed-in participant registers for E2 | 201, ACTIVE at once, no approval step; panel shows "Registered" | API, UI, E2E | |
| REG-02 | Same participant registers again while ACTIVE | 409 `ALREADY_REGISTERED` *(proposed)*; still one active row | API, UI | |
| REG-03 | Anonymous request to register | 401; no row | API | |
| REG-04 | E2 has no capacity: 3 participants register | All succeed (unlimited) | API | |
| REG-05 | E1 capacity 1: Ana and Bob register concurrently (parallel threads, barrier) | Exactly one 201, other 409 `EVENT_FULL` *(proposed)*; active count = 1 | API (PostgreSQL) | |
| REG-06 | E1 full, a third participant registers | 409 `EVENT_FULL`; UI shows a clear message | API, UI | |
| REG-07 | Register on E3 (deadline passed) | 409 `REGISTRATION_CLOSED` *(proposed)*; server time decides, not UI state | API, UI | |
| REG-08 | Register exactly at the deadline instant (fixed clock) | Denied: allowed only while now < deadline | API | |
| REG-09 | Register for draft E4 or cancelled E5 | 404 for draft (not public), 409 for cancelled | API | |
| REG-10 | Registration does not grant membership | Ana can still not read Alpha internals or chats (403/404) | API | |
| REG-11 | Organizer extends E3's deadline into the future | Registration reopens; Ana's panel sees the new value | API, E2E | |
| REG-12 | Cancel then re-register before cutoff | One new ACTIVE row; history keeps the cancelled one | API | |
| REG-13 | Public event response | Contains no roster, participant identities or counts beyond what the contract allows | API | |

## B. Cancellation (FR-13)

| ID | Scenario | Expected | Level | Result |
|----|----------|----------|-------|--------|
| CAN-01 | Ana cancels before the cutoff | 200/204, CANCELLED, seat released | API, UI, E2E | |
| CAN-02 | After CAN-01 on full E1, Bob registers | Succeeds (capacity released) | API | |
| CAN-03 | Ana cancels after the cutoff | 409 `CANCELLATION_CLOSED` *(proposed)*; stays ACTIVE | API, UI, E2E | |
| CAN-04 | Authorized organizer cancels Ana after the cutoff | Allowed; seat released; marked as organizer cancellation | API, E2E | |
| CAN-05 | Unauthorized user (other Alpha member, Cara, Bob) tries organizer cancel | 403/404; no change | API | |
| CAN-06 | Participant cancels another person's registration | 403/404 | API | |
| CAN-07 | Cancel an already cancelled registration | Idempotent or 409 as the contract states; capacity not released twice | API | |
| CAN-08 | Event cancelled by organizer | Active registrations handled per decision (open, see below) | API | |

## C. Roster and attendance (FR-14)

Organizer predicate matches events: project event needs a live project manager of
that project; standalone event needs current association ADMIN. An ADMIN alone is not
a project manager.

| ID | Scenario | Expected | Level | Result |
|----|----------|----------|-------|--------|
| ROS-01 | Project manager of P opens roster of E1 | Lists active registrations | API, E2E | |
| ROS-02 | ADMIN opens roster of standalone E2 | Allowed | API | |
| ROS-03 | Alpha ADMIN (not project manager) opens roster of project event E1 | Denied, same as event edit rules | API | |
| ROS-04 | Ordinary member, department manager, Cara, Ana open roster | 403/404; no registrant data in the body | API, UI | |
| ROS-05 | Organizer of event X reads roster of event Y in another project/association | 403/404 | API | |
| ROS-06 | Roster response fields | Only fields the organizer needs; no passwords, emails beyond contract | API | |
| ATT-01 | Organizer marks Ana present | One attendance record, timestamp and actor stored | API, E2E | |
| ATT-02 | Same "present" update repeated | Still one record; no duplicate row | API | |
| ATT-03 | Organizer corrects present to absent, then back | Same record updated, correction visible | API, UI, E2E | |
| ATT-04 | Mark attendance for a cancelled registration, or one from another event | 409/404 | API | |
| ATT-05 | Non-organizer tries to mark attendance | 403/404; nothing written | API | |
| ATT-06 | Two organizers update the same registration concurrently | Single record, last write wins, no constraint error surfaced | API | |

## D. Messaging UI (FR-15) against Tudor's API

| ID | Scenario | Expected | Level | Result |
|----|----------|----------|-------|--------|
| MSG-01 | Open Alpha's chat page | List shows association chat plus the user's project/department chats; same list/thread components for every type | UI, E2E | |
| MSG-02 | Send a message, then poll with `after=<last seq>` | Own message appears once, in order; new messages from others appear on refresh | UI, E2E | |
| MSG-03 | Start a direct conversation from contacts | Existing pair reopens the same chat (no duplicate); title is the other person | UI | |
| MSG-04 | Create a custom group | Contact picker lists only current Alpha members (no emails); group appears with creator able to manage | UI, E2E | |
| MSG-05 | Creator adds/removes members; non-creator cannot | Controls hidden unless `canManageMembers`; 403 `GROUP_MANAGER_REQUIRED` handled | UI | |
| MSG-06 | Load older messages (`before=<seq>`, `hasMore`) | Older page prepends without duplicates | UI | |
| MSG-07 | States | Loading, empty ("no conversations yet"), and error with retry are all shown | UI | |
| MSG-08 | 404 `CONVERSATION_NOT_FOUND` for a chat the user lost access to | Friendly message, no crash, list refreshes | UI | |
| MSG-09 | Switch between Alpha and Beta (user in both) | Separate conversations per association; no cross-association chat or direct-message mode | UI, E2E | |
| MSG-10 | Member removed from the association while the page is open | Next request fails; chat access gone without new login | API/UI | |
| MSG-11 | Chat membership does not grant task access | Message UI exposes no task data | API | |

## E. Manual checks before the demo

- Two browsers, one seat left, press Register at the same moment (REG-05 live).
- Let the deadline pass while a page is open, then press Cancel (CAN-03 message).
- Expired session on each screen: redirect to sign-in, no stale data shown.
- Mobile viewport (Pixel 7) for the registration panel, roster and chat.

## Traceability to the acceptance checklist

| Acceptance item | Scenarios |
|---|---|
| Registration confirms immediately; duplicate active registration prevented | REG-01, REG-02, REG-12 |
| Two attempts for the last seat cannot overbook | REG-05, REG-06, CAN-02 |
| Missing capacity means unlimited | REG-04 |
| Cancel before cutoff releases capacity; after cutoff participant denied, organizer may cancel | CAN-01 to CAN-05 |
| Roster/attendance scoped to the event's organizers; repeated updates do not duplicate | ROS-01 to ROS-06, ATT-01 to ATT-06 |
| Chat navigation and group creation never expose another association | MSG-04, MSG-09, MSG-10 |

## Open decisions to settle with Yasmine/Tudor/Gabriel (plan section 11)

1. Event cancelled: what happens to active registrations (CAN-08).
2. Re-registration after cancellation, and after an extended deadline (REG-11, REG-12).
3. Capacity reduction below active registrations: events currently return 409
   `CAPACITY_REDUCTION_UNAVAILABLE`; `EventService` must call the active-seat count.
4. Attendance visibility: this document assumes the same organizer predicate as events.
5. Polling interval and group controls for the UI (plan proposes plain text and polling).

## How to run

From `Backend`: `./mvnw -Dtest=RegistrationWorkflowTests test`. Concurrency scenario
REG-05 must also run on a disposable PostgreSQL database (see
[testing-events](testing-events.md) for the container recipe); do not present H2 as
PostgreSQL evidence. From `Frontend`: `npm run test:unit`, `npm run lint`,
`npm run build`, then the Playwright spec.