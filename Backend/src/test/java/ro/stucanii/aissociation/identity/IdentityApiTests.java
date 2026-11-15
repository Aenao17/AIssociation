package ro.stucanii.aissociation.identity;

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

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:identity_api;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password="
})
class IdentityApiTests {
    @Autowired WebApplicationContext context;
    @Autowired JdbcClient jdbc;
    @Autowired PasswordEncoder encoder;
    MockMvc mvc;

    @BeforeEach
    void prepare() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        jdbc.sql("DELETE FROM auth_sessions").update();
        jdbc.sql("DELETE FROM association_memberships").update();
        jdbc.sql("DELETE FROM app_users").update();
        jdbc.sql("DELETE FROM associations").update();
    }

    String register(String email) throws Exception {
        String body = "{\"displayName\":\" Alex \",\"email\":\"" + email + "\",\"password\":\"example-password\"}";
        return mvc.perform(post("/api/auth/register").contentType("application/json").content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();
    }

    String login(String email) throws Exception {
        String response = mvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"email\":\"" + email + "\",\"password\":\"example-password\"}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.accessToken");
    }

    @Test
    void normalizesIdentityHashesPasswordAndRejectsDuplicateEmail() throws Exception {
        String user = register(" Alex@Example.com ");
        assertEquals("alex@example.com", JsonPath.read(user, "$.email"));
        assertEquals("Alex", JsonPath.read(user, "$.displayName"));
        String hash = jdbc.sql("SELECT password_hash FROM app_users").query(String.class).single();
        assertNotEquals("example-password", hash);
        assertTrue(encoder.matches("example-password", hash));
        mvc.perform(post("/api/auth/register").contentType("application/json")
                        .content("{\"displayName\":\"Other\",\"email\":\"ALEX@example.com\",\"password\":\"example-password\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_EXISTS"));
        assertEquals(1, jdbc.sql("SELECT COUNT(*) FROM app_users").query(Integer.class).single());
    }

    @Test
    void loginReturnsCurrentUserWithoutAutomaticallyGrantingMembership() throws Exception {
        register("alex@example.com");
        String token = login(" ALEX@example.com ");
        String stored = jdbc.sql("SELECT token_hash FROM auth_sessions").query(String.class).single();
        assertNotEquals(token, stored);
        assertEquals(IdentityService.hashToken(token), stored);
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("alex@example.com"))
                .andExpect(jsonPath("$.memberships").isEmpty())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.sessionId").doesNotExist())
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void returnsOnlyCurrentUsersMembershipsAndReflectsRoleChanges() throws Exception {
        String first = register("alex@example.com");
        String second = register("other@example.com");
        UUID userId = UUID.fromString(JsonPath.read(first, "$.id"));
        UUID otherId = UUID.fromString(JsonPath.read(second, "$.id"));
        UUID association = UUID.randomUUID();
        UUID privateAssociation = UUID.randomUUID();
        addAssociation(association, "Association One", "one");
        addAssociation(privateAssociation, "Association Two", "two");
        addMembership(association, userId, "MEMBER");
        addMembership(privateAssociation, otherId, "ADMIN");
        String token = login("alex@example.com");
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.memberships.length()").value(1))
                .andExpect(jsonPath("$.memberships[0].associationId").value(association.toString()))
                .andExpect(jsonPath("$.memberships[0].role").value("MEMBER"));
        jdbc.sql("UPDATE association_memberships SET role = 'ADMIN' WHERE user_id = ?").param(userId).update();
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.memberships[0].role").value("ADMIN"));
        jdbc.sql("DELETE FROM association_memberships WHERE user_id = ?").param(userId).update();
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.memberships").isEmpty());
    }

    @Test
    void unknownEmailAndWrongPasswordHaveTheSameError() throws Exception {
        register("alex@example.com");
        for (String email : new String[]{"unknown@example.com", "alex@example.com"}) {
            mvc.perform(post("/api/auth/login").contentType("application/json")
                            .content("{\"email\":\"" + email + "\",\"password\":\"wrong-password\"}"))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                    .andExpect(jsonPath("$.message").value("Email or password is incorrect."));
        }
    }

    @Test
    void rejectsMissingMalformedUnknownAndExpiredSessions() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").header("Authorization", "Basic bad")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + "a".repeat(43)))
                .andExpect(status().isUnauthorized());
        register("alex@example.com");
        String token = login("alex@example.com");
        jdbc.sql("UPDATE auth_sessions SET created_at = ?, expires_at = ?")
                .params(Timestamp.from(Instant.now().minusSeconds(7200)), Timestamp.from(Instant.now().minusSeconds(3600)))
                .update();
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesOnlyTheCurrentSession() throws Exception {
        register("alex@example.com");
        String first = login("alex@example.com");
        String second = login("alex@example.com");
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + first))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + first))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + second)).andExpect(status().isOk());
    }

    @Test
    void validatesRegistrationAndMalformedBodiesWithoutEchoingPasswords() throws Exception {
        mvc.perform(post("/api/auth/register").contentType("application/json")
                        .content("{\"displayName\":\" \",\"email\":\"bad\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
        mvc.perform(post("/api/auth/register").contentType("application/json").content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_JSON"));
        mvc.perform(post("/api/auth/register").contentType("application/json")
                        .content("{\"displayName\":\"Alex\",\"email\":\"alex@example.com\",\"password\":\"" + "é".repeat(40) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void securityChainPreservesCorsAndProtectsFuturePrivateEndpoints() throws Exception {
        mvc.perform(options("/api/me").header("Origin", "https://aissociation.vercel.app")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://aissociation.vercel.app"));
        mvc.perform(options("/api/me").header("Origin", "https://untrusted.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/me").header("Origin", "https://aissociation.vercel.app"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://aissociation.vercel.app"));
        mvc.perform(get("/api/associations/private")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    private void addAssociation(UUID id, String name, String slug) {
        jdbc.sql("INSERT INTO associations (id, name, slug, created_at) VALUES (?, ?, ?, ?)")
                .params(id, name, slug, Timestamp.from(Instant.now())).update();
    }
    private void addMembership(UUID association, UUID user, String role) {
        jdbc.sql("INSERT INTO association_memberships (id, association_id, user_id, role, created_at) VALUES (?, ?, ?, ?, ?)")
                .params(UUID.randomUUID(), association, user, role, Timestamp.from(Instant.now())).update();
    }
}
