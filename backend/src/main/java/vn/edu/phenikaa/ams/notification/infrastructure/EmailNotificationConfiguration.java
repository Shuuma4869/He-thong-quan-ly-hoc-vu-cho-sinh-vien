package vn.edu.phenikaa.ams.notification.infrastructure;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import vn.edu.phenikaa.ams.notification.application.NotificationOutboxWorker;
import vn.edu.phenikaa.ams.notification.application.port.EmailNotificationGateway;

@Configuration(proxyBeanMethods = false)
public class EmailNotificationConfiguration {
    @Bean NotificationOutboxStore notificationOutboxStore(JdbcTemplate jdbc) {
        return new NotificationOutboxStore(jdbc);
    }

    @Bean
    @ConditionalOnProperty(name = {"ams.notification.email.enabled", "ams.notification.email.worker-enabled"},
            havingValue = "true")
    NotificationOutboxWorker notificationOutboxWorker(NotificationOutboxStore store, EmailNotificationGateway gateway,
                                                      Clock clock, MeterRegistry metrics) {
        return new NotificationOutboxWorker(store, gateway, clock, metrics);
    }
    @Bean
    @ConditionalOnProperty(name = "ams.notification.email.enabled", havingValue = "true")
    EmailNotificationGateway resendEmailGateway(
            @Value("${ams.notification.email.api-key:}") String apiKey,
            @Value("${ams.notification.email.from:}") String from,
            @Value("${ams.notification.email.endpoint:https://api.resend.com/emails}") URI endpoint) {
        return new ResendEmailGateway(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
                endpoint, apiKey, from);
    }
}
