# Tasks API and authorization matrix

Owner: Tudor. Implemented locally, 16 November 2026. Requirements: FR-08, FR-09,
with FR-03 association isolation. Consumes Raul's
[projects and reporting](projects-and-reporting.md) contract (`ProjectAccess`)
and Gabriel's [association access](association-workspaces.md). Messaging (FR-15)
is not part of this document.

## Shared conventions

Same as the projects contract: live bearer session, the actor is always the
authenticated account (`AuthenticatedUser`), UUIDs, ISO-8601 instants, explicit
DTOs, `Cache-Control: no-store`, errors as `{code, message, fieldErrors}`.
Association scope is derived from the project; a task has no separate
association column.

## Access rules

Evaluated on every request from live data. Nothing is cached in the task or token.

- **Project entry:** active project team member, or association ADMIN
  (`requireDetailAccess`). Others get 403 `PROJECT_TEAM_REQUIRED`;
  non-members of the association get 403 `MEMBERSHIP_REQUIRED`.
- **Read a task:** the actor is the assignee, **or** the assignee is (or, if
  removed, was) a strict descendant of the actor in this project's reporting tree
  (`ProjectAccess.isStrictDescendantOrFormerMember`), **or** the actor is ADMIN of
  this association.
- **Assign to:** an active team member below the actor; the actor themself when
  they are a PROJECT_MANAGER or DEPARTMENT_MANAGER; any active team member for ADMIN.
- **Manage a task** (edit, reassign, delete): strict ancestor of the assignee,
  ADMIN, or a manager on a task they created for themself. A task a superior
  assigned to a manager stays read-only for that manager (status aside).
- **Change status:** anyone who can read the task, so the assignee
  or a manager above them, or ADMIN.
- Tasks the actor may not read are reported as **404 `TASK_NOT_FOUND`**, so their
  existence is not revealed to peers or other projects.
- Chat/department membership grants nothing here. Position labels grant nothing.

## Authorization matrix

| Actor | Read | Create / assign | Edit / reassign | Status | Test |
| --- | --- | --- | --- | --- | --- |
| Association ADMIN (own association) | all tasks in project | any active team member | any task | any task | `associationAdministratorManagesEveryTaskInTheirAssociation`, `visibilityFollows…` |
| ADMIN of another association | denied (403) | denied | denied | denied | `outsidersAndOtherAssociationsAreDenied` |
| Project manager | own + whole branch | self or strict descendants | descendants' tasks and self-created own tasks; delete same | own + branch | `visibilityFollows…`, `managersAssignAndReassign…` |
| Peer project manager | own + own branch; not peer's own tasks | denied outside branch | 404 outside branch | 404 outside branch | `visibilityFollows…`, `changingProjectOrTaskIdentifiers…` |
| Department manager | own + subordinates | self or subordinates (not peers) | subordinate tasks and self-created own tasks; not tasks a superior assigned; delete same | own + subordinates | `managersAssignAndReassign…` |
| Project member | own only | denied (`ASSIGNEE_OUT_OF_BRANCH`) | denied (`TASK_MANAGE_DENIED`) | own only | `assigneeUpdatesOwnStatus…` |
| Association member not on team | 403 `PROJECT_TEAM_REQUIRED` | denied | denied | denied | `outsidersAndOtherAssociationsAreDenied` |
| Manager of project Q using project P's task ID | 404 `TASK_NOT_FOUND` | n/a | 404 | 404 | `changingProjectOrTaskIdentifiers…` |
| Removed from association | 403 `MEMBERSHIP_REQUIRED`; their tasks stay visible to ADMIN and former managers above them, who can reassign | cannot receive tasks (`INVALID_ASSIGNEE`) | denied | denied | `myTasksListsOwnTasks…` |
| Member deleting own task / peer deleting | 403 `TASK_MANAGE_DENIED` / 404 | | | | `managersAndAdminsDeleteTasks…` |

All tests are in `Backend/src/test/java/ro/stucanii/aissociation/tasks/TaskApiTests.java`.

## Routes

Prefix: `/api/associations/{associationId}`.

| Method / path | Behavior |
| --- | --- |
| GET `/tasks/mine` | Association member; own tasks across every project where still an active team member. Built for the "My tasks" screen. |
| GET `/projects/{projectId}/tasks` | Server-filtered visible tasks, ordered by deadline |
| GET `/projects/{projectId}/tasks/assignee-options` | Team members the actor may assign to |
| POST `/projects/{projectId}/tasks` | Create; status starts as `TODO`; 201 |
| GET `/projects/{projectId}/tasks/{taskId}` | Detail of a visible task |
| PUT `/projects/{projectId}/tasks/{taskId}` | Manager edit/reassign (full body) |
| PATCH `/projects/{projectId}/tasks/{taskId}/status` | Status change |
| DELETE `/projects/{projectId}/tasks/{taskId}` | Delete (same permission as edit); 204 |

### Requests

~~~json
// POST and PUT
{ "title": "Draw poster", "description": "optional", "assigneeUserId": "account-uuid",
  "dueAt": "2026-11-20T18:00:00Z" }
// PATCH status
{ "status": "IN_PROGRESS" }
~~~

Title required, max 200; description optional, max 5000 (blank stored as null);
assignee and `dueAt` required. A new deadline must be in the future. On edit, an
unchanged past deadline may be kept. Status is one of `TODO`, `IN_PROGRESS`, `DONE`.

### Responses

Task: `id, associationId, projectId, projectName, title, description, dueAt,
status, assigneeUserId, assigneeName, createdByUserId, createdByName, createdAt,
updatedAt, canManage, canChangeStatus`. The two booleans guide the UI only.

Assignee option: `userId, displayName, projectRole, departmentName`.

## Errors

| Status | Code | When |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | missing/oversized fields (`fieldErrors`) |
| 400 | `INVALID_JSON` | malformed body or unknown status value |
| 400 | `INVALID_DEADLINE` | new deadline not in the future |
| 400 | `INVALID_ASSIGNEE` | assignee is not an active member of this project team |
| 401 | — | no/expired session |
| 403 | `MEMBERSHIP_REQUIRED` / `PROJECT_TEAM_REQUIRED` | outside association / project |
| 403 | `ASSIGNEE_OUT_OF_BRANCH` | target assignee not below the actor |
| 403 | `TASK_MANAGE_DENIED` | visible task but actor may not edit it (e.g. own task) |
| 403 | `TASK_STATUS_DENIED` | reserved; currently every reader may change status |
| 404 | `PROJECT_NOT_FOUND` / `TASK_NOT_FOUND` | not in this association/project, or not visible |

## Decisions (confirmed by Tudor, 16 November 2026)

- Project and department managers may assign tasks to themselves and manage those
  self-created tasks. Members cannot self-assign.
- When an assignee leaves the association, the task stays. It remains visible to
  ADMIN and to the active managers formerly above that member, who can reassign or
  delete it. A removed member cannot receive new tasks.
- Writes take the association then project row lock (same order as
  `MembershipService`/`ProjectService`), so hierarchy changes cannot race task writes.
- Tasks can be deleted (hard delete) by anyone who may manage them.

## Persistence

Migration `V4__tasks.sql` (originally V3; renumbered because main received `V3__research_responses.sql`): table `tasks`
(`project_id`, `assignee_user_id`, `created_by_user_id`, `title`, `description`,
`due_at`, `status`, timestamps), indexed on `(project_id, assignee_user_id)` and
`assignee_user_id`.

## Frontend

Route `/workspaces/:slug/projects/:projectId/tasks` (`Frontend/src/tasks/ProjectTasksPage.tsx`),
opened from the "Open tasks" link on the project page (shown when `canOpen`).

- Board with To do / In progress / Done columns built from `GET …/tasks`.
- "New task" form appears only when `assignee-options` is not empty (managers, ADMIN).
- Status select when `canChangeStatus`; Edit and two-step Delete when `canManage`.
- Overdue open tasks are highlighted. Server errors are shown using their `message`.
- The association-wide "My tasks" screen (`GET /tasks/mine`) is Tudor's separate
  independent feature and is not part of this page.
