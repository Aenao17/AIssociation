package ro.stucanii.aissociation.platform;

import java.sql.Timestamp;
import java.time.Instant;
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
import ro.stucanii.aissociation.identity.ApiException;
import ro.stucanii.aissociation.identity.AuthenticatedUser;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:platform_api;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "app.platform-admin.user-ids=00000000-0000-0000-0000-000000000001"
})
class PlatformAssociationApiTests {
    private static final UUID PLATFORM_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID MEMBER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    @Autowired WebApplicationContext context;
    @Autowired JdbcClient jdbc;
    @Autowired PasswordEncoder encoder;
    MockMvc mvc;

    @BeforeEach
    void prepare() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        jdbc.sql("DELETE FROM auth_sessions").update();
        jdbc.sql("DELETE FROM association_memberships").update();
        jdbc.sql("DELETE FROM associations").update();
        jdbc.sql("DELETE FROM app_users").update();
        addUser(PLATFORM_ID, "platform@example.test");
        addUser(MEMBER_ID, "member@example.test");
    }

    @Test
    void platformAdminCreatesAssociationAndAssignsExistingAccount() throws Exception {
        String token = login("platform@example.test");
        String result = mvc.perform(post("/api/admin/associations").header("Authorization", "Bearer " + token)
                        .contentType("application/json").content(body(" Tech Society ", " TECH-SOCIETY ", " MEMBER@example.test ")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.name").value("Tech Society"))
                .andExpect(jsonPath("$.slug").value("tech-society"))
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(JsonPath.read(result, "$.id"));
        assertEquals(MEMBER_ID, jdbc.sql("SELECT user_id FROM association_memberships WHERE association_id = ?")
                .param(id).query(UUID.class).single());
        assertEquals("ADMIN", jdbc.sql("SELECT role FROM association_memberships WHERE association_id = ?")
                .param(id).query(String.class).single());
        mvc.perform(get("/api/admin/associations").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(id.toString()));
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.platformAdmin").value(true)).andExpect(jsonPath("$.memberships").isEmpty());
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + login("member@example.test")))
                .andExpect(jsonPath("$.platformAdmin").value(false))
                .andExpect(jsonPath("$.memberships[0].role").value("ADMIN"));
    }

    @Test
    void ordinaryAndAssociationAdminsCannotCreateOrListAssociations() throws Exception {
        String platformToken = login("platform@example.test");
        mvc.perform(post("/api/admin/associations").header("Authorization", "Bearer " + platformToken)
                        .contentType("application/json").content(body("One", "one", "member@example.test")))
                .andExpect(status().isCreated());
        String memberToken = login("member@example.test");
        mvc.perform(get("/api/admin/associations").header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PLATFORM_ADMIN_REQUIRED"));
        mvc.perform(post("/api/admin/associations").header("Authorization", "Bearer " + memberToken)
                        .contentType("application/json").content(body("Two", "two", "member@example.test")))
                .andExpect(status().isForbidden());
        assertEquals(1, jdbc.sql("SELECT COUNT(*) FROM associations").query(Integer.class).single());
    }

    @Test
    void missingAuthenticationIsRejected() throws Exception {
        mvc.perform(get("/api/admin/associations")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/associations").contentType("application/json")
                        .content(body("One", "one", "member@example.test")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingInitialAdminAndDuplicateSlugLeaveNoPartialRecords() throws Exception {
        String token = login("platform@example.test");
        mvc.perform(post("/api/admin/associations").header("Authorization", "Bearer " + token)
                        .contentType("application/json").content(body("Missing", "missing", "unknown@example.test")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("INITIAL_ADMIN_NOT_FOUND"));
        assertEquals(0, jdbc.sql("SELECT COUNT(*) FROM associations").query(Integer.class).single());
        mvc.perform(post("/api/admin/associations").header("Authorization", "Bearer " + token)
                        .contentType("application/json").content(body("One", "same", "member@example.test")))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/admin/associations").header("Authorization", "Bearer " + token)
                        .contentType("application/json").content(body("Duplicate", "SAME", "platform@example.test")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SLUG_EXISTS"));
        assertEquals(1, jdbc.sql("SELECT COUNT(*) FROM associations").query(Integer.class).single());
        assertEquals(1, jdbc.sql("SELECT COUNT(*) FROM association_memberships").query(Integer.class).single());
    }

    @Test
    void validatesAssociationInput() throws Exception {
        String token = login("platform@example.test");
        mvc.perform(post("/api/admin/associations").header("Authorization", "Bearer " + token)
                        .contentType("application/json").content(body(" ", "bad/slug", "invalid")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.slug").exists()).andExpect(jsonPath("$.fieldErrors.initialAdminEmail").exists());
        assertEquals(0, jdbc.sql("SELECT COUNT(*) FROM associations").query(Integer.class).single());
    }

    @Test
    void publicSignupCannotGrantPlatformAccess() throws Exception {
        mvc.perform(post("/api/auth/register").contentType("application/json")
                        .content("{\"displayName\":\"New\",\"email\":\"new@example.test\",\"password\":\"example-password\",\"platformAdmin\":true}"))
                .andExpect(status().isCreated());
        String token = login("new@example.test");
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.platformAdmin").value(false));
        mvc.perform(get("/api/admin/associations").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void emptyPlatformAllowlistIsClosedAndInvalidConfigurationFails() {
        var access = new PlatformAdminAccess("");
        assertFalse(access.isPlatformAdmin(PLATFORM_ID));
        assertThrows(ApiException.class, () -> access.requirePlatformAdmin(
                new AuthenticatedUser(PLATFORM_ID, "Admin", "platform@example.test", UUID.randomUUID())));
        assertThrows(IllegalArgumentException.class, () -> new PlatformAdminAccess("not-a-uuid"));
    }

    private String login(String email) throws Exception {
        String response = mvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"email\":\"" + email + "\",\"password\":\"example-password\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.accessToken");
    }

    private String body(String name, String slug, String email) {
        return "{\"name\":\"" + name + "\",\"slug\":\"" + slug + "\",\"initialAdminEmail\":\"" + email + "\"}";
    }

    private void addUser(UUID id, String email) {
        jdbc.sql("INSERT INTO app_users (id, display_name, email, password_hash, created_at) VALUES (?, ?, ?, ?, ?)")
                .params(id, "Test user", email, encoder.encode("example-password"), Timestamp.from(Instant.now())).update();
    }
}
