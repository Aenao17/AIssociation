package ro.stucanii.aissociation.events;

import java.time.Instant;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/public/events")
public class PublicEventController {
    private final EventService events;
    public PublicEventController(EventService events) { this.events=events; }
    @GetMapping
    EventDtos.Page list(@RequestParam(required=false) UUID associationId,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return events.discover(associationId,from,to,page,size);
    }
    @GetMapping("/{id}")
    EventDtos.PublicEvent detail(@PathVariable UUID id) { return events.publicDetail(id); }
}
