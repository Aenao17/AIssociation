package ro.stucanii.aissociation.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import jakarta.validation.constraints.*;

public final class EventDtos {
    private EventDtos() {}
    public record SaveEvent(UUID projectId,
            @NotBlank @Size(max=150) String title,
            @NotBlank @Size(max=5000) String description,
            @NotBlank @Size(max=500) String location,
            @NotNull Instant startsAt, @NotNull Instant endsAt,
            @NotBlank @Size(max=100) String timeZone,
            @NotNull Instant registrationDeadline, @Positive Integer capacity) {}
    public record Association(UUID id, String name, String slug) {}
    public record PublicEvent(UUID id, Association association, String title, String description,
            String location, Instant startsAt, Instant endsAt, String timeZone,
            Instant registrationDeadline, Integer capacity, String status) {}
    public record OrganizerEvent(PublicEvent event, UUID projectId, Instant updatedAt) {}
    public record Page(List<PublicEvent> items, long total, int page, int size) {}
    public record Profile(@NotNull @Size(max=5000) String description, @Size(max=500) String website) {}
}
