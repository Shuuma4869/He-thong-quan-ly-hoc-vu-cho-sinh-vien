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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.data.redis.password=")
class AccountAuthenticationIT {
    private static final String PASSWORD = "Phase1-test-password";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder encoder;
    @Autowired private SessionRepository<? extends Session> sessions;

    @Test
    void registrationNormalizesEmailHashesPasswordAndCreatesStudentSettings() throws Exception {
        var browser = new Browser();
        String email = email().toUpperCase();
        assertThat(browser.register(email).statusCode()).isEqualTo(201);
        String normalized = email.toLowerCase(java.util.Locale.ROOT);
        String hash = jdbc.queryForObject("select password_hash from app_user where email = ?", String.class, normalized);
        assertThat(hash).startsWith("$2a$12$").isNotEqualTo(PASSWORD);
        assertThat(encoder.matches(PASSWORD, hash)).isTrue();
        assertThat(jdbc.queryForObject("select role from app_user where email = ?", String.class, normalized)).isEqualTo("STUDENT");
        assertThat(browser.login(email, PASSWORD).statusCode()).isEqualTo(204);
        var me = JSON.readTree(browser.get("/api/me").body());
        assertThat(UUID.fromString(me.get("id").asText())).isNotNull();
        assertThat(me.get("email").asText()).isEqualTo(normalized);
        assertThat(me.get("role").asText()).isEqualTo("STUDENT");
        assertThat(me.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(me.get("settings").get("timezone").asText()).isEqualTo("Asia/Ho_Chi_Minh");
        assertThat(me.has("passwordHash")).isFalse();
        assertThat(me.has("password")).isFalse();
    }

    @Test
    void duplicateEmailIsRejectedCaseInsensitively() throws Exception {
        String email = email();
        assertThat(new Browser().register(email).statusCode()).isEqualTo(201);
        assertThat(new Browser().register(email.toUpperCase()).statusCode()).isEqualTo(409);
        assertThat(jdbc.queryForObject("select count(*) from app_user where email = ?", Integer.class, email)).isEqualTo(1);
    }

    @Test
    void validatesEmailAndPasswordWithoutReturningSubmittedSecrets() throws Exception {
        var browser = new Browser();
        for (var body : new Object[][] {
                {"invalid-email", PASSWORD}, {email(), "short"}, {email(), "a".repeat(73)},
                {email(), "ậ".repeat(30)} }) {
            var response = browser.postJson("/api/auth/register", Map.of("email", body[0], "password", body[1], "displayName", "Test user"));
            assertThat(response.statusCode()).isEqualTo(400);
            assertThat(response.body()).doesNotContain((String) body[1]);
        }
    }

    @Test
    void invalidCredentialsDoNotCreateAnAuthenticatedSession() throws Exception {
        String email = email();
        var browser = new Browser();
        browser.register(email);
        var wrongPassword = browser.login(email, "Incorrect-test-password");
        var unknownAccount = browser.login(email(), PASSWORD);
        assertThat(wrongPassword.statusCode()).isEqualTo(401);
        assertThat(unknownAccount.statusCode()).isEqualTo(401);
        assertThat(wrongPassword.body()).isEqualTo(unknownAccount.body());
        assertThat(browser.login(email, "a".repeat(100)).statusCode()).isEqualTo(401);
        assertThat(browser.get("/api/me").statusCode()).isEqualTo(401);
    }

    @Test
    void loginRotatesSessionAndLogoutRevokesIt() throws Exception {
        var browser = new Browser();
        String email = email();
        browser.register(email);
        String anonymousSession = browser.sessionCookie();
        assertThat(browser.login(email, PASSWORD).statusCode()).isEqualTo(204);
        String authenticatedSession = browser.sessionCookie();
        assertThat(authenticatedSession).isNotEqualTo(anonymousSession);
        String sessionId = new String(java.util.Base64.getDecoder().decode(authenticatedSession), StandardCharsets.UTF_8);
        Session session = sessions.findById(sessionId);
        assertThat(session).isNotNull();
        assertThat(session.getMaxInactiveInterval()).isEqualTo(Duration.ofMinutes(30));
        SecurityContext context = session.getAttribute("SPRING_SECURITY_CONTEXT");
        assertThat(context.getAuthentication().getCredentials()).isNull();
        assertThat(((UserDetails) context.getAuthentication().getPrincipal()).getPassword()).isNull();
        assertThat(browser.get("/api/me").statusCode()).isEqualTo(200);
        assertThat(replaySession(anonymousSession).statusCode()).isEqualTo(401);
        assertThat(browser.postJson("/api/auth/logout", Map.of()).statusCode()).isEqualTo(204);
        assertThat(browser.get("/api/me").statusCode()).isEqualTo(401);
        assertThat(replaySession(authenticatedSession).statusCode()).isEqualTo(401);
    }

    @Test
    void csrfIsRequiredForRegisterLoginLogoutAndSettingsAndRotatesAfterLogin() throws Exception {
        var browser = new Browser();
        for (String path : new String[] {"/api/auth/register", "/api/auth/login", "/api/auth/logout"}) {
            assertThat(browser.send("POST", path, "{}", "application/json", null).statusCode()).isEqualTo(403);
            assertThat(browser.send("POST", path, "{}", "application/json", "invalid-token").statusCode()).isEqualTo(403);
        }
        String email = email();
        browser.register(email);
        String oldToken = browser.csrf();
        browser.login(email, PASSWORD);
        String settings = JSON.writeValueAsString(settings("en-US"));
        assertThat(browser.send("PUT", "/api/me/settings", settings, "application/json", oldToken).statusCode()).isEqualTo(403);
        assertThat(browser.send("PUT", "/api/me/settings", settings, "application/json", null).statusCode()).isEqualTo(403);
        assertThat(browser.send("POST", "/api/auth/logout", "", "application/json", null).statusCode()).isEqualTo(403);
        assertThat(browser.get("/api/me").statusCode()).isEqualTo(200);
        assertThat(browser.send("PUT", "/api/me/settings", settings, "application/json", browser.csrf()).statusCode()).isEqualTo(200);
    }

    @Test
    void settingsAreOwnedByTheSessionUserAndCannotEscalateRoles() throws Exception {
        var first = registeredBrowser();
        var second = registeredBrowser();
        var firstUser = JSON.readTree(first.get("/api/me").body());
        var secondUser = JSON.readTree(second.get("/api/me").body());
        String otherId = secondUser.get("id").asText();
        assertThat(firstUser.get("id").asText()).isNotEqualTo(otherId);
        var payload = new java.util.HashMap<String, Object>(settings("en-US"));
        payload.put("userId", otherId);
        payload.put("role", "ADMIN");
        var response = first.send("PUT", "/api/me/settings?userId=" + otherId,
                JSON.writeValueAsString(payload), "application/json", first.csrf());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(first.get("/api/me").body()).get("settings").get("locale").asText()).isEqualTo("en-US");
        assertThat(JSON.readTree(second.get("/api/me").body()).get("settings").get("locale").asText()).isEqualTo("vi-VN");
        assertThat(JSON.readTree(first.get("/api/me").body()).get("role").asText()).isEqualTo("STUDENT");
        assertThat(first.get("/api/me/" + otherId).statusCode()).isEqualTo(404);
    }

    @Test
    void rejectsInvalidSettingsAndDisabledAccounts() throws Exception {
        String email = email();
        var browser = new Browser();
        browser.register(email);
        browser.login(email, PASSWORD);
        var payload = new java.util.HashMap<String, Object>(settings("vi-VN"));
        payload.put("timezone", "Invalid/Timezone");
        assertThat(browser.send("PUT", "/api/me/settings", JSON.writeValueAsString(payload), "application/json", browser.csrf()).statusCode()).isEqualTo(400);
        jdbc.update("update app_user set account_status = 'DISABLED' where email = ?", email);
        assertThat(browser.get("/api/me").statusCode()).isEqualTo(403);
        assertThat(browser.login(email, PASSWORD).statusCode()).isEqualTo(401);
    }

    @Test
    void sessionCookieIsHttpOnlySameSiteAndPrivateResponsesAreNotCached() throws Exception {
        var browser = new Browser();
        var csrf = browser.get("/api/auth/csrf");
        String cookie = csrf.headers().firstValue("Set-Cookie").orElseThrow();
        assertThat(cookie).contains("AMS_SESSION=", "HttpOnly", "SameSite=Lax", "Path=/");
        assertThat(csrf.headers().firstValue("Cache-Control").orElseThrow()).contains("no-store");
        assertThat(browser.get("/api/me").statusCode()).isEqualTo(401);
    }

    private Browser registeredBrowser() throws Exception {
        var browser = new Browser();
        String email = email();
        assertThat(browser.register(email).statusCode()).isEqualTo(201);
        assertThat(browser.login(email, PASSWORD).statusCode()).isEqualTo(204);
        return browser;
    }

    private Map<String, Object> settings(String locale) {
        return Map.of("notificationEmail", "notifications@example.test", "timezone", "Asia/Ho_Chi_Minh", "locale", locale, "theme", "SYSTEM");
    }

    private String email() { return "phase1-" + UUID.randomUUID() + "@example.test"; }

    private HttpResponse<String> replaySession(String cookie) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/me"))
                .header("Cookie", "AMS_SESSION=" + cookie).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private class Browser {
        private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        private final HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(Duration.ofSeconds(5)).build();
        HttpResponse<String> get(String path) throws Exception { return send("GET", path, "", "application/json", null); }
        String csrf() throws Exception { return JSON.readTree(get("/api/auth/csrf").body()).get("token").asText(); }
        String sessionCookie() {
            return cookies.getCookieStore().getCookies().stream().filter(cookie -> cookie.getName().equals("AMS_SESSION"))
                    .findFirst().orElseThrow().getValue();
        }
        HttpResponse<String> register(String email) throws Exception {
            return postJson("/api/auth/register", Map.of("email", email, "password", PASSWORD, "displayName", "Test student", "role", "ADMIN"));
        }
        HttpResponse<String> postJson(String path, Object payload) throws Exception {
            return send("POST", path, JSON.writeValueAsString(payload), "application/json", csrf());
        }
        HttpResponse<String> login(String email, String password) throws Exception {
            return send("POST", "/api/auth/login", "email=" + URLEncoder.encode(email, StandardCharsets.UTF_8)
                    + "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8), "application/x-www-form-urlencoded", csrf());
        }
        HttpResponse<String> send(String method, String path, String body, String contentType, String csrf) throws Exception {
            var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                    .timeout(Duration.ofSeconds(15)).header("Content-Type", contentType)
                    .method(method, HttpRequest.BodyPublishers.ofString(body));
            if (csrf != null) builder.header("X-CSRF-TOKEN", csrf);
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
