package vn.edu.phenikaa.ams.notification.infrastructure;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.edu.phenikaa.ams.notification.application.port.EmailNotificationGateway.DeliveryResult;
import vn.edu.phenikaa.ams.notification.application.port.EmailNotificationGateway.DeliveryStatus;

public class NotificationOutboxStore {
    private final JdbcTemplate jdbc;
    public NotificationOutboxStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public boolean enqueueIfEligible(UUID runId, Instant now) {
        var rows = jdbc.query("""
                select r.user_id, r.status, r.failure_code, r.finished_at, p.notification_email
                from sync_run r join user_preferences p on p.user_id = r.user_id
                join app_user u on u.id = r.user_id
                where r.id = ? and r.status in ('PARTIAL','FAILED') and u.account_status = 'ACTIVE'
                    and p.notification_email is not null and p.notification_email_verified_at is not null
                    and p.sync_email_alerts_enabled = true
                """, (rs, index) -> new AlertSource(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                rs.getTimestamp(4).toInstant(), rs.getString(5)), runId);
        if (rows.isEmpty()) return false;
        var source = rows.getFirst();
        String reason = switch (source.failureCode() == null ? "" : source.failureCode()) {
            case "RECONNECTION_REQUIRED" -> "Cần kết nối lại nguồn học vụ.";
            case "CONNECTION_NOT_FOUND" -> "Kết nối học vụ không còn sẵn sàng.";
            case "SOURCE_TIMEOUT", "SOURCE_UNAVAILABLE" -> "Nguồn học vụ tạm thời không phản hồi.";
            case "SOURCE_SCHEMA_CHANGED" -> "AMS chưa thể đọc an toàn dữ liệu nguồn hiện tại.";
            default -> "AMS chưa thể hoàn tất lượt đồng bộ. Hãy xem trạng thái trong ứng dụng.";
        };
        String subject = source.status().equals("PARTIAL")
                ? "AMS — Đồng bộ hoàn tất một phần" : "AMS — Đồng bộ cần bạn kiểm tra";
        String body = "Lượt đồng bộ " + (source.status().equals("PARTIAL") ? "hoàn tất một phần" : "thất bại")
                + " lúc " + source.finishedAt() + " (UTC).\n" + reason + "\nMở AMS → Đồng bộ để xem chi tiết.";
        return jdbc.update("""
                insert into notification_outbox(id,user_id,sync_run_id,notification_type,recipient,subject,text_body,
                    status,next_attempt_at,created_at,updated_at)
                values (?,?,?,'SYNC_ALERT',?,?,?,'PENDING',?,?,?) on conflict do nothing
                """, UUID.randomUUID(), source.userId(), runId, source.recipient(), subject, body,
                time(now), time(now), time(now)) == 1;
    }

    public List<OutboxEmail> claim(Instant now, int limit, Duration lease) {
        jdbc.update("""
                update notification_outbox set status = 'DEAD', last_error_code = 'RETRY_EXHAUSTED',
                    updated_at = ?, lease_token = null, lease_expires_at = null
                where status = 'PROCESSING' and lease_expires_at <= ? and attempt_count >= 5
                """, time(now), time(now));
        UUID token = UUID.randomUUID();
        return jdbc.query("""
                update notification_outbox set status = 'PROCESSING', lease_token = ?, lease_expires_at = ?,
                    attempt_count = attempt_count + 1, updated_at = ?
                where id in (
                    select id from notification_outbox
                    where attempt_count < 5 and ((status = 'PENDING' and next_attempt_at <= ?)
                        or (status = 'PROCESSING' and lease_expires_at <= ?))
                    order by next_attempt_at, id limit ? for update skip locked
                ) returning id,user_id,recipient,subject,text_body,attempt_count,lease_token
                """, (rs, row) -> new OutboxEmail(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                rs.getString(3), rs.getString(4), rs.getString(5), rs.getInt(6), rs.getObject(7, UUID.class)),
                token, time(now.plus(lease)), time(now), time(now), time(now), limit);
    }

    public boolean stillEligible(OutboxEmail email) {
        Boolean eligible = jdbc.queryForObject("""
                select exists (select 1 from user_preferences p join app_user u on u.id = p.user_id
                    where p.user_id = ? and u.account_status = 'ACTIVE' and p.sync_email_alerts_enabled
                        and p.notification_email_verified_at is not null and p.notification_email = ?)
                """, Boolean.class, email.userId(), email.recipient());
        return Boolean.TRUE.equals(eligible);
    }

    public boolean finish(OutboxEmail email, DeliveryResult result, Instant now) {
        String status;
        Instant next = now;
        String error = null;
        if (result.status() == DeliveryStatus.ACCEPTED) status = "SENT";
        else if (result.status() == DeliveryStatus.REJECTED) { status = "DEAD"; error = "PROVIDER_REJECTED"; }
        else if (email.attempts() >= 5) { status = "DEAD"; error = "RETRY_EXHAUSTED"; }
        else {
            status = "PENDING"; error = "PROVIDER_RETRYABLE";
            long seconds = switch (email.attempts()) { case 1 -> 60; case 2 -> 300; case 3 -> 900; default -> 3600; };
            next = now.plusSeconds(seconds);
        }
        return jdbc.update("""
                update notification_outbox set status = ?, next_attempt_at = ?, updated_at = ?,
                    sent_at = ?, provider_message_id = ?, last_error_code = ?, lease_token = null, lease_expires_at = null
                where id = ? and status = 'PROCESSING' and lease_token = ?
                """, status, time(next), time(now), status.equals("SENT") ? time(now) : null,
                status.equals("SENT") ? result.providerMessageId() : null, error, email.id(), email.leaseToken()) == 1;
    }

    public boolean skip(OutboxEmail email, Instant now) {
        return jdbc.update("""
                update notification_outbox set status = 'SKIPPED', updated_at = ?, lease_token = null,
                    lease_expires_at = null where id = ? and status = 'PROCESSING' and lease_token = ?
                """, time(now), email.id(), email.leaseToken()) == 1;
    }

    public int deleteExpired(Instant cutoff, int limit) {
        return jdbc.update("""
                delete from notification_outbox where id in (
                    select id from notification_outbox where status in ('SENT','DEAD','SKIPPED')
                        and updated_at < ? order by updated_at,id limit ? for update skip locked)
                """, time(cutoff), limit);
    }

    private static Timestamp time(Instant value) { return Timestamp.from(value); }
    private record AlertSource(UUID userId, String status, String failureCode, Instant finishedAt, String recipient) {}
    public record OutboxEmail(UUID id, UUID userId, String recipient, String subject, String textBody,
                              int attempts, UUID leaseToken) {}
}
