package ro.stucanii.aissociation.tasks;

import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ro.stucanii.aissociation.identity.AuthenticatedUser;

@RestController
@RequestMapping("/api/associations/{associationId}")
public class TaskController {
    private final TaskService service;
    public TaskController(TaskService service) { this.service = service; }
    private <T> ResponseEntity<T> ok(T body) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body); }
    private <T> ResponseEntity<T> created(T body) { return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(body); }

    @GetMapping("/tasks/mine")
    ResponseEntity<List<TaskDtos.Task>> mine(@PathVariable UUID associationId, @AuthenticationPrincipal AuthenticatedUser actor) {
        return ok(service.mine(associationId, actor));
    }

    @GetMapping("/projects/{projectId}/tasks")
    ResponseEntity<List<TaskDtos.Task>> list(@PathVariable UUID associationId, @PathVariable UUID projectId,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ok(service.list(associationId, projectId, actor));
    }

    @GetMapping("/projects/{projectId}/tasks/assignee-options")
    ResponseEntity<List<TaskDtos.AssigneeOption>> assigneeOptions(@PathVariable UUID associationId, @PathVariable UUID projectId,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return ok(service.assigneeOptions(associationId, projectId, actor));
    }

    @PostMapping("/projects/{projectId}/tasks")
    ResponseEntity<TaskDtos.Task> create(@PathVariable UUID associationId, @PathVariable UUID projectId,
            @Valid @RequestBody TaskDtos.CreateTask body, @AuthenticationPrincipal AuthenticatedUser actor) {
        return created(service.create(associationId, projectId, body, actor));
    }

    @GetMapping("/projects/{projectId}/tasks/{taskId}")
    ResponseEntity<TaskDtos.Task> detail(@PathVariable UUID associationId, @PathVariable UUID projectId,
            @PathVariable UUID taskId, @AuthenticationPrincipal AuthenticatedUser actor) {
        return ok(service.detail(associationId, projectId, taskId, actor));
    }

    @PutMapping("/projects/{projectId}/tasks/{taskId}")
    ResponseEntity<TaskDtos.Task> edit(@PathVariable UUID associationId, @PathVariable UUID projectId,
            @PathVariable UUID taskId, @Valid @RequestBody TaskDtos.EditTask body, @AuthenticationPrincipal AuthenticatedUser actor) {
        return ok(service.edit(associationId, projectId, taskId, body, actor));
    }

    @DeleteMapping("/projects/{projectId}/tasks/{taskId}")
    ResponseEntity<Void> delete(@PathVariable UUID associationId, @PathVariable UUID projectId,
            @PathVariable UUID taskId, @AuthenticationPrincipal AuthenticatedUser actor) {
        service.delete(associationId, projectId, taskId, actor);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @PatchMapping("/projects/{projectId}/tasks/{taskId}/status")
    ResponseEntity<TaskDtos.Task> status(@PathVariable UUID associationId, @PathVariable UUID projectId,
            @PathVariable UUID taskId, @Valid @RequestBody TaskDtos.ChangeStatus body, @AuthenticationPrincipal AuthenticatedUser actor) {
        return ok(service.changeStatus(associationId, projectId, taskId, body, actor));
    }
}
