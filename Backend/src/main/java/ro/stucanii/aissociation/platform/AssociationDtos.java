package ro.stucanii.aissociation.platform;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AssociationDtos {
    private AssociationDtos() {}

    public record CreateAssociation(
            @NotBlank @Size(max = 150) String name,
            @NotBlank @Size(max = 100) @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*", message = "Use lowercase letters, numbers and single hyphens between words.") String slug,
            @NotBlank @Email @Size(max = 254) String initialAdminEmail) {
        public CreateAssociation {
            name = name == null ? null : name.strip();
            slug = slug == null ? null : slug.strip().toLowerCase(Locale.ROOT);
            initialAdminEmail = initialAdminEmail == null ? null : initialAdminEmail.strip().toLowerCase(Locale.ROOT);
        }
    }

    public record AssociationView(UUID id, String name, String slug, Instant createdAt) {}
}
