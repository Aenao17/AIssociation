package ro.stucanii.aissociation.associations;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
        "spring.datasource.url=jdbc:h2:mem:workspace_api;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "app.platform-admin.user-ids=00000000-0000-0000-0000-000000000005"
})
class AssociationWorkspaceApiTests {
    private static final UUID A = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID B = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID ADMIN_A = userId(1), ADMIN_B = userId(2), MEMBER = userId(3), NEW = userId(4), PLATFORM = userId(5);
    @Autowired WebApplicationContext context;
    @Autowired JdbcClient jdbc;
    @Autowired PasswordEncoder encoder;
    MockMvc mvc;

    @BeforeEach
    void prepare() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        for (String table : List.of("auth_sessions", "association_memberships", "associations", "app_users")) jdbc.sql("DELETE FROM " + table).update();
        for (int i = 1; i <= 5; i++) jdbc.sql("INSERT INTO app_users (id, display_name, email, password_hash, created_at) VALUES (?, ?, ?, ?, ?)")
                .params(userId(i), "User " + i, "user" + i + "@example.test", encoder.encode("example-password"), Timestamp.from(Instant.now())).update();
        for (UUID id : List.of(A, B)) jdbc.sql("INSERT INTO associations (id, name, slug, created_at) VALUES (?, ?, ?, ?)")
                .params(id, id.equals(A) ? "Association A" : "Association B", id.equals(A) ? "assoc-a" : "assoc-b", Timestamp.from(Instant.now())).update();
        addMembership(A, ADMIN_A, "ADMIN"); addMembership(B, ADMIN_B, "ADMIN"); addMembership(A, MEMBER, "MEMBER");
    }

    @Test
    void membersEnterTheirWorkspaceButNotAnotherAssociation() throws Exception {
        String token = login(3);
        mvc.perform(get("/api/workspaces/assoc-a").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.associationId").value(A.toString()))
                .andExpect(jsonPath("$.role").value("MEMBER"));
        mvc.perform(get("/api/workspaces/assoc-b").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        mvc.perform(get("/api/workspaces/assoc-a")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/workspaces/assoc-a").header("Authorization", "Bearer " + login(5)))
                .andExpect(status().isForbidden()); // Platform access is not association membership.
    }

    @Test
    void onlyTheAssociationsAdminsCanReadOrManageTheRoster() throws Exception {
        String member = login(3), otherAdmin = login(2);
        for (String token : List.of(member, otherAdmin)) {
            mvc.perform(get(base(A)).header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
            mvc.perform(post(base(A)).header("Authorization", "Bearer " + token).contentType("application/json")
                            .content(addBody("user4@example.test", "ADMIN"))).andExpect(status().isForbidden());
        }
        mvc.perform(get(base(A)).header("Authorization", "Bearer " + login(1)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void adminAddsExistingAccountAndTheCurrentUserImmediatelySeesMembership() throws Exception {
        String admin = login(1), newcomer = login(4);
        mvc.perform(post(base(A)).header("Authorization", "Bearer " + admin).contentType("application/json")
                        .content(addBody(" USER4@example.test ", "MEMBER")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.userId").value(NEW.toString()));
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + newcomer))
                .andExpect(jsonPath("$.memberships[0].associationId").value(A.toString()));
        mvc.perform(get("/api/workspaces/assoc-a").header("Authorization", "Bearer " + newcomer)).andExpect(status().isOk());
        mvc.perform(post(base(A)).header("Authorization", "Bearer " + admin).contentType("application/json")
                        .content(addBody("user4@example.test", "MEMBER")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MEMBER_EXISTS"));
        mvc.perform(post(base(A)).header("Authorization", "Bearer " + admin).contentType("application/json")
                        .content(addBody("unknown@example.test", "MEMBER")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
    }

    @Test
    void adminChangesRoleButMemberCannotPromoteThemself() throws Exception {
        UUID id = membership(A, MEMBER);
        mvc.perform(patch(base(A) + "/" + id).header("Authorization", "Bearer " + login(3))
                        .contentType("application/json").content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch(base(A) + "/" + id).header("Authorization", "Bearer " + login(1))
                        .contentType("application/json").content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("ADMIN"));
        mvc.perform(get("/api/workspaces/assoc-a").header("Authorization", "Bearer " + login(3)))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void crossAssociationMembershipIdentifiersCannotBeChangedOrDeleted() throws Exception {
        String admin = login(1);
        UUID other = membership(B, ADMIN_B);
        mvc.perform(patch(base(A) + "/" + other).header("Authorization", "Bearer " + admin)
                        .contentType("application/json").content("{\"role\":\"MEMBER\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete(base(A) + "/" + other).header("Authorization", "Bearer " + admin)).andExpect(status().isNotFound());
        assertEquals("ADMIN", jdbc.sql("SELECT role FROM association_memberships WHERE id = ?").param(other).query(String.class).single());
    }

    @Test
    void lastAdministratorCannotBeRemovedOrDemotedButCanKeepTheirRole() throws Exception {
        String admin = login(1);
        UUID id = membership(A, ADMIN_A);
        mvc.perform(delete(base(A) + "/" + id).header("Authorization", "Bearer " + admin))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LAST_ADMIN_REQUIRED"));
        mvc.perform(patch(base(A) + "/" + id).header("Authorization", "Bearer " + admin)
                        .contentType("application/json").content("{\"role\":\"MEMBER\"}"))
                .andExpect(status().isConflict());
        mvc.perform(patch(base(A) + "/" + id).header("Authorization", "Bearer " + admin)
                        .contentType("application/json").content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void removalRevokesWorkspaceAndRosterAccessWithoutRequiringNewLogin() throws Exception {
        String member = login(3);
        mvc.perform(delete(base(A) + "/" + membership(A, MEMBER)).header("Authorization", "Bearer " + login(1)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/workspaces/assoc-a").header("Authorization", "Bearer " + member)).andExpect(status().isForbidden());
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + member)).andExpect(jsonPath("$.memberships").isEmpty());
        // A removed administrator's existing session also cannot manage members.
        addMembership(A, NEW, "ADMIN");
        String secondAdmin = login(4);
        mvc.perform(delete(base(A) + "/" + membership(A, NEW)).header("Authorization", "Bearer " + login(1)))
                .andExpect(status().isNoContent());
        mvc.perform(get(base(A)).header("Authorization", "Bearer " + secondAdmin)).andExpect(status().isForbidden());
    }

    @Test
    void simultaneousSelfDemotionsKeepOneAdministrator() throws Exception {
        addMembership(A, NEW, "ADMIN");
        String first = login(1), second = login(4);
        UUID firstMembership = membership(A, ADMIN_A), secondMembership = membership(A, NEW);
        var pool = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2); var go = new CountDownLatch(1);
        try {
            var f1 = pool.submit(() -> { ready.countDown(); go.await(); return demote(first, firstMembership); });
            var f2 = pool.submit(() -> { ready.countDown(); go.await(); return demote(second, secondMembership); });
            assertTrue(ready.await(5, TimeUnit.SECONDS)); go.countDown();
            var statuses = List.of(f1.get(10, TimeUnit.SECONDS), f2.get(10, TimeUnit.SECONDS)).stream().sorted().toList();
            assertEquals(List.of(200, 409), statuses);
            assertEquals(1L, jdbc.sql("SELECT COUNT(*) FROM association_memberships WHERE association_id = ? AND role = 'ADMIN'")
                    .param(A).query(Long.class).single());
        } finally { go.countDown(); pool.shutdownNow(); }
    }

    @Test
    void malformedIdentifiersAndInvalidRoleOrEmailReturnValidationErrors() throws Exception {
        String admin = login(1);
        mvc.perform(get("/api/associations/not-a-uuid/members").header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(post(base(A)).header("Authorization", "Bearer " + admin).contentType("application/json")
                        .content("{\"email\":\"bad\",\"role\":null}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.role").exists());
        mvc.perform(post(base(A)).header("Authorization", "Bearer " + admin).contentType("application/json")
                        .content(addBody("user4@example.test", "PLATFORM_ADMIN")))
                .andExpect(status().isBadRequest());
    }

    private int demote(String token, UUID id) throws Exception {
        return mvc.perform(patch(base(A) + "/" + id).header("Authorization", "Bearer " + token)
                .contentType("application/json").content("{\"role\":\"MEMBER\"}")).andReturn().getResponse().getStatus();
    }
    private String login(int user) throws Exception {
        String result = mvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"email\":\"user" + user + "@example.test\",\"password\":\"example-password\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(result, "$.accessToken");
    }
    private UUID addMembership(UUID association, UUID user, String role) {
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO association_memberships (id, association_id, user_id, role, created_at) VALUES (?, ?, ?, ?, ?)")
                .params(id, association, user, role, Timestamp.from(Instant.now())).update(); return id;
    }
    private UUID membership(UUID association, UUID user) {
        return jdbc.sql("SELECT id FROM association_memberships WHERE association_id = ? AND user_id = ?")
                .params(association, user).query(UUID.class).single();
    }
    private static UUID userId(int n) { return UUID.fromString("00000000-0000-0000-0000-" + String.format("%012d", n)); }
    private String base(UUID association) { return "/api/associations/" + association + "/members"; }
    private String addBody(String email, String role) { return "{\"email\":\"" + email + "\",\"role\":\"" + role + "\"}"; }
}
