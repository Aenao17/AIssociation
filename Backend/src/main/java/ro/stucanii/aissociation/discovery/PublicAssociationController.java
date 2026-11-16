package ro.stucanii.aissociation.discovery;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.*;
import ro.stucanii.aissociation.identity.ApiException;

/** FR-11: intentionally public identity only; never reuse administrative DTOs. */
@RestController
@RequestMapping("/api/public/associations")
public class PublicAssociationController {
    public record PublicAssociation(UUID id, String name, String slug) {}
    public record PublicProfile(UUID id, String name, String slug, String description, String website) {}

    private final JdbcClient jdbc;

    public PublicAssociationController(JdbcClient jdbc) { this.jdbc = jdbc; }

    @GetMapping
    List<PublicAssociation> list() {
        return jdbc.sql("SELECT id, name, slug FROM associations ORDER BY LOWER(name), slug")
                .query((rs, row) -> new PublicAssociation(rs.getObject("id", UUID.class),
                        rs.getString("name"), rs.getString("slug"))).list();
    }

    @GetMapping("/{slug}")
    PublicProfile detail(@PathVariable String slug) {
        return jdbc.sql("SELECT id, name, slug, description, website FROM associations WHERE slug = ?")
                .param(slug).query((rs, row) -> new PublicProfile(rs.getObject("id", UUID.class),
                        rs.getString("name"), rs.getString("slug"),rs.getString("description"),rs.getString("website"))).optional()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "ASSOCIATION_NOT_FOUND", "Association not found."));
    }
}
