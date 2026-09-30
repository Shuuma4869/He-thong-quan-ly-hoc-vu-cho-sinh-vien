package vn.edu.phenikaa.ams.sync.application;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import vn.edu.phenikaa.ams.academic.application.CurriculumImportService;
import vn.edu.phenikaa.ams.academic.application.ProfileImportService;
import vn.edu.phenikaa.ams.academic.application.port.AcademicPortalException;
import vn.edu.phenikaa.ams.sync.domain.SyncRun;
import vn.edu.phenikaa.ams.sync.domain.SyncRun.*;
import vn.edu.phenikaa.ams.sync.infrastructure.RedisSyncLock;
import vn.edu.phenikaa.ams.sync.infrastructure.SyncLeaseLostException;
import vn.edu.phenikaa.ams.sync.infrastructure.SyncRunStore;

public class SyncWorker implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(SyncWorker.class);
    private final SyncRunStore runs;
    private final RedisSyncLock lock;
    private final ProfileImportService profiles;
    private final CurriculumImportService curricula;
    private final Clock clock;
    private final MeterRegistry metrics;
    private final int maxAttempts;
    private final int batchSize;
    private final Duration backoff;
    private final Duration staleTimeout;
    private final boolean enabled;
    private final ScheduledExecutorService heartbeats = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("ams-sync-heartbeat").factory());

    public SyncWorker(SyncRunStore runs, RedisSyncLock lock, ProfileImportService profiles,
                      CurriculumImportService curricula, Clock clock, MeterRegistry metrics,
                      int maxAttempts, int batchSize, Duration backoff, Duration staleTimeout, boolean enabled) {
        if (maxAttempts < 1 || maxAttempts > 5 || batchSize < 1 || batchSize > 100
                || backoff.compareTo(Duration.ofSeconds(1)) < 0 || backoff.compareTo(Duration.ofHours(1)) > 0
                || staleTimeout.compareTo(lock.ttl().plusSeconds(5)) <= 0
                || staleTimeout.compareTo(Duration.ofMinutes(30)) > 0)
            throw new IllegalArgumentException("Invalid sync worker settings");
        this.runs = runs; this.lock = lock; this.profiles = profiles; this.curricula = curricula;
        this.clock = clock; this.metrics = metrics; this.maxAttempts = maxAttempts;
        this.batchSize = batchSize; this.backoff = backoff; this.staleTimeout = staleTimeout; this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${ams.sync.poll-interval:2s}")
    public void poll() {
        if (!enabled) return;
        recoverStale();
        for (int i = 0; i < batchSize; i++) if (!runOnce()) break;
    }

    public boolean runOnce() {
        var run = runs.claim(clock.instant()).orElse(null);
        if (run == null) return false;
        String owner;
        try { owner = lock.acquire(run.userId()); }
        catch (RuntimeException ex) { fail(run, Step.PROFILE, run.profileStepStatus() == StepStatus.SUCCEEDED, FailureCode.LOCK_UNAVAILABLE); return true; }
        if (owner == null) { fail(run, Step.PROFILE, run.profileStepStatus() == StepStatus.SUCCEEDED, FailureCode.LOCK_UNAVAILABLE); return true; }
        var valid = new AtomicBoolean(true);
        long heartbeatMs = Math.max(1000, lock.ttl().toMillis() / 3);
        var heartbeat = heartbeats.scheduleAtFixedRate(() -> {
            try {
                if (!lock.renew(run.userId(), owner) || !runs.heartbeat(run.id(), run.attemptCount(), clock.instant()))
                    valid.set(false);
            } catch (RuntimeException ex) { valid.set(false); }
        }, heartbeatMs, heartbeatMs, TimeUnit.MILLISECONDS);
        try {
            if (!runs.heartbeat(run.id(), run.attemptCount(), clock.instant())) throw new SyncLeaseLostException();
            Runnable guard = () -> {
                if (!valid.get() || !lock.renew(run.userId(), owner)) throw new SyncLeaseLostException();
                runs.assertClaimForWrite(run.id(), run.attemptCount());
            };
            boolean profileSucceeded = run.profileStepStatus() == StepStatus.SUCCEEDED;
            Step step = Step.PROFILE;
            try {
                runs.step(run.id(), run.attemptCount(), step, StepStatus.PENDING, clock.instant());
                profiles.importCurrentProfile(run.userId(), guard);
                profileSucceeded = true;
                if (!runs.step(run.id(), run.attemptCount(), step, StepStatus.SUCCEEDED, clock.instant()))
                    throw new SyncLeaseLostException();
                step = Step.CURRICULUM;
                runs.step(run.id(), run.attemptCount(), step, StepStatus.PENDING, clock.instant());
                curricula.refreshAvailableCurricula(run.userId(), guard);
                if (!runs.step(run.id(), run.attemptCount(), step, StepStatus.SUCCEEDED, clock.instant()))
                    throw new SyncLeaseLostException();
                if (!runs.finish(run.id(), run.attemptCount(), Status.SUCCEEDED, null, clock.instant()))
                    throw new SyncLeaseLostException();
                metric(run, Status.SUCCEEDED, null);
            } catch (RuntimeException ex) {
                fail(run, step, profileSucceeded, failure(ex, step));
            }
        } catch (SyncLeaseLostException ex) {
            fail(run, Step.PROFILE, run.profileStepStatus() == StepStatus.SUCCEEDED, FailureCode.LOCK_UNAVAILABLE);
        } finally {
            heartbeat.cancel(false);
            try { lock.release(run.userId(), owner); }
            catch (RuntimeException ignored) { /* TTL still bounds a failed release. */ }
        }
        return true;
    }

    public int recoverStale() {
        var now = clock.instant();
        int recovered = 0;
        for (var run : runs.staleBefore(now.minus(staleTimeout), batchSize)) {
            try {
                if (!lock.locked(run.userId()) && runs.recover(run, now.minus(staleTimeout),
                        now.plus(backoff), now, maxAttempts)) recovered++;
            } catch (RuntimeException ex) { log.warn("Sync stale recovery unavailable"); }
        }
        return recovered;
    }

    private void fail(SyncRun run, Step step, boolean profileSucceeded, FailureCode code) {
        var now = clock.instant();
        if (code != FailureCode.LOCK_UNAVAILABLE)
            runs.step(run.id(), run.attemptCount(), step, StepStatus.FAILED, now);
        if ((code == FailureCode.SOURCE_TIMEOUT || code == FailureCode.SOURCE_UNAVAILABLE
                || code == FailureCode.LOCK_UNAVAILABLE) && run.attemptCount() < maxAttempts) {
            long multiplier = 1L << Math.min(run.attemptCount() - 1, 4);
            runs.retry(run.id(), run.attemptCount(), code, now.plus(backoff.multipliedBy(multiplier)), now);
            return;
        }
        var current = runs.owned(run.userId(), run.id()).orElse(run);
        var status = current.profileStepStatus() == StepStatus.SUCCEEDED
                && current.curriculumStepStatus() == StepStatus.SUCCEEDED ? Status.SUCCEEDED
                : profileSucceeded ? Status.PARTIAL : Status.FAILED;
        if (status == Status.PARTIAL)
            runs.step(run.id(), run.attemptCount(), Step.CURRICULUM, StepStatus.FAILED, now);
        var terminalFailure = status == Status.SUCCEEDED ? null : code;
        if (runs.finish(run.id(), run.attemptCount(), status, terminalFailure, now)) metric(run, status, terminalFailure);
    }

    private static FailureCode failure(RuntimeException ex, Step step) {
        if (ex instanceof SyncLeaseLostException) return FailureCode.LOCK_UNAVAILABLE;
        if (ex instanceof AcademicPortalException portal) return switch (portal.code()) {
            case SESSION_EXPIRED -> FailureCode.RECONNECTION_REQUIRED;
            case CONNECTION_UNAVAILABLE, SOURCE_ACCOUNT_MISMATCH, SESSION_INTEGRITY_FAILURE -> FailureCode.CONNECTION_NOT_FOUND;
            case TIMEOUT -> FailureCode.SOURCE_TIMEOUT;
            case NETWORK_ERROR -> FailureCode.SOURCE_UNAVAILABLE;
            case UNEXPECTED_SCHEMA, DECODE_ERROR, RESPONSE_TOO_LARGE -> FailureCode.SOURCE_SCHEMA_CHANGED;
            default -> step == Step.PROFILE ? FailureCode.PROFILE_REFRESH_FAILED : FailureCode.CURRICULUM_REFRESH_FAILED;
        };
        if (ex instanceof vn.edu.phenikaa.ams.academic.application.CurriculumImportException)
            return FailureCode.CURRICULUM_REFRESH_FAILED;
        return FailureCode.INTERNAL_ERROR;
    }

    private void metric(SyncRun run, Status status, FailureCode failure) {
        metrics.counter("sync.run.total", "status", status.name()).increment();
        if (failure != null) metrics.counter("sync.run.failure", "code", failure.name()).increment();
        metrics.timer("sync.run.duration", "status", status.name())
                .record(Duration.between(run.requestedAt(), clock.instant()));
        log.info("Sync run {} ended with {} after attempt {}{}", run.id(), status, run.attemptCount(),
                failure == null ? "" : " code=" + failure.name());
    }

    @Override public void close() { heartbeats.shutdownNow(); }
}
