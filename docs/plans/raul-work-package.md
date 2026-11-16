# Raul work package: implementation status

16 November 2026. Branch: feature/raul-projects-recruitment.
Deadline: 18 November 2026, 16:00 Europe/Bucharest.
Source: the supplied team-plan HTML, Raul's work-package prompt and subsequent
instruction to implement the complete assigned scope without further questions.

## Delivered requirement mapping

| Requirement | Implemented behavior | Evidence |
| --- | --- | --- |
| FR-04 | Administrator project creation and manager appointments; manager editing; project IDs/access for dependent owners | ProjectService/Controller, ProjectPages, creation/appointment authorization tests |
| FR-05 | Project-local configurable positions/departments; call description/deadline; multiple motivated member applications | Recruitment APIs/UI; deadline, duplicate, privacy and non-member tests |
| FR-06 | Manager accept/reject; one accepted position and membership; close other pending applications atomically | Concurrent acceptance test on H2 and PostgreSQL; direct SQL uniqueness test |
| FR-07 | Explicit department-manager appointment, one parent per accepted member, root managers, scoped ancestry/audiences | Invalid-supervisor/cycle/branch tests; real browser appointment |
| FR-03 / FR-16 | Live association/project checks; independent position labels and permissions | Cross-association/unrelated-manager tests and custom-label test |
| Additional artifact | Current ERD, constraints and migration order; shared API/helper contract | [Data model](../data-model.md), [contract](../contracts/projects-and-reporting.md) |

Initial inspection at 76e3238 found accounts/workspaces but no project domain.
The first vertical slice was project creation/appointment; recruitment, selection,
reporting and dependent-owner helpers were then added on the same focused branch.
The pre-existing untracked root package-lock.json remains untouched.

## Decisions recorded under the instruction to proceed

| Topic | Implemented/default choice | Remaining boundary |
| --- | --- | --- |
| Other pending applications | Close as CLOSED_ACCEPTED_ELSEWHERE after acceptance | Rejected applications remain rejected |
| Reporting | Root project managers; department managers report to a project manager; members have one valid supervisor | Task branches remain separate from shared department audiences |
| Department vocabulary | Project-local configurable department labels, reused within a project | No mandatory association department template |
| Recruitment lifecycle | One call; strict deadline for submissions; review afterward; future deadline reopens | Repeated distinct calls are future work |
| Withdrawal/reapplication | No withdrawal or repeat submission to the same position | This choice is explicit in UI/API; future policy can extend it |
| Membership removal | Preserve history, revoke access through the original membership reference; rejoin does not restore project access | General team removal/former-recruit reinstatement need a later workflow; explicit root reappointment is supported |
| Independent assessment | The implemented scope is AI-assisted following Raul's authorization | No independent/no-GenAI feature is claimed or generated as independent; Gabriel's research form is excluded |

These are implementation decisions, not fabricated teammate approvals or research findings.

## Handoffs

- Gabriel: identity/session/membership conventions reused. No duplicate user model;
  original membership UUIDs prevent stale access after removal/rejoin. Manager member
  options expose only account UUID/display name; the admin roster remains private.
- Tudor: ProjectAccess supplies current membership, strict ancestry and project/
  department audiences. Use the helpers rather than reconstructing the hierarchy.
- Yasmine/Denisa: reuse requireManager for the exact project, then apply the
  event/organizer rules owned by your modules. Standalone events remain outside scope.
- All schema owners: V2 is now the local project-domain migration. Reconcile any
  concurrent V2 allocation before shared application; do not modify applied migrations.
  No external teammate messages, pushes, merge or deployment were performed.

## Verification and contribution evidence

- Backend verify: 45 tests passed on Java 17, including 14 project workflow tests.
- The same 14 workflow tests passed on isolated PostgreSQL 17, including concurrent
  acceptance, SQL uniqueness and migration of an existing V1 schema with retained users.
- Frontend: 26 unit tests passed; production build, ESLint and end-to-end typecheck
  passed. Whole-repository Prettier had 28 existing failures before implementation;
  only touched TS/TSX files were formatted.
- Real Playwright flow passed on desktop and mobile with separate account contexts.
  Screenshots checked the responsive team/reporting interface; no horizontal overflow.
- Development Node remains 24.14.0 while the repository specifies 26. No toolchain
  or dependency versions were changed. No hosted verification was performed.

Work was prepared with Codex assistance. Human review/technical defense remain Raul's
responsibility. Exact model metadata should be recorded from the session in the
GenAI declaration; no model ID or human contribution is invented here.
See [RAUL_IMPLEMENTATION.md](../../RAUL_IMPLEMENTATION.md) for the brief explanation.

