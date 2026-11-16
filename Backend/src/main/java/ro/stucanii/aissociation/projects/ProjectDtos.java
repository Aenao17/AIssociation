package ro.stucanii.aissociation.projects;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import jakarta.validation.constraints.*;

public final class ProjectDtos {
    private ProjectDtos() {}

    public record CreateProject(@NotBlank @Size(max = 150) String name,
            @NotBlank @Size(max = 5000) String description,
            @NotNull @Size(min = 1, max = 20) Set<@NotNull UUID> managerUserIds) {}
    public record EditProject(@NotBlank @Size(max = 150) String name,
            @NotBlank @Size(max = 5000) String description) {}
    public record ConfigureRecruitment(@NotBlank @Size(max = 5000) String description,
            @NotNull Instant deadline) {}
    public record SavePosition(@NotBlank @Size(max = 100) String roleLabel,
            @NotBlank @Size(max = 100) String departmentName) {}
    public record Apply(@NotNull UUID positionId, @NotBlank @Size(max = 5000) String motivation) {}
    public record Supervisor(@NotNull UUID reportingParentMembershipId) {}

    public record Summary(UUID id, UUID associationId, String name, String description,
            Instant recruitmentDeadline, boolean canManage, boolean canOpen) {}
    public record Position(UUID id, UUID departmentId, String departmentName, String roleLabel) {}
    public record Overview(Summary project, String recruitmentDescription, Instant recruitmentDeadline,
            List<Position> positions, boolean associationAdmin) {}
    public record MemberOption(UUID userId, String displayName) {}
    public record TeamMember(UUID id, UUID userId, String displayName, String projectRole,
            UUID departmentId, String departmentName, UUID positionId, String positionLabel,
            UUID reportingParentMembershipId, boolean active) {}
    public record Application(UUID id, UUID positionId, String positionLabel, String departmentName,
            UUID userId, String displayName, String motivation, String status,
            Instant createdAt, Instant reviewedAt) {}
}
