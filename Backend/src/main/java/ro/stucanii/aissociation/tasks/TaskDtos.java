package ro.stucanii.aissociation.tasks;

import java.time.Instant;
import java.util.UUID;
import jakarta.validation.constraints.*;

public final class TaskDtos {
    private TaskDtos() {}

    public enum Status { TODO, IN_PROGRESS, DONE }

    public record CreateTask(@NotBlank @Size(max = 200) String title,
            @Size(max = 5000) String description,
            @NotNull UUID assigneeUserId,
            @NotNull Instant dueAt) {}

    /** Full manager edit; every field is required so the request is explicit. */
    public record EditTask(@NotBlank @Size(max = 200) String title,
            @Size(max = 5000) String description,
            @NotNull UUID assigneeUserId,
            @NotNull Instant dueAt) {}

    public record ChangeStatus(@NotNull Status status) {}

    /**
     * canManage: actor may edit title/description/deadline/assignee.
     * canChangeStatus: actor may change status. Both guide the UI only; the server rechecks.
     */
    public record Task(UUID id, UUID associationId, UUID projectId, String projectName,
            String title, String description, Instant dueAt, Status status,
            UUID assigneeUserId, String assigneeName, UUID createdByUserId, String createdByName,
            Instant createdAt, Instant updatedAt, boolean canManage, boolean canChangeStatus) {}

    public record AssigneeOption(UUID userId, String displayName, String projectRole, String departmentName) {}
}
