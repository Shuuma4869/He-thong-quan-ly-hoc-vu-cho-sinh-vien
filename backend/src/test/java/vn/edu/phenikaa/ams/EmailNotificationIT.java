package vn.edu.phenikaa.ams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.phenikaa.ams.auth.application.AccountPrincipal;
import vn.edu.phenikaa.ams.notification.application.EmailVerificationService;
import vn.edu.phenikaa.ams.notification.application.NotificationOutboxWorker;
import vn.edu.phenikaa.ams.notification.application.port.EmailNotificationGateway;
import vn.edu.phenikaa.ams.notification.application.port.EmailNotificationGateway.*;
import vn.edu.phenikaa.ams.notification.infrastructure.NotificationOutboxStore;
import vn.edu.phenikaa.ams.sync.application.SyncRunFinalizer;
import vn.edu.phenikaa.ams.sync.domain.SyncRun;
import vn.edu.phenikaa.ams.sync.infrastructure.SyncRunStore;
import vn.edu.phenikaa.ams.user.api.AccountDtos.SettingsRequest;
import vn.edu.phenikaa.ams.user.application.AccountService;
import vn.edu.phenikaa.ams.user.domain.AppUser;
import vn.edu.phenikaa.ams.user.domain.UserPreferences;
import vn.edu.phenikaa.ams.user.infrastructure.UserPreferencesRepository;
import vn.edu.phenikaa.ams.user.infrastructure.UserRepository;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {"spring.data.redis.password=", "ams.notification.email.enabled=true",
        "ams.notification.email.worker-enabled=false", "ams.notification.email.api-key=synthetic-test-key",
        "ams.notification.email.from=notify@example.test"})
@AutoConfigureMockMvc
class EmailNotificationIT {
    @Autowired UserRepository users;
    @Autowired UserPreferencesRepository preferences;
    @Autowired AccountService accounts;
    @Autowired EmailVerificationService verification;
    @Autowired NotificationOutboxStore outbox;
    @Autowired SyncRunFinalizer finalizer;
    @Autowired SyncRunStore runs;
    @Autowired JdbcTemplate jdbc;
    @Autowired MeterRegistry metrics;
    @Autowired MockMvc mvc;
    @MockitoBean EmailNotificationGateway gateway;

    private AppUser account() {
        var user = users.save(new AppUser(UUID.randomUUID() + "@example.test", "!synthetic", "Test"));
        preferences.save(new UserPreferences(user.getId()));
        return user;
    }

    private SettingsRequest settings(String email, boolean alerts) {
        return new SettingsRequest(email, "Asia/Ho_Chi_Minh", "vi-VN", UserPreferences.Theme.SYSTEM, alerts);
    }

    private String sentCode() {
        var capture = ArgumentCaptor.forClass(NotificationEmail.class);
        verify(gateway, atLeastOnce()).send(capture.capture());
        String body = capture.getValue().textBody();
        return body.substring(body.indexOf(": ") + 2, body.indexOf('\n'));
    }

    @Test void verificationIsOwnedHasCsrfAndNeverPersistsRawCode() throws Exception {
        var owner = account(); var other = account();
        assertThat(verification.status(owner.getId()).syncAlertsEnabled()).isFalse();
        assertThat(verification.status(owner.getId()).notificationEmail()).isNull();
        assertThatThrownBy(() -> verification.request(owner.getId())).isInstanceOf(RuntimeException.class);
        mvc.perform(get("/api/me/notifications/email")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/me/notifications/email/verification").with(user(new AccountPrincipal(owner))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/me/notifications/email/verification/confirm")
                .with(user(new AccountPrincipal(owner))).contentType("application/json")
                .content("{\"code\":\"23456789AB\"}"))
                .andExpect(status().isForbidden());
        accounts.updateSettings(owner.getId(), settings("notify@example.test", false));
        when(gateway.send(any())).thenReturn(new DeliveryResult("synthetic-id", DeliveryStatus.ACCEPTED));
        mvc.perform(post("/api/me/notifications/email/verification")
                .with(user(new AccountPrincipal(owner))).with(csrf())).andExpect(status().isAccepted());
        String code = sentCode();
        assertThat(code).matches("[2-9A-HJ-NP-Z]{10}");
        assertThat(jdbc.queryForObject("select code_hash from notification_email_verification where user_id = ?",
                String.class, owner.getId())).isNotEqualTo(code);
        assertThatThrownBy(() -> verification.confirm(other.getId(), code)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> verification.request(owner.getId())).isInstanceOf(RuntimeException.class);
        mvc.perform(post("/api/me/notifications/email/verification/confirm")
                .with(user(new AccountPrincipal(owner))).with(csrf())
                .contentType("application/json").content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.verified").value(true));
        assertThat(jdbc.queryForObject("select count(*) from notification_email_verification where user_id = ?",
                Integer.class, owner.getId())).isZero();
        assertThatThrownBy(() -> verification.confirm(owner.getId(), code)).isInstanceOf(RuntimeException.class);
        assertThat(accounts.updateSettings(owner.getId(), settings(" NOTIFY@EXAMPLE.TEST ", true))
                .syncEmailAlertsEnabled()).isTrue();
        var timezoneOnly = new SettingsRequest("notify@example.test", "Asia/Tokyo", "vi-VN",
                UserPreferences.Theme.SYSTEM, null);
        assertThat(accounts.updateSettings(owner.getId(), timezoneOnly).notificationEmailVerifiedAt()).isNotNull();
        assertThat(accounts.currentUser(owner.getId()).settings().syncEmailAlertsEnabled()).isTrue();
        accounts.updateSettings(owner.getId(), new SettingsRequest("new@example.test", "Asia/Tokyo", "vi-VN",
                UserPreferences.Theme.SYSTEM, null));
        assertThat(verification.status(owner.getId()).verified()).isFalse();
        assertThat(verification.status(owner.getId()).syncAlertsEnabled()).isFalse();
        assertThatThrownBy(() -> accounts.updateSettings(owner.getId(), settings("new@example.test", true)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test void wrongCodesAreBoundedAndEmailChangeInvalidatesOldCode() {
        var owner = account();
        accounts.updateSettings(owner.getId(), settings("notify@example.test", false));
        when(gateway.send(any())).thenReturn(new DeliveryResult("synthetic-id", DeliveryStatus.ACCEPTED));
        verification.request(owner.getId());
        String code = sentCode();
        for (int i = 0; i < 5; i++)
            assertThatThrownBy(() -> verification.confirm(owner.getId(), "2222222222")).isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("select attempt_count from notification_email_verification where user_id = ?",
                Integer.class, owner.getId())).isEqualTo(5);
        assertThatThrownBy(() -> verification.confirm(owner.getId(), code)).isInstanceOf(RuntimeException.class);
        jdbc.update("update notification_email_verification set sent_at = now() - interval '2 minute' where user_id = ?", owner.getId());
        verification.request(owner.getId());
        String next = sentCode();
        jdbc.update("update notification_email_verification set expires_at = now() - interval '1 second' where user_id = ?", owner.getId());
        assertThatThrownBy(() -> verification.confirm(owner.getId(), next)).isInstanceOf(RuntimeException.class);
        accounts.updateSettings(owner.getId(), settings("new@example.test", false));
        assertThatThrownBy(() -> verification.confirm(owner.getId(), next)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> verification.confirm(owner.getId(), code)).isInstanceOf(RuntimeException.class);
    }

    @Test void providerRejectionIsSafeAndStillStartsResendCooldown() throws Exception {
        var owner = account();
        accounts.updateSettings(owner.getId(), settings("notify@example.test", false));
        when(gateway.send(any())).thenReturn(new DeliveryResult(null, DeliveryStatus.REJECTED));
        mvc.perform(post("/api/me/notifications/email/verification")
                .with(user(new AccountPrincipal(owner))).with(csrf()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("EMAIL_DELIVERY_UNAVAILABLE"));
        assertThat(jdbc.queryForObject("select attempt_count from notification_email_verification where user_id = ?",
                Integer.class, owner.getId())).isEqualTo(5);
        mvc.perform(post("/api/me/notifications/email/verification")
                .with(user(new AccountPrincipal(owner))).with(csrf()))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("VERIFICATION_COOLDOWN"));
        verify(gateway, times(1)).send(any());
    }

    @Test void terminalAlertsAreDeduplicatedAndProviderFailureDoesNotChangeSyncOutcome() {
        var owner = account();
        accounts.updateSettings(owner.getId(), settings("notify@example.test", false));
        UUID none = running(owner, "PENDING", "PENDING");
        assertThat(finalizer.finish(none, 1, SyncRun.Status.FAILED, SyncRun.FailureCode.SOURCE_TIMEOUT, Instant.now())).isTrue();
        assertThat(outboxCount(none)).isZero();
        jdbc.update("update user_preferences set notification_email_verified_at = now(), sync_email_alerts_enabled = true where user_id = ?", owner.getId());
        UUID success = running(owner, "SUCCEEDED", "SUCCEEDED");
        assertThat(finalizer.finish(success, 1, SyncRun.Status.SUCCEEDED, null, Instant.now())).isTrue();
        assertThat(outboxCount(success)).isZero();
        UUID failure = running(owner, "PENDING", "FAILED");
        assertThat(finalizer.finish(failure, 1, SyncRun.Status.FAILED, SyncRun.FailureCode.SOURCE_TIMEOUT, Instant.now())).isTrue();
        assertThat(finalizer.finish(failure, 1, SyncRun.Status.FAILED, SyncRun.FailureCode.SOURCE_TIMEOUT, Instant.now())).isFalse();
        assertThat(outboxCount(failure)).isEqualTo(1);
        UUID partial = running(owner, "SUCCEEDED", "FAILED");
        assertThat(finalizer.finish(partial, 1, SyncRun.Status.PARTIAL, SyncRun.FailureCode.SOURCE_UNAVAILABLE, Instant.now())).isTrue();
        assertThat(outboxCount(partial)).isEqualTo(1);
        when(gateway.send(any())).thenReturn(new DeliveryResult(null, DeliveryStatus.RETRYABLE_FAILURE));
        new NotificationOutboxWorker(outbox, gateway, Clock.systemUTC(), metrics).poll();
        assertThat(jdbc.queryForObject("select status from sync_run where id = ?", String.class, failure)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("select status from notification_outbox where sync_run_id = ?", String.class, failure))
                .isEqualTo("PENDING");
    }

    @Test void concurrentClaimsNeverReturnTheSameRow() throws Exception {
        var owner = account();
        jdbc.update("update user_preferences set notification_email = 'notify@example.test', notification_email_verified_at = now(), sync_email_alerts_enabled = true where user_id = ?", owner.getId());
        UUID run = running(owner, "PENDING", "FAILED");
        finalizer.finish(run, 1, SyncRun.Status.FAILED, SyncRun.FailureCode.INTERNAL_ERROR, Instant.now());
        var gate = new CountDownLatch(1);
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var a = pool.submit(() -> { gate.await(); return outbox.claim(Instant.now(), 1, Duration.ofMinutes(2)); });
            var b = pool.submit(() -> { gate.await(); return outbox.claim(Instant.now(), 1, Duration.ofMinutes(2)); });
            gate.countDown();
            var ids = new HashSet<UUID>();
            a.get(10, TimeUnit.SECONDS).forEach(item -> assertThat(ids.add(item.id())).isTrue());
            b.get(10, TimeUnit.SECONDS).forEach(item -> assertThat(ids.add(item.id())).isTrue());
            assertThat(ids).contains(jdbc.queryForObject("select id from notification_outbox where sync_run_id = ?", UUID.class, run));
        }
    }

    @Test void retriesUseOneProviderKeyThenAcceptOrDie() {
        var owner = account();
        jdbc.update("update user_preferences set notification_email = 'notify@example.test', notification_email_verified_at = now(), sync_email_alerts_enabled = true where user_id = ?", owner.getId());
        UUID run = running(owner, "PENDING", "FAILED");
        finalizer.finish(run, 1, SyncRun.Status.FAILED, SyncRun.FailureCode.SOURCE_TIMEOUT, Instant.now());
        UUID id = jdbc.queryForObject("select id from notification_outbox where sync_run_id = ?", UUID.class, run);
        var attempts = new AtomicInteger();
        when(gateway.send(any())).thenAnswer(invocation -> {
            NotificationEmail message = invocation.getArgument(0);
            if (!message.idempotencyKey().equals("sync-alert-" + id))
                return new DeliveryResult("synthetic-other-id", DeliveryStatus.ACCEPTED);
            return attempts.incrementAndGet() == 1
                    ? new DeliveryResult(null, DeliveryStatus.RETRYABLE_FAILURE)
                    : new DeliveryResult("synthetic-message-id", DeliveryStatus.ACCEPTED);
        });
        var worker = new NotificationOutboxWorker(outbox, gateway, Clock.systemUTC(), metrics);
        worker.poll();
        assertThat(jdbc.queryForObject("select status from notification_outbox where id = ?", String.class, id)).isEqualTo("PENDING");
        jdbc.update("update notification_outbox set next_attempt_at = now() - interval '1 second' where id = ?", id);
        worker.poll();
        assertThat(jdbc.queryForObject("select status from notification_outbox where id = ?", String.class, id)).isEqualTo("SENT");
        var sent = ArgumentCaptor.forClass(NotificationEmail.class);
        verify(gateway, atLeast(2)).send(sent.capture());
        assertThat(sent.getAllValues().stream().filter(item -> item.idempotencyKey().equals("sync-alert-" + id)).count())
                .isEqualTo(2);

        UUID rejected = running(owner, "PENDING", "FAILED");
        finalizer.finish(rejected, 1, SyncRun.Status.FAILED, SyncRun.FailureCode.INTERNAL_ERROR, Instant.now());
        reset(gateway);
        when(gateway.send(any())).thenReturn(new DeliveryResult(null, DeliveryStatus.REJECTED));
        worker.poll();
        assertThat(jdbc.queryForObject("select status from notification_outbox where sync_run_id = ?", String.class, rejected))
                .isEqualTo("DEAD");

        UUID exhausted = running(owner, "PENDING", "FAILED");
        finalizer.finish(exhausted, 1, SyncRun.Status.FAILED, SyncRun.FailureCode.INTERNAL_ERROR, Instant.now());
        jdbc.update("update notification_outbox set attempt_count = 4 where sync_run_id = ?", exhausted);
        when(gateway.send(any())).thenReturn(new DeliveryResult(null, DeliveryStatus.RETRYABLE_FAILURE));
        worker.poll();
        assertThat(jdbc.queryForObject("select status from notification_outbox where sync_run_id = ?", String.class, exhausted))
                .isEqualTo("DEAD");
    }

    @Test void staleTerminalRecoveryQueuesOneAlertButRetryDoesNot() {
        var owner = account();
        jdbc.update("update user_preferences set notification_email = 'notify@example.test', notification_email_verified_at = now(), sync_email_alerts_enabled = true where user_id = ?", owner.getId());
        UUID runId = running(owner, "SUCCEEDED", "PENDING");
        Instant now = Instant.now();
        jdbc.update("update sync_run set heartbeat_at = ?, attempt_count = 3 where id = ?",
                Timestamp.from(now.minus(Duration.ofMinutes(5))), runId);
        var stale = runs.owned(owner.getId(), runId).orElseThrow();
        assertThat(finalizer.recover(stale, now.minus(Duration.ofMinutes(3)), now.plusSeconds(10), now, 3)).isTrue();
        assertThat(runs.owned(owner.getId(), runId).orElseThrow().status()).isEqualTo(SyncRun.Status.PARTIAL);
        assertThat(outboxCount(runId)).isEqualTo(1);
        assertThat(finalizer.recover(stale, now.minus(Duration.ofMinutes(3)), now.plusSeconds(10), now, 3)).isFalse();
        assertThat(outboxCount(runId)).isEqualTo(1);
    }

    private UUID running(AppUser user, String profile, String curriculum) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("""
                insert into sync_run(id,user_id,trigger_type,status,requested_at,started_at,heartbeat_at,
                    next_attempt_at,profile_step_status,curriculum_step_status,attempt_count,updated_at)
                values (?,?,'MANUAL','RUNNING',?,?,?,?,?,?,1,?)
                """, id, user.getId(), Timestamp.from(now), Timestamp.from(now), Timestamp.from(now),
                Timestamp.from(now), profile, curriculum, Timestamp.from(now));
        return id;
    }

    private int outboxCount(UUID run) {
        return jdbc.queryForObject("select count(*) from notification_outbox where sync_run_id = ?", Integer.class, run);
    }
}
