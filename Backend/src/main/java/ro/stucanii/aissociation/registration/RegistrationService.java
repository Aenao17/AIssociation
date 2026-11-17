package ro.stucanii.aissociation.registration;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.stucanii.aissociation.associations.AssociationAccess;
import ro.stucanii.aissociation.associations.AssociationRole;
import ro.stucanii.aissociation.identity.ApiException;
import ro.stucanii.aissociation.identity.AuthenticatedUser;
import ro.stucanii.aissociation.projects.ProjectAccess;

@Service
public class RegistrationService {
    private static final String ENTRY_SQL = """
            SELECT r.id, r.user_id, u.display_name, u.email, r.status, r.registered_at, r.cancelled_at,
                   r.cancelled_by_organizer, r.attendance, r.checked_in_at
            FROM event_registrations r JOIN app_users u ON u.id = r.user_id
            """;

    private final JdbcClient jdbc;
    private final AssociationAccess associations;
    private final ProjectAccess projects;
    private final Clock clock;

    public RegistrationService(JdbcClient jdbc, AssociationAccess associations, ProjectAccess projects, Clock clock) {
        this.jdbc = jdbc; this.associations = associations; this.projects = projects; this.clock = clock;
    }

    private record EventRow(UUID id, UUID associationId, UUID projectId, String title, String status,
            Instant deadline, Integer capacity) {}

  

    public RegistrationDtos.Availability availability(UUID eventId) {
        var event = visibleEvent(eventId, false);
        return availabilityOf(event, countActive(eventId), clock.instant());
    }

    public RegistrationDtos.MyRegistration mine(UUID eventId, AuthenticatedUser actor) {
        return view(visibleEvent(eventId, false), actor.id());
    }

    @Transactional
    public RegistrationDtos.MyRegistration register(UUID eventId, AuthenticatedUser actor) {
        var event = visibleEvent(eventId, true);
        Instant now = clock.instant();

        if (!event.status().equals("PUBLISHED")) {
            throw conflict("EVENT_NOT_OPEN", "This event is not open for registration.");
        }
        if (!now.isBefore(event.deadline())) {
            throw conflict("REGISTRATION_CLOSED", "The registration deadline has passed.");
        }
        var existing = findByUser(eventId, actor.id());
        if (existing.isPresent() && existing.get().status().equals("ACTIVE")) {
            throw conflict("ALREADY_REGISTERED", "You are already registered for this event.");
        }
        if (existing.isPresent() && existing.get().cancelledByOrganizer()) {
            throw conflict("REGISTRATION_CANCELLED_BY_ORGANIZER",
                    "An organizer cancelled your registration; contact them to register again.");
        }
        if (event.capacity() != null && countActive(eventId) >= event.capacity()) {
            throw conflict("EVENT_FULL", "There are no places left for this event.");
        }

        if (existing.isPresent()) {
            jdbc.sql("""
                    UPDATE event_registrations
                    SET status = 'ACTIVE', registered_at = ?, cancelled_at = NULL, cancelled_by_user_id = NULL,
                        cancelled_by_organizer = FALSE, attendance = 'NOT_CHECKED_IN',
                        checked_in_at = NULL, checked_in_by_user_id = NULL
                    WHERE id = ?
                    """).params(Timestamp.from(now), existing.get().registrationId()).update();
        } else {
            jdbc.sql("""
                    INSERT INTO event_registrations (id, event_id, user_id, status, registered_at)
                    VALUES (?, ?, ?, 'ACTIVE', ?)
                    """).params(UUID.randomUUID(), eventId, actor.id(), Timestamp.from(now)).update();
        }
        return view(event, actor.id());
    }

    @Transactional
    public RegistrationDtos.MyRegistration cancel(UUID eventId, AuthenticatedUser actor) {
        var event = visibleEvent(eventId, true);   // lock: releasing a seat must not race a registration
        Instant now = clock.instant();
        var registration = findByUser(eventId, actor.id()).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "REGISTRATION_NOT_FOUND", "You are not registered for this event."));
        if (!registration.status().equals("ACTIVE")) {
            throw conflict("ALREADY_CANCELLED", "Your registration is already cancelled.");
        }
        if (!now.isBefore(event.deadline())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "CANCELLATION_CLOSED",
                    "The deadline has passed; ask an organizer to cancel your registration.");
        }
        markCancelled(registration.registrationId(), now, actor.id(), false);
        return view(event, actor.id());
    }

   
    public RegistrationDtos.Roster roster(UUID associationId, UUID eventId, AuthenticatedUser actor) {
        associations.requireMember(associationId, actor.id());          // outsiders: 403
        var event = load(eventId, associationId, false);                // wrong association: 404
        authorizeOrganizer(associationId, event, actor);                // not an organizer: 403
        var items = jdbc.sql(ENTRY_SQL + " WHERE r.event_id = ? ORDER BY r.status, LOWER(u.display_name), r.id")
                .param(eventId).query(this::entry).list();
        long active = items.stream().filter(e -> e.status().equals("ACTIVE")).count();
        long checkedIn = items.stream().filter(e -> e.attendance().equals("CHECKED_IN")).count();
        return new RegistrationDtos.Roster(eventId, event.title(), event.status(), event.capacity(), active, checkedIn, items);
    }

    @Transactional
    public RegistrationDtos.RosterEntry cancelAsOrganizer(UUID associationId, UUID eventId, UUID registrationId,
            AuthenticatedUser actor) {
        var event = lockedOrganizerEvent(associationId, eventId, actor);
        if (!event.status().equals("PUBLISHED")) {
            throw conflict("EVENT_NOT_OPEN", "Registrations can only be cancelled on a published event.");
        }
        var registration = requireRegistration(eventId, registrationId);
        if (!registration.status().equals("ACTIVE")) {
            throw conflict("ALREADY_CANCELLED", "This registration is already cancelled.");
        }
        markCancelled(registrationId, clock.instant(), actor.id(), true);
        return requireRegistration(eventId, registrationId);
    }

    
    @Transactional
    public RegistrationDtos.RosterEntry setAttendance(UUID associationId, UUID eventId, UUID registrationId,
            boolean attended, AuthenticatedUser actor) {
        var event = lockedOrganizerEvent(associationId, eventId, actor);
        if (!event.status().equals("PUBLISHED") && !event.status().equals("COMPLETED")) {
            throw conflict("EVENT_NOT_OPEN", "Attendance can only be recorded for a published or completed event.");
        }
        var registration = requireRegistration(eventId, registrationId);
        if (!registration.status().equals("ACTIVE")) {
            throw conflict("REGISTRATION_NOT_ACTIVE", "Only an active registration can be checked in.");
        }
        boolean alreadyChecked = registration.attendance().equals("CHECKED_IN");
        if (attended && !alreadyChecked) {
            jdbc.sql("UPDATE event_registrations SET attendance = 'CHECKED_IN', checked_in_at = ?, checked_in_by_user_id = ? WHERE id = ?")
                    .params(Timestamp.from(clock.instant()), actor.id(), registrationId).update();
        } else if (!attended && alreadyChecked) {
            // Correction of a mistaken check-in.
            jdbc.sql("UPDATE event_registrations SET attendance = 'NOT_CHECKED_IN', checked_in_at = NULL, checked_in_by_user_id = NULL WHERE id = ?")
                    .param(registrationId).update();
        }
        return requireRegistration(eventId, registrationId);
    }

    
    private void authorizeOrganizer(UUID associationId, EventRow event, AuthenticatedUser actor) {
        AssociationRole role = associations.requireMember(associationId, actor.id());
        if (role == AssociationRole.ADMIN) return;
        if (event.projectId() == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ORGANIZER_REQUIRED",
                    "Only association administrators can manage a standalone event.");
        }
        projects.requireManager(associationId, event.projectId(), actor.id());   // throws 403 if not the manager
    }

   
    private EventRow lockedOrganizerEvent(UUID associationId, UUID eventId, AuthenticatedUser actor) {
        associations.requireMember(associationId, actor.id());                      // outsiders never take a lock
        var peek = load(eventId, associationId, false);                             // 404 for another association
        jdbc.sql("SELECT id FROM associations WHERE id = ? FOR UPDATE").param(associationId).query(UUID.class).optional()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ASSOCIATION_NOT_FOUND", "Association not found."));
        if (peek.projectId() != null) {
            jdbc.sql("SELECT id FROM projects WHERE id = ? AND association_id = ? FOR UPDATE")
                    .params(peek.projectId(), associationId).query(UUID.class).optional()
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", "Project not found in this association."));
        }
        var event = load(eventId, associationId, true);
        authorizeOrganizer(associationId, event, actor);
        return event;
    }


    private EventRow load(UUID eventId, UUID associationId, boolean lock) {
        String sql = "SELECT id, association_id, project_id, title, status, registration_deadline, capacity FROM events WHERE id = ?"
                + (associationId == null ? "" : " AND association_id = ?")
                + (lock ? " FOR UPDATE" : "");
        var spec = associationId == null ? jdbc.sql(sql).param(eventId) : jdbc.sql(sql).params(eventId, associationId);
        return spec.query(this::event).optional().orElseThrow(this::eventMissing);
    }

    private EventRow visibleEvent(UUID eventId, boolean lock) {
        var event = load(eventId, null, lock);
        if (event.status().equals("DRAFT")) throw eventMissing();
        return event;
    }

    private long countActive(UUID eventId) {
        return jdbc.sql("SELECT COUNT(*) FROM event_registrations WHERE event_id = ? AND status = 'ACTIVE'")
                .param(eventId).query(Long.class).single();
    }

    private Optional<RegistrationDtos.RosterEntry> findByUser(UUID eventId, UUID userId) {
        return jdbc.sql(ENTRY_SQL + " WHERE r.event_id = ? AND r.user_id = ?").params(eventId, userId)
                .query(this::entry).optional();
    }

    private RegistrationDtos.RosterEntry requireRegistration(UUID eventId, UUID registrationId) {
        return jdbc.sql(ENTRY_SQL + " WHERE r.event_id = ? AND r.id = ?").params(eventId, registrationId)
                .query(this::entry).optional()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "REGISTRATION_NOT_FOUND", "Registration not found for this event."));
    }

    private void markCancelled(UUID registrationId, Instant now, UUID byUserId, boolean byOrganizer) {
        jdbc.sql("""
                UPDATE event_registrations
                SET status = 'CANCELLED', cancelled_at = ?, cancelled_by_user_id = ?, cancelled_by_organizer = ?,
                    attendance = 'NOT_CHECKED_IN', checked_in_at = NULL, checked_in_by_user_id = NULL
                WHERE id = ?
                """).params(Timestamp.from(now), byUserId, byOrganizer, registrationId).update();
    }


    private RegistrationDtos.Availability availabilityOf(EventRow event, long taken, Instant now) {
        Long remaining = event.capacity() == null ? null : Math.max(0L, event.capacity() - taken);
        boolean open = event.status().equals("PUBLISHED") && now.isBefore(event.deadline());
        return new RegistrationDtos.Availability(event.id(), event.status(), event.capacity(), taken, remaining, open, event.deadline());
    }

    private RegistrationDtos.MyRegistration view(EventRow event, UUID userId) {
        Instant now = clock.instant();
        var availability = availabilityOf(event, countActive(event.id()), now);
        var registration = findByUser(event.id(), userId);
        boolean active = registration.map(r -> r.status().equals("ACTIVE")).orElse(false);
        boolean blocked = registration.map(RegistrationDtos.RosterEntry::cancelledByOrganizer).orElse(false);
        boolean full = availability.seatsRemaining() != null && availability.seatsRemaining() == 0;
        return new RegistrationDtos.MyRegistration(event.id(),
                registration.map(RegistrationDtos.RosterEntry::status).orElse("NOT_REGISTERED"),
                registration.map(RegistrationDtos.RosterEntry::registeredAt).orElse(null),
                registration.map(RegistrationDtos.RosterEntry::cancelledAt).orElse(null),
                blocked,
                availability.registrationOpen() && !active && !blocked && !full,
                active && now.isBefore(event.deadline()),
                availability);
    }

    private EventRow event(ResultSet rs, int row) throws SQLException {
        return new EventRow(rs.getObject("id", UUID.class), rs.getObject("association_id", UUID.class),
                rs.getObject("project_id", UUID.class), rs.getString("title"), rs.getString("status"),
                rs.getTimestamp("registration_deadline").toInstant(), rs.getObject("capacity", Integer.class));
    }

    private RegistrationDtos.RosterEntry entry(ResultSet rs, int row) throws SQLException {
        return new RegistrationDtos.RosterEntry(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class),
                rs.getString("display_name"), rs.getString("email"), rs.getString("status"),
                instant(rs, "registered_at"), instant(rs, "cancelled_at"), rs.getBoolean("cancelled_by_organizer"),
                rs.getString("attendance"), instant(rs, "checked_in_at"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private ApiException eventMissing() { return new ApiException(HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", "Event not found."); }
    private ApiException conflict(String code, String message) { return new ApiException(HttpStatus.CONFLICT, code, message); }
}