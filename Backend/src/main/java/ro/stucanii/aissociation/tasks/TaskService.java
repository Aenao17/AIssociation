package ro.stucanii.aissociation.tasks;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.stucanii.aissociation.associations.AssociationAccess;
import ro.stucanii.aissociation.associations.AssociationRole;
import ro.stucanii.aissociation.identity.ApiException;
import ro.stucanii.aissociation.identity.AuthenticatedUser;
import ro.stucanii.aissociation.projects.ProjectAccess;
import ro.stucanii.aissociation.projects.ProjectDtos;
import ro.stucanii.aissociation.projects.ProjectService;

/**
 * FR-08/FR-09 tasks. Every operation re-derives access from live data:
 * <ul>
 *   <li>read: own task, a task whose assignee is (or was, if removed) a strict descendant in the
 *       reporting tree, or any task when the actor is ADMIN of this association;</li>
 *   <li>assign: to subordinates, to oneself when a project/department manager, or anyone for ADMIN;</li>
 *   <li>manage (edit/reassign/delete): strict ancestor of the assignee, ADMIN, or a manager on a
 *       task they created for themself;</li>
 *   <li>status: anyone who can read the task.</li>
 * </ul>
 * Tasks outside the actor's visibility are reported as not found so their existence is not leaked.
 */
@Service
public class TaskService {
    private final JdbcClient jdbc;
    private final AssociationAccess associations;
    private final ProjectAccess projects;
    private final ProjectService projectService;
    private final Clock clock;

    public TaskService(JdbcClient jdbc, AssociationAccess associations, ProjectAccess projects,
            ProjectService projectService, Clock clock) {
        this.jdbc = jdbc; this.associations = associations; this.projects = projects;
        this.projectService = projectService; this.clock = clock;
    }

    public List<TaskDtos.Task> list(UUID associationId, UUID projectId, AuthenticatedUser actor) {
        var ctx = context(associationId, projectId, actor);
        return query("WHERE t.project_id = ? ORDER BY t.due_at, t.created_at, t.id", projectId).stream()
                .filter(row -> ctx.canSee(row.assigneeUserId()))
                .map(ctx::toDto).toList();
    }

    public TaskDtos.Task detail(UUID associationId, UUID projectId, UUID taskId, AuthenticatedUser actor) {
        var ctx = context(associationId, projectId, actor);
        return ctx.toDto(visibleTask(ctx, projectId, taskId));
    }

    /** Own tasks across every project where the actor is still an active team member. */
    public List<TaskDtos.Task> mine(UUID associationId, AuthenticatedUser actor) {
        boolean admin = associations.requireMember(associationId, actor.id()) == AssociationRole.ADMIN;
        Set<UUID> managed = managerProjects(actor.id());
        return query("""
                JOIN project_memberships pm ON pm.project_id = t.project_id AND pm.user_id = t.assignee_user_id
                JOIN association_memberships am ON am.id = pm.association_membership_id
                    AND am.association_id = p.association_id AND am.user_id = pm.user_id
                WHERE p.association_id = ? AND t.assignee_user_id = ?
                ORDER BY t.due_at, t.created_at, t.id
                """, associationId, actor.id()).stream()
                .map(row -> row.toDto(admin || (row.createdByUserId().equals(actor.id())
                        && managed.contains(row.projectId())), true)).toList();
    }

    private Set<UUID> managerProjects(UUID userId) {
        return new HashSet<>(jdbc.sql("SELECT project_id FROM project_memberships WHERE user_id = ? AND project_role <> 'MEMBER'")
                .param(userId).query(UUID.class).list());
    }

    /** Team members the actor may assign tasks to: strict descendants, or every active member for ADMIN. */
    public List<TaskDtos.AssigneeOption> assigneeOptions(UUID associationId, UUID projectId, AuthenticatedUser actor) {
        var ctx = context(associationId, projectId, actor);
        return ctx.activeTeam.values().stream()
                .filter(m -> ctx.canAssignTo(m.userId()))
                .map(m -> new TaskDtos.AssigneeOption(m.userId(), m.displayName(), m.projectRole(), m.departmentName()))
                .toList();
    }

    @Transactional
    public TaskDtos.Task create(UUID associationId, UUID projectId, TaskDtos.CreateTask request, AuthenticatedUser actor) {
        lock(associationId, projectId);
        var ctx = context(associationId, projectId, actor);
        requireAssignable(ctx, request.assigneeUserId());
        requireFuture(request.dueAt());
        UUID id = UUID.randomUUID();
        Timestamp now = now();
        jdbc.sql("""
                INSERT INTO tasks (id, project_id, assignee_user_id, created_by_user_id, title, description,
                    due_at, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'TODO', ?, ?)
                """).params(id, projectId, request.assigneeUserId(), actor.id(), request.title().strip(),
                    blankToNull(request.description()), Timestamp.from(request.dueAt()), now, now).update();
        return ctx.toDto(visibleTask(ctx, projectId, id));
    }

    @Transactional
    public TaskDtos.Task edit(UUID associationId, UUID projectId, UUID taskId, TaskDtos.EditTask request,
            AuthenticatedUser actor) {
        lock(associationId, projectId);
        var ctx = context(associationId, projectId, actor);
        var task = visibleTask(ctx, projectId, taskId);
        requireManage(ctx, task);
        if (!request.assigneeUserId().equals(task.assigneeUserId())) requireAssignable(ctx, request.assigneeUserId());
        // An unchanged past deadline may stay; a new deadline must be in the future.
        if (!request.dueAt().equals(task.dueAt())) requireFuture(request.dueAt());
        jdbc.sql("""
                UPDATE tasks SET title = ?, description = ?, assignee_user_id = ?, due_at = ?, updated_at = ?
                WHERE id = ? AND project_id = ?
                """).params(request.title().strip(), blankToNull(request.description()), request.assigneeUserId(),
                    Timestamp.from(request.dueAt()), now(), taskId, projectId).update();
        return ctx.toDto(visibleTask(ctx, projectId, taskId));
    }

    @Transactional
    public TaskDtos.Task changeStatus(UUID associationId, UUID projectId, UUID taskId, TaskDtos.ChangeStatus request,
            AuthenticatedUser actor) {
        lock(associationId, projectId);
        var ctx = context(associationId, projectId, actor);
        var task = visibleTask(ctx, projectId, taskId);
        if (!ctx.canChangeStatus(task)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "TASK_STATUS_DENIED", "You cannot change this task's status.");
        }
        jdbc.sql("UPDATE tasks SET status = ?, updated_at = ? WHERE id = ? AND project_id = ?")
                .params(request.status().name(), now(), taskId, projectId).update();
        return ctx.toDto(visibleTask(ctx, projectId, taskId));
    }

    @Transactional
    public void delete(UUID associationId, UUID projectId, UUID taskId, AuthenticatedUser actor) {
        lock(associationId, projectId);
        var ctx = context(associationId, projectId, actor);
        requireManage(ctx, visibleTask(ctx, projectId, taskId));
        jdbc.sql("DELETE FROM tasks WHERE id = ? AND project_id = ?").params(taskId, projectId).update();
    }

    // ----- access context -----

    /** Requires association membership, project scope, and active team membership or ADMIN. */
    private Context context(UUID associationId, UUID projectId, AuthenticatedUser actor) {
        // ProjectService.team enforces requireDetailAccess (active team or association ADMIN).
        var team = projectService.team(associationId, projectId, actor);
        boolean admin = associations.requireMember(associationId, actor.id()) == AssociationRole.ADMIN;
        Map<UUID, ProjectDtos.TeamMember> active = new LinkedHashMap<>();
        team.stream().filter(ProjectDtos.TeamMember::active).forEach(m -> active.put(m.userId(), m));
        return new Context(associationId, projectId, actor.id(), admin, active);
    }

    private final class Context {
        final UUID associationId, projectId, actorId;
        final boolean admin;
        final Map<UUID, ProjectDtos.TeamMember> activeTeam;
        private final Map<UUID, Boolean> descendantCache = new HashMap<>();

        Context(UUID associationId, UUID projectId, UUID actorId, boolean admin, Map<UUID, ProjectDtos.TeamMember> activeTeam) {
            this.associationId = associationId; this.projectId = projectId; this.actorId = actorId;
            this.admin = admin; this.activeTeam = activeTeam;
        }

        /**
         * Raul's shared helper is the single source of reporting-branch truth. Former (removed) members
         * still count as below their former active managers, so those managers keep their open tasks.
         */
        boolean isBelowActor(UUID userId) {
            return descendantCache.computeIfAbsent(userId,
                    target -> projects.isStrictDescendantOrFormerMember(associationId, projectId, actorId, target));
        }
        /** PROJECT_MANAGER or DEPARTMENT_MANAGER in this project (active). */
        boolean actorIsManager() {
            var self = activeTeam.get(actorId);
            return self != null && !self.projectRole().equals("MEMBER");
        }
        /** Who may receive a task from the actor: subordinates, the actor themself if a manager, anyone for ADMIN. */
        boolean canAssignTo(UUID assigneeId) {
            return admin || isBelowActor(assigneeId) || (assigneeId.equals(actorId) && actorIsManager());
        }
        /**
         * Edit/reassign/delete: ADMIN, a manager above the assignee, or a manager on a task they created
         * for themself. A task a superior assigned to a manager stays read-only for that manager (status aside).
         */
        boolean canManage(Row task) {
            return admin || isBelowActor(task.assigneeUserId())
                    || (task.assigneeUserId().equals(actorId) && task.createdByUserId().equals(actorId) && actorIsManager());
        }
        boolean canSee(UUID assigneeId) { return admin || actorId.equals(assigneeId) || isBelowActor(assigneeId); }
        boolean canChangeStatus(Row task) { return canSee(task.assigneeUserId()); }
        TaskDtos.Task toDto(Row row) { return row.toDto(canManage(row), canChangeStatus(row)); }
    }

    private void requireManage(Context ctx, Row task) {
        if (!ctx.canManage(task)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "TASK_MANAGE_DENIED",
                    "Only a manager above the assignee or an association administrator can change this task.");
        }
    }

    private void requireAssignable(Context ctx, UUID assigneeId) {
        if (!ctx.activeTeam.containsKey(assigneeId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ASSIGNEE", "The assignee must be an active member of this project team.");
        }
        if (!ctx.canAssignTo(assigneeId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ASSIGNEE_OUT_OF_BRANCH",
                    "You can only assign tasks to members below you in the reporting structure.");
        }
    }

    private Row visibleTask(Context ctx, UUID projectId, UUID taskId) {
        return query("WHERE t.id = ? AND t.project_id = ?", taskId, projectId).stream()
                .filter(row -> ctx.canSee(row.assigneeUserId())).findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", "Task not found in this project."));
    }

    // ----- persistence -----

    private record Row(UUID id, UUID associationId, UUID projectId, String projectName, String title, String description,
            Instant dueAt, TaskDtos.Status status, UUID assigneeUserId, String assigneeName,
            UUID createdByUserId, String createdByName, Instant createdAt, Instant updatedAt) {
        TaskDtos.Task toDto(boolean canManage, boolean canChangeStatus) {
            return new TaskDtos.Task(id, associationId, projectId, projectName, title, description, dueAt, status,
                    assigneeUserId, assigneeName, createdByUserId, createdByName, createdAt, updatedAt,
                    canManage, canChangeStatus);
        }
    }

    private List<Row> query(String tail, Object... params) {
        return jdbc.sql("""
                SELECT t.id, p.association_id, t.project_id, p.name AS project_name, t.title, t.description,
                    t.due_at, t.status, t.assignee_user_id, a.display_name AS assignee_name,
                    t.created_by_user_id, c.display_name AS created_by_name, t.created_at, t.updated_at
                FROM tasks t
                JOIN projects p ON p.id = t.project_id
                JOIN app_users a ON a.id = t.assignee_user_id
                JOIN app_users c ON c.id = t.created_by_user_id
                """ + tail).params(params).query(TaskService::row).list();
    }

    private static Row row(ResultSet rs, int rowNum) throws SQLException {
        return new Row(rs.getObject("id", UUID.class), rs.getObject("association_id", UUID.class),
                rs.getObject("project_id", UUID.class), rs.getString("project_name"), rs.getString("title"),
                rs.getString("description"), rs.getTimestamp("due_at").toInstant(),
                TaskDtos.Status.valueOf(rs.getString("status")), rs.getObject("assignee_user_id", UUID.class),
                rs.getString("assignee_name"), rs.getObject("created_by_user_id", UUID.class),
                rs.getString("created_by_name"), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }

    /** Same lock order as MembershipService/ProjectService, so hierarchy changes cannot race task writes. */
    private void lock(UUID associationId, UUID projectId) {
        jdbc.sql("SELECT id FROM associations WHERE id = ? FOR UPDATE").param(associationId).query(UUID.class).optional()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ASSOCIATION_NOT_FOUND", "Association not found."));
        jdbc.sql("SELECT id FROM projects WHERE id = ? AND association_id = ? FOR UPDATE")
                .params(projectId, associationId).query(UUID.class).optional()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", "Project not found in this association."));
    }

    private void requireFuture(Instant dueAt) {
        if (!dueAt.isAfter(clock.instant())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DEADLINE", "Choose a future deadline.");
        }
    }

    private Timestamp now() { return Timestamp.from(clock.instant()); }
    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.strip(); }
}
