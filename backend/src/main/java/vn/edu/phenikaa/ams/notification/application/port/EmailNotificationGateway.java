package vn.edu.phenikaa.ams.notification.application.port;

public interface EmailNotificationGateway {

    DeliveryResult send(NotificationEmail notification);

    record NotificationEmail(String recipient, String subject, String textBody, String idempotencyKey) {}

    record DeliveryResult(String providerMessageId, DeliveryStatus status) {}

    enum DeliveryStatus {
        ACCEPTED,
        REJECTED,
        RETRYABLE_FAILURE
    }
}
