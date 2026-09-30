package vn.edu.phenikaa.ams.calendar.google;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

final class GoogleCalendarConnectionStore {
    enum Status { CONNECTED, SETUP_REQUIRED, RECONNECTION_REQUIRED, DISCONNECTED }

    record Connection(UUID id, UUID userId, Status status, byte[] encryptedTokens, int keyVersion,
                      String calendarId, Instant tokenExpiresAt, Instant connectedAt,
                      Instant lastSuccessfulAccessAt, long version) {
        @Override public String toString() { return "GoogleCalendarConnection[redacted]"; }
    }

    private final JdbcTemplate jdbc;

    GoogleCalendarConnectionStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    Optional<Connection> find(UUID userId) {
        return jdbc.query("""
                SELECT id, user_id, status, encrypted_tokens, encryption_key_version, calendar_id,
                       token_expires_at, connected_at, last_successful_access_at, version
                FROM google_calendar_connection WHERE user_id = ?
                """, GoogleCalendarConnectionStore::map, userId).stream().findFirst();
    }

    int create(UUID id, UUID userId, byte[] encrypted, int keyVersion, Instant expiry, Instant now) {
        return jdbc.update("""
                INSERT INTO google_calendar_connection
                    (id, user_id, status, encrypted_tokens, encryption_key_version, token_expires_at,
                     created_at, updated_at)
                SELECT ?, u.id, 'SETUP_REQUIRED', ?, ?, ?, ?, ? FROM app_user u
                WHERE u.id = ? AND u.account_status = 'ACTIVE'
                ON CONFLICT (user_id) DO NOTHING
                """, id, encrypted, keyVersion, time(expiry), time(now), time(now), userId);
    }

    int createNeedsReconnect(UUID id, UUID userId, int keyVersion, Instant now) {
        return jdbc.update("""
                INSERT INTO google_calendar_connection
                    (id, user_id, status, encryption_key_version, created_at, updated_at)
                SELECT ?, u.id, 'RECONNECTION_REQUIRED', ?, ?, ? FROM app_user u
                WHERE u.id = ? AND u.account_status = 'ACTIVE'
                ON CONFLICT (user_id) DO NOTHING
                """, id, keyVersion, time(now), time(now), userId);
    }

    int replaceTokens(UUID userId, long version, byte[] encrypted, int keyVersion, Instant expiry, Instant now) {
        return jdbc.update("""
                UPDATE google_calendar_connection c SET status = 'SETUP_REQUIRED', encrypted_tokens = ?,
                    encryption_key_version = ?, token_expires_at = ?, updated_at = ?, version = version + 1
                WHERE c.user_id = ? AND c.version = ?
                  AND EXISTS (SELECT 1 FROM app_user u WHERE u.id = c.user_id AND u.account_status = 'ACTIVE')
                """, encrypted, keyVersion, time(expiry), time(now), userId, version);
    }

    int refresh(UUID userId, long version, byte[] encrypted, Instant expiry, Instant now) {
        return jdbc.update("""
                UPDATE google_calendar_connection SET encrypted_tokens = ?, token_expires_at = ?,
                    updated_at = ?, version = version + 1
                WHERE user_id = ? AND version = ? AND status IN ('CONNECTED', 'SETUP_REQUIRED')
                """, encrypted, time(expiry), time(now), userId, version);
    }

    int ready(UUID userId, long version, String calendarId, Instant now) {
        return jdbc.update("""
                UPDATE google_calendar_connection SET status = 'CONNECTED', calendar_id = ?,
                    connected_at = ?, last_successful_access_at = ?, updated_at = ?, version = version + 1
                WHERE user_id = ? AND version = ? AND status = 'SETUP_REQUIRED'
                """, calendarId, time(now), time(now), time(now), userId, version);
    }

    int requireReconnect(UUID userId, long version, Instant now) {
        return jdbc.update("""
                UPDATE google_calendar_connection SET status = 'RECONNECTION_REQUIRED',
                    encrypted_tokens = NULL, token_expires_at = NULL, updated_at = ?, version = version + 1
                WHERE user_id = ? AND version = ? AND status IN ('CONNECTED', 'SETUP_REQUIRED')
                """, time(now), userId, version);
    }

    int disconnect(UUID userId, long version, Instant now) {
        return jdbc.update("""
                UPDATE google_calendar_connection SET status = 'DISCONNECTED', encrypted_tokens = NULL,
                    token_expires_at = NULL, updated_at = ?, version = version + 1
                WHERE user_id = ? AND version = ? AND status <> 'DISCONNECTED'
                """, time(now), userId, version);
    }

    private static Connection map(ResultSet rs, int row) throws SQLException {
        return new Connection(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class),
                Status.valueOf(rs.getString("status")), rs.getBytes("encrypted_tokens"),
                rs.getInt("encryption_key_version"), rs.getString("calendar_id"),
                instant(rs, "token_expires_at"), instant(rs, "connected_at"),
                instant(rs, "last_successful_access_at"), rs.getLong("version"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        var value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static Timestamp time(Instant instant) { return Timestamp.from(instant); }
}
