package ro.stucanii.aissociation.projects;

import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ro.stucanii.aissociation.identity.AuthenticatedUser;

@RestController
@RequestMapping("/api/associations/{associationId}/projects")
public class ProjectController {
    private final ProjectService service;
    public ProjectController(ProjectService service) { this.service = service; }
    private <T> ResponseEntity<T> ok(T body) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body); }
    private <T> ResponseEntity<T> created(T body) { return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(body); }

    @GetMapping
    ResponseEntity<List<ProjectDtos.Summary>> list(@PathVariable UUID associationId, @AuthenticationPrincipal AuthenticatedUser actor) {
        return ok(service.list(associationId, actor));
    }
    @PostMapping
    ResponseEntity<ProjectDtos.Overview> create(@PathVariable UUID associationId, @Valid @RequestBody ProjectDtos.CreateProject body,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return created(service.create(associationId, body, actor));
    }
    @GetMapping("/{projectId}")
    ResponseEntity<ProjectDtos.Overview> detail(@PathVariable UUID associationId, @PathVariable UUID projectId,
            @AuthenticationPrincipal AuthenticatedUser actor) { return ok(service.detail(associationId, projectId, actor)); }
    @DeleteMapping("/{projectId}")
    ResponseEntity<Void> delete(@PathVariable UUID associationId, @PathVariable UUID projectId,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        service.delete(associationId, projectId, actor);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
    @PatchMapping("/{projectId}")
    ResponseEntity<ProjectDtos.Overview> edit(@PathVariable UUID associationId, @PathVariable UUID projectId,
            @Valid @RequestBody ProjectDtos.EditProject body, @AuthenticationPrincipal AuthenticatedUser actor) {
        return ok(service.edit(associationId, projectId, body, actor));
    }
    @PutMapping("/{projectId}/managers/{userId}")
    ResponseEntity<ProjectDtos.TeamMember> appoint(@PathVariable UUID associationId, @PathVariable UUID projectId,
            @PathVariable UUID userId, @AuthenticationPrincipal AuthenticatedUser actor) {
        return ok(service.appointManager(associationId, projectId, userId, actor));
    }
    @GetMapping("/{projectId}/member-options")
    ResponseEntity<List<ProjectDtos.MemberOption>> memberOptions(@PathVariable UUID associationId, @PathVariable UUID projectId,
            @AuthenticationPrincipal AuthenticatedUser actor) { return ok(service.memberOptions(associationId, projectId, actor)); }
    @GetMapping("/{projectId}/recruitment")
    ResponseEntity<ProjectDtos.Overview> recruitment(@PathVariable UUID associationId, @PathVariable UUID projectId,
            @AuthenticationPrincipal AuthenticatedUser actor) { return ok(service.overview(associationId, projectId, actor)); }
    @PutMapping("/{projectId}/recruitment")
    ResponseEntity<ProjectDtos.Overview> configure(@PathVariable UUID associationId, @PathVariable UUID projectId,
            @Valid @RequestBody ProjectDtos.ConfigureRecruitment body, @AuthenticationPrincipal AuthenticatedUser actor) {
        return ok(service.configureRecruitment(associationId, projectId, body, actor));
    }
    @PostMapping("/{projectId}/positions")
    ResponseEntity<ProjectDtos.Position> addPosition(@PathVariable UUID associationId, @PathVariable UUID projectId,
            @Valid @RequestBody ProjectDtos.SavePosition body, @AuthenticationPrincipal AuthenticatedUser actor) {
        return created(service.savePosition(associationId, projectId, null, body, actor));
    }
    @PatchMapping("/{projectId}/positions/{positionId}")
    ResponseEntity<ProjectDtos.Position> editPosition(@PathVariable UUID associationId, @PathVariable UUID projectId,
            @PathVariable UUID positionId, @Valid @RequestBody ProjectDtos.SavePosition body, @AuthenticationPrincipal AuthenticatedUser actor) {
        return ok(service.savePosition(associationId, projectId, positionId, body, actor));
    }
    @PostMapping("/{projectId}/applications")
    ResponseEntity<ProjectDtos.Application> apply(@PathVariable UUID associationId, @PathVariable UUID projectId,
            @Valid @RequestBody ProjectDtos.Apply body, @AuthenticationPrincipal AuthenticatedUser actor) {
        return created(service.apply(associationId, projectId, body, actor));
    }
    @GetMapping("/{projectId}/applications/mine")
    ResponseEntity<List<ProjectDtos.Application>> mine(@PathVariable UUID associationId, @PathVariable UUID projectId,
            @AuthenticationPrincipal AuthenticatedUser actor) { return ok(service.applications(associationId, projectId, true, actor)); }
    @GetMapping("/{projectId}/applications")
    ResponseEntity<List<ProjectDtos.Application>> applications(@PathVariable UUID associationId, @PathVariable UUID projectId,
            @AuthenticationPrincipal AuthenticatedUser actor) { return ok(service.applications(associationId, projectId, false, actor)); }
    @PostMapping("/{projectId}/applications/{applicationId}/accept")
    ResponseEntity<ProjectDtos.Application> accept(@PathVariable UUID associationId, @PathVariable UUID projectId,
            @PathVariable UUID applicationId, @Valid @RequestBody ProjectDtos.Supervisor body, @AuthenticationPrincipal AuthenticatedUser actor) {
        return ok(service.accept(associationId, projectId, applicationId, body, actor));
    }
    @PostMapping("/{projectId}/applications/{applicationId}/reject")
    ResponseEntity<ProjectDtos.Application> reject(@PathVariable UUID associationId, @PathVariable UUID projectId,
            @PathVariable UUID applicationId, @AuthenticationPrincipal AuthenticatedUser actor) {
        return ok(service.reject(associationId, projectId, applicationId, actor));
    }
    @GetMapping("/{projectId}/team")
    ResponseEntity<List<ProjectDtos.TeamMember>> team(@PathVariable UUID associationId, @PathVariable UUID projectId,
            @AuthenticationPrincipal AuthenticatedUser actor) { return ok(service.team(associationId, projectId, actor)); }
    @PutMapping("/{projectId}/department-managers/{memberId}")
    ResponseEntity<ProjectDtos.TeamMember> departmentManager(@PathVariable UUID associationId, @PathVariable UUID projectId,
            @PathVariable UUID memberId, @AuthenticationPrincipal AuthenticatedUser actor) {
        return ok(service.appointDepartmentManager(associationId, projectId, memberId, actor));
    }
    @PatchMapping("/{projectId}/team/{memberId}/reporting")
    ResponseEntity<ProjectDtos.TeamMember> reporting(@PathVariable UUID associationId, @PathVariable UUID projectId,
            @PathVariable UUID memberId, @Valid @RequestBody ProjectDtos.Supervisor body, @AuthenticationPrincipal AuthenticatedUser actor) {
        return ok(service.changeReporting(associationId, projectId, memberId, body, actor));
    }
}
