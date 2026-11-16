package ro.stucanii.aissociation.projects;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.stucanii.aissociation.associations.AssociationAccess;
import ro.stucanii.aissociation.associations.AssociationRole;
import ro.stucanii.aissociation.identity.ApiException;
import ro.stucanii.aissociation.identity.AuthenticatedUser;

@Service
public class ProjectService {
    private final JdbcClient jdbc;
    private final AssociationAccess associations;
    private final ProjectAccess access;
    private final Clock clock;

    public ProjectService(JdbcClient jdbc, AssociationAccess associations, ProjectAccess access, Clock clock) {
        this.jdbc = jdbc; this.associations = associations; this.access = access; this.clock = clock;
    }

    public List<ProjectDtos.Summary> list(UUID associationId, AuthenticatedUser actor) {
        associations.requireMember(associationId, actor.id());
        return jdbc.sql("SELECT id FROM projects WHERE association_id = ? ORDER BY created_at DESC, id")
                .param(associationId).query(UUID.class).list().stream().map(id -> summary(associationId, id, actor)).toList();
    }

    @Transactional
    public ProjectDtos.Overview create(UUID associationId, ProjectDtos.CreateProject request, AuthenticatedUser actor) {
        lockAssociation(associationId);
        associations.requireAdmin(associationId, actor.id());
        // Resolve every appointment before inserting any project data.
        Map<UUID, UUID> managers = new LinkedHashMap<>();
        request.managerUserIds().forEach(user -> managers.put(user, currentMembership(associationId, user)));
        UUID projectId = UUID.randomUUID();
        jdbc.sql("INSERT INTO projects (id, association_id, name, description, created_at) VALUES (?, ?, ?, ?, ?)")
                .params(projectId, associationId, request.name().strip(), request.description().strip(), now()).update();
        managers.forEach((user, membership) -> insertManager(projectId, user, membership));
        return overview(associationId, projectId, actor);
    }

    public ProjectDtos.Overview overview(UUID associationId, UUID projectId, AuthenticatedUser actor) {
        access.requireScope(associationId, projectId, actor.id());
        var call = jdbc.sql("SELECT recruitment_description, recruitment_deadline FROM projects WHERE id = ?")
                .param(projectId).query((rs, row) -> new Call(rs.getString(1), instant(rs.getTimestamp(2)))).single();
        return new ProjectDtos.Overview(summary(associationId, projectId, actor), call.description(), call.deadline(),
                positions(projectId), associations.requireMember(associationId, actor.id()) == AssociationRole.ADMIN);
    }

    public ProjectDtos.Overview detail(UUID associationId, UUID projectId, AuthenticatedUser actor) {
        access.requireDetailAccess(associationId, projectId, actor.id());
        return overview(associationId, projectId, actor);
    }

    @Transactional
    public ProjectDtos.Overview edit(UUID associationId, UUID projectId, ProjectDtos.EditProject request, AuthenticatedUser actor) {
        lock(associationId, projectId);
        access.requireManager(associationId, projectId, actor.id());
        jdbc.sql("UPDATE projects SET name = ?, description = ? WHERE id = ?")
                .params(request.name().strip(), request.description().strip(), projectId).update();
        return overview(associationId, projectId, actor);
    }

    @Transactional
    public ProjectDtos.Overview configureRecruitment(UUID associationId, UUID projectId,
            ProjectDtos.ConfigureRecruitment request, AuthenticatedUser actor) {
        lock(associationId, projectId);
        access.requireManager(associationId, projectId, actor.id());
        if (!request.deadline().isAfter(clock.instant())) throw invalid("INVALID_DEADLINE", "Choose a future recruitment deadline.");
        jdbc.sql("UPDATE projects SET recruitment_description = ?, recruitment_deadline = ? WHERE id = ?")
                .params(request.description().strip(), Timestamp.from(request.deadline()), projectId).update();
        return overview(associationId, projectId, actor);
    }

    @Transactional
    public ProjectDtos.TeamMember appointManager(UUID associationId, UUID projectId, UUID userId, AuthenticatedUser actor) {
        lock(associationId, projectId);
        associations.requireAdmin(associationId, actor.id());
        UUID membership = currentMembership(associationId, userId);
        var existing = access.team(projectId).stream().filter(m -> m.userId().equals(userId)).findFirst();
        if (existing.isPresent()) {
            jdbc.sql("UPDATE project_memberships SET project_role = 'PROJECT_MANAGER', reporting_parent_id = NULL, association_membership_id = ? WHERE id = ?")
                    .params(membership, existing.get().id()).update();
        } else insertManager(projectId, userId, membership);
        return access.activeMember(projectId, userId).orElseThrow();
    }

    public List<ProjectDtos.MemberOption> memberOptions(UUID associationId, UUID projectId, AuthenticatedUser actor) {
        var role = access.requireScope(associationId, projectId, actor.id());
        if (role != AssociationRole.ADMIN) access.requireManager(associationId, projectId, actor.id());
        return jdbc.sql("""
                SELECT u.id, u.display_name FROM association_memberships m JOIN app_users u ON u.id = m.user_id
                WHERE m.association_id = ? ORDER BY u.display_name, u.id
                """).param(associationId).query((rs, row) -> new ProjectDtos.MemberOption(
                    rs.getObject(1, UUID.class), rs.getString(2))).list();
    }

    @Transactional
    public ProjectDtos.Position savePosition(UUID associationId, UUID projectId, UUID positionId,
            ProjectDtos.SavePosition request, AuthenticatedUser actor) {
        lock(associationId, projectId);
        access.requireManager(associationId, projectId, actor.id());
        String departmentName = request.departmentName().strip();
        UUID department = jdbc.sql("SELECT id FROM project_departments WHERE project_id = ? AND name = ?")
                .params(projectId, departmentName).query(UUID.class).optional().orElse(null);
        ProjectDtos.Position previous = positionId == null ? null : position(projectId, positionId);
        if (previous != null && !previous.departmentName().equals(departmentName)
                && jdbc.sql("SELECT COUNT(*) FROM project_applications WHERE project_id = ? AND position_id = ?")
                    .params(projectId, positionId).query(Long.class).single() > 0) {
            throw conflict("POSITION_IN_USE", "A position with applications cannot move to another department.");
        }
        if (department == null) {
            department = UUID.randomUUID();
            jdbc.sql("INSERT INTO project_departments (id, project_id, name) VALUES (?, ?, ?)")
                    .params(department, projectId, departmentName).update();
        }
        UUID id = positionId == null ? UUID.randomUUID() : positionId;
        try {
            if (previous == null) {
                jdbc.sql("INSERT INTO recruitment_positions (id, project_id, department_id, role_label) VALUES (?, ?, ?, ?)")
                        .params(id, projectId, department, request.roleLabel().strip()).update();
            } else {
                jdbc.sql("UPDATE recruitment_positions SET department_id = ?, role_label = ? WHERE id = ? AND project_id = ?")
                        .params(department, request.roleLabel().strip(), id, projectId).update();
            }
        } catch (DuplicateKeyException ex) {
            throw conflict("POSITION_EXISTS", "This role and department position already exists.");
        }
        return position(projectId, id);
    }

    @Transactional
    public ProjectDtos.Application apply(UUID associationId, UUID projectId, ProjectDtos.Apply request, AuthenticatedUser actor) {
        lock(associationId, projectId);
        access.requireScope(associationId, projectId, actor.id());
        UUID membership = currentMembership(associationId, actor.id());
        var deadline = jdbc.sql("SELECT recruitment_deadline FROM projects WHERE id = ?").param(projectId)
                .query((rs, row) -> instant(rs.getTimestamp(1))).optional().orElse(null);
        if (deadline == null || !clock.instant().isBefore(deadline)) throw conflict("RECRUITMENT_CLOSED", "Recruitment is closed.");
        position(projectId, request.positionId());
        if (access.team(projectId).stream().anyMatch(m -> m.userId().equals(actor.id()))) {
            throw conflict("ALREADY_ON_TEAM", "This account already has a current or retained project assignment.");
        }
        UUID id = UUID.randomUUID();
        try {
            jdbc.sql("""
                    INSERT INTO project_applications (id, project_id, position_id, user_id, association_membership_id,
                        motivation, status, created_at) VALUES (?, ?, ?, ?, ?, ?, 'PENDING', ?)
                    """).params(id, projectId, request.positionId(), actor.id(), membership, request.motivation().strip(), now()).update();
        } catch (DuplicateKeyException ex) {
            throw conflict("APPLICATION_EXISTS", "You have already applied to this position. Reapplication is not enabled.");
        }
        return application(projectId, id);
    }

    public List<ProjectDtos.Application> applications(UUID associationId, UUID projectId, boolean mine, AuthenticatedUser actor) {
        access.requireScope(associationId, projectId, actor.id());
        if (!mine) access.requireManager(associationId, projectId, actor.id());
        var query = jdbc.sql(APPLICATION_SELECT + " WHERE a.project_id = ?" + (mine ? " AND a.user_id = ?" : "") + " ORDER BY a.created_at, a.id")
                .param(projectId);
        if (mine) query.param(actor.id());
        return query.query(this::mapApplication).list();
    }

    @Transactional
    public ProjectDtos.Application accept(UUID associationId, UUID projectId, UUID applicationId,
            ProjectDtos.Supervisor request, AuthenticatedUser actor) {
        lock(associationId, projectId);
        access.requireManager(associationId, projectId, actor.id());
        var application = application(projectId, applicationId);
        if (!application.status().equals("PENDING")) throw conflict("APPLICATION_NOT_PENDING", "Only pending applications can be accepted.");
        UUID originalMembership = jdbc.sql("SELECT association_membership_id FROM project_applications WHERE id = ?")
                .param(applicationId).query((rs, row) -> rs.getObject(1, UUID.class)).optional().orElse(null);
        UUID membership = currentMembership(associationId, application.userId());
        if (!membership.equals(originalMembership)) throw conflict("APPLICANT_MEMBERSHIP_CHANGED", "This application's original association membership is no longer active.");
        if (access.team(projectId).stream().anyMatch(m -> m.userId().equals(application.userId()))) {
            throw conflict("ALREADY_ON_TEAM", "This account already has a project assignment.");
        }
        var position = position(projectId, application.positionId());
        var parent = validSupervisor(projectId, request.reportingParentMembershipId(), position.departmentId());
        jdbc.sql("""
                INSERT INTO project_memberships (id, project_id, user_id, association_membership_id,
                    project_role, department_id, position_id, reporting_parent_id, created_at)
                VALUES (?, ?, ?, ?, 'MEMBER', ?, ?, ?, ?)
                """).params(UUID.randomUUID(), projectId, application.userId(), membership,
                    position.departmentId(), position.id(), parent.id(), now()).update();
        jdbc.sql("UPDATE project_applications SET status = 'ACCEPTED', selected_user_id = user_id, reviewed_at = ? WHERE id = ?")
                .params(now(), applicationId).update();
        jdbc.sql("""
                UPDATE project_applications SET status = 'CLOSED_ACCEPTED_ELSEWHERE', reviewed_at = ?
                WHERE project_id = ? AND user_id = ? AND status = 'PENDING' AND id <> ?
                """).params(now(), projectId, application.userId(), applicationId).update();
        return application(projectId, applicationId);
    }

    @Transactional
    public ProjectDtos.Application reject(UUID associationId, UUID projectId, UUID applicationId, AuthenticatedUser actor) {
        lock(associationId, projectId);
        access.requireManager(associationId, projectId, actor.id());
        if (!application(projectId, applicationId).status().equals("PENDING")) {
            throw conflict("APPLICATION_NOT_PENDING", "Only pending applications can be rejected.");
        }
        jdbc.sql("UPDATE project_applications SET status = 'REJECTED', reviewed_at = ? WHERE id = ?")
                .params(now(), applicationId).update();
        return application(projectId, applicationId);
    }

    public List<ProjectDtos.TeamMember> team(UUID associationId, UUID projectId, AuthenticatedUser actor) {
        access.requireDetailAccess(associationId, projectId, actor.id());
        return access.team(projectId);
    }

    @Transactional
    public ProjectDtos.TeamMember appointDepartmentManager(UUID associationId, UUID projectId, UUID memberId, AuthenticatedUser actor) {
        lock(associationId, projectId);
        var manager = access.requireManager(associationId, projectId, actor.id());
        var member = activeTeamMember(projectId, memberId);
        if (member.projectRole().equals("PROJECT_MANAGER")) throw invalid("INVALID_APPOINTMENT", "A root project manager cannot be appointed as a department manager.");
        jdbc.sql("UPDATE project_memberships SET project_role = 'DEPARTMENT_MANAGER', reporting_parent_id = ? WHERE id = ?")
                .params(manager.id(), memberId).update();
        return activeTeamMember(projectId, memberId);
    }

    @Transactional
    public ProjectDtos.TeamMember changeReporting(UUID associationId, UUID projectId, UUID memberId,
            ProjectDtos.Supervisor request, AuthenticatedUser actor) {
        lock(associationId, projectId);
        access.requireManager(associationId, projectId, actor.id());
        var member = activeTeamMember(projectId, memberId);
        if (member.projectRole().equals("PROJECT_MANAGER")) throw invalid("ROOT_MANAGER", "Root project managers do not have a reporting parent.");
        var parent = validSupervisor(projectId, request.reportingParentMembershipId(), member.departmentId());
        if (member.projectRole().equals("DEPARTMENT_MANAGER") && !parent.projectRole().equals("PROJECT_MANAGER")) {
            throw invalid("INVALID_SUPERVISOR", "Department managers report directly to a project manager.");
        }
        Set<UUID> visited = new HashSet<>();
        UUID cursor = parent.id();
        Map<UUID, ProjectDtos.TeamMember> graph = new HashMap<>();
        access.team(projectId).forEach(m -> graph.put(m.id(), m));
        while (cursor != null) {
            if (cursor.equals(memberId) || !visited.add(cursor)) throw invalid("REPORTING_CYCLE", "A reporting relationship cannot form a cycle.");
            var node = graph.get(cursor);
            cursor = node == null ? null : node.reportingParentMembershipId();
        }
        jdbc.sql("UPDATE project_memberships SET reporting_parent_id = ? WHERE id = ? AND project_id = ?")
                .params(parent.id(), memberId, projectId).update();
        return activeTeamMember(projectId, memberId);
    }

    private ProjectDtos.TeamMember validSupervisor(UUID projectId, UUID memberId, UUID departmentId) {
        var parent = activeTeamMember(projectId, memberId);
        if (parent.projectRole().equals("MEMBER") || (parent.projectRole().equals("DEPARTMENT_MANAGER")
                && !Objects.equals(parent.departmentId(), departmentId))) {
            throw invalid("INVALID_SUPERVISOR", "Choose an active project manager or a manager in the position's department.");
        }
        return parent;
    }

    private ProjectDtos.TeamMember activeTeamMember(UUID projectId, UUID memberId) {
        return access.team(projectId).stream().filter(m -> m.id().equals(memberId) && m.active()).findFirst()
                .orElseThrow(() -> invalid("INVALID_SUPERVISOR_OR_MEMBER", "Choose an active team member from this project."));
    }

    private ProjectDtos.Summary summary(UUID associationId, UUID projectId, AuthenticatedUser actor) {
        var membership = access.activeMember(projectId, actor.id());
        boolean manager = membership.map(m -> m.projectRole().equals("PROJECT_MANAGER")).orElse(false);
        boolean open = membership.isPresent() || associations.requireMember(associationId, actor.id()) == AssociationRole.ADMIN;
        return jdbc.sql("SELECT id, association_id, name, description, recruitment_deadline FROM projects WHERE id = ? AND association_id = ?")
                .params(projectId, associationId).query((rs, row) -> new ProjectDtos.Summary(rs.getObject(1, UUID.class),
                    rs.getObject(2, UUID.class), rs.getString(3), rs.getString(4), instant(rs.getTimestamp(5)), manager, open)).single();
    }

    private List<ProjectDtos.Position> positions(UUID projectId) {
        return jdbc.sql("""
                SELECT r.id, r.department_id, d.name, r.role_label FROM recruitment_positions r
                JOIN project_departments d ON d.id = r.department_id AND d.project_id = r.project_id
                WHERE r.project_id = ? ORDER BY d.name, r.role_label, r.id
                """).param(projectId).query((rs, row) -> new ProjectDtos.Position(rs.getObject(1, UUID.class),
                    rs.getObject(2, UUID.class), rs.getString(3), rs.getString(4))).list();
    }

    private ProjectDtos.Position position(UUID projectId, UUID id) {
        return positions(projectId).stream().filter(p -> p.id().equals(id)).findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "POSITION_NOT_FOUND", "Position not found in this project."));
    }

    private static final String APPLICATION_SELECT = """
            SELECT a.*, u.display_name, r.role_label, d.name AS department_name FROM project_applications a
            JOIN app_users u ON u.id = a.user_id
            JOIN recruitment_positions r ON r.id = a.position_id AND r.project_id = a.project_id
            JOIN project_departments d ON d.id = r.department_id AND d.project_id = r.project_id
            """;

    private ProjectDtos.Application application(UUID projectId, UUID id) {
        return jdbc.sql(APPLICATION_SELECT + " WHERE a.project_id = ? AND a.id = ?").params(projectId, id)
                .query(this::mapApplication).optional().orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                    "APPLICATION_NOT_FOUND", "Application not found in this project."));
    }

    private ProjectDtos.Application mapApplication(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new ProjectDtos.Application(rs.getObject("id", UUID.class), rs.getObject("position_id", UUID.class),
                rs.getString("role_label"), rs.getString("department_name"), rs.getObject("user_id", UUID.class),
                rs.getString("display_name"), rs.getString("motivation"), rs.getString("status"),
                instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("reviewed_at")));
    }

    private void insertManager(UUID projectId, UUID userId, UUID membershipId) {
        jdbc.sql("""
                INSERT INTO project_memberships (id, project_id, user_id, association_membership_id, project_role, created_at)
                VALUES (?, ?, ?, ?, 'PROJECT_MANAGER', ?)
                """).params(UUID.randomUUID(), projectId, userId, membershipId, now()).update();
    }

    private UUID currentMembership(UUID associationId, UUID userId) {
        return jdbc.sql("SELECT id FROM association_memberships WHERE association_id = ? AND user_id = ?")
                .params(associationId, userId).query(UUID.class).optional()
                .orElseThrow(() -> invalid("ASSOCIATION_MEMBER_REQUIRED", "The selected account must currently belong to this association."));
    }

    private void lockAssociation(UUID associationId) {
        jdbc.sql("SELECT id FROM associations WHERE id = ? FOR UPDATE").param(associationId).query(UUID.class).optional()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ASSOCIATION_NOT_FOUND", "Association not found."));
    }

    private void lock(UUID associationId, UUID projectId) {
        // Match MembershipService's parent lock first: no removal/role-change race.
        lockAssociation(associationId);
        jdbc.sql("SELECT id FROM projects WHERE id = ? AND association_id = ? FOR UPDATE")
                .params(projectId, associationId).query(UUID.class).optional()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", "Project not found in this association."));
    }

    private Timestamp now() { return Timestamp.from(clock.instant()); }
    private static Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }
    private record Call(String description, Instant deadline) {}
    private ApiException conflict(String code, String message) { return new ApiException(HttpStatus.CONFLICT, code, message); }
    private ApiException invalid(String code, String message) { return new ApiException(HttpStatus.BAD_REQUEST, code, message); }
}
