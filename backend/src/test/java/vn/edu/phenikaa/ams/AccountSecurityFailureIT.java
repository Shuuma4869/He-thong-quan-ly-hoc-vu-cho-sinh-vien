package vn.edu.phenikaa.ams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doCallRealMethod;

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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.json.JsonMapper;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.data.redis.password=")
class AccountSecurityFailureIT {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String OLD_PASSWORD = "Phase18-test-password";
    private static final String NEW_PASSWORD = "Phase18-new-password";

    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @MockitoSpyBean private RedisIndexedSessionRepository sessions;

    @Test
    void redisDeletionFailureIsVisibleButOldSessionsCannotAccessAfterPasswordCommit() throws Exception {
        String email = email();
        var current = new Browser();
        var other = new Browser();
        assertThat(current.register(email).statusCode()).isEqualTo(201);
        assertThat(current.login(email, OLD_PASSWORD).statusCode()).isEqualTo(204);
        assertThat(other.login(email, OLD_PASSWORD).statusCode()).isEqualTo(204);
        doThrow(new IllegalStateException("synthetic Redis deletion failure")).doCallRealMethod()
                .when(sessions).deleteById(anyString());

        assertThat(current.password(OLD_PASSWORD, NEW_PASSWORD).statusCode()).isEqualTo(503);
        assertThat(current.get("/api/me").statusCode()).isEqualTo(200);
        assertThat(other.get("/api/me").statusCode()).isEqualTo(401);
        assertThat(new Browser().login(email, OLD_PASSWORD).statusCode()).isEqualTo(401);
        assertThat(new Browser().login(email, NEW_PASSWORD).statusCode()).isEqualTo(204);
        assertThat(jdbc.queryForObject("select session_generation from app_user where email = ?", Long.class, email))
                .isEqualTo(1L);
    }

    @Test
    void postgresRollbackDoesNotChangePasswordOrRevokeSessions() throws Exception {
        String email = email();
        var current = new Browser();
        var other = new Browser();
        assertThat(current.register(email).statusCode()).isEqualTo(201);
        assertThat(current.login(email, OLD_PASSWORD).statusCode()).isEqualTo(204);
        assertThat(other.login(email, OLD_PASSWORD).statusCode()).isEqualTo(204);
        String previousHash = jdbc.queryForObject("select password_hash from app_user where email = ?", String.class, email);
        jdbc.execute("""
                create function phase18_reject_password_update() returns trigger language plpgsql as $$
                begin
                    if new.password_hash <> old.password_hash then
                        raise exception 'synthetic password update failure';
                    end if;
                    return new;
                end $$
                """);
        jdbc.execute("""
                create trigger phase18_reject_password_update before update of password_hash on app_user
                for each row execute function phase18_reject_password_update()
                """);
        try {
            assertThat(current.password(OLD_PASSWORD, NEW_PASSWORD).statusCode()).isNotEqualTo(204);
        } finally {
            jdbc.execute("drop trigger phase18_reject_password_update on app_user");
            jdbc.execute("drop function phase18_reject_password_update()");
        }
        assertThat(jdbc.queryForObject("select password_hash from app_user where email = ?", String.class, email))
                .isEqualTo(previousHash);
        assertThat(jdbc.queryForObject("select session_generation from app_user where email = ?", Long.class, email))
                .isZero();
        assertThat(current.get("/api/me").statusCode()).isEqualTo(200);
        assertThat(other.get("/api/me").statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("select count(*) from security_audit_event where event_type = 'PASSWORD_CHANGED' "
                + "and user_id = (select id from app_user where email = ?)", Integer.class, email)).isZero();
    }

    private String email() { return "phase18-failure-" + UUID.randomUUID() + "@example.test"; }

    private class Browser {
        private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        private final HttpClient client = HttpClient.newBuilder().cookieHandler(cookies)
                .connectTimeout(Duration.ofSeconds(5)).build();

        HttpResponse<String> get(String path) throws Exception { return send("GET", path, "", null); }
        String csrf() throws Exception { return JSON.readTree(get("/api/auth/csrf").body()).get("token").asText(); }
        HttpResponse<String> register(String email) throws Exception {
            return send("POST", "/api/auth/register", JSON.writeValueAsString(Map.of(
                    "email", email, "password", OLD_PASSWORD, "displayName", "Test student")), csrf());
        }
        HttpResponse<String> login(String email, String password) throws Exception {
            String form = "email=" + URLEncoder.encode(email, StandardCharsets.UTF_8)
                    + "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8);
            return send("POST", "/api/auth/login", form, csrf(), "application/x-www-form-urlencoded");
        }
        HttpResponse<String> password(String oldPassword, String newPassword) throws Exception {
            return send("PUT", "/api/me/security/password", JSON.writeValueAsString(Map.of(
                    "currentPassword", oldPassword, "newPassword", newPassword)), csrf());
        }
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
