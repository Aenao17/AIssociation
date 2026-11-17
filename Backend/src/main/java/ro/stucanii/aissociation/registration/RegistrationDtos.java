package ro.stucanii.aissociation.registration;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import jakarta.validation.constraints.NotNull;

public final class RegistrationDtos {
    private RegistrationDtos() {}

    public record Availability(UUID eventId, String eventStatus, Integer capacity, long activeRegistrations,
            Long seatsRemaining, boolean registrationOpen, Instant registrationDeadline) {}

    
    public record MyRegistration(UUID eventId, String status, Instant registeredAt, Instant cancelledAt,
            boolean cancelledByOrganizer, boolean canRegister, boolean canCancel, Availability availability) {}

    public record RosterEntry(UUID registrationId, UUID userId, String displayName, String email, String status,
            Instant registeredAt, Instant cancelledAt, boolean cancelledByOrganizer,
            String attendance, Instant checkedInAt) {}

    public record Roster(UUID eventId, String eventTitle, String eventStatus, Integer capacity,
            long activeRegistrations, long checkedIn, List<RosterEntry> items) {}

    public record SetAttendance(@NotNull Boolean attended) {}
}
