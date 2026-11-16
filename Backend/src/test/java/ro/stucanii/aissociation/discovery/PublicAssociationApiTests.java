package ro.stucanii.aissociation.discovery;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:public_associations;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password="
})
class PublicAssociationApiTests {
    @Autowired WebApplicationContext context;
    @Autowired JdbcClient jdbc;
    MockMvc mvc;

    @BeforeEach
    void prepare() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        jdbc.sql("DELETE FROM associations").update();
    }

    void add(String name, String slug) {
        jdbc.sql("INSERT INTO associations (id, name, slug, created_at) VALUES (?, ?, ?, ?)")
                .params(UUID.randomUUID(), name, slug, Timestamp.from(Instant.now())).update();
    }

    @Test
    void anonymousVisitorsDiscoverBothAssociationsWithOnlyPublicFields() throws Exception {
        add("Zeta", "zeta");
        add("Alpha", "alpha");
        mvc.perform(get("/api/public/associations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Alpha"))
                .andExpect(jsonPath("$[0].slug").value("alpha"))
                .andExpect(jsonPath("$[1].name").value("Zeta"))
                .andExpect(jsonPath("$[0].length()").value(3))
                .andExpect(jsonPath("$[1].length()").value(3));
    }

    @Test
    void publicDetailUsesAnExplicitProjectionAndDoesNotUnlockPrivateRoutes() throws Exception {
        add("Alpha", "alpha");
        String id = jdbc.sql("SELECT id FROM associations WHERE slug = 'alpha'").query(UUID.class).single().toString();
        mvc.perform(get("/api/public/associations/alpha"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"id\":\"" + id + "\",\"name\":\"Alpha\",\"slug\":\"alpha\",\"description\":\"\",\"website\":null}",
                        org.springframework.test.json.JsonCompareMode.STRICT));
        mvc.perform(get("/api/associations/" + id + "/members")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/workspaces/alpha")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/associations/" + id + "/projects")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/public/associations").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void emptyDirectoryAndUnknownSlugHaveDistinctResults() throws Exception {
        mvc.perform(get("/api/public/associations")).andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/api/public/associations/missing"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ASSOCIATION_NOT_FOUND"));
    }
}
