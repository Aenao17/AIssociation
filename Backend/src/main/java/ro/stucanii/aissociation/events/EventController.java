package ro.stucanii.aissociation.events;

import java.util.*;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ro.stucanii.aissociation.identity.AuthenticatedUser;

@RestController
@RequestMapping("/api/associations/{associationId}/events")
public class EventController {
    private final EventService events;
    public EventController(EventService events) { this.events=events; }
    @GetMapping
    ResponseEntity<List<EventDtos.OrganizerEvent>> list(@PathVariable UUID associationId, @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(events.list(associationId,actor));
    }
    @GetMapping("/{id}")
    ResponseEntity<EventDtos.OrganizerEvent> detail(@PathVariable UUID associationId,@PathVariable UUID id,@AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(events.detail(associationId,id,actor));
    }
    @PostMapping
    ResponseEntity<EventDtos.OrganizerEvent> create(@PathVariable UUID associationId,@Valid @RequestBody EventDtos.SaveEvent body,@AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(events.create(associationId,body,actor));
    }
    @PutMapping("/{id}")
    ResponseEntity<EventDtos.OrganizerEvent> edit(@PathVariable UUID associationId,@PathVariable UUID id,@Valid @RequestBody EventDtos.SaveEvent body,@AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(events.edit(associationId,id,body,actor));
    }
    @PostMapping("/{id}/{action:publish|cancel|complete}")
    ResponseEntity<EventDtos.OrganizerEvent> transition(@PathVariable UUID associationId,@PathVariable UUID id,@PathVariable String action,@AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(events.transition(associationId,id,action,actor));
    }
}
