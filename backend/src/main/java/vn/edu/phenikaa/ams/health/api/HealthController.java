package vn.edu.phenikaa.ams.health.api;

import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    @GetMapping
    HealthResponse health() {
        return new HealthResponse("UP", "ams-api", Instant.now());
    }

    record HealthResponse(String status, String service, Instant timestamp) {}
}
