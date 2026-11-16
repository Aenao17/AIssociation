package ro.stucanii.aissociation.events;

import java.net.URI;
import java.util.Set;
import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import ro.stucanii.aissociation.associations.AssociationAccess;
import ro.stucanii.aissociation.identity.*;

/** Only presentation fields: identity/name/slug administration stays in Gabriel's module. */
@RestController
@RequestMapping("/api/associations/{associationId}/public-profile")
public class AssociationProfileController {
    private final JdbcClient jdbc;
    private final AssociationAccess access;
    public AssociationProfileController(JdbcClient jdbc,AssociationAccess access) { this.jdbc=jdbc;this.access=access; }
    @PutMapping
    @Transactional
    ResponseEntity<Void> edit(@PathVariable UUID associationId,@Valid @RequestBody EventDtos.Profile body,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        jdbc.sql("SELECT id FROM associations WHERE id=? FOR UPDATE").param(associationId).query(UUID.class).optional();
        access.requireAdmin(associationId,actor.id());
        String website=body.website()==null || body.website().isBlank()?null:body.website().strip();
        if (website!=null) {
            try {
                var uri=URI.create(website);
                if (uri.getScheme()==null || !Set.of("http","https").contains(uri.getScheme().toLowerCase(java.util.Locale.ROOT))
                        || uri.getHost()==null || uri.getUserInfo()!=null) throw new IllegalArgumentException();
            } catch (IllegalArgumentException ex) {
                throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_WEBSITE","Use a full http or https website URL without credentials.");
            }
        }
        jdbc.sql("UPDATE associations SET description=?, website=? WHERE id=?")
                .param(body.description().strip()).param(website).param(associationId).update();
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
