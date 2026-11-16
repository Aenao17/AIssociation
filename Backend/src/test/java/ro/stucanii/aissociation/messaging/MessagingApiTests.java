package ro.stucanii.aissociation.messaging;

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
import ro.stucanii.aissociation.messaging.MessagingDtos.Type;
import ro.stucanii.aissociation.projects.ProjectAccess;
import ro.stucanii.aissociation.projects.ProjectDtos;
import ro.stucanii.aissociation.projects.ProjectService;
import ro.stucanii.aissociation.tasks.TaskDtos;
import ro.stucanii.aissociation.tasks.TaskService;
import com.jayway.jsonpath.JsonPath;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * FR-15 messaging. Association A: 1 ADMIN (not on any team), project "Main" with managers 3 and 4,
 * 5 in Engineering and 6 in Design (both report to 3), 7 member not on the team, 8 member of A and B.
 * Association B: 2 ADMIN, 8 member.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:messaging_api;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
class MessagingApiTests {
    static final UUID A = UUID.fromString("60000000-0000-0000-0000-000000000001");
    static final UUID B = UUID.fromString("60000000-0000-0000-0000-000000000002");
    @Autowired WebApplicationContext context;
    @Autowired JdbcClient jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired ProjectService projects;
    @Autowired ProjectAccess projectAccess;
    @Autowired TaskService tasks;
    @Autowired MessagingService messaging;
    MockMvc mvc;
    UUID project, engineering, design;

    @BeforeEach
    void prepare() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        for (String table : List.of("messages", "conversation_members", "conversations", "tasks", "project_applications",
                "project_memberships", "recruitment_positions", "project_departments", "projects", "auth_sessions",
                "association_memberships", "associations", "app_users")) {
            jdbc.sql("DELETE FROM " + table).update();
        }
        String hash = encoder.encode("synthetic-password");
        for (int i = 1; i <= 8; i++) {
            jdbc.sql("INSERT INTO app_users (id, display_name, email, password_hash, created_at) VALUES (?, ?, ?, ?, ?)")
                    .params(uid(i), "Synthetic " + i, email(i), hash, Timestamp.from(Instant.now())).update();
        }
        for (UUID association : List.of(A, B)) {
            jdbc.sql("INSERT INTO associations (id, name, slug, created_at) VALUES (?, ?, ?, ?)")
                    .params(association, association.equals(A) ? "Chat A" : "Chat B",
                            association.equals(A) ? "chat-a" : "chat-b", Timestamp.from(Instant.now())).update();
        }
        for (int i : List.of(1, 3, 4, 5, 6, 7, 8)) addMembership(A, uid(i), i == 1 ? "ADMIN" : "MEMBER");
        addMembership(B, uid(2), "ADMIN");
        addMembership(B, uid(8), "MEMBER");

        project = projects.create(A, createProject("Main", 3, 4), actor(1)).project().id();
        projects.configureRecruitment(A, project, new ProjectDtos.ConfigureRecruitment("Join", Instant.now().plusSeconds(3600)), actor(3));
        var eng = projects.savePosition(A, project, null, new ProjectDtos.SavePosition("Developer", "Engineering"), actor(3));
        var des = projects.savePosition(A, project, null, new ProjectDtos.SavePosition("Designer", "Design"), actor(3));
        engineering = eng.departmentId();
        design = des.departmentId();
        UUID root = projectAccess.requireManager(A, project, uid(3)).id();
        accept(5, eng.id(), root);
        accept(6, des.id(), root);
    }

    @Test
    void listShowsOnlyTheConversationsEachPersonBelongsTo() {
        assertEquals(Set.of("ASSOCIATION:Chat A", "PROJECT:Main", "DEPARTMENT:Main · Engineering"), titles(5));
        assertEquals(Set.of("ASSOCIATION:Chat A", "PROJECT:Main", "DEPARTMENT:Main · Design"), titles(6));
        assertEquals(Set.of("ASSOCIATION:Chat A", "PROJECT:Main", "DEPARTMENT:Main · Engineering",
                "DEPARTMENT:Main · Design"), titles(3), "project managers join every department chat");
        assertEquals(Set.of("ASSOCIATION:Chat A"), titles(7), "member outside the team");
        assertEquals(Set.of("ASSOCIATION:Chat A"), titles(1), "an association ADMIN is not automatically a team chat member");
        long before = count("SELECT COUNT(*) FROM conversations");
        titles(3);
        titles(5);
        assertEquals(before, count("SELECT COUNT(*) FROM conversations"), "listing again creates no duplicates");
        assertCode("MEMBERSHIP_REQUIRED", () -> messaging.list(A, actor(2)));
    }

    @Test
    void teamMembersSendAndPollWhileOutsidersAreDenied() throws Exception {
        UUID chat = conversation(5, Type.PROJECT);
        String path = "/api/associations/" + A + "/conversations/" + chat + "/messages";
        String sent = mvc.perform(post(path).header("Authorization", bearer(5)).contentType("application/json")
                        .content("{\"body\":\"  Hello team  \"}"))
                .andExpect(status().isCreated()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.body").value("Hello team")).andExpect(jsonPath("$.mine").value(true))
                .andExpect(jsonPath("$.senderName").value("Synthetic 5"))
                .andReturn().getResponse().getContentAsString();
        long seq = ((Number) JsonPath.read(sent, "$.seq")).longValue();
        messaging.send(A, chat, new MessagingDtos.SendMessage("Second"), actor(6));

        mvc.perform(get(path).header("Authorization", bearer(6)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.messages.length()").value(2))
                .andExpect(jsonPath("$.messages[0].body").value("Hello team"))
                .andExpect(jsonPath("$.messages[0].mine").value(false))
                .andExpect(jsonPath("$.messages[1].mine").value(true))
                .andExpect(jsonPath("$.hasMore").value(false));
        mvc.perform(get(path + "?after=" + seq).header("Authorization", bearer(5)))
                .andExpect(jsonPath("$.messages.length()").value(1)).andExpect(jsonPath("$.messages[0].body").value("Second"));

        for (int outsider : List.of(7, 1)) {
            mvc.perform(get(path).header("Authorization", bearer(outsider)))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CONVERSATION_NOT_FOUND"));
            mvc.perform(post(path).header("Authorization", bearer(outsider)).contentType("application/json").content("{\"body\":\"x\"}"))
                    .andExpect(status().isNotFound());
        }
        mvc.perform(get(path).header("Authorization", bearer(2))).andExpect(status().isForbidden());
        mvc.perform(post(path).header("Authorization", bearer(5)).contentType("application/json").content("{\"body\":\"   \"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        assertEquals(2L, count("SELECT COUNT(*) FROM messages"));
    }

    @Test
    void departmentChatsStaySeparate() {
        UUID engineeringChat = conversation(5, Type.DEPARTMENT);
        assertCode("CONVERSATION_NOT_FOUND", () -> messaging.messages(A, engineeringChat, null, null, null, actor(6)));
        assertCode("CONVERSATION_NOT_FOUND", () -> messaging.send(A, engineeringChat, new MessagingDtos.SendMessage("hi"), actor(6)));
        messaging.send(A, engineeringChat, new MessagingDtos.SendMessage("Manager here"), actor(3));
        assertEquals(Set.of(uid(3), uid(4), uid(5)), participantIds(engineeringChat, 5));
    }

    @Test
    void sharedChatDoesNotGrantTaskAccess() {
        UUID chat = conversation(5, Type.PROJECT);
        messaging.send(A, chat, new MessagingDtos.SendMessage("We share a chat"), actor(6));
        UUID task = tasks.create(A, project, new TaskDtos.CreateTask("Private", null, uid(6),
                Instant.now().plusSeconds(3600)), actor(3)).id();
        assertTrue(tasks.list(A, project, actor(5)).stream().noneMatch(t -> t.id().equals(task)));
    }

    @Test
    void groupCreatorManagesMembersAndOthersMayLeave() throws Exception {
        String created = mvc.perform(post("/api/associations/" + A + "/conversations/groups").header("Authorization", bearer(5))
                        .contentType("application/json")
                        .content("{\"name\":\"Fair volunteers\",\"memberUserIds\":[\"" + uid(6) + "\",\"" + uid(7) + "\"]}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.type").value("GROUP"))
                .andExpect(jsonPath("$.title").value("Fair volunteers")).andExpect(jsonPath("$.canManageMembers").value(true))
                .andReturn().getResponse().getContentAsString();
        UUID group = UUID.fromString(JsonPath.read(created, "$.id"));
        assertEquals(Set.of(uid(5), uid(6), uid(7)), participantIds(group, 7));
        assertFalse(messaging.detail(A, group, actor(6)).canManageMembers());

        assertCode("INVALID_PARTICIPANT", () -> messaging.createGroup(A,
                new MessagingDtos.CreateGroup("Cross", Set.of(uid(2))), actor(5)));
        assertCode("GROUP_MANAGER_REQUIRED", () -> messaging.addGroupMember(A, group, uid(3), actor(6)));
        assertCode("GROUP_MANAGER_REQUIRED", () -> messaging.removeGroupMember(A, group, uid(7), actor(6)));
        assertCode("CONVERSATION_NOT_FOUND", () -> messaging.addGroupMember(A, group, uid(3), actor(1)));
        assertCode("INVALID_PARTICIPANT", () -> messaging.addGroupMember(A, group, uid(2), actor(5)));
        messaging.addGroupMember(A, group, uid(3), actor(5));
        assertTrue(participantIds(group, 5).contains(uid(3)));

        messaging.removeGroupMember(A, group, uid(6), actor(6));
        assertCode("CONVERSATION_NOT_FOUND", () -> messaging.messages(A, group, null, null, null, actor(6)));
        messaging.removeGroupMember(A, group, uid(7), actor(5));
        assertCode("CONVERSATION_NOT_FOUND", () -> messaging.messages(A, group, null, null, null, actor(7)));
        assertCode("CREATOR_CANNOT_LEAVE", () -> messaging.removeGroupMember(A, group, uid(5), actor(5)));
        UUID associationChat = conversation(5, Type.ASSOCIATION);
        assertCode("NOT_A_GROUP", () -> messaging.addGroupMember(A, associationChat, uid(7), actor(5)));
    }

    @Test
    void directMessagesAreUniquePerPairAndStayInsideTheAssociation() throws Exception {
        var first = messaging.openDirect(A, new MessagingDtos.OpenDirect(uid(7)), actor(5));
        var again = messaging.openDirect(A, new MessagingDtos.OpenDirect(uid(5)), actor(7));
        assertEquals(first.id(), again.id(), "one direct conversation per pair");
        assertEquals("Synthetic 7", first.title());
        assertEquals("Synthetic 5", again.title());
        messaging.send(A, first.id(), new MessagingDtos.SendMessage("Private hello"), actor(5));
        assertEquals(1, messaging.messages(A, first.id(), null, null, null, actor(7)).messages().size());
        assertCode("CONVERSATION_NOT_FOUND", () -> messaging.messages(A, first.id(), null, null, null, actor(6)));
        assertCode("CONVERSATION_NOT_FOUND", () -> messaging.messages(A, first.id(), null, null, null, actor(1)));
        assertCode("INVALID_PARTICIPANT", () -> messaging.openDirect(A, new MessagingDtos.OpenDirect(uid(5)), actor(5)));
        assertCode("INVALID_PARTICIPANT", () -> messaging.openDirect(A, new MessagingDtos.OpenDirect(uid(2)), actor(5)));

        // 8 belongs to both associations: an A conversation is not reachable through B.
        var shared = messaging.openDirect(A, new MessagingDtos.OpenDirect(uid(8)), actor(5));
        mvc.perform(get("/api/associations/" + B + "/conversations/" + shared.id() + "/messages").header("Authorization", bearer(8)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CONVERSATION_NOT_FOUND"));
        assertTrue(messaging.list(B, actor(8)).stream().noneMatch(c -> c.id().equals(shared.id())));
        assertEquals(1, messaging.contacts(B, actor(8)).size(), "contacts are association-scoped");
    }

    @Test
    void leavingTheAssociationRevokesChatAccessAndRejoiningDoesNotRestoreGroups() {
        var group = messaging.createGroup(A, new MessagingDtos.CreateGroup("Crew", Set.of(uid(6))), actor(5));
        UUID projectChat = conversation(6, Type.PROJECT);
        jdbc.sql("DELETE FROM association_memberships WHERE association_id = ? AND user_id = ?").params(A, uid(6)).update();
        assertCode("MEMBERSHIP_REQUIRED", () -> messaging.messages(A, group.id(), null, null, null, actor(6)));
        assertEquals(Set.of(uid(5)), participantIds(group.id(), 5), "removed member no longer receives group messages");

        addMembership(A, uid(6), "MEMBER");
        assertCode("CONVERSATION_NOT_FOUND", () -> messaging.messages(A, group.id(), null, null, null, actor(6)));
        assertCode("CONVERSATION_NOT_FOUND", () -> messaging.messages(A, projectChat, null, null, null, actor(6)));
        messaging.addGroupMember(A, group.id(), uid(6), actor(5));
        assertEquals(0, messaging.messages(A, group.id(), null, null, null, actor(6)).messages().size());
    }

    @Test
    void historyIsPagedOldestFirstWithCursors() {
        UUID chat = conversation(5, Type.ASSOCIATION);
        for (int i = 1; i <= 7; i++) messaging.send(A, chat, new MessagingDtos.SendMessage("m" + i), actor(i % 2 == 0 ? 5 : 7));
        var newest = messaging.messages(A, chat, null, null, 3, actor(5));
        assertEquals(List.of("m5", "m6", "m7"), bodies(newest));
        assertTrue(newest.hasMore());
        var older = messaging.messages(A, chat, null, newest.messages().get(0).seq(), 3, actor(5));
        assertEquals(List.of("m2", "m3", "m4"), bodies(older));
        assertTrue(older.hasMore());
        var oldest = messaging.messages(A, chat, null, older.messages().get(0).seq(), 3, actor(5));
        assertEquals(List.of("m1"), bodies(oldest));
        assertFalse(oldest.hasMore());
        var poll = messaging.messages(A, chat, older.messages().get(2).seq(), null, 2, actor(5));
        assertEquals(List.of("m5", "m6"), bodies(poll));
        assertTrue(poll.hasMore());
        assertEquals("m7", messaging.list(A, actor(5)).stream().filter(c -> c.id().equals(chat))
                .findFirst().orElseThrow().lastMessage().body());
        assertCode("VALIDATION_ERROR", () -> messaging.messages(A, chat, 1L, 5L, null, actor(5)));
    }

    // ----- helpers -----

    private Set<String> titles(int user) {
        Set<String> result = new HashSet<>();
        messaging.list(A, actor(user)).forEach(c -> result.add(c.type() + ":" + c.title()));
        return result;
    }
    private UUID conversation(int user, Type type) {
        return messaging.list(A, actor(user)).stream().filter(c -> c.type() == type).findFirst().orElseThrow().id();
    }
    private Set<UUID> participantIds(UUID conversation, int user) {
        Set<UUID> ids = new HashSet<>();
        messaging.participants(A, conversation, actor(user)).forEach(p -> ids.add(p.userId()));
        return ids;
    }
    private static List<String> bodies(MessagingDtos.Messages page) {
        return page.messages().stream().map(MessagingDtos.Message::body).toList();
    }
    private void accept(int user, UUID position, UUID parent) {
        var application = projects.apply(A, project, new ProjectDtos.Apply(position, "Synthetic motivation"), actor(user));
        projects.accept(A, project, application.id(), new ProjectDtos.Supervisor(parent), actor(3));
    }
    private long count(String sql) { return jdbc.sql(sql).query(Long.class).single(); }
    private void assertCode(String code, Runnable action) { assertEquals(code, assertThrows(ApiException.class, action::run).code()); }
    private String bearer(int user) throws Exception {
        String result = mvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"email\":\"" + email(user) + "\",\"password\":\"synthetic-password\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(result, "$.accessToken");
    }
    private ProjectDtos.CreateProject createProject(String name, Integer... managers) {
        Set<UUID> ids = new HashSet<>(); for (int manager : managers) ids.add(uid(manager));
        return new ProjectDtos.CreateProject(name, "Synthetic description", ids);
    }
    private void addMembership(UUID association, UUID user, String role) {
        jdbc.sql("INSERT INTO association_memberships (id, association_id, user_id, role, created_at) VALUES (?, ?, ?, ?, ?)")
                .params(UUID.randomUUID(), association, user, role, Timestamp.from(Instant.now())).update();
    }
    private static String email(int i) { return "tudor-chat-" + i + "@example.test"; }
    private static UUID uid(int value) { return UUID.fromString("70000000-0000-0000-0000-" + String.format("%012d", value)); }
    private static AuthenticatedUser actor(int value) {
        return new AuthenticatedUser(uid(value), "Synthetic " + value, email(value), UUID.randomUUID());
    }
}
