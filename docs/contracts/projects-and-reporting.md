# Projects, recruitment and reporting API

Owner: Raul. Implemented locally, 16 November 2026. Requirements: FR-04–FR-07,
with FR-03 association isolation and FR-16 configurable labels.
No task, event, attendance or messaging workflow is implemented by this module.

## Shared conventions

Reuse [identity/current user](identity-and-current-user.md) and
[association access](association-workspaces.md). Account UUID, association
membership UUID and project membership UUID are distinct identifiers.
Controllers obtain the trusted actor from AuthenticatedUser; a submitted user ID
selects a target and never proves the actor's permissions.

All routes below require a live bearer session and current association membership.
Project and child identifiers are resolved within the path's association/project.
Responses use explicit DTOs and Cache-Control: no-store. UUIDs and ISO-8601 instants
follow the existing contract. Positions never imply management permissions.

## Routes

Prefix: /api/associations/{associationId}/projects.

| Method / suffix | Authorization and behavior |
| --- | --- |
| GET collection | Association member; internal summaries only |
| POST collection | Association ADMIN; create project and 1–20 initial manager appointments atomically |
| GET /{projectId} | Active project team or association ADMIN; project details |
| PATCH /{projectId} | Active project manager; edit name/description |
| PUT /{projectId}/managers/{userId} | Association ADMIN; appoint/promote an existing association member to root project manager |
| GET /{projectId}/member-options | Project manager or association ADMIN; current member userId/displayName only, without emails |
| GET /{projectId}/recruitment | Association member; summary, recruitment call and positions |
| PUT /{projectId}/recruitment | Project manager; configure/extend/reopen the single call with a future deadline |
| POST /{projectId}/positions | Project manager; create a role/department position |
| PATCH /{projectId}/positions/{positionId} | Project manager; edit labels; department changes blocked once applications exist |
| POST /{projectId}/applications | Current association member; apply as the authenticated account |
| GET /{projectId}/applications/mine | Own applications only |
| GET /{projectId}/applications | Project manager only; review queue including motivations |
| POST /{projectId}/applications/{applicationId}/accept | Project manager; atomic selection, membership, parent and other-application closure |
| POST /{projectId}/applications/{applicationId}/reject | Project manager; reject a pending application |
| GET /{projectId}/team | Active team or association ADMIN; team/reporting projection including inactive history |
| PUT /{projectId}/department-managers/{memberId} | Project manager; promote an accepted active member; they report to the appointing manager |
| PATCH /{projectId}/team/{memberId}/reporting | Project manager; change a non-root member's validated reporting parent |

A manager in another project, a department manager, or an ordinary association
member cannot configure recruitment or review applications here. Association ADMIN
alone does not grant project-manager configuration/review authority; administrators
can appoint themselves explicitly if they also need to manage the project.
The existing association-admin roster remains admin-only.

## Request examples

~~~json
{
  "name": "Community fair",
  "description": "A student-led community project.",
  "managerUserIds": ["account-uuid"]
}
~~~

Project name is required, max 150 characters; description required, max 5000.
PATCH uses name/description. Recruitment PUT uses description (required, max 5000)
and deadline (required future instant). Position create/edit uses roleLabel and
departmentName (required, max 100 each). Labels are trimmed and configurable;
departments are project-local, and identical exact department names reuse one ID.

Application POST uses positionId and motivation (required, max 5000). Accept and
reporting PATCH use reportingParentMembershipId (a project membership UUID).
Manager appointment uses an account UUID in the URL; department-manager
appointment uses an existing project membership UUID.

Overview response: project, recruitmentDescription, recruitmentDeadline, positions,
associationAdmin. The embedded project has id, associationId, name, description,
recruitmentDeadline, canManage and canOpen. The latter booleans guide UI only.

Position: id, departmentId, departmentName, roleLabel.
Team member: id, userId, displayName, projectRole, departmentId, departmentName,
positionId, positionLabel, reportingParentMembershipId, active.
Application: id, positionId, positionLabel, departmentName, userId, displayName,
motivation, status, createdAt, reviewedAt. Motivation appears only in the
applicant's own response or authorized manager review, never in project summaries.

## State and reporting rules adopted for this implementation

- One configurable recruitment call per project. Applications require time strictly
  before its deadline; review can continue afterward. A future deadline reopens it.
- One application per account/position; several distinct positions are allowed.
  Withdrawal and reapplication are unavailable. Rejection is final for that position.
- Only PENDING applications can be accepted/rejected. Accepting one produces
  ACCEPTED and closes the member's other pending applications as
  CLOSED_ACCEPTED_ELSEWHERE. Rejected applications retain REJECTED.
- Acceptance creates exactly one project membership, selected position and
  reporting parent in the same transaction. Already assigned accounts cannot
  apply again. A position title such as “Manager” confers no management role.
- Project managers are roots with no reporting parent. Department managers are
  explicitly appointed from accepted members and report directly to a project
  manager. Ordinary members report to a project manager or an active department
  manager in their own department. Multiple roots/department managers are supported.
- Self-parenting, cycles, inactive supervisors and cross-project/association
  references are rejected. Project managers may administer this project's
  recruitment/hierarchy; task access still follows their actual reporting branch.
- Association removal nulls the original membership reference and immediately
  revokes active project access while retaining history. Rejoining creates a
  different membership and does not revive old project privileges. An administrator
  may explicitly reappoint a root manager. General team removal, historical
  reassignment and former recruit reinstatement remain future policy work.

These defaults implement the plan's proposals under Raul's instruction to proceed.
They are recorded choices, not a claim that other owners reviewed them.

## Shared backend helpers

Inject ProjectAccess. Supply the authenticated actor's account UUID, association ID
and project ID; helpers resolve live scope before granting access.

| Public method | Consumer contract |
| --- | --- |
| requireManager(associationId, projectId, actorId) | Explicit active PROJECT_MANAGER for exactly this project; use for Yasmine's project-event access and Denisa's agreed organizer predicate |
| requireTeamMember(associationId, projectId, actorId) | Active project membership, returned as TeamMember |
| requireDetailAccess(associationId, projectId, actorId) | Active team or association ADMIN; use for authorized team/detail reads |
| isStrictDescendant(associationId, projectId, managerUserId, targetUserId) | Strict reporting ancestry; self/peers/other branches/inactive intermediates are false |
| isStrictDescendantOrFormerMember(associationId, projectId, managerUserId, targetUserId) | Same, but the target may be a removed member (ancestry from their retained row through active managers). Added by Tudor for orphaned tasks |
| projectAudienceUserIds(associationId, projectId, actorId) | Current active project audience; authorized team/admin caller |
| departmentAudienceUserIds(associationId, projectId, departmentId, actorId) | Current department plus active project managers; caller must be in that department, root manager or association ADMIN |

Tudor combines strict ancestry with own-task access and the association ADMIN
task override from the plan. Chat audience membership never grants task access.
An association ADMIN is not automatically a team member or chat participant;
the returned audience contains actual active assignments. Yasmine and Denisa
retain ownership of event-specific organizer rules and checks.

## Transactions, constraints and errors

All mutations lock association then project and recheck permissions afterward;
this composes with MembershipService's existing removal/role-change lock.
Acceptance and hierarchy changes are serialized within that scope.

Unique project/account membership prevents duplicate team assignments. Applications
use a nullable selected_user_id mirror constrained to match user_id exactly when
ACCEPTED; UNIQUE(project_id, selected_user_id) permits multiple pending records
while enforcing one accepted position at the database level on PostgreSQL and H2.
Composite FKs scope department, position and reporting parent to the same project.
Service validation handles supervisor role, department compatibility and cycles.

Errors reuse code/message/fieldErrors: 400 validation or invalid supervisor,
401 unauthenticated, 403 membership/project permission denied, 404 scoped project/
position/application/department absent, 409 state conflict. Feature codes include
PROJECT_MANAGER_REQUIRED, PROJECT_TEAM_REQUIRED, RECRUITMENT_CLOSED,
APPLICATION_EXISTS, APPLICATION_NOT_PENDING, ALREADY_ON_TEAM,
APPLICANT_MEMBERSHIP_CHANGED, POSITION_EXISTS, POSITION_IN_USE,
INVALID_DEADLINE, INVALID_SUPERVISOR, ROOT_MANAGER and REPORTING_CYCLE.

## Evidence

ProjectWorkflowTests covers FR-04–07 permissions, selection, concurrency, database
constraints, reporting branches/audiences, removal/rejoining and V1 upgrade.
It passes on H2 and PostgreSQL 17 using Java 17. Frontend unit tests cover scope,
application submission/errors, explicit acceptance parent, revocation and creation.
The opt-in Playwright suite demonstrates the real administrator → manager →
member → selection → department-manager workflow on desktop and mobile.
See [the short handoff](../../RAUL_IMPLEMENTATION.md) for checks and reproduction.

