package vn.edu.phenikaa.ams.calendar.google;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class GoogleCalendarHttpTest {
    private HttpServer server;
    private GoogleCalendarHttpGateway gateway;
    private final AtomicInteger createCount = new AtomicInteger();
    private final AtomicInteger eventCount = new AtomicInteger();
    private final AtomicReference<String> createBody = new AtomicReference<>();
    private final AtomicInteger getStatus = new AtomicInteger(200);
    private final AtomicInteger createStatus = new AtomicInteger(200);
    private final AtomicReference<String> getReply = new AtomicReference<>("{\"id\":\"synthetic-calendar-id\"}");
    private final AtomicInteger createDelayMillis = new AtomicInteger();

    @BeforeEach void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/calendar/v3/calendars", exchange -> {
            if (exchange.getRequestURI().getPath().contains("/events")) eventCount.incrementAndGet();
            if (exchange.getRequestMethod().equals("POST")) {
                createCount.incrementAndGet();
                createBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                pause(createDelayMillis.get());
                respond(exchange, createStatus.get(), "{\"id\":\"synthetic-calendar-id\"}");
            } else {
                respond(exchange, getStatus.get(), getReply.get());
            }
        });
        server.start();
        gateway = new GoogleCalendarHttpGateway(URI.create("http://127.0.0.1:"
                + server.getAddress().getPort() + "/calendar/v3"));
    }

    @AfterEach void stop() { server.stop(0); }

    @Test void createsOnlySecondaryCalendarAndReusesKnownId() {
        assertThat(gateway.create("synthetic-access")).isEqualTo("synthetic-calendar-id");
        assertThat(createBody.get()).contains("AMS - Lịch học vụ", "Asia/Ho_Chi_Minh")
                .doesNotContain("primary", "student@example.test");
        assertThat(gateway.exists("synthetic-access", "synthetic-calendar-id")).isTrue();
        assertThat(createCount.get()).isEqualTo(1);
        assertThat(eventCount.get()).isZero();
        getStatus.set(404);
        assertThat(gateway.exists("synthetic-access", "synthetic-calendar-id")).isFalse();
        assertThat(gateway.create("synthetic-access")).isEqualTo("synthetic-calendar-id");
        assertThat(createCount.get()).isEqualTo(2);
        assertThat(eventCount.get()).isZero();
    }

    @Test void mapsRevokedPermissionDeniedAndUncertainCreateWithoutRetries() {
        getStatus.set(401);
        assertThatThrownBy(() -> gateway.exists("synthetic-access", "synthetic-calendar-id"))
                .hasMessage("GOOGLE_RECONNECTION_REQUIRED");
        getStatus.set(403);
        assertThatThrownBy(() -> gateway.exists("synthetic-access", "synthetic-calendar-id"))
                .hasMessage("GOOGLE_CALENDAR_SETUP_FAILED");
        createStatus.set(503);
        assertThatThrownBy(() -> gateway.create("synthetic-access")).hasMessage("GOOGLE_UNAVAILABLE");
        assertThat(createCount.get()).isEqualTo(1);
        getStatus.set(429);
        assertThatThrownBy(() -> gateway.exists("synthetic-access", "synthetic-calendar-id"))
                .hasMessage("GOOGLE_UNAVAILABLE");
        getStatus.set(200);
        getReply.set("not-json");
        assertThatThrownBy(() -> gateway.exists("synthetic-access", "synthetic-calendar-id"))
                .hasMessage("GOOGLE_UNAVAILABLE");
        assertThat(eventCount.get()).isZero();
    }

    @Test void calendarCreateTimeoutIsNotRetried() {
        createDelayMillis.set(500);
        gateway = new GoogleCalendarHttpGateway(URI.create("http://127.0.0.1:"
                + server.getAddress().getPort() + "/calendar/v3"),
                Duration.ofSeconds(1), Duration.ofMillis(150));
        assertThatThrownBy(() -> gateway.create("synthetic-access"))
                .hasMessage("GOOGLE_CALENDAR_SETUP_FAILED").hasNoCause();
        assertThat(createCount.get()).isEqualTo(1);
        assertThat(eventCount.get()).isZero();
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, status == 404 ? -1 : bytes.length);
        if (status != 404) exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static void pause(int millis) {
        try { Thread.sleep(millis); }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
    }
}
