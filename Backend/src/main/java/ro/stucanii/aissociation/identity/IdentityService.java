package ro.stucanii.aissociation.identity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.stucanii.aissociation.platform.PlatformAdminAccess;

@Service
public class IdentityService {
    private final IdentityRepository repository;
    private final PlatformAdminAccess platformAdminAccess;
    private final PasswordEncoder encoder;
    private final Clock clock;
    private final Duration sessionDuration;
    private final SecureRandom random = new SecureRandom();
    private final String dummyHash;

    public IdentityService(IdentityRepository repository, PasswordEncoder encoder, Clock clock, PlatformAdminAccess platformAdminAccess,
            @Value("${app.auth.session-duration}") Duration sessionDuration) {
        if (sessionDuration.isZero() || sessionDuration.isNegative()) {
            throw new IllegalArgumentException("Session duration must be positive.");
        }
        this.repository = repository;
        this.platformAdminAccess = platformAdminAccess;
        this.encoder = encoder;
        this.clock = clock;
        this.sessionDuration = sessionDuration;
        this.dummyHash = encoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public AuthDtos.UserView register(AuthDtos.RegisterRequest request) {
        validatePasswordBytes(request.password());
        IdentityRepository.Account user = new IdentityRepository.Account(UUID.randomUUID(), request.displayName(),
                request.email(), encoder.encode(request.password()), clock.instant());
        try {
            repository.insertAccount(user);
        } catch (DuplicateKeyException ex) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_EXISTS", "An account with this email already exists.");
        }
        return user.view();
    }

    @Transactional
    public AuthDtos.LoginResponse login(AuthDtos.LoginRequest request) {
        validatePasswordBytes(request.password());
        Optional<IdentityRepository.Account> candidate = repository.findByEmail(request.email());
        boolean matches = encoder.matches(request.password(), candidate.map(IdentityRepository.Account::passwordHash).orElse(dummyHash));
        if (candidate.isEmpty() || !matches) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email or password is incorrect.");
        }
        IdentityRepository.Account user = candidate.orElseThrow();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = clock.instant();
        Instant expiry = now.plus(sessionDuration);
        repository.insertSession(UUID.randomUUID(), user.id(), hashToken(token), now, expiry);
        return new AuthDtos.LoginResponse(token, "Bearer", expiry, user.view());
    }

    public Optional<AuthenticatedUser> authenticate(String token) {
        if (!token.matches("[A-Za-z0-9_-]{43}")) { return Optional.empty(); }
        return repository.findSession(hashToken(token), clock.instant());
    }

    @Transactional
    public void logout(AuthenticatedUser user) { repository.deleteSession(user.sessionId()); }

    public AuthDtos.CurrentUser currentUser(AuthenticatedUser user) {
        return new AuthDtos.CurrentUser(user.id(), user.displayName(), user.email(), repository.memberships(user.id()), platformAdminAccess.isPlatformAdmin(user.id()));
    }

    static String hashToken(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime.", ex);
        }
    }

    private void validatePasswordBytes(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Password must fit within 72 UTF-8 bytes.");
        }
    }
}
