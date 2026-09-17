package vn.edu.phenikaa.ams.health.api;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import vn.edu.phenikaa.ams.config.SecurityConfiguration;

@WebMvcTest(HealthController.class)
@Import(SecurityConfiguration.class)
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsApplicationHealth() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("ams-api"))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }

    @Test
    void rejectsUnauthenticatedPrivateRequests() throws Exception {
        mockMvc.perform(get("/api/me/profile")).andExpect(status().isForbidden());
    }

    @Test
    void rejectsStateChangesWithoutCsrfToken() throws Exception {
        mockMvc.perform(post("/api/health")).andExpect(status().isForbidden());
    }

    @Test
    void allowsConfiguredFrontendOrigin() throws Exception {
        mockMvc.perform(options("/api/health")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
    }

    @Test
    void rejectsUntrustedOrigin() throws Exception {
        mockMvc.perform(options("/api/health")
                        .header("Origin", "https://untrusted.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
