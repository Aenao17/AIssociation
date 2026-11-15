package ro.stucanii.aissociation.identity;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class IdentityRepository {
    private final JdbcClient jdbc;
    public IdentityRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    record Account(UUID id, String displayName, String email, String passwordHash, Instant createdAt) {
        AuthDtos.UserView view() { return new AuthDtos.UserView(id, displayName, email, createdAt); }
    }

    void insertAccount(Account user) {
        jdbc.sql("INSERT INTO app_users (id, display_name, email, password_hash, created_at) VALUES (?, ?, ?, ?, ?)")
                .params(user.id(), user.displayName(), user.email(), user.passwordHash(), Timestamp.from(user.createdAt()))
                .update();
    }

    Optional<Account> findByEmail(String email) {
        return jdbc.sql("SELECT id, display_name, email, password_hash, created_at FROM app_users WHERE email = ?")
                .param(email).query((rs, row) -> new Account(rs.getObject("id", UUID.class),
                        rs.getString("display_name"), rs.getString("email"), rs.getString("password_hash"),
                        rs.getTimestamp("created_at").toInstant())).optional();
    }

    void insertSession(UUID id, UUID userId, String tokenHash, Instant now, Instant expiry) {
        jdbc.sql("INSERT INTO auth_sessions (id, user_id, token_hash, created_at, expires_at) VALUES (?, ?, ?, ?, ?)")
                .params(id, userId, tokenHash, Timestamp.from(now), Timestamp.from(expiry)).update();
    }

    Optional<AuthenticatedUser> findSession(String tokenHash, Instant now) {
        return jdbc.sql("""
                SELECT u.id, u.display_name, u.email, s.id AS session_id
                FROM auth_sessions s JOIN app_users u ON u.id = s.user_id
                WHERE s.token_hash = ? AND s.expires_at > ?
                """).params(tokenHash, Timestamp.from(now)).query((rs, row) -> new AuthenticatedUser(
                        rs.getObject("id", UUID.class), rs.getString("display_name"), rs.getString("email"),
                        rs.getObject("session_id", UUID.class))).optional();
    }

    void deleteSession(UUID sessionId) {
        jdbc.sql("DELETE FROM auth_sessions WHERE id = ?").param(sessionId).update();
    }

    List<AuthDtos.MembershipView> memberships(UUID userId) {
        return jdbc.sql("""
                SELECT a.id, a.name, a.slug, m.role
                FROM association_memberships m JOIN associations a ON a.id = m.association_id
                WHERE m.user_id = ? ORDER BY a.name, a.id
                """).param(userId).query((rs, row) -> new AuthDtos.MembershipView(
                        rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("slug"),
                        rs.getString("role"))).list();
    }
}
