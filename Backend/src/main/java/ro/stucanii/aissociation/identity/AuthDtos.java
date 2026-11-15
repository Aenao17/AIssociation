package ro.stucanii.aissociation.identity;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {}

    public record RegisterRequest(
            @NotBlank @Size(max = 100) String displayName,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 10, max = 72) String password) {
        public RegisterRequest {
            displayName = displayName == null ? null : displayName.strip();
            email = normalizeEmail(email);
        }
    }

    public record LoginRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 72) String password) {
        public LoginRequest { email = normalizeEmail(email); }
    }

    public record UserView(UUID id, String displayName, String email, Instant createdAt) {}
    public record LoginResponse(String accessToken, String tokenType, Instant expiresAt, UserView user) {}
    public record MembershipView(UUID associationId, String associationName, String associationSlug, String role) {}
    public record CurrentUser(UUID id, String displayName, String email, List<MembershipView> memberships, boolean platformAdmin) {}

    private static String normalizeEmail(String email) {
        return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }
}
