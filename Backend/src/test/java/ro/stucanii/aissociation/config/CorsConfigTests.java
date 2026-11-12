package ro.stucanii.aissociation.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CorsConfigTests {

    private final MockMvc mvc = mvcFor("https://aissociation.vercel.app,http://localhost:5173");

    private MockMvc mvcFor(String origins) {
        return MockMvcBuilders.standaloneSetup(new ProbeController())
                .addFilters(new CorsConfig(origins).corsFilter())
                .build();
    }

    @Test
    void allowsProductionOrigin() throws Exception {
        mvc.perform(get("/api/probe").header("Origin", "https://aissociation.vercel.app"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://aissociation.vercel.app"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    }

    @Test
    void allowsAuthenticatedJsonPreflight() throws Exception {
        mvc.perform(options("/api/probe")
                        .header("Origin", "https://aissociation.vercel.app")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type,authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://aissociation.vercel.app"));
    }

    @Test
    void allowsLocalFrontend() throws Exception {
        mvc.perform(get("/api/probe").header("Origin", "http://localhost:5173"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    void rejectsOtherOrigins() throws Exception {
        mvc.perform(options("/api/probe")
                        .header("Origin", "https://untrusted.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void allowsBrowserHealthCheckAndOrdinaryHealthProbe() throws Exception {
        mvc.perform(get("/actuator/health").header("Origin", "https://aissociation.vercel.app"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://aissociation.vercel.app"));
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void supportsReplacingTheOriginList() throws Exception {
        MockMvc custom = mvcFor(" https://custom.example ");
        custom.perform(get("/api/probe").header("Origin", "https://custom.example"))
                .andExpect(status().isOk());
        custom.perform(get("/api/probe").header("Origin", "https://aissociation.vercel.app"))
                .andExpect(status().isForbidden());
    }

    @RestController
    static class ProbeController {
        @GetMapping({"/api/probe", "/actuator/health"})
        String probe() {
            return "ok";
        }
    }
}
