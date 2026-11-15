package ro.stucanii.aissociation.identity;

import java.security.Principal;
import java.util.UUID;

/** Trusted identity established from a valid server-side session, never a client role. */
public record AuthenticatedUser(UUID id, String displayName, String email, UUID sessionId) implements Principal {
    @Override
    public String getName() { return id.toString(); }
}
