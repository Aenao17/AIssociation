package ro.stucanii.aissociation.messaging;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.stucanii.aissociation.associations.AssociationAccess;
import ro.stucanii.aissociation.identity.ApiException;
import ro.stucanii.aissociation.identity.AuthenticatedUser;
import ro.stucanii.aissociation.messaging.MessagingDtos.Type;
import ro.stucanii.aissociation.projects.ProjectAccess;

/**
 * FR-15 messaging backend. Every operation re-checks access from live data:
 * <ul>
 *   <li>ASSOCIATION: current association member;</li>
 *   <li>PROJECT: in {@link ProjectAccess#projectAudienceUserIds} (active project team);</li>
 *   <li>DEPARTMENT: in {@link ProjectAccess#departmentAudienceUserIds} (department + project managers);</li>
 *   <li>GROUP / DIRECT: explicit member whose association membership is still the live one.</li>
 * </ul>
 * Conversations the actor cannot access are reported as not found. Chat access never grants task access.
 */
@Service
public class MessagingService {
    static final int DEFAULT_PAGE = 50;
    static final int MAX_PAGE = 200;

    private final JdbcClient jdbc;
    private final AssociationAccess associations;
    private final ProjectAccess projects;
    private final Clock clock;

    public MessagingService(JdbcClient jdbc, AssociationAccess associations, ProjectAccess projects, Clock clock) {
        this.jdbc = jdbc; this.associations = associations; this.projects = projects; this.clock = clock;
    }

    // ----- conversations -----

    /** Ensures the actor's built-in conversations exist, then lists every conversation they can open. */
    @Transactional
    public List<MessagingDtos.Conversation> list(UUID associationId, AuthenticatedUser actor) {
        associations.requireMember(associationId, actor.id());
        ensureBuiltIns(associationId, actor.id());
        return rows("WHERE c.association_id = ?", associationId).stream()
                .filter(row -> canAccess(row, actor.id()))
                .map(row -> toDto(row, actor.id()))
                .sorted(Comparator.comparing((MessagingDtos.Conversation c) -> c.lastMessage() == null
                                ? Instant.EPOCH : c.lastMessage().createdAt()).reversed()
                        .thenComparing(c -> c.type().ordinal())
                        .thenComparing(MessagingDtos.Conversation::title, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public MessagingDtos.Conversation detail(UUID associationId, UUID conversationId, AuthenticatedUser actor) {
        return toDto(requireAccess(associationId, conversationId, actor.id()), actor.id());
    }

    /** People who currently receive messages in this conversation. */
    public List<MessagingDtos.Participant> participants(UUID associationId, UUID conversationId, AuthenticatedUser actor) {
        var row = requireAccess(associationId, conversationId, actor.id());
        List<UUID> ids = switch (row.type()) {
            case ASSOCIATION -> jdbc.sql("SELECT user_id FROM association_memberships WHERE association_id = ?")
                    .param(associationId).query(UUID.class).list();
            case PROJECT -> projects.projectAudienceUserIds(associationId, row.projectId(), actor.id());
            case DEPARTMENT -> projects.departmentAudienceUserIds(associationId, row.projectId(), row.departmentId(), actor.id());
            case GROUP, DIRECT -> liveMemberIds(row.id());
        };
        return names(ids);
    }

    /** Association members the actor can start a direct chat with or add to a group (no emails). */
    public List<MessagingDtos.Participant> contacts(UUID associationId, AuthenticatedUser actor) {
        associations.requireMember(associationId, actor.id());
        return jdbc.sql("""
                SELECT u.id, u.display_name FROM association_memberships m JOIN app_users u ON u.id = m.user_id
                WHERE m.association_id = ? AND m.user_id <> ? ORDER BY u.display_name, u.id
                """).params(associationId, actor.id())
                .query((rs, n) -> new MessagingDtos.Participant(rs.getObject(1, UUID.class), rs.getString(2))).list();
    }

    @Transactional
    public MessagingDtos.Conversation createGroup(UUID associationId, MessagingDtos.CreateGroup request, AuthenticatedUser actor) {
        associations.requireMember(associationId, actor.id());
        Set<UUID> members = new LinkedHashSet<>(request.memberUserIds());
        members.add(actor.id());
        Map<UUID, UUID> memberships = new LinkedHashMap<>();
        for (UUID user : members) memberships.put(user, requireParticipantMembership(associationId, user));
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO conversations (id, association_id, type, scope_key, name, created_by_user_id, created_at)
                VALUES (?, ?, 'GROUP', ?, ?, ?, ?)
                """).params(id, associationId, "GROUP:" + id, request.name().strip(), actor.id(), now()).update();
        memberships.forEach((user, membership) -> upsertMember(id, user, membership));
        return detail(associationId, id, actor);
    }

    /** Returns the one direct conversation between the actor and another member of this association. */
    @Transactional
    public MessagingDtos.Conversation openDirect(UUID associationId, MessagingDtos.OpenDirect request, AuthenticatedUser actor) {
        UUID actorMembership = currentMembership(associationId, actor.id()).orElseThrow(
                () -> new ApiException(HttpStatus.FORBIDDEN, "MEMBERSHIP_REQUIRED", "You are not a member of this association."));
        if (request.userId().equals(actor.id())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PARTICIPANT", "Choose another association member.");
        }
        UUID otherMembership = requireParticipantMembership(associationId, request.userId());
        String low = actor.id().toString().compareTo(request.userId().toString()) < 0 ? actor.id().toString() : request.userId().toString();
        String high = low.equals(actor.id().toString()) ? request.userId().toString() : actor.id().toString();
        String key = "DIRECT:" + associationId + ":" + low + ":" + high;
        jdbc.sql("""
                INSERT INTO conversations (id, association_id, type, scope_key, created_by_user_id, created_at)
                VALUES (?, ?, 'DIRECT', ?, ?, ?) ON CONFLICT DO NOTHING
                """).params(UUID.randomUUID(), associationId, key, actor.id(), now()).update();
        UUID id = jdbc.sql("SELECT id FROM conversations WHERE scope_key = ?").param(key).query(UUID.class).single();
        upsertMember(id, actor.id(), actorMembership);
        upsertMember(id, request.userId(), otherMembership);
        return detail(associationId, id, actor);
    }

    @Transactional
    public List<MessagingDtos.Participant> addGroupMember(UUID associationId, UUID conversationId, UUID userId, AuthenticatedUser actor) {
        var row = requireGroupManager(associationId, conversationId, actor.id());
        upsertMember(row.id(), userId, requireParticipantMembership(associationId, userId));
        return participants(associationId, conversationId, actor);
    }

    /** The creator removes someone, or any member leaves. The creator cannot leave their own group. */
    @Transactional
    public void removeGroupMember(UUID associationId, UUID conversationId, UUID userId, AuthenticatedUser actor) {
        var row = lockedGroup(associationId, conversationId, actor.id());
        boolean leaving = userId.equals(actor.id());
        if (leaving && actor.id().equals(row.createdByUserId())) {
            throw new ApiException(HttpStatus.CONFLICT, "CREATOR_CANNOT_LEAVE", "The group creator cannot leave the group.");
        }
        if (!leaving && !actor.id().equals(row.createdByUserId())) throw groupManagerRequired();
        jdbc.sql("DELETE FROM conversation_members WHERE conversation_id = ? AND user_id = ?").params(row.id(), userId).update();
    }

    // ----- messages -----

    /**
     * Without cursors: the newest page. after=seq: newer messages (polling). before=seq: older history.
     * Messages are always returned oldest first.
     */
    public MessagingDtos.Messages messages(UUID associationId, UUID conversationId, Long after, Long before,
            Integer limit, AuthenticatedUser actor) {
        var row = requireAccess(associationId, conversationId, actor.id());
        if (after != null && before != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Use either after or before, not both.");
        }
        int size = limit == null ? DEFAULT_PAGE : Math.max(1, Math.min(limit, MAX_PAGE));
        List<MessagingDtos.Message> page;
        if (after != null) {
            page = messageQuery("WHERE m.conversation_id = ? AND m.seq > ? ORDER BY m.seq ASC LIMIT ?",
                    actor.id(), row.id(), after, size + 1);
        } else {
            page = new ArrayList<>(messageQuery("WHERE m.conversation_id = ? AND m.seq < ? ORDER BY m.seq DESC LIMIT ?",
                    actor.id(), row.id(), before == null ? Long.MAX_VALUE : before, size + 1));
            Collections.reverse(page);
            if (page.size() > size) page = page.subList(page.size() - size, page.size());
            return new MessagingDtos.Messages(List.copyOf(page), messageCountBefore(row.id(), page) > 0);
        }
        boolean more = page.size() > size;
        return new MessagingDtos.Messages(more ? List.copyOf(page.subList(0, size)) : page, more);
    }

    @Transactional
    public MessagingDtos.Message send(UUID associationId, UUID conversationId, MessagingDtos.SendMessage request,
            AuthenticatedUser actor) {
        var row = requireAccess(associationId, conversationId, actor.id());
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO messages (id, conversation_id, sender_user_id, body, created_at) VALUES (?, ?, ?, ?, ?)")
                .params(id, row.id(), actor.id(), request.body().strip(), now()).update();
        return messageQuery("WHERE m.id = ?", actor.id(), id).get(0);
    }

    // ----- access -----

    private Row requireAccess(UUID associationId, UUID conversationId, UUID actorId) {
        associations.requireMember(associationId, actorId);
        return rows("WHERE c.association_id = ? AND c.id = ?", associationId, conversationId).stream()
                .filter(row -> canAccess(row, actorId)).findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CONVERSATION_NOT_FOUND", "Conversation not found."));
    }

    private boolean canAccess(Row row, UUID actorId) {
        return switch (row.type()) {
            case ASSOCIATION -> true;
            case PROJECT -> inAudience(() -> projects.projectAudienceUserIds(row.associationId(), row.projectId(), actorId), actorId);
            case DEPARTMENT -> inAudience(() -> projects.departmentAudienceUserIds(
                    row.associationId(), row.projectId(), row.departmentId(), actorId), actorId);
            case GROUP, DIRECT -> liveMemberIds(row.id()).contains(actorId);
        };
    }

    /** Raul's helpers throw 403 for people outside the project/department; for chat that simply means no access. */
    private static boolean inAudience(Supplier<List<UUID>> audience, UUID actorId) {
        try {
            return audience.get().contains(actorId);
        } catch (ApiException ex) {
            if (ex.status() == HttpStatus.FORBIDDEN) return false;
            throw ex;
        }
    }

    private Row lockedGroup(UUID associationId, UUID conversationId, UUID actorId) {
        var row = requireAccess(associationId, conversationId, actorId);
        if (row.type() != Type.GROUP) {
            throw new ApiException(HttpStatus.CONFLICT, "NOT_A_GROUP", "Only custom groups have editable members.");
        }
        jdbc.sql("SELECT id FROM conversations WHERE id = ? FOR UPDATE").param(row.id()).query(UUID.class).single();
        return row;
    }

    private Row requireGroupManager(UUID associationId, UUID conversationId, UUID actorId) {
        var row = lockedGroup(associationId, conversationId, actorId);
        if (!actorId.equals(row.createdByUserId())) throw groupManagerRequired();
        return row;
    }

    private static ApiException groupManagerRequired() {
        return new ApiException(HttpStatus.FORBIDDEN, "GROUP_MANAGER_REQUIRED", "Only the group creator can manage its members.");
    }

    private UUID requireParticipantMembership(UUID associationId, UUID userId) {
        return currentMembership(associationId, userId).orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST,
                "INVALID_PARTICIPANT", "Every participant must be a current member of this association."));
    }

    private Optional<UUID> currentMembership(UUID associationId, UUID userId) {
        return jdbc.sql("SELECT id FROM association_memberships WHERE association_id = ? AND user_id = ?")
                .params(associationId, userId).query(UUID.class).optional();
    }

    private List<UUID> liveMemberIds(UUID conversationId) {
        return jdbc.sql("""
                SELECT cm.user_id FROM conversation_members cm
                JOIN conversations c ON c.id = cm.conversation_id
                JOIN association_memberships am ON am.id = cm.association_membership_id
                    AND am.user_id = cm.user_id AND am.association_id = c.association_id
                WHERE cm.conversation_id = ?
                """).param(conversationId).query(UUID.class).list();
    }

    private void upsertMember(UUID conversationId, UUID userId, UUID membershipId) {
        int updated = jdbc.sql("UPDATE conversation_members SET association_membership_id = ? WHERE conversation_id = ? AND user_id = ?")
                .params(membershipId, conversationId, userId).update();
        if (updated == 0) {
            jdbc.sql("INSERT INTO conversation_members (conversation_id, user_id, association_membership_id, added_at) VALUES (?, ?, ?, ?)")
                    .params(conversationId, userId, membershipId, now()).update();
        }
    }

    // ----- built-in conversations -----

    private void ensureBuiltIns(UUID associationId, UUID actorId) {
        ensure(associationId, Type.ASSOCIATION, "ASSOCIATION:" + associationId, null, null);
        var seats = jdbc.sql("""
                SELECT pm.project_id, pm.project_role, pm.department_id
                FROM project_memberships pm JOIN projects p ON p.id = pm.project_id
                JOIN association_memberships am ON am.id = pm.association_membership_id
                    AND am.association_id = p.association_id AND am.user_id = pm.user_id
                WHERE p.association_id = ? AND pm.user_id = ?
                """).params(associationId, actorId).query((rs, n) -> new Seat(rs.getObject(1, UUID.class),
                    rs.getString(2), rs.getObject(3, UUID.class))).list();
        for (Seat seat : seats) {
            ensure(associationId, Type.PROJECT, "PROJECT:" + seat.projectId(), seat.projectId(), null);
            List<UUID> departments = seat.role().equals("PROJECT_MANAGER")
                    ? jdbc.sql("SELECT id FROM project_departments WHERE project_id = ?").param(seat.projectId()).query(UUID.class).list()
                    : seat.departmentId() == null ? List.of() : List.of(seat.departmentId());
            for (UUID department : departments) {
                ensure(associationId, Type.DEPARTMENT, "DEPARTMENT:" + department, seat.projectId(), department);
            }
        }
    }

    private void ensure(UUID associationId, Type type, String key, UUID projectId, UUID departmentId) {
        jdbc.sql("""
                INSERT INTO conversations (id, association_id, type, scope_key, project_id, department_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING
                """).params(UUID.randomUUID(), associationId, type.name(), key, projectId, departmentId, now()).update();
    }

    // ----- mapping -----

    private record Seat(UUID projectId, String role, UUID departmentId) {}

    private record Row(UUID id, UUID associationId, Type type, String associationName, UUID projectId, String projectName,
            UUID departmentId, String departmentName, String name, UUID createdByUserId) {}

    private List<Row> rows(String where, Object... params) {
        return jdbc.sql("""
                SELECT c.id, c.association_id, c.type, a.name AS association_name, c.project_id, p.name AS project_name,
                    c.department_id, d.name AS department_name, c.name, c.created_by_user_id
                FROM conversations c JOIN associations a ON a.id = c.association_id
                LEFT JOIN projects p ON p.id = c.project_id
                LEFT JOIN project_departments d ON d.id = c.department_id AND d.project_id = c.project_id
                """ + where).params(params).query(MessagingService::row).list();
    }

    private static Row row(ResultSet rs, int n) throws SQLException {
        return new Row(rs.getObject("id", UUID.class), rs.getObject("association_id", UUID.class),
                Type.valueOf(rs.getString("type")), rs.getString("association_name"), rs.getObject("project_id", UUID.class),
                rs.getString("project_name"), rs.getObject("department_id", UUID.class), rs.getString("department_name"),
                rs.getString("name"), rs.getObject("created_by_user_id", UUID.class));
    }

    private MessagingDtos.Conversation toDto(Row row, UUID actorId) {
        String title = switch (row.type()) {
            case ASSOCIATION -> row.associationName();
            case PROJECT -> row.projectName();
            case DEPARTMENT -> row.projectName() + " · " + row.departmentName();
            case GROUP -> row.name();
            case DIRECT -> jdbc.sql("""
                    SELECT u.display_name FROM conversation_members cm JOIN app_users u ON u.id = cm.user_id
                    WHERE cm.conversation_id = ? AND cm.user_id <> ?
                    """).params(row.id(), actorId).query(String.class).optional().orElse("Direct message");
        };
        var last = messageQuery("WHERE m.conversation_id = ? ORDER BY m.seq DESC LIMIT 1", actorId, row.id());
        return new MessagingDtos.Conversation(row.id(), row.associationId(), row.type(), title,
                row.projectId(), row.projectName(), row.departmentId(), row.departmentName(), row.createdByUserId(),
                row.type() == Type.GROUP && actorId.equals(row.createdByUserId()), last.isEmpty() ? null : last.get(0));
    }

    private List<MessagingDtos.Message> messageQuery(String tail, UUID actorId, Object... params) {
        return jdbc.sql("""
                SELECT m.id, m.seq, m.conversation_id, m.sender_user_id, u.display_name, m.body, m.created_at
                FROM messages m JOIN app_users u ON u.id = m.sender_user_id
                """ + tail).params(params).query((rs, n) -> new MessagingDtos.Message(
                    rs.getObject(1, UUID.class), rs.getLong(2), rs.getObject(3, UUID.class), rs.getObject(4, UUID.class),
                    rs.getString(5), rs.getString(6), rs.getTimestamp(7).toInstant(),
                    rs.getObject(4, UUID.class).equals(actorId))).list();
    }

    private long messageCountBefore(UUID conversationId, List<MessagingDtos.Message> page) {
        if (page.isEmpty()) return 0;
        return jdbc.sql("SELECT COUNT(*) FROM messages WHERE conversation_id = ? AND seq < ?")
                .params(conversationId, page.get(0).seq()).query(Long.class).single();
    }

    private List<MessagingDtos.Participant> names(Collection<UUID> ids) {
        if (ids.isEmpty()) return List.of();
        List<MessagingDtos.Participant> result = new ArrayList<>();
        for (UUID id : new LinkedHashSet<>(ids)) {
            jdbc.sql("SELECT display_name FROM app_users WHERE id = ?").param(id).query(String.class).optional()
                    .ifPresent(name -> result.add(new MessagingDtos.Participant(id, name)));
        }
        result.sort(Comparator.comparing(MessagingDtos.Participant::displayName, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    private Timestamp now() { return Timestamp.from(clock.instant()); }
}
