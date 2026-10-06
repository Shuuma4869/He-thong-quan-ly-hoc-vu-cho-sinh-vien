package vn.edu.phenikaa.ams.notification.application;

import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.phenikaa.ams.notification.application.port.EmailNotificationGateway;
import vn.edu.phenikaa.ams.notification.application.port.EmailNotificationGateway.*;
import vn.edu.phenikaa.ams.user.domain.AppUser;
import vn.edu.phenikaa.ams.user.infrastructure.UserPreferencesRepository;
import vn.edu.phenikaa.ams.user.infrastructure.UserRepository;

@Service
public class EmailVerificationService {
    private static final String ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final Duration TTL = Duration.ofMinutes(15);
    private static final Duration COOLDOWN = Duration.ofSeconds(60);
    private final UserRepository users;
    private final UserPreferencesRepository preferences;
    private final JdbcTemplate jdbc;
    private final ObjectProvider<EmailNotificationGateway> gateways;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final MeterRegistry metrics;
    private final SecureRandom random = new SecureRandom();
    private final boolean enabled;

    public EmailVerificationService(UserRepository users, UserPreferencesRepository preferences, JdbcTemplate jdbc,
                                    ObjectProvider<EmailNotificationGateway> gateways,
                                    PlatformTransactionManager manager, Clock clock, MeterRegistry metrics,
                                    @Value("${ams.notification.email.enabled:false}") boolean enabled) {
        this.users = users; this.preferences = preferences; this.jdbc = jdbc; this.gateways = gateways;
        this.transactions = new TransactionTemplate(manager); this.clock = clock; this.metrics = metrics;
        this.enabled = enabled;
    }

    public EmailStatus status(UUID userId) {
        active(userId);
        var prefs = preferences.findById(userId).orElseThrow();
        return new EmailStatus(enabled && gateways.getIfAvailable() != null,
                gateways.getIfAvailable() != null, prefs.getNotificationEmail(),
                prefs.getNotificationEmailVerifiedAt() != null, prefs.getNotificationEmailVerifiedAt(),
                prefs.isSyncEmailAlertsEnabled());
    }

    public void request(UUID userId) {
        active(userId);
        var gateway = requiredGateway();
        NotificationException deliveryError = transactions.execute(tx -> {
            active(userId);
            var prefs = preferences.findLocked(userId).orElseThrow();
            String email = prefs.getNotificationEmail();
            if (email == null) throw error(HttpStatus.CONFLICT, "NOTIFICATION_EMAIL_MISSING", "Hãy lưu email nhận thông báo trước.");
            if (prefs.getNotificationEmailVerifiedAt() != null)
                throw error(HttpStatus.CONFLICT, "EMAIL_ALREADY_VERIFIED", "Email này đã được xác minh.");
            Instant now = clock.instant();
            var last = jdbc.query("select sent_at from notification_email_verification where user_id = ?",
                    (rs, row) -> rs.getTimestamp(1).toInstant(), userId);
            if (!last.isEmpty() && last.getFirst().plus(COOLDOWN).isAfter(now))
                throw error(HttpStatus.TOO_MANY_REQUESTS, "VERIFICATION_COOLDOWN", "Hãy đợi một phút trước khi gửi mã mới.");
            String code = newCode();
            String emailHash = digest(email);
            String codeHash = digest(userId + ":" + emailHash + ":" + code);
            String body = "Mã xác minh email nhận thông báo AMS: " + code
                    + "\nMã có hiệu lực 15 phút. Nếu bạn không yêu cầu, hãy bỏ qua email này.";
            jdbc.update("""
                    insert into notification_email_verification
                        (user_id, code_hash, email_hash, expires_at, attempt_count, created_at, sent_at)
                    values (?, ?, ?, ?, 0, ?, ?)
                    on conflict (user_id) do update set code_hash = excluded.code_hash, email_hash = excluded.email_hash,
                        expires_at = excluded.expires_at, attempt_count = 0, created_at = excluded.created_at,
                        sent_at = excluded.sent_at
                    """, userId, codeHash, emailHash, Timestamp.from(now.plus(TTL)), Timestamp.from(now), Timestamp.from(now));
            DeliveryResult result;
            try {
                result = gateway.send(new NotificationEmail(email, "AMS — Xác minh email nhận thông báo",
                        body, "verify-" + userId + "-" + codeHash.substring(0, 20)));
            } catch (RuntimeException ex) { result = new DeliveryResult(null, DeliveryStatus.RETRYABLE_FAILURE); }
            if (result.status() != DeliveryStatus.ACCEPTED) {
                jdbc.update("""
                        update notification_email_verification set code_hash = repeat('0', 64),
                            expires_at = ?, attempt_count = 5 where user_id = ?
                        """, Timestamp.from(now), userId);
                metrics.counter("notification.email.verification.total", "result", "DELIVERY_FAILED").increment();
                return error(HttpStatus.SERVICE_UNAVAILABLE, "EMAIL_DELIVERY_UNAVAILABLE",
                        "Chưa thể gửi mã xác minh. Vui lòng thử lại sau.");
            }
            metrics.counter("notification.email.verification.total", "result", "SENT").increment();
            return null;
        });
        if (deliveryError != null) throw deliveryError;
    }

    public EmailStatus confirm(UUID userId, String code) {
        active(userId);
        if (code == null || !code.matches("[2-9A-HJ-NP-Z]{10}"))
            throw error(HttpStatus.BAD_REQUEST, "INVALID_VERIFICATION_CODE", "Mã xác minh không hợp lệ hoặc đã hết hạn.");
        ConfirmResult result = transactions.execute(tx -> {
            active(userId);
            var prefs = preferences.findLocked(userId).orElseThrow();
            var rows = jdbc.query("""
                    select code_hash, email_hash, expires_at, attempt_count
                    from notification_email_verification where user_id = ? for update
                    """, (rs, row) -> new Verification(rs.getString(1), rs.getString(2),
                    rs.getTimestamp(3).toInstant(), rs.getInt(4)), userId);
            if (rows.isEmpty()) return ConfirmResult.INVALID;
            var stored = rows.getFirst();
            Instant now = clock.instant();
            if (stored.attempts() >= 5 || !stored.expiresAt().isAfter(now)
                    || prefs.getNotificationEmail() == null
                    || !stored.emailHash().equals(digest(prefs.getNotificationEmail()))) {
                return ConfirmResult.INVALID;
            }
            String candidate = digest(userId + ":" + stored.emailHash() + ":" + code);
            if (!MessageDigest.isEqual(candidate.getBytes(StandardCharsets.US_ASCII),
                    stored.codeHash().getBytes(StandardCharsets.US_ASCII))) {
                jdbc.update("update notification_email_verification set attempt_count = attempt_count + 1 where user_id = ?", userId);
                return ConfirmResult.INVALID;
            }
            prefs.verify(now);
            jdbc.update("delete from notification_email_verification where user_id = ?", userId);
            return ConfirmResult.VERIFIED;
        });
        metrics.counter("notification.email.verification.total", "result", result.name()).increment();
        if (result != ConfirmResult.VERIFIED)
            throw error(HttpStatus.BAD_REQUEST, "INVALID_VERIFICATION_CODE", "Mã xác minh không hợp lệ hoặc đã hết hạn.");
        return status(userId);
    }

    private EmailNotificationGateway requiredGateway() {
        var gateway = enabled ? gateways.getIfAvailable() : null;
        if (gateway == null) throw error(HttpStatus.SERVICE_UNAVAILABLE, "EMAIL_NOT_CONFIGURED", "Thông báo email chưa khả dụng.");
        return gateway;
    }

    private void active(UUID userId) {
        if (users.findById(userId).filter(user -> user.getAccountStatus() == AppUser.Status.ACTIVE).isEmpty())
            throw new AccessDeniedException("Account unavailable");
    }

    private String newCode() {
        var chars = new char[10];
        for (int i = 0; i < chars.length; i++) chars[i] = ALPHABET.charAt(random.nextInt(ALPHABET.length()));
        return new String(chars);
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }

    private static NotificationException error(HttpStatus status, String code, String message) {
        return new NotificationException(status, code, message);
    }

    private record Verification(String codeHash, String emailHash, Instant expiresAt, int attempts) {}
    private enum ConfirmResult { VERIFIED, INVALID }
    public record EmailStatus(boolean featureEnabled, boolean configured, String notificationEmail,
                              boolean verified, Instant verifiedAt, boolean syncAlertsEnabled) {}
}
