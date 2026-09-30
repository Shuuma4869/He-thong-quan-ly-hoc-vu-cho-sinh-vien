package vn.edu.phenikaa.ams.sync.application;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.edu.phenikaa.ams.academic.application.port.AcademicPortalClient;
import vn.edu.phenikaa.ams.sync.application.port.SyncJobDispatcher;
import vn.edu.phenikaa.ams.sync.domain.SyncRun;
import vn.edu.phenikaa.ams.sync.infrastructure.SyncRunStore;
import vn.edu.phenikaa.ams.user.domain.AppUser;
import vn.edu.phenikaa.ams.user.infrastructure.UserRepository;
import static vn.edu.phenikaa.ams.sync.application.SyncCommandException.Code.*;

public class SyncService implements SyncJobDispatcher {
    private final SyncRunStore runs;
    private final UserRepository users;
    private final AcademicPortalClient portal;
    private final StringRedisTemplate redis;
    private final Duration manualCooldown;
    private final int historyMaxPageSize;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public SyncService(SyncRunStore runs, UserRepository users, AcademicPortalClient portal,
                       StringRedisTemplate redis, Duration manualCooldown, int historyMaxPageSize,
                       PlatformTransactionManager manager, Clock clock) {
        if (manualCooldown.compareTo(Duration.ofSeconds(5)) < 0 || manualCooldown.compareTo(Duration.ofHours(1)) > 0)
            throw new IllegalArgumentException("Manual sync cooldown must be 5 seconds to 1 hour");
        if (historyMaxPageSize < 1 || historyMaxPageSize > 100)
            throw new IllegalArgumentException("History page limit must be 1 to 100");
        this.runs = runs; this.users = users; this.portal = portal; this.redis = redis;
        this.manualCooldown = manualCooldown; this.historyMaxPageSize = historyMaxPageSize;
        this.transactions = new TransactionTemplate(manager); this.clock = clock;
    }

    @Override public DispatchResult dispatchAcademicSync(UUID userId, Trigger trigger) {
        return transactions.execute(status -> {
            var user = users.lockById(userId).orElseThrow(() -> new SyncCommandException(ACCOUNT_UNAVAILABLE));
            if (user.getAccountStatus() != AppUser.Status.ACTIVE) throw new SyncCommandException(ACCOUNT_UNAVAILABLE);
            var active = runs.active(userId);
            if (active.isPresent()) return new DispatchResult(active.orElseThrow().id(), Status.ALREADY_QUEUED);
            var state = portal.connectionInfo(userId).state();
            if (state == AcademicPortalClient.ConnectionInfo.State.RECONNECTION_REQUIRED)
                throw new SyncCommandException(RECONNECTION_REQUIRED);
            if (state != AcademicPortalClient.ConnectionInfo.State.CONNECTED)
                throw new SyncCommandException(CONNECTION_NOT_FOUND);
            if (trigger == Trigger.MANUAL) {
                boolean allowed;
                try { allowed = Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(
                        "ams:sync:manual:" + userId, "1", manualCooldown)); }
                catch (RuntimeException ex) { throw new SyncCommandException(QUEUE_UNAVAILABLE); }
                if (!allowed) throw new SyncCommandException(RATE_LIMITED);
            }
            var run = runs.enqueue(userId, SyncRun.Trigger.valueOf(trigger.name()), clock.instant());
            return new DispatchResult(run.id(), Status.QUEUED);
        });
    }

    public SyncRun current(UUID userId) {
        requireActive(userId);
        return runs.latest(userId).orElseThrow(() -> new SyncCommandException(RUN_NOT_FOUND));
    }

    public SyncRun owned(UUID userId, UUID runId) {
        requireActive(userId);
        return runs.owned(userId, runId).orElseThrow(() -> new SyncCommandException(RUN_NOT_FOUND));
    }

    public HistoryPage history(UUID userId, int limit, String cursor) {
        requireActive(userId);
        if (limit < 1 || limit > historyMaxPageSize) throw new SyncCommandException(INVALID_HISTORY_LIMIT);
        var anchor = cursor == null ? null : SyncHistoryCursor.decode(cursor);
        if (anchor != null && runs.owned(userId, anchor.runId())
                .filter(run -> run.requestedAt().equals(anchor.requestedAt())).isEmpty())
            throw new SyncCommandException(INVALID_HISTORY_CURSOR);
        var rows = runs.history(userId, anchor == null ? null : anchor.requestedAt(),
                anchor == null ? null : anchor.runId(), limit + 1);
        var items = List.copyOf(rows.subList(0, Math.min(rows.size(), limit)));
        String next = rows.size() > limit ? SyncHistoryCursor.encode(items.getLast()) : null;
        return new HistoryPage(items, next);
    }

    public record HistoryPage(List<SyncRun> items, String nextCursor) {}

    private void requireActive(UUID userId) {
        if (users.findById(userId).filter(user -> user.getAccountStatus() == AppUser.Status.ACTIVE).isEmpty())
            throw new SyncCommandException(ACCOUNT_UNAVAILABLE);
    }
}
