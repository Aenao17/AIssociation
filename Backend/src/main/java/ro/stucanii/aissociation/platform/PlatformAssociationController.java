package ro.stucanii.aissociation.platform;

import java.util.List;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ro.stucanii.aissociation.identity.AuthenticatedUser;

@RestController
@RequestMapping("/api/admin/associations")
public class PlatformAssociationController {
    private final PlatformAssociationService service;
    public PlatformAssociationController(PlatformAssociationService service) { this.service = service; }

    @GetMapping
    ResponseEntity<List<AssociationDtos.AssociationView>> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.list(user));
    }

    @PostMapping
    ResponseEntity<AssociationDtos.AssociationView> create(@AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody AssociationDtos.CreateAssociation request) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(service.create(user, request));
    }
}
