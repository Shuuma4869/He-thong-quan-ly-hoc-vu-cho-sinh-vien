package vn.edu.phenikaa.ams.notification.infrastructure;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;
import vn.edu.phenikaa.ams.notification.application.port.EmailNotificationGateway;

public class ResendEmailGateway implements EmailNotificationGateway {
    private final HttpClient client;
    private final URI endpoint;
    private final String apiKey;
    private final String from;
    private final JsonMapper json = JsonMapper.builder().build();

    public ResendEmailGateway(HttpClient client, URI endpoint, String apiKey, String from) {
        if (apiKey == null || apiKey.isBlank() || from == null || from.isBlank())
            throw new IllegalArgumentException("Resend API key and sender address are required when email is enabled");
        if (!"https".equalsIgnoreCase(endpoint.getScheme())
                && !("http".equalsIgnoreCase(endpoint.getScheme())
                    && ("127.0.0.1".equals(endpoint.getHost()) || "localhost".equals(endpoint.getHost()))))
            throw new IllegalArgumentException("Resend endpoint must use HTTPS outside loopback tests");
        this.client = client; this.endpoint = endpoint; this.apiKey = apiKey; this.from = from;
    }

    @Override
    public DeliveryResult send(NotificationEmail notification) {
        try {
            String payload = json.writeValueAsString(Map.of("from", from, "to", new String[]{notification.recipient()},
                    "subject", notification.subject(), "text", notification.textBody()));
            var request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(12))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .header("Idempotency-Key", notification.idempotencyKey())
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8)).build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int code = response.statusCode();
            if (code >= 200 && code < 300) {
                String id = json.readTree(response.body()).path("id").asText();
                return id == null || id.isBlank()
                        ? new DeliveryResult(null, DeliveryStatus.RETRYABLE_FAILURE)
                        : new DeliveryResult(id, DeliveryStatus.ACCEPTED);
            }
            if (code == 429 || code >= 500)
                return new DeliveryResult(null, DeliveryStatus.RETRYABLE_FAILURE);
            if (code == 409) {
                String providerCode = json.readTree(response.body()).path("name").asText();
                return new DeliveryResult(null, "concurrent_idempotent_requests".equals(providerCode)
                        ? DeliveryStatus.RETRYABLE_FAILURE : DeliveryStatus.REJECTED);
            }
            return new DeliveryResult(null, DeliveryStatus.REJECTED);
        } catch (IOException ex) {
            return new DeliveryResult(null, DeliveryStatus.RETRYABLE_FAILURE);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new DeliveryResult(null, DeliveryStatus.RETRYABLE_FAILURE);
        } catch (RuntimeException ex) {
            return new DeliveryResult(null, DeliveryStatus.RETRYABLE_FAILURE);
        }
    }
}
