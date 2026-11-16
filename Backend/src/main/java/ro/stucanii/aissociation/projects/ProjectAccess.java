package ro.stucanii.aissociation.projects;

import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import ro.stucanii.aissociation.associations.AssociationAccess;
import ro.stucanii.aissociation.associations.AssociationRole;
import ro.stucanii.aissociation.identity.ApiException;

/** Shared live project/reporting checks for tasks, events, rosters and audiences. */
@Component
public class ProjectAccess {
    private final JdbcClient jdbc;
    private final AssociationAccess associations;

    public ProjectAccess(JdbcClient jdbc, AssociationAccess associations) {
        this.jdbc = jdbc; this.associations = associations;
    }

    public AssociationRole requireScope(UUID associationId, UUID projectId, UUID actorId) {
        var role = associations.requireMember(associationId, actorId);
        if (jdbc.sql("SELECT COUNT(*) FROM projects WHERE id = ? AND association_id = ?")
                .params(projectId, associationId).query(Long.class).single() == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", "Project not found in this association.");
        }
        return role;
    }

    Optional<ProjectDtos.TeamMember> activeMember(UUID projectId, UUID userId) {
        return team(projectId).stream().filter(m -> m.userId().equals(userId) && m.active()).findFirst();
    }

    public ProjectDtos.TeamMember requireManager(UUID associationId, UUID projectId, UUID actorId) {
        requireScope(associationId, projectId, actorId);
        return activeMember(projectId, actorId).filter(m -> m.projectRole().equals("PROJECT_MANAGER"))
                .orElseThrow(() -> denied("PROJECT_MANAGER_REQUIRED", "Project manager access is required."));
    }

    public ProjectDtos.TeamMember requireTeamMember(UUID associationId, UUID projectId, UUID actorId) {
        requireScope(associationId, projectId, actorId);
        return activeMember(projectId, actorId)
                .orElseThrow(() -> denied("PROJECT_TEAM_REQUIRED", "Project team membership is required."));
    }

    public void requireDetailAccess(UUID associationId, UUID projectId, UUID actorId) {
        if (requireScope(associationId, projectId, actorId) != AssociationRole.ADMIN
                && activeMember(projectId, actorId).isEmpty()) {
            throw denied("PROJECT_TEAM_REQUIRED", "Project team membership is required.");
        }
    }

    /** Strict ancestry: self, peers, inactive intermediates and other projects are false. */
    public boolean isStrictDescendant(UUID associationId, UUID projectId, UUID managerUserId, UUID targetUserId) {
        return descends(associationId, projectId, managerUserId, targetUserId, false);
    }

    /**
     * Same as isStrictDescendant, but the target may also be a former (inactive) member: ancestry then
     * starts from their retained reporting row and still passes only through active managers.
     * Used by tasks so a removed member's former managers keep their open tasks.
     */
    public boolean isStrictDescendantOrFormerMember(UUID associationId, UUID projectId, UUID managerUserId, UUID targetUserId) {
        return descends(associationId, projectId, managerUserId, targetUserId, true);
    }

    private boolean descends(UUID associationId, UUID projectId, UUID managerUserId, UUID targetUserId, boolean allowInactiveTarget) {
        requireScope(associationId, projectId, managerUserId);
        var team = team(projectId);
        Map<UUID, ProjectDtos.TeamMember> members = new HashMap<>();
        team.stream().filter(ProjectDtos.TeamMember::active).forEach(m -> members.put(m.id(), m));
        var manager = members.values().stream().filter(m -> m.userId().equals(managerUserId)).findFirst();
        var target = (allowInactiveTarget ? team.stream() : members.values().stream())
                .filter(m -> m.userId().equals(targetUserId)).findFirst();
        if (manager.isEmpty() || target.isEmpty() || managerUserId.equals(targetUserId)
                || manager.get().projectRole().equals("MEMBER")) return false;
        UUID cursor = target.get().reportingParentMembershipId();
        Set<UUID> visited = new HashSet<>();
        while (cursor != null && visited.add(cursor)) {
            var node = members.get(cursor);
            if (node == null) return false;
            if (cursor.equals(manager.get().id())) return true;
            cursor = node.reportingParentMembershipId();
        }
        return false;
    }

    public List<UUID> projectAudienceUserIds(UUID associationId, UUID projectId, UUID actorId) {
        requireDetailAccess(associationId, projectId, actorId);
        return team(projectId).stream().filter(ProjectDtos.TeamMember::active).map(ProjectDtos.TeamMember::userId).toList();
    }

    /** Department chat includes the department and active project managers, never task privileges. */
    public List<UUID> departmentAudienceUserIds(UUID associationId, UUID projectId, UUID departmentId, UUID actorId) {
        requireDetailAccess(associationId, projectId, actorId);
        if (jdbc.sql("SELECT COUNT(*) FROM project_departments WHERE project_id = ? AND id = ?")
                .params(projectId, departmentId).query(Long.class).single() == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "DEPARTMENT_NOT_FOUND", "Department not found in this project.");
        }
        var members = team(projectId).stream().filter(ProjectDtos.TeamMember::active).toList();
        boolean admin = associations.requireMember(associationId, actorId) == AssociationRole.ADMIN;
        if (!admin && members.stream().noneMatch(m -> m.userId().equals(actorId)
                && (m.projectRole().equals("PROJECT_MANAGER") || departmentId.equals(m.departmentId())))) {
            throw denied("DEPARTMENT_ACCESS_REQUIRED", "Department membership is required.");
        }
        return members.stream().filter(m -> departmentId.equals(m.departmentId()) || m.projectRole().equals("PROJECT_MANAGER"))
                .map(ProjectDtos.TeamMember::userId).toList();
    }

    // Package-private raw query: controllers and other modules use authorized methods above.
    List<ProjectDtos.TeamMember> team(UUID projectId) {
        return jdbc.sql("""
                SELECT t.*, u.display_name, d.name AS department_name, r.role_label,
                    CASE WHEN am.id IS NOT NULL THEN TRUE ELSE FALSE END AS active
                FROM project_memberships t JOIN projects p ON p.id = t.project_id
                JOIN app_users u ON u.id = t.user_id
                LEFT JOIN association_memberships am ON am.id = t.association_membership_id
                    AND am.association_id = p.association_id AND am.user_id = t.user_id
                LEFT JOIN project_departments d ON d.id = t.department_id AND d.project_id = t.project_id
                LEFT JOIN recruitment_positions r ON r.id = t.position_id AND r.project_id = t.project_id
                WHERE t.project_id = ? ORDER BY t.created_at, t.id
                """).param(projectId).query((rs, row) -> new ProjectDtos.TeamMember(
                    rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class), rs.getString("display_name"),
                    rs.getString("project_role"), rs.getObject("department_id", UUID.class), rs.getString("department_name"),
                    rs.getObject("position_id", UUID.class), rs.getString("role_label"),
                    rs.getObject("reporting_parent_id", UUID.class), rs.getBoolean("active"))).list();
    }

    private ApiException denied(String code, String message) {
        return new ApiException(HttpStatus.FORBIDDEN, code, message);
    }
}
