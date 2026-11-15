package ro.stucanii.aissociation.associations;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.stucanii.aissociation.identity.ApiException;
import ro.stucanii.aissociation.identity.AuthenticatedUser;

@Service
public class MembershipService {
    private static final String MEMBER_SELECT = """
            SELECT m.id, m.user_id, u.display_name, u.email, m.role, m.created_at
            FROM association_memberships m JOIN app_users u ON u.id = m.user_id
            """;
    private final JdbcClient jdbc;
    private final AssociationAccess access;
    private final Clock clock;

    public MembershipService(JdbcClient jdbc, AssociationAccess access, Clock clock) {
        this.jdbc = jdbc; this.access = access; this.clock = clock;
    }

    public MembershipDtos.Workspace workspace(String slug, AuthenticatedUser user) {
        var association = jdbc.sql("SELECT id, name, slug FROM associations WHERE slug = ?").param(slug)
                .query((rs, row) -> new MembershipDtos.Workspace(rs.getObject("id", UUID.class),
                        rs.getString("name"), rs.getString("slug"), null)).optional()
                .orElseThrow(() -> missing("ASSOCIATION_NOT_FOUND", "Association not found."));
        var role = access.requireMember(association.associationId(), user.id());
        return new MembershipDtos.Workspace(association.associationId(), association.name(), association.slug(), role);
    }

    public List<MembershipDtos.Member> list(UUID associationId, AuthenticatedUser user) {
        access.requireAdmin(associationId, user.id());
        return jdbc.sql(MEMBER_SELECT + " WHERE m.association_id = ? ORDER BY u.display_name, m.id")
                .param(associationId).query(this::mapMember).list();
    }

    @Transactional
    public MembershipDtos.Member add(UUID associationId, MembershipDtos.AddMember request, AuthenticatedUser user) {
        lockAssociation(associationId);
        access.requireAdmin(associationId, user.id());
        UUID targetUser = jdbc.sql("SELECT id FROM app_users WHERE email = ?").param(request.email())
                .query(UUID.class).optional().orElseThrow(() -> missing("ACCOUNT_NOT_FOUND",
                        "Ask this person to register an account first."));
        UUID id = UUID.randomUUID();
        try {
            jdbc.sql("INSERT INTO association_memberships (id, association_id, user_id, role, created_at) VALUES (?, ?, ?, ?, ?)")
                    .params(id, associationId, targetUser, request.role().name(), Timestamp.from(clock.instant())).update();
        } catch (DuplicateKeyException ex) {
            throw new ApiException(HttpStatus.CONFLICT, "MEMBER_EXISTS", "This account is already a member of the association.");
        }
        return member(associationId, id);
    }

    @Transactional
    public MembershipDtos.Member changeRole(UUID associationId, UUID membershipId,
            MembershipDtos.ChangeRole request, AuthenticatedUser user) {
        lockAssociation(associationId);
        access.requireAdmin(associationId, user.id());
        var target = member(associationId, membershipId);
        protectLastAdmin(associationId, target.role(), request.role() != AssociationRole.ADMIN);
        jdbc.sql("UPDATE association_memberships SET role = ? WHERE id = ? AND association_id = ?")
                .params(request.role().name(), membershipId, associationId).update();
        return member(associationId, membershipId);
    }

    @Transactional
    public void remove(UUID associationId, UUID membershipId, AuthenticatedUser user) {
        lockAssociation(associationId);
        access.requireAdmin(associationId, user.id());
        var target = member(associationId, membershipId);
        protectLastAdmin(associationId, target.role(), true);
        jdbc.sql("DELETE FROM association_memberships WHERE id = ? AND association_id = ?")
                .params(membershipId, associationId).update();
    }

    private void protectLastAdmin(UUID associationId, AssociationRole currentRole, boolean removingAdmin) {
        if (currentRole == AssociationRole.ADMIN && removingAdmin) {
            long count = jdbc.sql("SELECT COUNT(*) FROM association_memberships WHERE association_id = ? AND role = 'ADMIN'")
                    .param(associationId).query(Long.class).single();
            if (count <= 1) throw new ApiException(HttpStatus.CONFLICT, "LAST_ADMIN_REQUIRED",
                    "Keep at least one administrator. Appoint another administrator first.");
        }
    }

    private void lockAssociation(UUID associationId) {
        // All membership writes share this lock. Recheck the actor's role after acquiring it.
        jdbc.sql("SELECT id FROM associations WHERE id = ? FOR UPDATE").param(associationId)
                .query(UUID.class).optional().orElseThrow(() -> missing("ASSOCIATION_NOT_FOUND", "Association not found."));
    }

    private MembershipDtos.Member member(UUID associationId, UUID id) {
        return jdbc.sql(MEMBER_SELECT + " WHERE m.association_id = ? AND m.id = ?")
                .params(associationId, id).query(this::mapMember).optional()
                .orElseThrow(() -> missing("MEMBER_NOT_FOUND", "Membership not found in this association."));
    }

    private MembershipDtos.Member mapMember(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new MembershipDtos.Member(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class),
                rs.getString("display_name"), rs.getString("email"), AssociationRole.valueOf(rs.getString("role")),
                rs.getTimestamp("created_at").toInstant());
    }

    private ApiException missing(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }
}
