package vn.edu.phenikaa.ams;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.data.redis.password=")
class AccountSecurityIT {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Phase18-test-password";
    private static final String NEW_PASSWORD = "Phase18-new-password";

    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder encoder;

    @Test
    void listsOnlyOwnedSessionsAndRevokesOneOtherSession() throws Exception {
        String emailA = email();
        String emailB = email();
        var a1 = new Browser();
        var a2 = new Browser();
        var b1 = new Browser();
        assertThat(a1.register(emailA).statusCode()).isEqualTo(201);
        assertThat(b1.register(emailB).statusCode()).isEqualTo(201);
        assertThat(a1.login(emailA, PASSWORD).statusCode()).isEqualTo(204);
        assertThat(a2.login(emailA, PASSWORD).statusCode()).isEqualTo(204);
        assertThat(b1.login(emailB, PASSWORD).statusCode()).isEqualTo(204);

        JsonNode aSessions = JSON.readTree(a1.get("/api/me/security/sessions").body());
        JsonNode bSessions = JSON.readTree(b1.get("/api/me/security/sessions").body());
        assertThat(aSessions.size()).isEqualTo(2);
        assertThat(bSessions.size()).isEqualTo(1);
        assertThat(aSessions.get(0).get("current").asBoolean()).isTrue();
        String currentRef = aSessions.get(0).get("sessionRef").asText();
        String otherRef = aSessions.get(1).get("sessionRef").asText();
        String foreignRef = bSessions.get(0).get("sessionRef").asText();
        assertThat(currentRef).matches("[0-9a-f]{64}");
        assertThat(otherRef).matches("[0-9a-f]{64}");
        assertThat(aSessions.toString()).doesNotContain("AMS_SESSION", "SPRING_SECURITY_CONTEXT", PASSWORD);
        assertThat(a1.delete("/api/me/security/sessions/" + foreignRef).statusCode()).isEqualTo(404);
        assertThat(a1.delete("/api/me/security/sessions/" + currentRef).statusCode()).isEqualTo(409);
        assertThat(a1.send("DELETE", "/api/me/security/sessions/" + otherRef, "", null).statusCode())
                .isEqualTo(403);
        assertThat(a1.delete("/api/me/security/sessions/" + otherRef).statusCode()).isEqualTo(204);
        assertThat(a2.get("/api/me").statusCode()).isEqualTo(401);
        assertThat(a1.get("/api/me").statusCode()).isEqualTo(200);
        assertThat(b1.get("/api/me").statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(a1.get("/api/me/security/sessions").body()).size()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from security_audit_event where event_type = 'SESSION_REVOKED' "
                + "and user_id = (select id from app_user where email = ?)", Integer.class, emailA)).isEqualTo(1);
        String metadata = jdbc.queryForObject("select metadata::text from security_audit_event "
                + "where event_type = 'SESSION_REVOKED' and user_id = (select id from app_user where email = ?)",
                String.class, emailA);
        assertThat(metadata).doesNotContain(currentRef, otherRef, foreignRef, emailA, PASSWORD);
    }

    @Test
    void revokesAllOtherSessionsButPreservesCurrent() throws Exception {
        String email = email();
        var a1 = new Browser();
        var a2 = new Browser();
        assertThat(a1.register(email).statusCode()).isEqualTo(201);
        assertThat(a1.login(email, PASSWORD).statusCode()).isEqualTo(204);
        assertThat(a2.login(email, PASSWORD).statusCode()).isEqualTo(204);
        assertThat(a1.delete("/api/me/security/sessions/others").statusCode()).isEqualTo(204);
        assertThat(a1.get("/api/me").statusCode()).isEqualTo(200);
        assertThat(a2.get("/api/me").statusCode()).isEqualTo(401);
        assertThat(JSON.readTree(a1.get("/api/me/security/sessions").body()).size()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select session_generation from app_user where email = ?", Long.class, email))
                .isEqualTo(1L);
    }

    @Test
    void passwordChangeChecksCurrentPasswordAndInvalidatesOtherSessions() throws Exception {
        String email = email();
        var a1 = new Browser();
        var a2 = new Browser();
        assertThat(a1.register(email).statusCode()).isEqualTo(201);
        assertThat(a1.login(email, PASSWORD).statusCode()).isEqualTo(204);
        assertThat(a2.login(email, PASSWORD).statusCode()).isEqualTo(204);
        String oldHash = jdbc.queryForObject("select password_hash from app_user where email = ?", String.class, email);
        assertThat(a1.password("wrong-current-password", NEW_PASSWORD).statusCode()).isEqualTo(400);
        assertThat(a1.password(PASSWORD, PASSWORD).statusCode()).isEqualTo(400);
        assertThat(a1.password(PASSWORD, "ậ".repeat(30)).statusCode()).isEqualTo(400);
        assertThat(a2.get("/api/me").statusCode()).isEqualTo(200);
        assertThat(a1.send("PUT", "/api/me/security/password", "{}", null).statusCode()).isEqualTo(403);

        assertThat(a1.password(PASSWORD, NEW_PASSWORD).statusCode()).isEqualTo(204);
        assertThat(a1.get("/api/me").statusCode()).isEqualTo(200);
        assertThat(a2.get("/api/me").statusCode()).isEqualTo(401);
        String newHash = jdbc.queryForObject("select password_hash from app_user where email = ?", String.class, email);
        assertThat(newHash).isNotEqualTo(oldHash).isNotEqualTo(NEW_PASSWORD);
        assertThat(encoder.matches(NEW_PASSWORD, newHash)).isTrue();
        assertThat(new Browser().login(email, PASSWORD).statusCode()).isEqualTo(401);
        assertThat(new Browser().login(email, NEW_PASSWORD).statusCode()).isEqualTo(204);
        assertThat(jdbc.queryForObject("select count(*) from security_audit_event where event_type = 'PASSWORD_CHANGED' "
                + "and user_id = (select id from app_user where email = ?)", Integer.class, email)).isEqualTo(1);
    }

    @Test
    void disabledAccountCannotManageSecurity() throws Exception {
        String email = email();
        var browser = new Browser();
        assertThat(browser.register(email).statusCode()).isEqualTo(201);
        assertThat(browser.login(email, PASSWORD).statusCode()).isEqualTo(204);
        jdbc.update("update app_user set account_status = 'DISABLED' where email = ?", email);
        assertThat(browser.get("/api/me/security/sessions").statusCode()).isEqualTo(403);
        assertThat(browser.password(PASSWORD, NEW_PASSWORD).statusCode()).isNotEqualTo(204);
    }

    @Test
    void concurrentPasswordChangesCannotBothUseTheOldPassword() throws Exception {
        String email = email();
        var first = new Browser();
        var second = new Browser();
        assertThat(first.register(email).statusCode()).isEqualTo(201);
        assertThat(first.login(email, PASSWORD).statusCode()).isEqualTo(204);
        assertThat(second.login(email, PASSWORD).statusCode()).isEqualTo(204);
        String candidateA = "Phase18-concurrent-A";
        String candidateB = "Phase18-concurrent-B";
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var resultA = executor.submit(() -> { start.await(); return first.password(PASSWORD, candidateA).statusCode(); });
            var resultB = executor.submit(() -> { start.await(); return second.password(PASSWORD, candidateB).statusCode(); });
            start.countDown();
            int statusA = resultA.get();
            int statusB = resultB.get();
            assertThat((statusA == 204 ? 1 : 0) + (statusB == 204 ? 1 : 0)).isEqualTo(1);
            String winner = statusA == 204 ? candidateA : candidateB;
            String loser = statusA == 204 ? candidateB : candidateA;
            assertThat((statusA == 204 ? first : second).get("/api/me").statusCode()).isEqualTo(200);
            assertThat((statusA == 204 ? second : first).get("/api/me").statusCode()).isEqualTo(401);
            assertThat(new Browser().login(email, winner).statusCode()).isEqualTo(204);
            assertThat(new Browser().login(email, loser).statusCode()).isEqualTo(401);
            assertThat(jdbc.queryForObject("select count(*) from security_audit_event where event_type = 'PASSWORD_CHANGED' "
                    + "and user_id = (select id from app_user where email = ?)", Integer.class, email)).isEqualTo(1);
        }
    }

    @Test
    void loginOverlappingPasswordChangeCannotLeaveAnOldPasswordSessionUsable() throws Exception {
        String email = email();
        var current = new Browser();
        var racingLogin = new Browser();
        assertThat(current.register(email).statusCode()).isEqualTo(201);
        assertThat(current.login(email, PASSWORD).statusCode()).isEqualTo(204);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var passwordChange = executor.submit(() -> {
                start.await(); return current.password(PASSWORD, NEW_PASSWORD).statusCode();
            });
            var login = executor.submit(() -> {
                start.await(); return racingLogin.login(email, PASSWORD).statusCode();
            });
            start.countDown();
            assertThat(passwordChange.get()).isEqualTo(204);
            assertThat(login.get()).isIn(204, 401);
            assertThat(racingLogin.get("/api/me").statusCode()).isEqualTo(401);
            assertThat(current.get("/api/me").statusCode()).isEqualTo(200);
        }
    }

    private String email() { return "phase18-" + UUID.randomUUID() + "@example.test"; }

    private class Browser {
        private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        private final HttpClient client = HttpClient.newBuilder().cookieHandler(cookies)
                .connectTimeout(Duration.ofSeconds(5)).build();

        HttpResponse<String> get(String path) throws Exception { return send("GET", path, "", null); }
        String csrf() throws Exception { return JSON.readTree(get("/api/auth/csrf").body()).get("token").asText(); }
        HttpResponse<String> register(String email) throws Exception {
            return send("POST", "/api/auth/register", JSON.writeValueAsString(Map.of(
                    "email", email, "password", PASSWORD, "displayName", "Test student")), csrf());
        }
        HttpResponse<String> login(String email, String password) throws Exception {
            String form = "email=" + URLEncoder.encode(email, StandardCharsets.UTF_8)
                    + "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8);
            return send("POST", "/api/auth/login", form, csrf(), "application/x-www-form-urlencoded");
        }
        HttpResponse<String> password(String current, String next) throws Exception {
            return send("PUT", "/api/me/security/password", JSON.writeValueAsString(Map.of(
                    "currentPassword", current, "newPassword", next)), csrf());
        }
        HttpResponse<String> delete(String path) throws Exception { return send("DELETE", path, "", csrf()); }
        HttpResponse<String> send(String method, String path, String body, String token) throws Exception {
            return send(method, path, body, token, "application/json");
        }
        HttpResponse<String> send(String method, String path, String body, String token, String contentType)
                throws Exception {
            var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                    .timeout(Duration.ofSeconds(15)).header("Content-Type", contentType)
                    .method(method, HttpRequest.BodyPublishers.ofString(body));
            if (token != null) request.header("X-CSRF-TOKEN", token);
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
