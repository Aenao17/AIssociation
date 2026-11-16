package ro.stucanii.aissociation.tasks;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
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
import ro.stucanii.aissociation.projects.ProjectAccess;
import ro.stucanii.aissociation.projects.ProjectDtos;
import ro.stucanii.aissociation.projects.ProjectService;
import com.jayway.jsonpath.JsonPath;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * FR-08/FR-09 authorization matrix. Reporting tree in project P of association A:
 * <pre>
 *   3 (project manager)                4 (peer project manager)
 *   ├── 5 (dept manager, Engineering)  └── 10 (member, Design)
 *   │   └── 6 (member, Engineering)
 *   └── 7 (dept manager, Design)
 *       └── 8 (member, Design)
 * </pre>
 * 1 = ADMIN of A, 9 = member of A not on P's team (manager of another project Q),
 * 2 = ADMIN of association B.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:task_api;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
class TaskApiTests {
    static final UUID A = UUID.fromString("40000000-0000-0000-0000-000000000001");
    static final UUID B = UUID.fromString("40000000-0000-0000-0000-000000000002");
    @Autowired WebApplicationContext context;
    @Autowired JdbcClient jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired ProjectService projects;
    @Autowired ProjectAccess projectAccess;
    @Autowired TaskService tasks;
    MockMvc mvc;
    UUID project, otherProject, foreignProject;
    UUID t3, t4, t5, t6, t7, t8, t10;

    @BeforeEach
    void prepare() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        for (String table : List.of("tasks", "project_applications", "project_memberships", "recruitment_positions",
                "project_departments", "projects", "auth_sessions", "association_memberships", "associations", "app_users")) {
            jdbc.sql("DELETE FROM " + table).update();
        }
        String hash = encoder.encode("synthetic-password");
        for (int i = 1; i <= 10; i++) {
            jdbc.sql("INSERT INTO app_users (id, display_name, email, password_hash, created_at) VALUES (?, ?, ?, ?, ?)")
                    .params(uid(i), "Synthetic " + i, email(i), hash, Timestamp.from(Instant.now())).update();
        }
        for (UUID association : List.of(A, B)) {
            jdbc.sql("INSERT INTO associations (id, name, slug, created_at) VALUES (?, ?, ?, ?)")
                    .params(association, association.equals(A) ? "Tasks A" : "Tasks B",
                            association.equals(A) ? "tasks-a" : "tasks-b", Timestamp.from(Instant.now())).update();
        }
        for (int i = 1; i <= 10; i++) if (i != 2) addMembership(A, uid(i), i == 1 ? "ADMIN" : "MEMBER");
        addMembership(B, uid(2), "ADMIN");

        project = projects.create(A, create("Main", 3, 4), actor(1)).project().id();
        otherProject = projects.create(A, create("Other", 9), actor(1)).project().id();
        foreignProject = projects.create(B, create("Foreign", 2), actor(2)).project().id();
        projects.configureRecruitment(A, project, new ProjectDtos.ConfigureRecruitment("Join", Instant.now().plusSeconds(3600)), actor(3));
        UUID eng = projects.savePosition(A, project, null, new ProjectDtos.SavePosition("Developer", "Engineering"), actor(3)).id();
        UUID design = projects.savePosition(A, project, null, new ProjectDtos.SavePosition("Designer", "Design"), actor(3)).id();
        UUID m3 = projectAccess.requireManager(A, project, uid(3)).id();
        UUID m4 = projectAccess.requireManager(A, project, uid(4)).id();
        UUID m5 = accept(5, eng, m3, 3);
        projects.appointDepartmentManager(A, project, m5, actor(3));
        UUID m7 = accept(7, design, m3, 3);
        projects.appointDepartmentManager(A, project, m7, actor(3));
        accept(6, eng, m5, 3);
        accept(8, design, m7, 3);
        accept(10, design, m4, 4);

        t3 = task(1, 3, "Root plan");
        t4 = task(1, 4, "Peer root plan");
        t5 = task(3, 5, "Engineering roadmap");
        t6 = task(5, 6, "Build signup");
        t7 = task(3, 7, "Design roadmap");
        t8 = task(7, 8, "Draw poster");
        t10 = task(4, 10, "Peer branch work");
    }

    @Test
    void visibilityFollowsOwnTasksPlusReportingBranch() {
        assertEquals(Set.of(t3, t4, t5, t6, t7, t8, t10), visible(1), "association admin sees every task");
        assertEquals(Set.of(t3, t5, t6, t7, t8), visible(3), "project manager sees own + whole branch");
        assertEquals(Set.of(t4, t10), visible(4), "peer project manager sees only their own branch");
        assertEquals(Set.of(t5, t6), visible(5), "department manager sees own + subordinates");
        assertEquals(Set.of(t7, t8), visible(7), "peer department manager branch stays separate");
        assertEquals(Set.of(t6), visible(6), "member sees only own tasks");
        assertEquals(Set.of(t8), visible(8));
    }

    @Test
    void outsidersAndOtherAssociationsAreDenied() throws Exception {
        mvc.perform(get(tasksPath(project)).header("Authorization", bearer(9)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PROJECT_TEAM_REQUIRED"));
        // Admin override applies only inside their own association.
        mvc.perform(get(tasksPath(project)).header("Authorization", bearer(2)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("MEMBERSHIP_REQUIRED"));
        mvc.perform(get("/api/associations/" + A + "/projects/" + foreignProject + "/tasks").header("Authorization", bearer(1)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
        mvc.perform(get(tasksPath(project))).andExpect(status().isUnauthorized());
    }

    @Test
    void changingProjectOrTaskIdentifiersDoesNotGrantAccess() throws Exception {
        // Manager of another project addresses a task of P through their own project.
        mvc.perform(get(tasksPath(otherProject) + "/" + t6).header("Authorization", bearer(9)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("TASK_NOT_FOUND"));
        mvc.perform(patch(tasksPath(otherProject) + "/" + t6 + "/status").header("Authorization", bearer(9))
                        .contentType("application/json").content("{\"status\":\"DONE\"}"))
                .andExpect(status().isNotFound());
        // Peers get not-found rather than a hint that the task exists.
        mvc.perform(get(tasksPath(project) + "/" + t8).header("Authorization", bearer(5)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("TASK_NOT_FOUND"));
        mvc.perform(get(tasksPath(project) + "/" + t3).header("Authorization", bearer(4)))
                .andExpect(status().isNotFound());
        assertEquals("TODO", storedStatus(t6));
    }

    @Test
    void assigneeUpdatesOwnStatusButCannotEditAssignmentOrDeadline() throws Exception {
        mvc.perform(patch(tasksPath(project) + "/" + t6 + "/status").header("Authorization", bearer(6))
                        .contentType("application/json").content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.canManage").value(false)).andExpect(jsonPath("$.canChangeStatus").value(true));
        mvc.perform(put(tasksPath(project) + "/" + t6).header("Authorization", bearer(6)).contentType("application/json")
                        .content(editBody("Build signup", 6, Instant.now().plusSeconds(999_999))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("TASK_MANAGE_DENIED"));
        mvc.perform(post(tasksPath(project)).header("Authorization", bearer(6)).contentType("application/json")
                        .content(editBody("Self task", 6, Instant.now().plusSeconds(3600))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ASSIGNEE_OUT_OF_BRANCH"));
        mvc.perform(patch(tasksPath(project) + "/" + t6 + "/status").header("Authorization", bearer(6))
                        .contentType("application/json").content("{\"status\":\"BLOCKED\"}"))
                .andExpect(status().isBadRequest());
        assertEquals("IN_PROGRESS", storedStatus(t6));
    }

    @Test
    void managersAssignAndReassignOnlyInsideTheirBranch() {
        assertCode("ASSIGNEE_OUT_OF_BRANCH", () -> tasks.create(A, project, createTask(8), actor(5)));
        assertCode("ASSIGNEE_OUT_OF_BRANCH", () -> tasks.create(A, project, createTask(10), actor(3)));
        assertCode("TASK_NOT_FOUND", () -> tasks.edit(A, project, t8, edit(t8, 8), actor(5)));
        assertCode("ASSIGNEE_OUT_OF_BRANCH", () -> tasks.edit(A, project, t6, edit(t6, 8), actor(5)));
        // A task assigned to a department manager by their superior stays read-only for them.
        assertCode("TASK_MANAGE_DENIED", () -> tasks.edit(A, project, t5, edit(t5, 5), actor(5)));
        assertFalse(tasks.detail(A, project, t5, actor(5)).canManage());

        var moved = tasks.edit(A, project, t6, edit(t6, 8), actor(3));
        assertEquals(uid(8), moved.assigneeUserId());
        assertFalse(visible(5).contains(t6), "reassigned out of 5's branch");
        assertTrue(visible(7).contains(t6), "now visible in 7's branch");
        assertEquals(Set.of(uid(5), uid(6)), optionIds(5));
    }

    @Test
    void managersMayAssignThemselvesButMembersMayNot() {
        var own = tasks.create(A, project, createTask(5), actor(5));
        assertTrue(own.canManage(), "manager manages a task they created for themself");
        assertEquals(uid(5), tasks.edit(A, project, own.id(), edit(own.id(), 5), actor(5)).assigneeUserId());
        assertEquals(uid(6), tasks.edit(A, project, own.id(), edit(own.id(), 6), actor(5)).assigneeUserId());
        UUID rootOwn = tasks.create(A, project, createTask(3), actor(3)).id();
        assertFalse(visible(4).contains(rootOwn), "peer manager cannot see another manager's own task");
        assertTrue(visible(1).contains(rootOwn));
        assertCode("ASSIGNEE_OUT_OF_BRANCH", () -> tasks.create(A, project, createTask(6), actor(6)));
        assertEquals(Set.of(uid(5), uid(6)), optionIds(5));
        assertEquals(Set.of(uid(3), uid(5), uid(6), uid(7), uid(8)), optionIds(3));
        assertEquals(Set.of(), optionIds(6));
    }

    @Test
    void managersAndAdminsDeleteTasksButMembersAndPeersCannot() throws Exception {
        mvc.perform(delete(tasksPath(project) + "/" + t6).header("Authorization", bearer(6)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("TASK_MANAGE_DENIED"));
        mvc.perform(delete(tasksPath(project) + "/" + t6).header("Authorization", bearer(7)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("TASK_NOT_FOUND"));
        mvc.perform(delete(tasksPath(otherProject) + "/" + t6).header("Authorization", bearer(9)))
                .andExpect(status().isNotFound());
        assertCode("TASK_MANAGE_DENIED", () -> tasks.delete(A, project, t5, actor(5)));
        mvc.perform(delete(tasksPath(project) + "/" + t6).header("Authorization", bearer(5)))
                .andExpect(status().isNoContent());
        mvc.perform(get(tasksPath(project) + "/" + t6).header("Authorization", bearer(5)))
                .andExpect(status().isNotFound());
        tasks.delete(A, project, t10, actor(1));
        UUID own = tasks.create(A, project, createTask(7), actor(7)).id();
        tasks.delete(A, project, own, actor(7));
        assertEquals(0L, jdbc.sql("SELECT COUNT(*) FROM tasks WHERE id IN (?, ?, ?)").params(t6, t10, own).query(Long.class).single());
    }

    @Test
    void associationAdministratorManagesEveryTaskInTheirAssociation() {
        var created = tasks.create(A, project, createTask(4), actor(1));
        assertTrue(created.canManage());
        var moved = tasks.edit(A, project, t10, edit(t10, 3), actor(1));
        assertEquals(uid(3), moved.assigneeUserId());
        assertEquals(TaskDtos.Status.DONE, tasks.changeStatus(A, project, t8,
                new TaskDtos.ChangeStatus(TaskDtos.Status.DONE), actor(1)).status());
        assertEquals(Set.of(uid(3), uid(4), uid(5), uid(6), uid(7), uid(8), uid(10)), optionIds(1));
        assertCode("INVALID_ASSIGNEE", () -> tasks.create(A, project, createTask(9), actor(1)));
    }

    @Test
    void deadlineIsMandatoryAndMustBeInTheFuture() throws Exception {
        assertCode("INVALID_DEADLINE", () -> tasks.create(A, project,
                new TaskDtos.CreateTask("Late", null, uid(6), Instant.now().minusSeconds(60)), actor(5)));
        mvc.perform(post(tasksPath(project)).header("Authorization", bearer(5)).contentType("application/json")
                        .content("{\"title\":\"No deadline\",\"assigneeUserId\":\"" + uid(6) + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.dueAt").exists());
        String created = mvc.perform(post(tasksPath(project)).header("Authorization", bearer(5)).contentType("application/json")
                        .content(editBody("Through HTTP", 6, Instant.now().plusSeconds(3600))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("TODO"))
                .andExpect(jsonPath("$.createdByUserId").value(uid(5).toString()))
                .andReturn().getResponse().getContentAsString();
        assertNotNull(JsonPath.read(created, "$.id"));
    }

    @Test
    void myTasksListsOwnTasksAcrossProjectsAndDropsRevokedMemberships() throws Exception {
        UUID q = tasks.create(A, otherProject, createTask(9), actor(1)).id();
        mvc.perform(get("/api/associations/" + A + "/tasks/mine").header("Authorization", bearer(6)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(t6.toString()))
                .andExpect(jsonPath("$[0].projectName").value("Main"));
        assertEquals(List.of(q), tasks.mine(A, actor(9)).stream().map(TaskDtos.Task::id).toList());
        mvc.perform(get("/api/associations/" + A + "/tasks/mine").header("Authorization", bearer(2)))
                .andExpect(status().isForbidden());

        // Removing 6 from the association revokes their access; the task stays with admin and former managers.
        jdbc.sql("DELETE FROM association_memberships WHERE association_id = ? AND user_id = ?").params(A, uid(6)).update();
        assertCode("MEMBERSHIP_REQUIRED", () -> tasks.mine(A, actor(6)));
        assertTrue(visible(1).contains(t6), "admin still sees the orphaned task");
        assertTrue(visible(5).contains(t6), "former direct manager still sees it");
        assertTrue(visible(3).contains(t6), "former higher manager still sees it");
        assertFalse(visible(7).contains(t6), "unrelated branch still cannot");
        assertFalse(visible(4).contains(t6));
        assertFalse(optionIds(5).contains(uid(6)), "removed members cannot receive new tasks");
        assertCode("INVALID_ASSIGNEE", () -> tasks.create(A, project, createTask(6), actor(5)));
        assertEquals(uid(5), tasks.edit(A, project, t6, edit(t6, 5), actor(5)).assigneeUserId());
        UUID t6b = task(3, 7, "Second orphan");
        jdbc.sql("DELETE FROM association_memberships WHERE association_id = ? AND user_id = ?").params(A, uid(7)).update();
        assertEquals(uid(3), tasks.edit(A, project, t6b, edit(t6b, 3), actor(1)).assigneeUserId(), "admin reassigns");
    }

    // ----- helpers -----

    private Set<UUID> visible(int user) {
        Set<UUID> ids = new HashSet<>();
        tasks.list(A, project, actor(user)).forEach(t -> ids.add(t.id()));
        return ids;
    }
    private Set<UUID> optionIds(int user) {
        Set<UUID> ids = new HashSet<>();
        tasks.assigneeOptions(A, project, actor(user)).forEach(o -> ids.add(o.userId()));
        return ids;
    }
    private UUID task(int creator, int assignee, String title) {
        return tasks.create(A, project, new TaskDtos.CreateTask(title, "Synthetic", uid(assignee),
                Instant.now().plusSeconds(86_400)), actor(creator)).id();
    }
    private TaskDtos.CreateTask createTask(int assignee) {
        return new TaskDtos.CreateTask("New task", null, uid(assignee), Instant.now().plusSeconds(3600));
    }
    private TaskDtos.EditTask edit(UUID taskId, int assignee) {
        Instant due = jdbc.sql("SELECT due_at FROM tasks WHERE id = ?").param(taskId).query(Timestamp.class).single().toInstant();
        return new TaskDtos.EditTask("Edited", "Edited description", uid(assignee), due);
    }
    private String storedStatus(UUID taskId) {
        return jdbc.sql("SELECT status FROM tasks WHERE id = ?").param(taskId).query(String.class).single();
    }
    private UUID accept(int user, UUID position, UUID parent, int manager) {
        var application = projects.apply(A, project, new ProjectDtos.Apply(position, "Synthetic motivation"), actor(user));
        projects.accept(A, project, application.id(), new ProjectDtos.Supervisor(parent), actor(manager));
        return projectAccess.requireTeamMember(A, project, uid(user)).id();
    }
    private String editBody(String title, int assignee, Instant due) {
        return "{\"title\":\"" + title + "\",\"assigneeUserId\":\"" + uid(assignee) + "\",\"dueAt\":\"" + due + "\"}";
    }
    private String tasksPath(UUID projectId) { return "/api/associations/" + A + "/projects/" + projectId + "/tasks"; }
    private void assertCode(String code, Runnable action) { assertEquals(code, assertThrows(ApiException.class, action::run).code()); }
    private String bearer(int user) throws Exception {
        String result = mvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"email\":\"" + email(user) + "\",\"password\":\"synthetic-password\"}"))
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
    private static String email(int i) { return "tudor-task-" + i + "@example.test"; }
    private static UUID uid(int value) { return UUID.fromString("50000000-0000-0000-0000-" + String.format("%012d", value)); }
    private static AuthenticatedUser actor(int value) {
        return new AuthenticatedUser(uid(value), "Synthetic " + value, email(value), UUID.randomUUID());
    }
}
