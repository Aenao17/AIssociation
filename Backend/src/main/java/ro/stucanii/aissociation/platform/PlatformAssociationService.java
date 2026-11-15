package ro.stucanii.aissociation.platform;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
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
public class PlatformAssociationService {
    private final JdbcClient jdbc;
    private final PlatformAdminAccess access;
    private final Clock clock;

    public PlatformAssociationService(JdbcClient jdbc, PlatformAdminAccess access, Clock clock) {
        this.jdbc = jdbc;
        this.access = access;
        this.clock = clock;
    }

    public List<AssociationDtos.AssociationView> list(AuthenticatedUser user) {
        access.requirePlatformAdmin(user);
        return jdbc.sql("SELECT id, name, slug, created_at FROM associations ORDER BY name, id")
                .query((rs, row) -> new AssociationDtos.AssociationView(rs.getObject("id", UUID.class),
                        rs.getString("name"), rs.getString("slug"), rs.getTimestamp("created_at").toInstant())).list();
    }

    @Transactional
    public AssociationDtos.AssociationView create(AuthenticatedUser user, AssociationDtos.CreateAssociation request) {
        access.requirePlatformAdmin(user);
        UUID adminId = jdbc.sql("SELECT id FROM app_users WHERE email = ?").param(request.initialAdminEmail())
                .query(UUID.class).optional().orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "INITIAL_ADMIN_NOT_FOUND", "The initial administrator must register an account first."));
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        try {
            jdbc.sql("INSERT INTO associations (id, name, slug, created_at) VALUES (?, ?, ?, ?)")
                    .params(id, request.name(), request.slug(), Timestamp.from(now)).update();
        } catch (DuplicateKeyException ex) {
            throw new ApiException(HttpStatus.CONFLICT, "SLUG_EXISTS", "An association with this URL slug already exists.");
        }
        jdbc.sql("INSERT INTO association_memberships (id, association_id, user_id, role, created_at) VALUES (?, ?, ?, 'ADMIN', ?)")
                .params(UUID.randomUUID(), id, adminId, Timestamp.from(now)).update();
        return new AssociationDtos.AssociationView(id, request.name(), request.slug(), now);
    }
}
