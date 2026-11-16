package ro.stucanii.aissociation.events;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import com.jayway.jsonpath.JsonPath;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import ro.stucanii.aissociation.identity.*;
import ro.stucanii.aissociation.projects.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Opt into EVENTS_TEST_DB_* only with a dedicated disposable database; fixtures are deleted. */
@SpringBootTest(properties={
    "spring.datasource.url=${EVENTS_TEST_DB_URL:jdbc:h2:mem:event_workflow;MODE=PostgreSQL;DB_CLOSE_DELAY=-1}",
    "spring.datasource.driver-class-name=${EVENTS_TEST_DB_DRIVER:org.h2.Driver}",
    "spring.datasource.username=${EVENTS_TEST_DB_USER:sa}",
    "spring.datasource.password=${EVENTS_TEST_DB_PASSWORD:}"
})
class EventWorkflowTests {
    @Autowired WebApplicationContext context;
    @Autowired JdbcClient jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired EventService events;
    @Autowired ProjectService projects;
    @MockitoBean Clock clock;
    MockMvc mvc;
    final UUID a=UUID.fromString("40000000-0000-0000-0000-000000000001");
    final UUID b=UUID.fromString("40000000-0000-0000-0000-000000000002");
    final Instant now=Instant.parse("2026-11-16T10:00:00Z");
    UUID project,other;
    UUID user(int n) { return UUID.fromString("41000000-0000-0000-0000-"+String.format("%012d",n)); }
    AuthenticatedUser actor(int n) { return new AuthenticatedUser(user(n),"Synthetic "+n,"event-"+n+"@example.test",UUID.randomUUID()); }
    @BeforeEach void prepare() {
        when(clock.instant()).thenReturn(now);
        mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        for (String table:List.of("events","project_applications","project_memberships","recruitment_positions","project_departments","projects","auth_sessions","association_memberships","associations","app_users"))
            jdbc.sql("DELETE FROM "+table).update();
        for (var id:List.of(a,b)) jdbc.sql("INSERT INTO associations (id,name,slug,created_at) VALUES (?,?,?,?)")
            .params(id,id.equals(a)?"Alpha":"Beta",id.equals(a)?"alpha":"beta",Timestamp.from(now)).update();
        String hash=encoder.encode("synthetic-event-password");
        for (int n=1;n<=5;n++) {
            jdbc.sql("INSERT INTO app_users (id,display_name,email,password_hash,created_at) VALUES (?,?,?,?,?)")
                .params(user(n),"Synthetic "+n,"event-"+n+"@example.test",hash,Timestamp.from(now)).update();
            jdbc.sql("INSERT INTO association_memberships (id,association_id,user_id,role,created_at) VALUES (?,?,?,?,?)")
                .params(UUID.randomUUID(),n==2?b:a,user(n),n<=2?"ADMIN":"MEMBER",Timestamp.from(now)).update();
        }
        project=projects.create(a,new ProjectDtos.CreateProject("Main","Main project",Set.of(user(3))),actor(1)).project().id();
        other=projects.create(a,new ProjectDtos.CreateProject("Other","Other project",Set.of(user(4))),actor(1)).project().id();
    }
    EventDtos.SaveEvent body(UUID project,String title,Integer capacity) {
        return new EventDtos.SaveEvent(project,title,"Description","Campus hall",now.plusSeconds(7200),now.plusSeconds(10800),"Europe/Bucharest",now.plusSeconds(3600),capacity);
    }
    String json(UUID project,String title,Integer capacity) {
        return """
            {"projectId":%s,"title":"%s","description":"Description","location":"Campus hall",
             "startsAt":"2026-11-16T12:00:00Z","endsAt":"2026-11-16T13:00:00Z","timeZone":"Europe/Bucharest",
             "registrationDeadline":"2026-11-16T11:00:00Z","capacity":%s}
            """.formatted(project==null?"null":"\""+project+"\"",title,capacity==null?"null":capacity);
    }
    String base() { return "/api/associations/"+a+"/events"; }
    String bearer(int n) throws Exception {
        String response=mvc.perform(post("/api/auth/login").contentType("application/json")
            .content("{\"email\":\"event-"+n+"@example.test\",\"password\":\"synthetic-event-password\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer "+JsonPath.read(response,"$.accessToken");
    }
    @Test void standaloneCreateEditPublishWorksWithoutApprovalAndDraftIsPrivate() throws Exception {
        String created=mvc.perform(post(base()).header("Authorization",bearer(1)).contentType("application/json").content(json(null,"First",null)))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.event.status").value("DRAFT")).andExpect(header().string("Cache-Control","no-store"))
            .andReturn().getResponse().getContentAsString();
        String id=JsonPath.read(created,"$.event.id");
        mvc.perform(get("/api/public/events/"+id)).andExpect(status().isNotFound());
        mvc.perform(get("/api/public/events")).andExpect(jsonPath("$.total").value(0));
        mvc.perform(put(base()+"/"+id).header("Authorization",bearer(1)).contentType("application/json").content(json(null,"Updated",10)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.event.title").value("Updated"));
        mvc.perform(post(base()+"/"+id+"/publish").header("Authorization",bearer(1))).andExpect(status().isOk()).andExpect(jsonPath("$.event.status").value("PUBLISHED"));
        mvc.perform(get("/api/public/events/"+id)).andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Updated"))
            .andExpect(jsonPath("$.projectId").doesNotExist()).andExpect(jsonPath("$.updatedAt").doesNotExist());
    }
    @Test void projectCanHaveSeveralEventsAndUnrelatedManagersCannotReadOrMutate() throws Exception {
        var first=events.create(a,body(project,"First",null),actor(3));
        events.create(a,body(project,"Second",20),actor(3));
        assertEquals(2,events.list(a,actor(3)).size());
        assertTrue(events.list(a,actor(4)).isEmpty());
        for (int n:List.of(1,2,4,5)) {
            mvc.perform(get(base()+"/"+first.event().id()).header("Authorization",bearer(n))).andExpect(status().isForbidden());
            mvc.perform(post(base()+"/"+first.event().id()+"/publish").header("Authorization",bearer(n))).andExpect(status().isForbidden());
            mvc.perform(put(base()+"/"+first.event().id()).header("Authorization",bearer(n)).contentType("application/json").content(json(project,"Stolen",10))).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/associations/"+b+"/events/"+first.event().id()).header("Authorization",bearer(2))).andExpect(status().isNotFound());
        mvc.perform(post(base()).contentType("application/json").content(json(null,"No token",null))).andExpect(status().isUnauthorized());
        mvc.perform(post(base()).header("Authorization",bearer(3)).contentType("application/json").content(json(null,"No admin",null))).andExpect(status().isForbidden());
        mvc.perform(post("/api/associations/"+b+"/events").header("Authorization",bearer(2)).contentType("application/json").content(json(project,"Wrong scope",null))).andExpect(status().isNotFound());
    }
    @Test void validationRejectsMissingDeadlineInvalidDatesTimezoneAndCapacity() throws Exception {
        String token=bearer(1);
        for (String value:List.of(json(null,"",null),json(null,"Valid",0),json(null,"Valid",null).replace("Europe/Bucharest","Invented/Zone"),
            json(null,"Valid",null).replace("2026-11-16T13:00:00Z","2026-11-16T11:00:00Z"),
            json(null,"Valid",null).replace("2026-11-16T11:00:00Z","2026-11-16T14:00:00Z"),
            json(null,"Valid",null).replace("\"registrationDeadline\":\"2026-11-16T11:00:00Z\"","\"registrationDeadline\":null"))) {
            mvc.perform(post(base()).header("Authorization",token).contentType("application/json").content(value)).andExpect(status().isBadRequest());
        }
        assertEquals(0L,jdbc.sql("SELECT COUNT(*) FROM events").query(Long.class).single());
    }
    @Test void sharedFiltersPaginationAndDraftPrivacyHaveConsistentResults() throws Exception {
        var one=events.create(a,body(null,"One",null),actor(1));
        var two=events.create(b,body(null,"Two",null),actor(2));
        events.create(a,body(null,"Private draft",null),actor(1));
        events.transition(a,one.event().id(),"publish",actor(1)); events.transition(b,two.event().id(),"publish",actor(2));
        mvc.perform(get("/api/public/events").param("size","1")).andExpect(jsonPath("$.total").value(2)).andExpect(jsonPath("$.items.length()").value(1));
        mvc.perform(get("/api/public/events").param("associationId",a.toString()).param("from","2026-11-16T12:30:00Z").param("to","2026-11-16T14:00:00Z"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].id").value(one.event().id().toString()));
        mvc.perform(get("/api/public/events").param("from","2026-11-16T13:00:00Z")).andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/public/events").param("size","101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/public/events").param("from","2026-11-17T00:00:00Z").param("to","2026-11-16T00:00:00Z")).andExpect(status().isBadRequest());
    }
    @Test void extensionCapacityOwnershipAndTerminalStatesAreEnforced() {
        var event=events.create(a,body(null,"Capacity",10),actor(1)); UUID id=event.event().id();
        events.transition(a,id,"publish",actor(1));
        assertThrows(ApiException.class,()->events.edit(a,id,body(null,"Capacity",9),actor(1)));
        var extended=new EventDtos.SaveEvent(null,"Extended","Description","Campus",now.plusSeconds(7200),now.plusSeconds(10800),"Europe/Bucharest",now.plusSeconds(7000),20);
        assertEquals(extended.registrationDeadline(),events.edit(a,id,extended,actor(1)).event().registrationDeadline());
        assertThrows(ApiException.class,()->events.edit(a,id,body(project,"Moved",20),actor(1)));
        assertThrows(ApiException.class,()->events.transition(a,id,"complete",actor(1)));
        events.transition(a,id,"cancel",actor(1));
        assertEquals("CANCELLED",events.publicDetail(id).status());
        assertEquals(0L,events.discover(null,null,null,0,20).total());
        assertThrows(ApiException.class,()->events.transition(a,id,"publish",actor(1)));
        assertThrows(ApiException.class,()->events.edit(a,id,extended,actor(1)));
        var ended=events.create(a,body(null,"Ended",null),actor(1));
        events.transition(a,ended.event().id(),"publish",actor(1));
        when(clock.instant()).thenReturn(now.plusSeconds(12000));
        assertEquals("COMPLETED",events.transition(a,ended.event().id(),"complete",actor(1)).event().status());
    }
    @Test void revokedMembershipRemovesEventPrivilegesImmediately() {
        var event=events.create(a,body(project,"Project event",null),actor(3));
        jdbc.sql("DELETE FROM association_memberships WHERE user_id=?").param(user(3)).update();
        assertThrows(ApiException.class,()->events.detail(a,event.event().id(),actor(3)));
        assertThrows(ApiException.class,()->events.transition(a,event.event().id(),"publish",actor(3)));
    }
    @Test void profileEditingIsAdminOnlyAndPublicOutputIsSafe() throws Exception {
        String path="/api/associations/"+a+"/public-profile";
        mvc.perform(put(path).header("Authorization",bearer(3)).contentType("application/json").content("{\"description\":\"No\",\"website\":null}"))
            .andExpect(status().isForbidden());
        mvc.perform(put(path).header("Authorization",bearer(1)).contentType("application/json").content("{\"description\":\"About Alpha\",\"website\":\"javascript:alert(1)\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(put(path).header("Authorization",bearer(1)).contentType("application/json").content("{\"description\":\"About Alpha\",\"website\":\"https://example.test\"}"))
            .andExpect(status().isNoContent());
        mvc.perform(get("/api/public/associations/alpha")).andExpect(jsonPath("$.description").value("About Alpha"))
            .andExpect(jsonPath("$.website").value("https://example.test")).andExpect(jsonPath("$.members").doesNotExist());
    }
    @Test void databaseRejectsProjectFromAnotherAssociation() {
        var event=events.create(a,body(null,"Scoped",null),actor(1));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->
            jdbc.sql("UPDATE events SET project_id=?,association_id=? WHERE id=?").params(project,b,event.event().id()).update());
    }
    @Test void v2UpgradePreservesAssociationIdentity() throws Exception {
        String url="jdbc:h2:mem:event_upgrade;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url,"sa","").target("2").load().migrate();
        try (var connection=java.sql.DriverManager.getConnection(url,"sa","");var statement=connection.createStatement()) {
            statement.execute("INSERT INTO associations (id,name,slug,created_at) VALUES ('"+a+"','Existing','existing',CURRENT_TIMESTAMP)");
        }
        Flyway.configure().dataSource(url,"sa","").load().migrate();
        try (var connection=java.sql.DriverManager.getConnection(url,"sa","");var statement=connection.createStatement();var row=statement.executeQuery("SELECT name,description FROM associations WHERE slug='existing'")) {
            assertTrue(row.next()); assertEquals("Existing",row.getString(1)); assertEquals("",row.getString(2));
        }
    }
}
