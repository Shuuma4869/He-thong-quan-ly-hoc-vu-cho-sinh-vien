package vn.edu.phenikaa.ams.notification.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.edu.phenikaa.ams.notification.application.port.EmailNotificationGateway.*;

class ResendEmailGatewayTest {
    private HttpServer server;
    private final AtomicInteger responseStatus = new AtomicInteger(200);
    private final AtomicReference<String> responseBody = new AtomicReference<>("{\"id\":\"synthetic-message-id\"}");
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> idempotency = new AtomicReference<>();

    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/emails", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            idempotency.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
            byte[] body = responseBody.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(responseStatus.get(), body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
    }

    @AfterEach void stop() { server.stop(0); }

    private ResendEmailGateway gateway() {
        return new ResendEmailGateway(HttpClient.newHttpClient(),
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/emails"),
                "synthetic-test-key", "notify@example.test");
    }

    private NotificationEmail email() {
        return new NotificationEmail("student@example.test", "AMS — Kiểm tra", "Nội dung giả định", "sync-alert-synthetic");
    }

    @Test void sendsDocumentedRequestAndMapsAcceptedResponse() {
        var result = gateway().send(email());
        assertThat(result.status()).isEqualTo(DeliveryStatus.ACCEPTED);
        assertThat(result.providerMessageId()).isEqualTo("synthetic-message-id");
        assertThat(authorization.get()).startsWith("Bearer ");
        assertThat(idempotency.get()).isEqualTo("sync-alert-synthetic");
        assertThat(requestBody.get()).contains("student@example.test", "notify@example.test", "Nội dung giả định");
    }

    @Test void mapsRateLimitServerErrorAndPermanentError() {
        responseStatus.set(429); responseBody.set("{}");
        assertThat(gateway().send(email()).status()).isEqualTo(DeliveryStatus.RETRYABLE_FAILURE);
        responseStatus.set(503);
        assertThat(gateway().send(email()).status()).isEqualTo(DeliveryStatus.RETRYABLE_FAILURE);
        responseStatus.set(400);
        assertThat(gateway().send(email()).status()).isEqualTo(DeliveryStatus.REJECTED);
        responseStatus.set(409); responseBody.set("{\"name\":\"concurrent_idempotent_requests\"}");
        assertThat(gateway().send(email()).status()).isEqualTo(DeliveryStatus.RETRYABLE_FAILURE);
        responseBody.set("{\"name\":\"invalid_idempotent_request\"}");
        assertThat(gateway().send(email()).status()).isEqualTo(DeliveryStatus.REJECTED);
    }

    @Test void treatsNetworkFailureAndMalformedSuccessAsRetryable() {
        responseBody.set("{}");
        assertThat(gateway().send(email()).status()).isEqualTo(DeliveryStatus.RETRYABLE_FAILURE);
        var unreachable = new ResendEmailGateway(HttpClient.newHttpClient(),
                URI.create("http://127.0.0.1:1/emails"), "synthetic-test-key", "notify@example.test");
        assertThat(unreachable.send(email()).status()).isEqualTo(DeliveryStatus.RETRYABLE_FAILURE);
    }
}
