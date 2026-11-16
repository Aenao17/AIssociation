package ro.stucanii.aissociation.research;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;
import ro.stucanii.aissociation.identity.ApiException;
import ro.stucanii.aissociation.identity.AuthenticatedUser;
import ro.stucanii.aissociation.platform.PlatformAdminAccess;

@Service
public class ResearchService {
    private final JdbcClient jdbc;
    private final PlatformAdminAccess access;
    private final Clock clock;
    private final JsonMapper json;

    public ResearchService(JdbcClient jdbc, PlatformAdminAccess access, Clock clock, JsonMapper json) {
        this.jdbc = jdbc;
        this.access = access;
        this.clock = clock;
        this.json = json;
    }

    public void submit(ResearchDtos.Submission submission) {
        var answers = submission.answers();
        for (var choices : List.of(answers.recruitment(), answers.tasks(), answers.tools(),
                answers.challenges(), answers.capabilities())) {
            if (new HashSet<>(choices).size() != choices.size()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "DUPLICATE_CHOICES", "Select each option only once.");
            }
        }
        String payload = json.writeValueAsString(answers);
        try {
            jdbc.sql("INSERT INTO research_responses (id, answers, submitted_at) VALUES (?, ?, ?)")
                    .params(submission.submissionId(), payload, Timestamp.from(clock.instant())).update();
        } catch (DuplicateKeyException ex) {
            String previous = jdbc.sql("SELECT answers FROM research_responses WHERE id = ?")
                    .param(submission.submissionId()).query(String.class).single();
            if (!previous.equals(payload)) {
                throw new ApiException(HttpStatus.CONFLICT, "SUBMISSION_EXISTS",
                        "This submission was already saved. Reload the page to send a different response.");
            }
        }
    }

    public List<ResearchDtos.Response> list(AuthenticatedUser user, int page) {
        access.requirePlatformAdmin(user);
        if (page < 0 || page > 1000000) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGE", "Provide a valid page number.");
        }
        return jdbc.sql("SELECT id, answers, submitted_at FROM research_responses ORDER BY submitted_at DESC, id DESC LIMIT 50 OFFSET ?")
                .param(page * 50)
                .query((rs, row) -> new ResearchDtos.Response(rs.getObject("id", UUID.class),
                        rs.getTimestamp("submitted_at").toInstant(),
                        json.readValue(rs.getString("answers"), ResearchDtos.Answers.class))).list();
    }
}
