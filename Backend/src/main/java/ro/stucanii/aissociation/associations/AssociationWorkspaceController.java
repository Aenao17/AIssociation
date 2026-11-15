package ro.stucanii.aissociation.associations;

import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ro.stucanii.aissociation.identity.AuthenticatedUser;

@RestController
@RequestMapping("/api")
public class AssociationWorkspaceController {
    private final MembershipService service;
    public AssociationWorkspaceController(MembershipService service) { this.service = service; }

    @GetMapping("/workspaces/{slug}")
    ResponseEntity<MembershipDtos.Workspace> workspace(@PathVariable String slug, @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.workspace(slug, user));
    }

    @GetMapping("/associations/{associationId}/members")
    ResponseEntity<List<MembershipDtos.Member>> list(@PathVariable UUID associationId, @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.list(associationId, user));
    }

    @PostMapping("/associations/{associationId}/members")
    ResponseEntity<MembershipDtos.Member> add(@PathVariable UUID associationId, @Valid @RequestBody MembershipDtos.AddMember request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(service.add(associationId, request, user));
    }

    @PatchMapping("/associations/{associationId}/members/{membershipId}")
    ResponseEntity<MembershipDtos.Member> changeRole(@PathVariable UUID associationId, @PathVariable UUID membershipId,
            @Valid @RequestBody MembershipDtos.ChangeRole request, @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.changeRole(associationId, membershipId, request, user));
    }

    @DeleteMapping("/associations/{associationId}/members/{membershipId}")
    ResponseEntity<Void> remove(@PathVariable UUID associationId, @PathVariable UUID membershipId,
            @AuthenticationPrincipal AuthenticatedUser user) {
        service.remove(associationId, membershipId, user);
        return ResponseEntity.noContent().build();
    }
}
