package vn.edu.phenikaa.ams.calendar.google;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class GoogleOAuthHttpTest {
    private HttpServer server;
    private URI base;
    private final AtomicReference<String> tokenBody = new AtomicReference<>();
    private final AtomicReference<String> tokenReply = new AtomicReference<>();
    private final AtomicReference<String> revokeBody = new AtomicReference<>();
    private final AtomicInteger tokenStatus = new AtomicInteger(200);
    private final AtomicInteger tokenDelayMillis = new AtomicInteger();

    @BeforeEach void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/token", exchange -> {
            tokenBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            pause(tokenDelayMillis.get());
            respond(exchange, tokenStatus.get(), tokenReply.get());
        });
        server.createContext("/revoke", exchange -> {
            revokeBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, "");
        });
        server.start();
        base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach void stop() { server.stop(0); }

    @Test void webFlowUsesOnlyAppCreatedScopeOfflineAccessAndPkce() {
        var gateway = gateway();
        String verifier = "a".repeat(43);
        String challenge = GoogleOAuthStateStore.challenge(verifier);
        var initial = URI.create(gateway.authorizationUrl("synthetic-state", challenge, false));
        var parameters = parameters(initial.getRawQuery());
        assertThat(parameters).containsEntry("scope", SpringGoogleOAuthGateway.SCOPE)
                .containsEntry("state", "synthetic-state").containsEntry("access_type", "offline")
                .containsEntry("code_challenge", challenge).containsEntry("code_challenge_method", "S256")
                .containsEntry("redirect_uri", "http://localhost:3000/api/integrations/google-calendar/callback");
        assertThat(parameters).doesNotContainKeys("prompt", "client_secret", "code_verifier");
        assertThat(parameters.get("scope")).doesNotContain("calendar.events", "calendar.readonly");
        assertThat(parameters(URI.create(gateway.authorizationUrl("other-state", challenge, true)).getRawQuery()))
                .containsEntry("prompt", "consent");

        tokenReply.set("""
                {"access_token":"synthetic-access","expires_in":3600,"refresh_token":"synthetic-refresh",
                 "scope":"https://www.googleapis.com/auth/calendar.app.created","token_type":"Bearer"}
                """);
        var tokens = gateway.exchange("synthetic-code", "synthetic-state", verifier);
        assertThat(tokens.refreshToken()).isEqualTo("synthetic-refresh");
        assertThat(tokens.toString()).doesNotContain("synthetic-access", "synthetic-refresh");
        assertThat(parameters(tokenBody.get())).containsEntry("grant_type", "authorization_code")
                .containsEntry("code", "synthetic-code").containsEntry("code_verifier", verifier)
                .containsEntry("client_secret", "synthetic-client-secret");

        tokenReply.set("""
                {"access_token":"renewed-access","expires_in":3600,"token_type":"Bearer"}
                """);
        var refreshed = gateway.refresh(tokens.accessToken(), tokens.refreshToken());
        assertThat(refreshed.refreshToken()).isEqualTo("synthetic-refresh");
        assertThat(parameters(tokenBody.get())).containsEntry("grant_type", "refresh_token")
                .containsEntry("refresh_token", "synthetic-refresh");
        assertThat(gateway.revoke("synthetic-refresh")).isTrue();
        assertThat(parameters(revokeBody.get())).containsEntry("token", "synthetic-refresh");
    }

    @Test void rejectsInvalidGrantMissingScopeAndNetworkFailureWithoutLeakingTokens() {
        var gateway = gateway();
        tokenStatus.set(400);
        tokenReply.set("{\"error\":\"invalid_grant\"}");
        assertThatThrownBy(() -> gateway.exchange("bad-code", "state", "a".repeat(43)))
                .hasMessage("GOOGLE_TOKEN_EXCHANGE_FAILED").hasNoCause();
        assertThatThrownBy(() -> gateway.refresh("synthetic-access", "synthetic-refresh"))
                .hasMessage("GOOGLE_RECONNECTION_REQUIRED").hasNoCause();
        tokenStatus.set(200);
        tokenReply.set("{\"access_token\":\"synthetic-access\",\"expires_in\":3600,\"token_type\":\"Bearer\"}");
        assertThatThrownBy(() -> gateway.exchange("code", "state", "a".repeat(43)))
                .hasMessage("GOOGLE_TOKEN_EXCHANGE_FAILED");
        tokenStatus.set(503);
        tokenReply.set("{\"error\":\"server_error\"}");
        assertThatThrownBy(() -> gateway.exchange("code", "state", "a".repeat(43)))
                .hasMessage("GOOGLE_TOKEN_EXCHANGE_FAILED").hasNoCause();
        tokenStatus.set(200);
        tokenReply.set("not-json");
        assertThatThrownBy(() -> gateway.exchange("code", "state", "a".repeat(43)))
                .hasMessage("GOOGLE_TOKEN_EXCHANGE_FAILED").hasNoCause();
        server.stop(0);
        assertThatThrownBy(() -> gateway.exchange("code", "state", "a".repeat(43)))
                .hasMessage("GOOGLE_TOKEN_EXCHANGE_FAILED").hasNoCause();
    }

    @Test void tokenExchangeTimeoutReturnsSafeError() {
        tokenDelayMillis.set(500);
        var gateway = new SpringGoogleOAuthGateway("synthetic-client-id", "synthetic-client-secret",
                "http://localhost:3000/api/integrations/google-calendar/callback",
                base.resolve("/authorize"), base.resolve("/token"), base.resolve("/revoke"),
                Duration.ofSeconds(1), Duration.ofMillis(150));
        assertThatThrownBy(() -> gateway.exchange("synthetic-code", "state", "a".repeat(43)))
                .hasMessage("GOOGLE_TOKEN_EXCHANGE_FAILED").hasNoCause();
    }

    private SpringGoogleOAuthGateway gateway() {
        return new SpringGoogleOAuthGateway("synthetic-client-id", "synthetic-client-secret",
                "http://localhost:3000/api/integrations/google-calendar/callback",
                base.resolve("/authorize"), base.resolve("/token"), base.resolve("/revoke"));
    }

    private static Map<String, String> parameters(String raw) {
        return Arrays.stream(raw.split("&")).map(part -> part.split("=", 2))
                .collect(Collectors.toMap(part -> URLDecoder.decode(part[0], StandardCharsets.UTF_8),
                        part -> URLDecoder.decode(part.length == 2 ? part[1] : "", StandardCharsets.UTF_8)));
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length != 0) exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static void pause(int millis) {
        try { Thread.sleep(millis); }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
    }
}
