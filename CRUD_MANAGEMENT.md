# Project and event CRUD management

Updated 17 November 2026 on feature/CRUD-for-projects.

Existing create, list/detail and edit flows were retained. This change completes
the management controls with deletion and administrator project-detail editing.

- Association administrators can create projects, edit their name/description,
  appoint managers and delete unused projects. Project managers retain their
  existing project editing and recruitment permissions.
- Project deletion returns 204 after removing unused positions/departments and
  root appointments. It returns 409 PROJECT_IN_USE when applications, non-root
  team members, tasks, conversations or events exist. This avoids silently
  destroying other owners' operational records and recruitment history.
- Event organizers can create, read, update and now permanently delete events.
  Existing permissions apply: project managers for their own project events,
  association administrators for standalone events. Deleting an event removes
  its public page/listing too; cancellation remains available for retaining it.
- Both delete controls require explicit confirmation. Failed requests display
  the server error without pretending that deletion succeeded. Successful project
  deletion returns to the project list; event deletion updates the current list.
- DELETE routes use the same association/project locks and live authorization
  checks as existing mutations. Foreign-key conflicts return 409 and roll back.
  No migrations or cascading deletion of tasks/messages/applications were added.
- Removed two unused imports from App.tsx that blocked ESLint. Research routes
  and implementation were not changed.

## Verification

- Java 17 Maven verify: 82 tests passed and backend packaged successfully.
- Project/event suites on isolated PostgreSQL 17: 26 tests passed.
- Frontend: 55 unit tests, production build, ESLint, end-to-end TypeScript checks
  and formatting checks for touched files passed.
- Added tests for delete permissions, public disappearance, repeat deletion,
  administrator edits, dependency conflicts, confirmation cancellation and UI errors.
- No new browser or hosted verification was performed in this change.

Changes are local and uncommitted. Open a workspace's Projects & recruitment or
events management screen to use the controls. The existing application database
was not used by the isolated PostgreSQL tests.
