package vn.edu.phenikaa.ams.notification.application;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import vn.edu.phenikaa.ams.notification.application.port.EmailNotificationGateway;
import vn.edu.phenikaa.ams.notification.application.port.EmailNotificationGateway.*;
import vn.edu.phenikaa.ams.notification.infrastructure.NotificationOutboxStore;

public class NotificationOutboxWorker {
    private static final Logger log = LoggerFactory.getLogger(NotificationOutboxWorker.class);
    private final NotificationOutboxStore store;
    private final EmailNotificationGateway gateway;
    private final Clock clock;
    private final MeterRegistry metrics;

    public NotificationOutboxWorker(NotificationOutboxStore store, EmailNotificationGateway gateway,
                                    Clock clock, MeterRegistry metrics) {
        this.store = store; this.gateway = gateway; this.clock = clock; this.metrics = metrics;
    }

    @Scheduled(fixedDelayString = "${ams.notification.email.poll-interval:10s}")
    public void poll() {
        try {
            for (var email : store.claim(clock.instant(), 10, Duration.ofMinutes(2))) {
                try {
                    var now = clock.instant();
                    if (!store.stillEligible(email)) {
                        if (store.skip(email, now)) metrics.counter("notification.email.outbox.total", "status", "SKIPPED", "type", "SYNC_ALERT").increment();
                        continue;
                    }
                    DeliveryResult result;
                    try {
                        result = gateway.send(new NotificationEmail(email.recipient(), email.subject(),
                                email.textBody(), "sync-alert-" + email.id()));
                    } catch (RuntimeException ex) {
                        result = new DeliveryResult(null, DeliveryStatus.RETRYABLE_FAILURE);
                    }
                    if (store.finish(email, result, clock.instant())) {
                        metrics.counter("notification.email.delivery.total", "result", result.status().name()).increment();
                        String finalStatus = result.status() == DeliveryStatus.ACCEPTED ? "SENT"
                                : result.status() == DeliveryStatus.REJECTED || email.attempts() >= 5 ? "DEAD" : "PENDING";
                        metrics.counter("notification.email.outbox.total", "status", finalStatus, "type", "SYNC_ALERT").increment();
                        log.info("Notification outbox {} delivery result {}", email.id(), result.status());
                    }
                } catch (RuntimeException ex) { log.warn("Notification outbox {} processing unavailable", email.id()); }
            }
        } catch (RuntimeException ex) { log.warn("Notification outbox polling unavailable"); }
    }

    @Scheduled(fixedDelayString = "${ams.notification.email.cleanup-interval:1h}")
    public void clean() {
        try {
            int removed = store.deleteExpired(clock.instant().minus(Duration.ofDays(90)), 100);
            if (removed > 0) log.info("Notification outbox cleanup deleted {} rows", removed);
        } catch (RuntimeException ex) { log.warn("Notification outbox cleanup unavailable"); }
    }
}
