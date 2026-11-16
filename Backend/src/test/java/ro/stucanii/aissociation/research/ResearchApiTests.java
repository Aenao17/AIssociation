package ro.stucanii.aissociation.research;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:research_api;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "app.platform-admin.user-ids=00000000-0000-0000-0000-000000000001"
})
class ResearchApiTests {
    @Autowired WebApplicationContext context;
    @Autowired JdbcClient jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired JsonMapper json;
    MockMvc mvc;

    @BeforeEach
    void prepare() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        jdbc.sql("DELETE FROM research_responses").update();
        jdbc.sql("DELETE FROM auth_sessions").update();
        jdbc.sql("DELETE FROM association_memberships").update();
        jdbc.sql("DELETE FROM associations").update();
        jdbc.sql("DELETE FROM app_users").update();
        for (int i = 1; i <= 2; i++) {
            jdbc.sql("INSERT INTO app_users (id, display_name, email, password_hash, created_at) VALUES (?, ?, ?, ?, ?)")
                    .params(UUID.fromString("00000000-0000-0000-0000-00000000000" + i), "Research tester",
                            "user" + i + "@example.test", encoder.encode("example-password"), Timestamp.from(Instant.now())).update();
        }
    }

    @Test
    void anonymousSubmissionIsStoredAndOnlyPlatformAdminCanReadIt() throws Exception {
        String body = body(UUID.randomUUID(), answers());
        mvc.perform(post("/api/public/research/responses").contentType("application/json").content(body))
                .andExpect(status().isNoContent()).andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get("/api/admin/research/responses")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/research/responses").header("Authorization", "Bearer " + login(2)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/research/responses").header("Authorization", "Bearer " + login(1)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$[0].answers.tasks[0]").value("Meetings"))
                .andExpect(jsonPath("$[0].answers.contactEmail").value(""))
                .andExpect(jsonPath("$[0].submittedAt").exists());
        mvc.perform(get("/api/public/research/responses")).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void repeatedSubmissionIsSafeButChangingTheSavedPayloadIsRejected() throws Exception {
        UUID id = UUID.randomUUID();
        var answers = answers();
        String body = body(id, answers);
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/public/research/responses").contentType("application/json").content(body))
                    .andExpect(status().isNoContent());
        }
        assertEquals(1, jdbc.sql("SELECT COUNT(*) FROM research_responses").query(Integer.class).single());
        answers.put("suggestions", "Different response");
        mvc.perform(post("/api/public/research/responses").contentType("application/json").content(body(id, answers)))
                .andExpect(status().isConflict());
    }

    @Test
    void validatesChoicesLengthsEmailAndRequiredFieldsWithoutSaving() throws Exception {
        var invalid = answers();
        invalid.put("challenges", List.of("Finding information", "Unclear responsibilities", "Recruiting teams", "Communication"));
        invalid.put("tools", List.of("Invented option"));
        invalid.put("contactEmail", "bad-email");
        invalid.put("barriers", "x".repeat(2001));
        invalid.remove("involvement");
        mvc.perform(post("/api/public/research/responses").contentType("application/json")
                        .content(body(UUID.randomUUID(), invalid)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        invalid = answers();
        invalid.put("tasks", List.of("Meetings", "Meetings"));
        mvc.perform(post("/api/public/research/responses").contentType("application/json")
                        .content(body(UUID.randomUUID(), invalid)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("DUPLICATE_CHOICES"));
        assertEquals(0, jdbc.sql("SELECT COUNT(*) FROM research_responses").query(Integer.class).single());
    }

    @Test
    void responsePagesAreBoundedAndNegativePagesAreRejected() throws Exception {
        String token = login(1);
        for (int i = 0; i < 51; i++) {
            jdbc.sql("INSERT INTO research_responses (id, answers, submitted_at) VALUES (?, ?, ?)")
                    .params(UUID.randomUUID(), json.writeValueAsString(answers()), Timestamp.from(Instant.now().plusSeconds(i))).update();
        }
        mvc.perform(get("/api/admin/research/responses").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(50));
        mvc.perform(get("/api/admin/research/responses?page=1").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/admin/research/responses?page=-1").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    private String login(int number) throws Exception {
        String response = mvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"email\":\"user" + number + "@example.test\",\"password\":\"example-password\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.accessToken");
    }

    private String body(UUID id, Map<String, Object> answers) {
        return json.writeValueAsString(Map.of("submissionId", id, "answers", answers));
    }

    private Map<String, Object> answers() {
        Map<String, Object> answers = new LinkedHashMap<>();
        answers.put("involvement", "Member");
        answers.put("memberCount", "21–50");
        answers.put("recruitment", List.of("Open call"));
        answers.put("tasks", List.of("Meetings"));
        answers.put("tools", List.of("Google Workspace"));
        answers.put("challenges", List.of("Finding information"));
        answers.put("capabilities", List.of("Tasks"));
        for (String key : List.of("organization", "recruitmentDetails", "taskDetails", "toolDetails", "challengeDetails",
                "registration", "capabilityDetails", "barriers", "suggestions", "associationName", "contactEmail")) {
            answers.put(key, "");
        }
        return answers;
    }
}
