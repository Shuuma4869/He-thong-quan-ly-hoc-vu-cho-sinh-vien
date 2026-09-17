package vn.edu.phenikaa.ams;

import static org.assertj.core.api.Assertions.assertThat;
import java.net.URI;
import java.net.http.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@Import(TestcontainersConfiguration.class)
@ActiveProfiles("prod")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "DATABASE_URL=jdbc:postgresql://localhost/unused", "DATABASE_USERNAME=test", "DATABASE_PASSWORD=test",
        "REDIS_HOST=localhost", "REDIS_PASSWORD=", "CORS_ALLOWED_ORIGINS=https://ams.example.test",
        "spring.data.redis.password="
})
class ProductionCookieIT {
    @LocalServerPort private int port;

    @Test
    void productionSessionCookieIsSecureAndHttpOnly() throws Exception {
        var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + "/api/auth/csrf")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Set-Cookie").orElseThrow()).contains("Secure", "HttpOnly", "SameSite=Lax");
    }
}
