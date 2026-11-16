package ro.stucanii.aissociation.projects;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import ro.stucanii.aissociation.associations.MembershipDtos;
import ro.stucanii.aissociation.associations.MembershipService;
import ro.stucanii.aissociation.associations.AssociationRole;
import ro.stucanii.aissociation.identity.ApiException;
import ro.stucanii.aissociation.identity.AuthenticatedUser;
import com.jayway.jsonpath.JsonPath;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Run unchanged on H2 or a dedicated disposable PostgreSQL database via PROJECT_TEST_DB_* variables. */
@SpringBootTest(properties = {
        "spring.datasource.url=${PROJECT_TEST_DB_URL:jdbc:h2:mem:project_workflow;MODE=PostgreSQL;DB_CLOSE_DELAY=-1}",
        "spring.datasource.driver-class-name=${PROJECT_TEST_DB_DRIVER:org.h2.Driver}",
        "spring.datasource.username=${PROJECT_TEST_DB_USER:sa}",
        "spring.datasource.password=${PROJECT_TEST_DB_PASSWORD:}"
})
class ProjectWorkflowTests {
    static final UUID A = UUID.fromString("20000000-0000-0000-0000-000000000001");
    static final UUID B = UUID.fromString("20000000-0000-0000-0000-000000000002");
    @Autowired WebApplicationContext context;
    @Autowired JdbcClient jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired ProjectService service;
    @Autowired ProjectAccess access;
    @Autowired MembershipService memberships;
    @Autowired DataSource dataSource;
    MockMvc mvc;
    UUID project, otherProject, foreignProject, engineeringPosition, designPosition;
    UUID root, peerRoot;

    @BeforeEach
    void prepare() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        for (String table : List.of("project_applications", "project_memberships", "recruitment_positions",
                "project_departments", "projects", "auth_sessions", "association_memberships", "associations", "app_users")) {
            jdbc.sql("DELETE FROM " + table).update();
        }
        String hash = encoder.encode("synthetic-password");
        for (int i = 1; i <= 9; i++) {
            jdbc.sql("INSERT INTO app_users (id, display_name, email, password_hash, created_at) VALUES (?, ?, ?, ?, ?)")
                    .params(uid(i), "Synthetic " + i, "raul-test-" + i + "@example.test", hash, Timestamp.from(Instant.now())).update();
        }
        for (UUID association : List.of(A, B)) {
            jdbc.sql("INSERT INTO associations (id, name, slug, created_at) VALUES (?, ?, ?, ?)")
                    .params(association, association.equals(A) ? "Test A" : "Test B", association.equals(A) ? "test-a" : "test-b", Timestamp.from(Instant.now())).update();
        }
        for (int i : List.of(1, 3, 4, 5, 6, 7, 8)) addMembership(A, uid(i), i == 1 ? "ADMIN" : "MEMBER");
        addMembership(B, uid(2), "ADMIN");
        project = service.create(A, create("Main project", 3, 4), actor(1)).project().id();
        otherProject = service.create(A, create("Unrelated project", 5), actor(1)).project().id();
        foreignProject = service.create(B, create("Foreign project", 2), actor(2)).project().id();
        service.configureRecruitment(A, project, new ProjectDtos.ConfigureRecruitment("Join our team", Instant.now().plusSeconds(3600)), actor(3));
        engineeringPosition = service.savePosition(A, project, null, new ProjectDtos.SavePosition("Developer", "Engineering"), actor(3)).id();
        designPosition = service.savePosition(A, project, null, new ProjectDtos.SavePosition("Designer", "Design"), actor(3)).id();
        root = access.requireManager(A, project, uid(3)).id();
        peerRoot = access.requireManager(A, project, uid(4)).id();
    }

    @Test
    void administratorCreatesProjectAndAppointedManagerOpensItThroughRealAuthentication() throws Exception {
        String result = mvc.perform(post(base()).header("Authorization", bearer(1)).contentType("application/json")
                        .content("{\"name\":\"New project\",\"description\":\"A real project\",\"managerUserIds\":[\"" + uid(6) + "\"]}"))
                .andExpect(status().isCreated()).andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(result, "$.project.id");
        mvc.perform(get(base() + "/" + id).header("Authorization", bearer(6)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.project.canManage").value(true));
        assertEquals(1L, count("SELECT COUNT(*) FROM projects WHERE name = 'New project'"));
        assertEquals(1L, count("SELECT COUNT(*) FROM project_memberships WHERE project_id = '" + id + "'"));
    }

    @Test
    void creationAndAppointmentsRejectNonAdminsOutsidersAndPartialChanges() throws Exception {
        for (int user : List.of(3, 6, 9, 2)) {
            mvc.perform(post(base()).header("Authorization", bearer(user)).contentType("application/json")
                            .content("{\"name\":\"Forbidden\",\"description\":\"No\",\"managerUserIds\":[\"" + uid(6) + "\"]}"))
                    .andExpect(status().isForbidden());
        }
        long before = count("SELECT COUNT(*) FROM projects");
        assertThrows(ApiException.class, () -> service.create(A, create("Atomic failure", 3, 9), actor(1)));
        assertEquals(before, count("SELECT COUNT(*) FROM projects"));
        mvc.perform(put(path() + "/managers/" + uid(9)).header("Authorization", bearer(1))).andExpect(status().isBadRequest());
        mvc.perform(put(path() + "/managers/" + uid(6)).header("Authorization", bearer(3))).andExpect(status().isForbidden());
        mvc.perform(get(path())).andExpect(status().isUnauthorized());
    }

    @Test
    void multipleApplicationsAcceptanceMembershipAndOtherClosuresPersistTogether() {
        var first = apply(6, engineeringPosition);
        var second = apply(6, designPosition);
        var accepted = service.accept(A, project, first.id(), new ProjectDtos.Supervisor(root), actor(3));
        assertEquals("ACCEPTED", accepted.status());
        var mine = service.applications(A, project, true, actor(6));
        assertEquals("CLOSED_ACCEPTED_ELSEWHERE", mine.stream().filter(a -> a.id().equals(second.id())).findFirst().orElseThrow().status());
        var member = access.requireTeamMember(A, project, uid(6));
        assertEquals(engineeringPosition, member.positionId());
        assertEquals(root, member.reportingParentMembershipId());
        assertEquals("MEMBER", member.projectRole());
        assertEquals(1L, count("SELECT COUNT(*) FROM project_memberships WHERE project_id = '" + project + "' AND user_id = '" + uid(6) + "'"));
        assertThrows(ApiException.class, () -> service.accept(A, project, second.id(), new ProjectDtos.Supervisor(root), actor(4)));
    }

    @Test
    void deadlineDuplicatesReopeningAndRejectionPoliciesAreConsistent() {
        var application = apply(6, engineeringPosition);
        assertCode("APPLICATION_EXISTS", () -> apply(6, engineeringPosition));
        service.reject(A, project, application.id(), actor(3));
        assertEquals("REJECTED", service.applications(A, project, true, actor(6)).get(0).status());
        assertCode("APPLICATION_EXISTS", () -> apply(6, engineeringPosition));
        jdbc.sql("UPDATE projects SET recruitment_deadline = ? WHERE id = ?").params(Timestamp.from(Instant.now().minusSeconds(1)), project).update();
        assertCode("RECRUITMENT_CLOSED", () -> apply(7, designPosition));
        service.configureRecruitment(A, project, new ProjectDtos.ConfigureRecruitment("Extended", Instant.now().plusSeconds(7200)), actor(3));
        assertEquals("PENDING", apply(7, designPosition).status());
        assertCode("INVALID_DEADLINE", () -> service.configureRecruitment(A, project,
                new ProjectDtos.ConfigureRecruitment("Invalid", Instant.now().minusSeconds(60)), actor(3)));
        assertCode("RECRUITMENT_CLOSED", () -> service.apply(A, otherProject, new ProjectDtos.Apply(engineeringPosition, "Not configured"), actor(8)));
    }

    @Test
    void memberVisibleSummariesDoNotExposeTeamsApplicationsOrAccountSecrets() throws Exception {
        apply(6, engineeringPosition);
        String token = bearer(7);
        mvc.perform(get(base()).header("Authorization", token)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist()).andExpect(jsonPath("$[0].email").doesNotExist());
        mvc.perform(get(path() + "/recruitment").header("Authorization", token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.applications").doesNotExist());
        for (String suffix : List.of("", "/team", "/applications", "/member-options")) {
            mvc.perform(get(path() + suffix).header("Authorization", token)).andExpect(status().isForbidden());
        }
        mvc.perform(get(path() + "/applications/mine").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/associations/" + A + "/members").header("Authorization", bearer(3))).andExpect(status().isForbidden());
        mvc.perform(get(path() + "/member-options").header("Authorization", bearer(3)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].email").doesNotExist());
    }

    @Test
    void unrelatedManagersAndCrossAssociationIdentifiersCannotReviewOrMutate() throws Exception {
        UUID application = apply(6, engineeringPosition).id();
        for (int user : List.of(5, 2, 9)) {
            String token = bearer(user);
            mvc.perform(get(path() + "/applications").header("Authorization", token)).andExpect(status().isForbidden());
            mvc.perform(post(path() + "/applications/" + application + "/reject").header("Authorization", token)).andExpect(status().isForbidden());
            mvc.perform(post(path() + "/applications/" + application + "/accept").header("Authorization", token)
                            .contentType("application/json").content(supervisorBody(root))).andExpect(status().isForbidden());
        }
        mvc.perform(get(base() + "/" + foreignProject + "/recruitment").header("Authorization", bearer(3))).andExpect(status().isNotFound());
        mvc.perform(post(path() + "/applications").header("Authorization", bearer(9)).contentType("application/json")
                        .content("{\"positionId\":\"" + engineeringPosition + "\",\"motivation\":\"No membership\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void concurrentAcceptancesCreateExactlyOneSelectedPositionAndMembership() throws Exception {
        UUID first = apply(6, engineeringPosition).id(), second = apply(6, designPosition).id();
        var pool = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2); var start = new CountDownLatch(1);
        try {
            var a = pool.submit(() -> concurrentAccept(first, 3, root, ready, start));
            var b = pool.submit(() -> concurrentAccept(second, 4, peerRoot, ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS)); start.countDown();
            assertEquals(List.of(200, 409), List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS)).stream().sorted().toList());
            assertEquals(1L, count("SELECT COUNT(*) FROM project_applications WHERE project_id = '" + project + "' AND status = 'ACCEPTED'"));
            assertEquals(1L, count("SELECT COUNT(*) FROM project_memberships WHERE project_id = '" + project + "' AND user_id = '" + uid(6) + "'"));
        } finally { start.countDown(); pool.shutdownNow(); }
    }

    @Test
    void databaseRejectsTwoAcceptedApplicationsEvenWhenServiceIsBypassed() {
        UUID first = apply(6, engineeringPosition).id(), second = apply(6, designPosition).id();
        jdbc.sql("UPDATE project_applications SET status = 'ACCEPTED', selected_user_id = user_id WHERE id = ?").param(first).update();
        assertThrows(DataAccessException.class, () -> jdbc.sql("UPDATE project_applications SET status = 'ACCEPTED', selected_user_id = user_id WHERE id = ?").param(second).update());
        assertThrows(DataAccessException.class, () -> jdbc.sql("UPDATE project_applications SET status = 'ACCEPTED' WHERE id = ?").param(second).update());
    }

    @Test
    void reportingBranchesAndDepartmentAudiencesHaveDistinctSemantics() {
        UUID managerA = acceptMember(6, engineeringPosition, root);
        UUID managerB = acceptMember(7, engineeringPosition, peerRoot);
        service.appointDepartmentManager(A, project, managerA, actor(3));
        service.appointDepartmentManager(A, project, managerB, actor(4));
        acceptMember(8, engineeringPosition, managerA);
        assertTrue(access.isStrictDescendant(A, project, uid(3), uid(8)));
        assertFalse(access.isStrictDescendant(A, project, uid(4), uid(8)));
        assertFalse(access.isStrictDescendant(A, project, uid(6), uid(7)));
        assertFalse(access.isStrictDescendant(A, project, uid(3), uid(4)));
        assertFalse(access.isStrictDescendant(A, project, uid(6), uid(6)));
        UUID department = access.requireTeamMember(A, project, uid(6)).departmentId();
        assertTrue(access.departmentAudienceUserIds(A, project, department, uid(6)).containsAll(List.of(uid(6), uid(7), uid(3), uid(4))));
        assertCode("DEPARTMENT_NOT_FOUND", () -> access.departmentAudienceUserIds(A, otherProject, department, uid(5)));
    }

    @Test
    void invalidSupervisorsCrossProjectAssignmentsAndCyclesAreRejected() {
        UUID ordinary = acceptMember(6, engineeringPosition, root);
        UUID departmentManager = acceptMember(7, designPosition, root);
        service.appointDepartmentManager(A, project, departmentManager, actor(3));
        UUID application = apply(8, engineeringPosition).id();
        assertCode("INVALID_SUPERVISOR", () -> service.accept(A, project, application, new ProjectDtos.Supervisor(ordinary), actor(3)));
        assertCode("INVALID_SUPERVISOR", () -> service.accept(A, project, application, new ProjectDtos.Supervisor(departmentManager), actor(3)));
        UUID otherRoot = access.requireManager(A, otherProject, uid(5)).id();
        assertCode("INVALID_SUPERVISOR_OR_MEMBER", () -> service.changeReporting(A, project, ordinary, new ProjectDtos.Supervisor(otherRoot), actor(3)));
        assertThrows(DataAccessException.class, () -> jdbc.sql("UPDATE project_memberships SET reporting_parent_id = ? WHERE id = ?")
                .params(otherRoot, ordinary).update());
        UUID foreignPosition = service.savePosition(A, otherProject, null, new ProjectDtos.SavePosition("Helper", "Other"), actor(5)).id();
        assertCode("POSITION_NOT_FOUND", () -> apply(8, foreignPosition));
        assertCode("ROOT_MANAGER", () -> service.changeReporting(A, project, root, new ProjectDtos.Supervisor(departmentManager), actor(3)));
        assertThrows(ApiException.class, () -> service.changeReporting(A, project, departmentManager, new ProjectDtos.Supervisor(departmentManager), actor(3)));
        assertEquals("PENDING", service.applications(A, project, true, actor(8)).get(0).status());
        assertTrue(access.activeMember(project, uid(8)).isEmpty());
        // Create a corrupt pre-existing cycle via operator SQL; the service must refuse to extend it.
        service.appointDepartmentManager(A, project, ordinary, actor(3));
        jdbc.sql("UPDATE project_memberships SET reporting_parent_id = ? WHERE id = ?").params(departmentManager, ordinary).update();
        jdbc.sql("UPDATE project_memberships SET reporting_parent_id = ? WHERE id = ?").params(ordinary, departmentManager).update();
        assertFalse(access.isStrictDescendant(A, project, uid(3), uid(6)));
        assertThrows(ApiException.class, () -> service.changeReporting(A, project, ordinary, new ProjectDtos.Supervisor(departmentManager), actor(3)));
    }

    @Test
    void removalRevokesAccessAndRejoiningDoesNotRestoreProjectPrivileges() throws Exception {
        UUID membershipId = jdbc.sql("SELECT id FROM association_memberships WHERE association_id = ? AND user_id = ?")
                .params(A, uid(3)).query(UUID.class).single();
        String token = bearer(3);
        memberships.remove(A, membershipId, actor(1));
        mvc.perform(get(path() + "/team").header("Authorization", token)).andExpect(status().isForbidden());
        memberships.add(A, new MembershipDtos.AddMember("raul-test-3@example.test", AssociationRole.MEMBER), actor(1));
        assertCode("PROJECT_MANAGER_REQUIRED", () -> access.requireManager(A, project, uid(3)));
        assertFalse(access.projectAudienceUserIds(A, project, uid(4)).contains(uid(3)));
        service.appointManager(A, project, uid(3), actor(1));
        assertNotNull(access.requireManager(A, project, uid(3)));
    }

    @Test
    void changedApplicantMembershipCannotBeAcceptedAndPositionLabelsGrantNoPrivileges() {
        UUID application = apply(6, engineeringPosition).id();
        UUID membershipId = jdbc.sql("SELECT id FROM association_memberships WHERE association_id = ? AND user_id = ?")
                .params(A, uid(6)).query(UUID.class).single();
        memberships.remove(A, membershipId, actor(1));
        memberships.add(A, new MembershipDtos.AddMember("raul-test-6@example.test", AssociationRole.MEMBER), actor(1));
        assertCode("APPLICANT_MEMBERSHIP_CHANGED", () -> service.accept(A, project, application, new ProjectDtos.Supervisor(root), actor(3)));
        UUID titledManager = service.savePosition(A, project, null, new ProjectDtos.SavePosition("Project Manager", "Custom department"), actor(3)).id();
        acceptMember(7, titledManager, root);
        assertCode("PROJECT_MANAGER_REQUIRED", () -> access.requireManager(A, project, uid(7)));
    }

    @Test
    void validationAndPositionChangesCannotInvalidateExistingApplications() throws Exception {
        String token = bearer(3);
        mvc.perform(post(path() + "/positions").header("Authorization", token).contentType("application/json")
                        .content("{\"roleLabel\":\" \",\"departmentName\":\"\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.roleLabel").exists());
        mvc.perform(post(base()).header("Authorization", bearer(1)).contentType("application/json")
                        .content("{\"name\":\"Project\",\"description\":\"Desc\",\"managerUserIds\":[]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(base() + "/not-a-uuid/recruitment").header("Authorization", token)).andExpect(status().isBadRequest());
        apply(6, engineeringPosition);
        assertCode("POSITION_IN_USE", () -> service.savePosition(A, project, engineeringPosition,
                new ProjectDtos.SavePosition("Developer", "Different department"), actor(3)));
        assertEquals("Engineer", service.savePosition(A, project, engineeringPosition,
                new ProjectDtos.SavePosition("Engineer", "Engineering"), actor(3)).roleLabel());
        assertCode("POSITION_EXISTS", () -> service.savePosition(A, project, null,
                new ProjectDtos.SavePosition("Engineer", "Engineering"), actor(3)));
    }

    @Test
    void migrationUpgradesAnExistingV1DatabaseWithoutLosingItsUsers() {
        String schema = "raul_upgrade_check";
        String quotedSchema = "\"" + schema + "\"";
        var v1 = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("1").load();
        try {
            v1.migrate();
            jdbc.sql("INSERT INTO " + quotedSchema + ".app_users (id, display_name, email, password_hash, created_at) VALUES (?, ?, ?, ?, ?)")
                    .params(UUID.randomUUID(), "Retained", "upgrade@example.test", "synthetic", Timestamp.from(Instant.now())).update();
            // This test owns the V1 -> V2 upgrade; later feature migrations have their own checks.
            var latest = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("2").load();
            assertEquals(1, latest.migrate().migrationsExecuted);
            latest.validate();
            assertEquals(1L, count("SELECT COUNT(*) FROM " + quotedSchema + ".app_users"));
            assertEquals(0L, count("SELECT COUNT(*) FROM " + quotedSchema + ".projects"));
        } finally {
            jdbc.sql("DROP SCHEMA IF EXISTS " + quotedSchema + " CASCADE").update();
        }
    }

    private int concurrentAccept(UUID id, int user, UUID parent, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown(); start.await();
        try { service.accept(A, project, id, new ProjectDtos.Supervisor(parent), actor(user)); return 200; }
        catch (ApiException ex) { return ex.status().value(); }
    }

    @Test
    void adminCanEditAndDeleteUnusedProjectButOtherRolesCannotDelete() throws Exception {
        for (int user : List.of(3, 5, 2, 9)) {
            mvc.perform(delete(path()).header("Authorization", bearer(user))).andExpect(status().isForbidden());
        }
        mvc.perform(patch(path()).header("Authorization", bearer(1)).contentType("application/json")
                .content("{\"name\":\"Renamed\",\"description\":\"Updated details\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.project.name").value("Renamed"));
        mvc.perform(delete(path()).header("Authorization", bearer(1))).andExpect(status().isNoContent());
        mvc.perform(get(path() + "/recruitment").header("Authorization", bearer(1))).andExpect(status().isNotFound());
        assertEquals(0L, jdbc.sql("SELECT COUNT(*) FROM project_memberships WHERE project_id = ?").param(project).query(Long.class).single());
        assertEquals(0L, jdbc.sql("SELECT COUNT(*) FROM recruitment_positions WHERE project_id = ?").param(project).query(Long.class).single());
        assertEquals(1L, jdbc.sql("SELECT COUNT(*) FROM projects WHERE id = ?").param(otherProject).query(Long.class).single());
    }

    @Test
    void deletingProjectWithApplicationsRejectsWithoutLosingConfiguration() throws Exception {
        apply(6, engineeringPosition);
        mvc.perform(delete(path()).header("Authorization", bearer(1))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROJECT_IN_USE"));
        assertEquals(2, service.overview(A, project, actor(1)).positions().size());
        assertEquals(2, access.team(project).size());
        assertEquals(1, service.applications(A, project, true, actor(6)).size());
        mvc.perform(delete(base() + "/" + foreignProject).header("Authorization", bearer(1))).andExpect(status().isNotFound());
    }
    private UUID acceptMember(int user, UUID position, UUID parent) {
        var application = apply(user, position);
        service.accept(A, project, application.id(), new ProjectDtos.Supervisor(parent), actor(3));
        return access.requireTeamMember(A, project, uid(user)).id();
    }
    private ProjectDtos.Application apply(int user, UUID position) { return service.apply(A, project, new ProjectDtos.Apply(position, "Synthetic motivation " + user), actor(user)); }
    private void assertCode(String code, Runnable action) { assertEquals(code, assertThrows(ApiException.class, action::run).code()); }
    private long count(String sql) { return jdbc.sql(sql).query(Long.class).single(); }
    private String base() { return "/api/associations/" + A + "/projects"; }
    private String path() { return base() + "/" + project; }
    private String supervisorBody(UUID id) { return "{\"reportingParentMembershipId\":\"" + id + "\"}"; }
    private String bearer(int user) throws Exception {
        String result = mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"email\":\"raul-test-" + user + "@example.test\",\"password\":\"synthetic-password\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(result, "$.accessToken");
    }
    private ProjectDtos.CreateProject create(String name, Integer... managers) {
        Set<UUID> ids = new HashSet<>(); for (int manager : managers) ids.add(uid(manager));
        return new ProjectDtos.CreateProject(name, "Synthetic description", ids);
    }
    private void addMembership(UUID association, UUID user, String role) {
        jdbc.sql("INSERT INTO association_memberships (id, association_id, user_id, role, created_at) VALUES (?, ?, ?, ?, ?)")
                .params(UUID.randomUUID(), association, user, role, Timestamp.from(Instant.now())).update();
    }
    private static UUID uid(int value) { return UUID.fromString("30000000-0000-0000-0000-" + String.format("%012d", value)); }
    private static AuthenticatedUser actor(int value) { return new AuthenticatedUser(uid(value), "Synthetic " + value, "raul-test-" + value + "@example.test", UUID.randomUUID()); }
}
