package ro.stucanii.aissociation.research;

import java.util.List;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ro.stucanii.aissociation.identity.AuthenticatedUser;

@RestController
public class ResearchController {
    private final ResearchService service;

    public ResearchController(ResearchService service) {
        this.service = service;
    }

    @PostMapping("/api/public/research/responses")
    ResponseEntity<Void> submit(@Valid @RequestBody ResearchDtos.Submission submission) {
        service.submit(submission);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @GetMapping("/api/admin/research/responses")
    ResponseEntity<List<ResearchDtos.Response>> list(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "0") int page) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.list(user, page));
    }
}
