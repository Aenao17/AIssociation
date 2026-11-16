# Raul's implementation — FR-04 to FR-07

Implemented on feature/raul-projects-recruitment, 16 November 2026.

## What changed and why

- Added Flyway V2 for projects, configurable departments/positions, applications,
  project membership and reporting. V1 stays unchanged so existing databases upgrade.
- Added authenticated APIs for administrator project creation/manager appointment,
  manager configuration, member applications, accept/reject and reporting changes.
  Every operation checks the current association and exact project permissions.
- Acceptance creates one team membership, selected position and reporting parent
  atomically. Association/project locks and database uniqueness prevent two concurrent
  acceptances from selecting different positions for the same member.
- Separated explicit management permissions from position names. Added shared
  ProjectAccess helpers for Tudor's branch/audience checks and Yasmine/Denisa's
  project access, without implementing their tasks/events/chat workflows.
- Added workspace-linked project/recruitment screens, application review and team
  reporting controls, including loading, errors, refresh and revoked-access handling.
- Preserved historical records after association removal while revoking access.
  Rejoining does not silently restore old project privileges.
- Updated the [API contract](docs/contracts/projects-and-reporting.md),
  [ERD/migration index](docs/data-model.md), requirement mapping and engineering log.

## Choices made explicit

Accepting one application closes the other pending applications in that project.
Project managers are roots; department managers report to project managers.
Departments use project-local configurable labels. One extendable recruitment call
is supported per project; review can continue after its submission deadline.
Withdrawal/reapplication and general project-member removal are not added.
All delivered implementation is AI-assisted; no independent/no-GenAI contribution
is claimed, and Gabriel's research form was excluded.

## Validation

- Java 17 backend verify: 45 tests passed, including 14 new workflow tests.
- All 14 new tests also passed on isolated PostgreSQL 17, including concurrency,
  SQL uniqueness and upgrading V1 while retaining existing users.
- Frontend: 26 unit tests, production build, ESLint and end-to-end typecheck passed.
- Real desktop/mobile browser flow passed with separate administrator, manager
  and member sessions. It covers creation, two applications, one acceptance,
  other-application closure and department-manager appointment.
- Existing repository-wide Prettier issues remain outside the touched files;
  touched TS/TSX files are formatted. Build retains its existing bundle-size warning.
- Frontend checks used Node 24.14.0; the repository specifies Node 26. No dependency
  versions were changed. Hosted deployment was not checked.

Start from a workspace's **Projects & recruitment** link.
Reproduction instructions: [PostgreSQL and browser checks](docs/testing-projects.md).

Changes are local and uncommitted. The pre-existing root package-lock.json was
left untouched. V2 needs coordination with teammates before shared application;
their integration/review and Raul's technical defense remain to be completed.

