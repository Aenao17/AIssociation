package ro.stucanii.aissociation.associations;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class MembershipDtos {
    private MembershipDtos() {}
    public record Workspace(UUID associationId, String name, String slug, AssociationRole role) {}
    public record Member(UUID id, UUID userId, String displayName, String email, AssociationRole role, Instant createdAt) {}
    public record AddMember(@NotBlank @Email @Size(max = 254) String email, @NotNull AssociationRole role) {
        public AddMember { email = email == null ? null : email.strip().toLowerCase(Locale.ROOT); }
    }
    public record ChangeRole(@NotNull AssociationRole role) {}
}
