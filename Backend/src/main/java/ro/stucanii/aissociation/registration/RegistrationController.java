package ro.stucanii.aissociation.registration;

import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ro.stucanii.aissociation.identity.AuthenticatedUser;


@RestController
public class RegistrationController {
    private final RegistrationService registrations;
    public RegistrationController(RegistrationService registrations) { this.registrations = registrations; }

    private static <T> ResponseEntity<T> ok(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    @GetMapping("/api/public/events/{eventId}/availability")
    ResponseEntity<RegistrationDtos.Availability> availability(@PathVariable UUID eventId) {
        return ok(registrations.availability(eventId));
    }

    @GetMapping("/api/events/{eventId}/registration")
    ResponseEntity<RegistrationDtos.MyRegistration> mine(@PathVariable UUID eventId,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ok(registrations.mine(eventId, actor));
    }

    @PostMapping("/api/events/{eventId}/registration")
    ResponseEntity<RegistrationDtos.MyRegistration> register(@PathVariable UUID eventId,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(registrations.register(eventId, actor));
    }

    @DeleteMapping("/api/events/{eventId}/registration")
    ResponseEntity<RegistrationDtos.MyRegistration> cancel(@PathVariable UUID eventId,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ok(registrations.cancel(eventId, actor));
    }

    @GetMapping("/api/associations/{associationId}/events/{eventId}/registrations")
    ResponseEntity<RegistrationDtos.Roster> roster(@PathVariable UUID associationId, @PathVariable UUID eventId,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ok(registrations.roster(associationId, eventId, actor));
    }

    @DeleteMapping("/api/associations/{associationId}/events/{eventId}/registrations/{registrationId}")
    ResponseEntity<RegistrationDtos.RosterEntry> cancelAsOrganizer(@PathVariable UUID associationId,
            @PathVariable UUID eventId, @PathVariable UUID registrationId,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ok(registrations.cancelAsOrganizer(associationId, eventId, registrationId, actor));
    }

    @PutMapping("/api/associations/{associationId}/events/{eventId}/registrations/{registrationId}/attendance")
    ResponseEntity<RegistrationDtos.RosterEntry> attendance(@PathVariable UUID associationId,
            @PathVariable UUID eventId, @PathVariable UUID registrationId,
            @Valid @RequestBody RegistrationDtos.SetAttendance body,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ok(registrations.setAttendance(associationId, eventId, registrationId, body.attended(), actor));
    }
}