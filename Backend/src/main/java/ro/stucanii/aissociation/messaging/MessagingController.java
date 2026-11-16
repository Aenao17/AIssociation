package ro.stucanii.aissociation.messaging;

import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ro.stucanii.aissociation.identity.AuthenticatedUser;

@RestController
@RequestMapping("/api/associations/{associationId}/conversations")
public class MessagingController {
    private final MessagingService service;
    public MessagingController(MessagingService service) { this.service = service; }
    private <T> ResponseEntity<T> ok(T body) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body); }
    private <T> ResponseEntity<T> created(T body) { return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(body); }

    @GetMapping
    ResponseEntity<List<MessagingDtos.Conversation>> list(@PathVariable UUID associationId,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ok(service.list(associationId, actor));
    }

    @GetMapping("/contacts")
    ResponseEntity<List<MessagingDtos.Participant>> contacts(@PathVariable UUID associationId,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ok(service.contacts(associationId, actor));
    }

    @PostMapping("/groups")
    ResponseEntity<MessagingDtos.Conversation> createGroup(@PathVariable UUID associationId,
            @Valid @RequestBody MessagingDtos.CreateGroup body, @AuthenticationPrincipal AuthenticatedUser actor) {
        return created(service.createGroup(associationId, body, actor));
    }

    @PostMapping("/direct")
    ResponseEntity<MessagingDtos.Conversation> openDirect(@PathVariable UUID associationId,
            @Valid @RequestBody MessagingDtos.OpenDirect body, @AuthenticationPrincipal AuthenticatedUser actor) {
        return ok(service.openDirect(associationId, body, actor));
    }

    @GetMapping("/{conversationId}")
    ResponseEntity<MessagingDtos.Conversation> detail(@PathVariable UUID associationId, @PathVariable UUID conversationId,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ok(service.detail(associationId, conversationId, actor));
    }

    @GetMapping("/{conversationId}/members")
    ResponseEntity<List<MessagingDtos.Participant>> members(@PathVariable UUID associationId, @PathVariable UUID conversationId,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ok(service.participants(associationId, conversationId, actor));
    }

    @PutMapping("/{conversationId}/members/{userId}")
    ResponseEntity<List<MessagingDtos.Participant>> addMember(@PathVariable UUID associationId, @PathVariable UUID conversationId,
            @PathVariable UUID userId, @AuthenticationPrincipal AuthenticatedUser actor) {
        return ok(service.addGroupMember(associationId, conversationId, userId, actor));
    }

    @DeleteMapping("/{conversationId}/members/{userId}")
    ResponseEntity<Void> removeMember(@PathVariable UUID associationId, @PathVariable UUID conversationId,
            @PathVariable UUID userId, @AuthenticationPrincipal AuthenticatedUser actor) {
        service.removeGroupMember(associationId, conversationId, userId, actor);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @GetMapping("/{conversationId}/messages")
    ResponseEntity<MessagingDtos.Messages> messages(@PathVariable UUID associationId, @PathVariable UUID conversationId,
            @RequestParam(required = false) Long after, @RequestParam(required = false) Long before,
            @RequestParam(required = false) Integer limit, @AuthenticationPrincipal AuthenticatedUser actor) {
        return ok(service.messages(associationId, conversationId, after, before, limit, actor));
    }

    @PostMapping("/{conversationId}/messages")
    ResponseEntity<MessagingDtos.Message> send(@PathVariable UUID associationId, @PathVariable UUID conversationId,
            @Valid @RequestBody MessagingDtos.SendMessage body, @AuthenticationPrincipal AuthenticatedUser actor) {
        return created(service.send(associationId, conversationId, body, actor));
    }
}
