package vn.edu.phenikaa.ams.sync.application;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.edu.phenikaa.ams.sync.domain.SyncRun;
import vn.edu.phenikaa.ams.sync.infrastructure.SyncRunStore;

public class SyncMaintenance {
    private static final Logger log = LoggerFactory.getLogger(SyncMaintenance.class);
    private final SyncRunStore runs;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final MeterRegistry metrics;
    private final boolean autoEnabled;
    private final boolean sourceEnabled;
    private final Duration autoInterval;
    private final Duration failureCooldown;
    private final int autoBatchSize;
    private final Duration retention;
    private final int cleanupBatchSize;

    public SyncMaintenance(SyncRunStore runs, PlatformTransactionManager manager, Clock clock,
                           MeterRegistry metrics, boolean autoEnabled, boolean sourceEnabled,
                           Duration autoScanInterval, Duration autoInterval, Duration failureCooldown,
                           int autoBatchSize, Duration retention, Duration cleanupInterval, int cleanupBatchSize) {
        if (autoScanInterval.compareTo(Duration.ofMinutes(1)) < 0
                || autoScanInterval.compareTo(Duration.ofHours(1)) > 0
                || autoInterval.compareTo(Duration.ofHours(6)) < 0
                || autoInterval.compareTo(Duration.ofDays(30)) > 0
                || failureCooldown.compareTo(Duration.ofHours(1)) < 0
                || failureCooldown.compareTo(Duration.ofDays(30)) > 0
                || autoBatchSize < 1 || autoBatchSize > 50
                || retention.compareTo(Duration.ofDays(1)) < 0
                || retention.compareTo(Duration.ofDays(365)) > 0
                || cleanupInterval.compareTo(Duration.ofMinutes(5)) < 0
                || cleanupInterval.compareTo(Duration.ofDays(1)) > 0
                || cleanupBatchSize < 1 || cleanupBatchSize > 1000)
            throw new IllegalArgumentException("Invalid sync maintenance settings");
        this.runs = runs;
        this.transactions = new TransactionTemplate(manager);
        this.clock = clock;
        this.metrics = metrics;
        this.autoEnabled = autoEnabled;
        this.sourceEnabled = sourceEnabled;
        this.autoInterval = autoInterval;
        this.failureCooldown = failureCooldown;
        this.autoBatchSize = autoBatchSize;
        this.retention = retention;
        this.cleanupBatchSize = cleanupBatchSize;
    }

    @Scheduled(fixedDelayString = "${ams.sync.auto-scan-interval:5m}")
    public void scan() {
        if (!autoEnabled || !sourceEnabled) return;
        try { enqueueDue(); }
        catch (RuntimeException ex) { log.warn("Scheduled sync enqueue unavailable"); }
    }

    public int enqueueDue() {
        if (!autoEnabled || !sourceEnabled) return 0;
        var now = clock.instant();
        int count = transactions.execute(status -> {
            var users = runs.dueUsers(now, autoInterval.toSeconds(), failureCooldown.toSeconds(), autoBatchSize);
            int created = 0;
            for (var userId : users)
                if (runs.enqueueWithResult(userId, SyncRun.Trigger.SCHEDULED, now).created()) created++;
            return created;
        });
        if (count > 0) {
            metrics.counter("sync.auto.enqueue.total", "result", "ENQUEUED").increment(count);
            log.info("Scheduled sync queued {} runs", count);
        }
        return count;
    }

    @Scheduled(fixedDelayString = "${ams.sync.cleanup-interval:1h}")
    public void clean() {
        try { cleanupExpired(); }
        catch (RuntimeException ex) { log.warn("Sync history cleanup unavailable"); }
    }

    public int cleanupExpired() {
        int deleted = runs.deleteExpired(clock.instant().minus(retention), cleanupBatchSize);
        if (deleted > 0) {
            metrics.counter("sync.run.cleanup.deleted").increment(deleted);
            log.info("Sync history cleanup deleted {} runs", deleted);
        }
        return deleted;
    }
}
