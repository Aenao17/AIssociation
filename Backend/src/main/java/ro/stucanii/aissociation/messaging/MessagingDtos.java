package ro.stucanii.aissociation.messaging;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import jakarta.validation.constraints.*;

public final class MessagingDtos {
    private MessagingDtos() {}

    public enum Type { ASSOCIATION, PROJECT, DEPARTMENT, GROUP, DIRECT }

    public record CreateGroup(@NotBlank @Size(max = 100) String name,
            @NotNull @Size(max = 100) Set<@NotNull UUID> memberUserIds) {}
    public record OpenDirect(@NotNull UUID userId) {}
    public record SendMessage(@NotBlank @Size(max = 4000) String body) {}

    /**
     * One shape for every chat type. title is ready to display (association/project name,
     * "Project · Department", group name, or the other person's name for DIRECT).
     * canManageMembers is true only for the creator of a GROUP; it guides the UI, the server rechecks.
     */
    public record Conversation(UUID id, UUID associationId, Type type, String title,
            UUID projectId, String projectName, UUID departmentId, String departmentName,
            UUID createdByUserId, boolean canManageMembers, Message lastMessage) {}

    /** seq is a strictly increasing cursor: poll with ?after=seq of the newest message you have. */
    public record Message(UUID id, long seq, UUID conversationId, UUID senderUserId, String senderName,
            String body, Instant createdAt, boolean mine) {}

    public record Participant(UUID userId, String displayName) {}

    public record Messages(List<Message> messages, boolean hasMore) {}
}
