package ro.stucanii.aissociation.associations;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import ro.stucanii.aissociation.identity.ApiException;

/** Reusable server-side association checks. Supply the authenticated user's ID. */
@Component
public class AssociationAccess {
    private final JdbcClient jdbc;
    public AssociationAccess(JdbcClient jdbc) { this.jdbc = jdbc; }

    public AssociationRole requireMember(UUID associationId, UUID userId) {
        return jdbc.sql("SELECT role FROM association_memberships WHERE association_id = ? AND user_id = ?")
                .params(associationId, userId).query(String.class).optional()
                .map(AssociationRole::valueOf).orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN,
                        "MEMBERSHIP_REQUIRED", "You are not a member of this association."));
    }

    public void requireAdmin(UUID associationId, UUID userId) {
        if (requireMember(associationId, userId) != AssociationRole.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ASSOCIATION_ADMIN_REQUIRED",
                    "Association administrator access is required.");
        }
    }
}
