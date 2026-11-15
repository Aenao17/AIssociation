package ro.stucanii.aissociation.platform;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import ro.stucanii.aissociation.identity.ApiException;
import ro.stucanii.aissociation.identity.AuthenticatedUser;

/** Platform privileges are configured by the operator, never by public signup. */
@Component
public class PlatformAdminAccess {
    private final Set<UUID> userIds;

    public PlatformAdminAccess(@Value("${app.platform-admin.user-ids:}") String configuredIds) {
        userIds = Arrays.stream(configuredIds.split(","))
                .map(String::trim).filter(value -> !value.isEmpty())
                .map(UUID::fromString).collect(Collectors.toUnmodifiableSet());
    }

    public boolean isPlatformAdmin(UUID userId) { return userIds.contains(userId); }

    public void requirePlatformAdmin(AuthenticatedUser user) {
        if (user == null || !isPlatformAdmin(user.id())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "PLATFORM_ADMIN_REQUIRED", "Platform administrator access is required.");
        }
    }
}
