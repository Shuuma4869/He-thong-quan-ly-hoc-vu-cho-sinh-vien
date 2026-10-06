package vn.edu.phenikaa.ams.sync.application;

import java.time.Instant;
import java.util.UUID;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.edu.phenikaa.ams.notification.infrastructure.NotificationOutboxStore;
import vn.edu.phenikaa.ams.sync.domain.SyncRun;
import vn.edu.phenikaa.ams.sync.domain.SyncRun.*;
import vn.edu.phenikaa.ams.sync.infrastructure.SyncRunStore;

public class SyncRunFinalizer {
    private final SyncRunStore runs;
    private final NotificationOutboxStore outbox;
    private final TransactionTemplate transactions;
    private final boolean emailEnabled;
    private final MeterRegistry metrics;

    public SyncRunFinalizer(SyncRunStore runs, NotificationOutboxStore outbox,
                            PlatformTransactionManager manager, MeterRegistry metrics, boolean emailEnabled) {
        this.runs = runs; this.outbox = outbox; this.transactions = new TransactionTemplate(manager);
        this.metrics = metrics; this.emailEnabled = emailEnabled;
    }

    public boolean finish(UUID id, int attempt, Status status, FailureCode failure, Instant now) {
        var result = transactions.execute(tx -> {
            boolean finished = runs.finish(id, attempt, status, failure, now);
            boolean enqueued = finished && emailEnabled && outbox.enqueueIfEligible(id, now);
            return new Result(finished, enqueued);
        });
        recordEnqueue(result);
        return result.transitioned();
    }

    public boolean recover(SyncRun run, Instant cutoff, Instant next, Instant now, int maxAttempts) {
        var result = transactions.execute(tx -> {
            boolean recovered = runs.recover(run, cutoff, next, now, maxAttempts);
            boolean enqueued = recovered && emailEnabled && run.attemptCount() >= maxAttempts
                    && outbox.enqueueIfEligible(run.id(), now);
            return new Result(recovered, enqueued);
        });
        recordEnqueue(result);
        return result.transitioned();
    }

    private void recordEnqueue(Result result) {
        if (result.enqueued())
            metrics.counter("notification.email.outbox.total", "status", "PENDING", "type", "SYNC_ALERT").increment();
    }

    private record Result(boolean transitioned, boolean enqueued) {}
}
