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
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import org.springframework.session.data.redis.RedisSessionRepository;
import tools.jackson.databind.json.JsonMapper;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.data.redis.password=", "spring.session.data.redis.repository-type=indexed"})
class IndexedSessionRepositoryIT {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String PASSWORD = "Phase18-test-password";

    @LocalServerPort private int port;
    @Autowired private SessionRepository<? extends Session> repository;

    @Test
    void indexesOnlyAuthenticatedSessionsOfTheSameAccount() throws Exception {
        assertThat(repository).isInstanceOf(FindByIndexNameSessionRepository.class);
        @SuppressWarnings("unchecked")
        var sessions = (FindByIndexNameSessionRepository<Session>) repository;
        String accountA = "phase18-a-" + UUID.randomUUID() + "@example.test";
        String accountB = "phase18-b-" + UUID.randomUUID() + "@example.test";
        var a1 = new Browser();
        var a2 = new Browser();
        var b1 = new Browser();

        assertThat(a1.register(accountA).statusCode()).isEqualTo(201);
        assertThat(b1.register(accountB).statusCode()).isEqualTo(201);
        assertThat(a1.login(accountA).statusCode()).isEqualTo(204);
        assertThat(a2.login(accountA).statusCode()).isEqualTo(204);
        assertThat(b1.login(accountB).statusCode()).isEqualTo(204);

        Map<String, ? extends Session> accountASessions = sessions.findByPrincipalName(accountA);
        Map<String, ? extends Session> accountBSessions = sessions.findByPrincipalName(accountB);
        String accountAId = JSON.readTree(a1.get("/api/me").body()).get("id").asText();
        assertThat(accountASessions).hasSize(2);
        assertThat(accountBSessions).hasSize(1);
        assertThat(sessions.findByPrincipalName(accountAId)).isEmpty();
        assertThat(accountASessions.keySet()).doesNotContainAnyElementsOf(accountBSessions.keySet());
        assertThat(a1.get("/api/me").statusCode()).isEqualTo(200);
        assertThat(a2.get("/api/me").statusCode()).isEqualTo(200);
        assertThat(b1.get("/api/me").statusCode()).isEqualTo(200);

        assertThat(a2.post("/api/auth/logout").statusCode()).isEqualTo(204);
        assertThat(sessions.findByPrincipalName(accountA)).hasSize(1);
        assertThat(sessions.findByPrincipalName(accountB)).hasSize(1);

        Session expiring = sessions.findById(accountBSessions.keySet().iterator().next());
        assertThat(expiring).isNotNull();
        expiring.setMaxInactiveInterval(Duration.ZERO);
        sessions.save(expiring);
        assertThat(sessions.findByPrincipalName(accountB)).isEmpty();
    }

    @Test
    void aStaleSaveCannotRestoreADeletedSession() throws Exception {
        String account = "phase18-stale-" + UUID.randomUUID() + "@example.test";
        var browser = new Browser();
        assertThat(browser.register(account).statusCode()).isEqualTo(201);
        assertThat(browser.login(account).statusCode()).isEqualTo(204);

        @SuppressWarnings("unchecked")
        var sessions = (FindByIndexNameSessionRepository<Session>) repository;
        String sessionId = new String(Base64.getDecoder().decode(browser.sessionCookie()), StandardCharsets.UTF_8);
        Session staleSession = sessions.findById(sessionId);
        assertThat(staleSession).isNotNull();
        sessions.deleteById(sessionId);
        assertThat(sessions.findById(sessionId)).isNull();
        staleSession.setAttribute("probe", "1");
        sessions.save(staleSession);
        assertThat(sessions.findById(sessionId)).isNull();
    }

    @Test
    void sessionsWrittenByThePreviousDefaultRepositoryHaveNoPrincipalIndex() {
        var indexed = (RedisIndexedSessionRepository) repository;
        @SuppressWarnings("unchecked")
        SessionRepository<Session> previous = (SessionRepository<Session>) (SessionRepository<?>)
                new RedisSessionRepository(indexed.getSessionRedisOperations());
        String account = "phase18-legacy-" + UUID.randomUUID() + "@example.test";
        var legacySession = previous.createSession();
        legacySession.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, account);
        previous.save(legacySession);

        assertThat(previous.findById(legacySession.getId())).isNotNull();
        assertThat(indexed.findByPrincipalName(account)).isEmpty();
    }

    private class Browser {
        private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        private final HttpClient client = HttpClient.newBuilder().cookieHandler(cookies)
                .connectTimeout(Duration.ofSeconds(5)).build();

        HttpResponse<String> get(String path) throws Exception { return send("GET", path, "", null); }
        String csrf() throws Exception {
            return JSON.readTree(get("/api/auth/csrf").body()).get("token").asText();
        }
        String sessionCookie() {
            return cookies.getCookieStore().getCookies().stream()
                    .filter(cookie -> cookie.getName().equals("AMS_SESSION"))
                    .findFirst().orElseThrow().getValue();
        }
        HttpResponse<String> register(String email) throws Exception {
            return send("POST", "/api/auth/register", JSON.writeValueAsString(Map.of(
                    "email", email, "password", PASSWORD, "displayName", "Test student")), csrf());
        }
        HttpResponse<String> login(String email) throws Exception {
            String form = "email=" + URLEncoder.encode(email, StandardCharsets.UTF_8)
                    + "&password=" + URLEncoder.encode(PASSWORD, StandardCharsets.UTF_8);
            return send("POST", "/api/auth/login", form, csrf(), "application/x-www-form-urlencoded");
        }
        HttpResponse<String> post(String path) throws Exception { return send("POST", path, "", csrf()); }
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
