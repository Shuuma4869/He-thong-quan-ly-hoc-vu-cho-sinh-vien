package vn.edu.phenikaa.ams.sync.application;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.UUID;
import vn.edu.phenikaa.ams.sync.domain.SyncRun;
import static vn.edu.phenikaa.ams.sync.application.SyncCommandException.Code.INVALID_HISTORY_CURSOR;

public record SyncHistoryCursor(Instant requestedAt, UUID runId) {
    public static String encode(SyncRun run) {
        var value = run.requestedAt() + "|" + run.id();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    public static SyncHistoryCursor decode(String cursor) {
        if (cursor == null || cursor.isEmpty() || cursor.length() > 128
                || !cursor.matches("[A-Za-z0-9_-]+")) throw new SyncCommandException(INVALID_HISTORY_CURSOR);
        try {
            var value = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            var parts = value.split("\\|", -1);
            if (parts.length != 2) throw new SyncCommandException(INVALID_HISTORY_CURSOR);
            return new SyncHistoryCursor(Instant.parse(parts[0]), UUID.fromString(parts[1]));
        } catch (IllegalArgumentException | DateTimeParseException ex) {
            throw new SyncCommandException(INVALID_HISTORY_CURSOR);
        }
    }
}
