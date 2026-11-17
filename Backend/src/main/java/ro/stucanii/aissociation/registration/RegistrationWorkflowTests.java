package ro.stucanii.aissociation.registration;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import ro.stucanii.aissociation.events.EventDtos;
import ro.stucanii.aissociation.events.EventService;
import ro.stucanii.aissociation.identity.ApiException;
import ro.stucanii.aissociation.identity.AuthenticatedUser;
import ro.stucanii.aissociation.projects.ProjectDtos;
import ro.stucanii.aissociation.projects.ProjectService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@SpringBootTest(properties = {
    "spring.datasource.url=${REGISTRATION_TEST_DB_URL:jdbc:h2:mem:registration_workflow;MODE=PostgreSQL;DB_CLOSE_DELAY=-1}",
    "spring.datasource.driver-class-name=${REGISTRATION_TEST_DB_DRIVER:org.h2.Driver}",
    "spring.datasource.username=${REGISTRATION_TEST_DB_USER:sa}",
    "spring.datasource.password=${REGISTRATION_TEST_DB_PASSWORD:}"
})
class RegistrationWorkflowTests {
    @Autowired WebApplicationContext context;
    @Autowired JdbcClient jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired EventService events;
    @Autowired ProjectService projects;
    @Autowired RegistrationService registrations;
    @MockitoBean Clock clock;

    MockMvc mvc;
    final UUID a = UUID.fromString("50000000-0000-0000-0000-000000000001");  
    final UUID b = UUID.fromString("50000000-0000-0000-0000-000000000002");  
    final Instant now = Instant.parse("2026-11-16T10:00:00Z");
    final Instant deadline = now.plusSeconds(3600);
    UUID project, other;
    final Map<Integer, String> tokens = new HashMap<>();

    UUID user(int n) { return UUID.fromString("51000000-0000-0000-0000-" + String.format("%012d", n)); }
    AuthenticatedUser actor(int n) {
        return new AuthenticatedUser(user(n), "Synthetic " + n, "reg-" + n + "@example.test", UUID.randomUUID());
    }

    @BeforeEach void prepare() {
        when(clock.instant()).thenReturn(now);
        tokens.clear();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        for (String table : List.of("event_registrations", "events", "project_applications", "project_memberships",
                "recruitment_positions", "project_departments", "projects", "auth_sessions",
                "association_memberships", "associations", "app_users"))
            jdbc.sql("DELETE FROM " + table).update();
        for (var id : List.of(a, b)) jdbc.sql("INSERT INTO associations (id,name,slug,created_at) VALUES (?,?,?,?)")
                .params(id, id.equals(a) ? "Alpha" : "Beta", id.equals(a) ? "alpha" : "beta", Timestamp.from(now)).update();
        String hash = encoder.encode("synthetic-registration-password");
        for (int n = 1; n <= 12; n++) {
            jdbc.sql("INSERT INTO app_users (id,display_name,email,password_hash,created_at) VALUES (?,?,?,?,?)")
                    .params(user(n), "Synthetic " + n, "reg-" + n + "@example.test", hash, Timestamp.from(now)).update();
        }
        jdbc.sql("INSERT INTO association_memberships (id,association_id,user_id,role,created_at) VALUES (?,?,?,?,?)")
                .params(UUID.randomUUID(), a, user(1), "ADMIN", Timestamp.from(now)).update();
        jdbc.sql("INSERT INTO association_memberships (id,association_id,user_id,role,created_at) VALUES (?,?,?,?,?)")
                .params(UUID.randomUUID(), b, user(2), "ADMIN", Timestamp.from(now)).update();
        for (int n : List.of(3, 4)) jdbc.sql("INSERT INTO association_memberships (id,association_id,user_id,role,created_at) VALUES (?,?,?,?,?)")
                .params(UUID.randomUUID(), a, user(n), "MEMBER", Timestamp.from(now)).update();
        project = projects.create(a, new ProjectDtos.CreateProject("Main", "Main project", Set.of(user(3))), actor(1)).project().id();
        other = projects.create(a, new ProjectDtos.CreateProject("Other", "Other project", Set.of(user(4))), actor(1)).project().id();
    }


    EventDtos.SaveEvent body(UUID projectId, String title, Integer capacity, Instant registrationDeadline) {
        return new EventDtos.SaveEvent(projectId, title, "Description", "Campus hall", now.plusSeconds(7200),
                now.plusSeconds(10800), "Europe/Bucharest", registrationDeadline, capacity);
    }
    EventDtos.SaveEvent body(UUID projectId, String title, Integer capacity) { return body(projectId, title, capacity, deadline); }

    UUID publishedEvent(UUID projectId, Integer capacity) {
        AuthenticatedUser organizer = projectId == null ? actor(1) : actor(3);
        UUID id = events.create(a, body(projectId, "Event", capacity), organizer).event().id();
        events.transition(a, id, "publish", organizer);
        return id;
    }

    String mine(UUID eventId) { return "/api/events/" + eventId + "/registration"; }
    String organizer(UUID eventId) { return "/api/associations/" + a + "/events/" + eventId + "/registrations"; }
    String availability(UUID eventId) { return "/api/public/events/" + eventId + "/availability"; }

    String bearer(int n) throws Exception {
        if (tokens.containsKey(n)) return tokens.get(n);
        String response = mvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"email\":\"reg-" + n + "@example.test\",\"password\":\"synthetic-registration-password\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = "Bearer " + JsonPath.read(response, "$.accessToken");
        tokens.put(n, token);
        return token;
    }

    long count(UUID eventId, String status) {
        return jdbc.sql("SELECT COUNT(*) FROM event_registrations WHERE event_id=? AND status=?")
                .params(eventId, status).query(Long.class).single();
    }
    long rows(UUID eventId, int userNumber) {
        return jdbc.sql("SELECT COUNT(*) FROM event_registrations WHERE event_id=? AND user_id=?")
                .params(eventId, user(userNumber)).query(Long.class).single();
    }
    UUID registrationId(UUID eventId, int userNumber) {
        return jdbc.sql("SELECT id FROM event_registrations WHERE event_id=? AND user_id=?")
                .params(eventId, user(userNumber)).query(UUID.class).single();
    }
    void register(int n, UUID eventId) throws Exception {
        mvc.perform(post(mine(eventId)).header("Authorization", bearer(n))).andExpect(status().isCreated());
    }


    @Test void registrationConfirmsImmediatelyAndDuplicatesAreRejected() throws Exception {
        UUID id = publishedEvent(project, null);
        mvc.perform(post(mine(id))).andExpect(status().isUnauthorized());                       // registration needs sign-in
        mvc.perform(post(mine(id)).header("Authorization", bearer(5)))
                .andExpect(status().isCreated())                                                // immediate, no approval step
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.canCancel").value(true))
                .andExpect(jsonPath("$.canRegister").value(false))
                .andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(post(mine(id)).header("Authorization", bearer(5)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_REGISTERED"));
        assertEquals(1L, count(id, "ACTIVE"));
        mvc.perform(get(mine(id)).header("Authorization", bearer(5))).andExpect(jsonPath("$.status").value("ACTIVE"));
        mvc.perform(get(mine(id)).header("Authorization", bearer(6))).andExpect(jsonPath("$.status").value("NOT_REGISTERED"));
        assertEquals(0L, jdbc.sql("SELECT COUNT(*) FROM association_memberships WHERE user_id=?")
                .param(user(5)).query(Long.class).single());
    }

    @Test void draftCancelledAndUnknownEventsCannotBeRegisteredFor() throws Exception {
        var draft = events.create(a, body(null, "Draft", null), actor(1));
        mvc.perform(post(mine(draft.event().id())).header("Authorization", bearer(5))).andExpect(status().isNotFound());
        mvc.perform(post(mine(UUID.randomUUID())).header("Authorization", bearer(5))).andExpect(status().isNotFound());
        UUID id = publishedEvent(project, null);
        events.transition(a, id, "cancel", actor(3));
        mvc.perform(post(mine(id)).header("Authorization", bearer(5)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EVENT_NOT_OPEN"));
        assertEquals(0L, count(id, "ACTIVE"));
    }

    @Test void capacityIsEnforcedAndMissingCapacityMeansUnlimited() throws Exception {
        UUID limited = publishedEvent(project, 2);
        register(5, limited);
        register(6, limited);
        mvc.perform(post(mine(limited)).header("Authorization", bearer(7)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EVENT_FULL"));
        mvc.perform(get(availability(limited))).andExpect(status().isOk())                       // public: no sign-in
                .andExpect(jsonPath("$.activeRegistrations").value(2)).andExpect(jsonPath("$.seatsRemaining").value(0))
                .andExpect(jsonPath("$.registrationOpen").value(true));

        UUID unlimited = publishedEvent(project, null);                                          // capacity null = unlimited
        for (int n = 5; n <= 12; n++) register(n, unlimited);
        assertEquals(8L, count(unlimited, "ACTIVE"));
        mvc.perform(get(availability(unlimited))).andExpect(jsonPath("$.seatsRemaining").doesNotExist());
    }

    @Test void publicAvailabilityRevealsCountsButNeverPeople() throws Exception {
        UUID id = publishedEvent(project, 5);
        register(5, id);
        mvc.perform(get(availability(id))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items").doesNotExist()).andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.userId").doesNotExist()).andExpect(jsonPath("$.seatsRemaining").value(4));
        var draft = events.create(a, body(null, "Draft", null), actor(1));
        mvc.perform(get(availability(draft.event().id()))).andExpect(status().isNotFound());      // drafts stay private
    }


    @Test void cancellingBeforeTheDeadlineReleasesTheSeatAndAllowsReRegistration() throws Exception {
        UUID id = publishedEvent(project, 1);
        register(5, id);
        mvc.perform(post(mine(id)).header("Authorization", bearer(6)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EVENT_FULL"));

        mvc.perform(delete(mine(id)).header("Authorization", bearer(5)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.canRegister").value(true));                                // the seat is free again
        mvc.perform(delete(mine(id)).header("Authorization", bearer(5)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_CANCELLED"));
        mvc.perform(delete(mine(id)).header("Authorization", bearer(7)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("REGISTRATION_NOT_FOUND"));

        register(6, id);                                                                         // takes the released seat
        mvc.perform(post(mine(id)).header("Authorization", bearer(5)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EVENT_FULL"));
        mvc.perform(delete(mine(id)).header("Authorization", bearer(6))).andExpect(status().isOk());
        register(5, id);                                                                         // re-registration reuses the row
        assertEquals(1L, rows(id, 5));
        assertEquals(1L, count(id, "ACTIVE"));
    }

    @Test void afterTheDeadlineParticipantsAreDeniedButAnOrganizerCanCancel() throws Exception {
        UUID id = publishedEvent(project, null);
        register(5, id);
        when(clock.instant()).thenReturn(deadline);        // exactly the deadline: "strictly before" means closed

        mvc.perform(post(mine(id)).header("Authorization", bearer(6)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REGISTRATION_CLOSED"));
        mvc.perform(delete(mine(id)).header("Authorization", bearer(5)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CANCELLATION_CLOSED"));
        assertEquals(1L, count(id, "ACTIVE"));

        mvc.perform(delete(organizer(id) + "/" + registrationId(id, 5)).header("Authorization", bearer(3)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledByOrganizer").value(true));
        assertEquals(0L, count(id, "ACTIVE"));
    }

    @Test void extendingTheDeadlineReopensRegistrationButOrganizerCancellationsStayFinal() throws Exception {
        UUID id = publishedEvent(project, null);
        register(5, id);
        when(clock.instant()).thenReturn(deadline);
        mvc.perform(delete(organizer(id) + "/" + registrationId(id, 5)).header("Authorization", bearer(3))).andExpect(status().isOk());
        mvc.perform(post(mine(id)).header("Authorization", bearer(6))).andExpect(status().isConflict());

        events.edit(a, id, body(project, "Event", null, now.plusSeconds(7000)), actor(3));
        register(6, id);
        mvc.perform(post(mine(id)).header("Authorization", bearer(5)))                          // organizer-cancelled: not self-service
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REGISTRATION_CANCELLED_BY_ORGANIZER"));
    }


    @Test void rosterAndOrganizerActionsAreLimitedToTheEventsOrganizers() throws Exception {
        UUID id = publishedEvent(project, null);
        register(5, id); register(6, id);
        for (int who : List.of(1, 3)) {                                                         // association admin, project manager
            mvc.perform(get(organizer(id)).header("Authorization", bearer(who)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(2))
                    .andExpect(jsonPath("$.activeRegistrations").value(2)).andExpect(header().string("Cache-Control", "no-store"));
        }
        mvc.perform(get(organizer(id)).header("Authorization", bearer(4))).andExpect(status().isForbidden());  // manager of ANOTHER project
        mvc.perform(get(organizer(id)).header("Authorization", bearer(2))).andExpect(status().isForbidden());  // admin of ANOTHER association
        mvc.perform(get(organizer(id)).header("Authorization", bearer(5))).andExpect(status().isForbidden());  // a participant
        mvc.perform(get(organizer(id))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/associations/" + b + "/events/" + id + "/registrations").header("Authorization", bearer(2)))
                .andExpect(status().isNotFound());

        UUID standalone = publishedEvent(null, null);
        mvc.perform(get(organizer(standalone)).header("Authorization", bearer(3))).andExpect(status().isForbidden());
        mvc.perform(get(organizer(standalone)).header("Authorization", bearer(1))).andExpect(status().isOk());

        UUID foreign = registrationId(id, 5);
        mvc.perform(delete(organizer(standalone) + "/" + foreign).header("Authorization", bearer(1))).andExpect(status().isNotFound());
        mvc.perform(put(organizer(standalone) + "/" + foreign + "/attendance").header("Authorization", bearer(1))
                .contentType("application/json").content("{\"attended\":true}")).andExpect(status().isNotFound());
        assertEquals(2L, count(id, "ACTIVE"));
    }

    @Test void removedMembersLoseOrganizerAccessImmediately() throws Exception {
        UUID id = publishedEvent(project, null);
        register(5, id);
        mvc.perform(get(organizer(id)).header("Authorization", bearer(3))).andExpect(status().isOk());
        jdbc.sql("DELETE FROM association_memberships WHERE user_id=?").param(user(3)).update();   // live check, no stale UI state
        mvc.perform(get(organizer(id)).header("Authorization", bearer(3))).andExpect(status().isForbidden());
    }


    @Test void attendanceIsIdempotentCorrectableAndOnlyForActiveRegistrations() throws Exception {
        UUID id = publishedEvent(project, null);
        register(5, id); register(6, id);
        String path = organizer(id) + "/" + registrationId(id, 5) + "/attendance";

        String first = mvc.perform(put(path).header("Authorization", bearer(3)).contentType("application/json").content("{\"attended\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.attendance").value("CHECKED_IN"))
                .andReturn().getResponse().getContentAsString();
        when(clock.instant()).thenReturn(now.plusSeconds(60));
        String second = mvc.perform(put(path).header("Authorization", bearer(3)).contentType("application/json").content("{\"attended\":true}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String firstTime = JsonPath.read(first, "$.checkedInAt");
        String secondTime = JsonPath.read(second, "$.checkedInAt");
        assertEquals(firstTime, secondTime);                                                                // original time kept
        assertEquals(1L, rows(id, 5));                                                                      // still one record

        mvc.perform(put(path).header("Authorization", bearer(3)).contentType("application/json").content("{\"attended\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.attendance").value("NOT_CHECKED_IN"))
                .andExpect(jsonPath("$.checkedInAt").doesNotExist());                                       // correction
        mvc.perform(put(path).header("Authorization", bearer(3)).contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());                                                        // attended is required

        for (int outsider : List.of(4, 2, 5)) {
            mvc.perform(put(path).header("Authorization", bearer(outsider)).contentType("application/json").content("{\"attended\":true}"))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(put(path).header("Authorization", bearer(3)).contentType("application/json").content("{\"attended\":true}")).andExpect(status().isOk());

        mvc.perform(delete(organizer(id) + "/" + registrationId(id, 5)).header("Authorization", bearer(3)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.attendance").value("NOT_CHECKED_IN"));
        mvc.perform(put(path).header("Authorization", bearer(3)).contentType("application/json").content("{\"attended\":true}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REGISTRATION_NOT_ACTIVE"));
        mvc.perform(get(organizer(id)).header("Authorization", bearer(3))).andExpect(jsonPath("$.checkedIn").value(0));
    }


    @Test void concurrentRegistrationsForTheLastSeatCanNeverOverbook() throws Exception {
        UUID id = publishedEvent(project, 1);
        int threads = 8;                                                                     // users 5..12 all fire at once
        var pool = Executors.newFixedThreadPool(threads);
        var gate = new CyclicBarrier(threads);
        List<Future<String>> futures = new ArrayList<>();
        for (int n = 5; n < 5 + threads; n++) {
            final int userNumber = n;
            futures.add(pool.submit(() -> {
                gate.await();                                                                // release every thread together
                try { registrations.register(id, actor(userNumber)); return "OK"; }
                catch (ApiException ex) { return ex.code(); }
            }));
        }
        List<String> outcomes = new ArrayList<>();
        for (var future : futures) outcomes.add(future.get(60, TimeUnit.SECONDS));
        pool.shutdown();
        assertEquals(1, outcomes.stream().filter("OK"::equals).count(), outcomes.toString());
        assertEquals(threads - 1, outcomes.stream().filter("EVENT_FULL"::equals).count(), outcomes.toString());
        assertEquals(1L, count(id, "ACTIVE"));
    }

    @Test void aDoubleClickByTheSamePersonCreatesOneRegistration() throws Exception {
        UUID id = publishedEvent(project, null);
        var pool = Executors.newFixedThreadPool(2);
        var gate = new CyclicBarrier(2);
        List<Future<String>> futures = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            futures.add(pool.submit(() -> {
                gate.await();
                try { registrations.register(id, actor(5)); return "OK"; }
                catch (ApiException ex) { return ex.code(); }
            }));
        }
        List<String> outcomes = new ArrayList<>();
        for (var future : futures) outcomes.add(future.get(60, TimeUnit.SECONDS));
        pool.shutdown();
        Collections.sort(outcomes);
        assertEquals(List.of("ALREADY_REGISTERED", "OK"), outcomes);
        assertEquals(1L, rows(id, 5));
    }


    @Test void databaseItselfRejectsDuplicateRowsAndInconsistentStates() {
        UUID id = publishedEvent(project, null);
        jdbc.sql("INSERT INTO event_registrations (id,event_id,user_id,status,registered_at) VALUES (?,?,?,'ACTIVE',?)")
                .params(UUID.randomUUID(), id, user(5), Timestamp.from(now)).update();
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () ->
                jdbc.sql("INSERT INTO event_registrations (id,event_id,user_id,status,registered_at) VALUES (?,?,?,'ACTIVE',?)")
                        .params(UUID.randomUUID(), id, user(5), Timestamp.from(now)).update());                // second row for same pair
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () ->
                jdbc.sql("UPDATE event_registrations SET status='CANCELLED' WHERE event_id=?").param(id).update());   // cancelled without time
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () ->
                jdbc.sql("UPDATE event_registrations SET attendance='CHECKED_IN' WHERE event_id=?").param(id).update()); // checked in without time
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () ->
                jdbc.sql("DELETE FROM events WHERE id=?").param(id).update());                                  // event with registrations
    }
}