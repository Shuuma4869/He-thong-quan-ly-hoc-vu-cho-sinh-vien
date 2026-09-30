package vn.edu.phenikaa.ams.sync.application;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import vn.edu.phenikaa.ams.sync.domain.SyncRun;
import vn.edu.phenikaa.ams.sync.infrastructure.SyncRunStore;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class SyncPolicyTest {
    @Test void historyCursorRoundTripsAndRejectsMalformedValue() {
        var run = new SyncRun(UUID.randomUUID(), UUID.randomUUID(), SyncRun.Trigger.MANUAL,
                SyncRun.Status.SUCCEEDED, Instant.parse("2026-09-30T12:34:56.123456Z"),
                null, null, null, null, SyncRun.StepStatus.SUCCEEDED,
                SyncRun.StepStatus.SUCCEEDED, null, 1);
        var cursor = SyncHistoryCursor.decode(SyncHistoryCursor.encode(run));
        assertThat(cursor.requestedAt()).isEqualTo(run.requestedAt());
        assertThat(cursor.runId()).isEqualTo(run.id());
        for (String invalid : new String[]{"", "bad!", "abc", "a".repeat(129)})
            assertThatThrownBy(() -> SyncHistoryCursor.decode(invalid))
                    .isInstanceOf(SyncCommandException.class).hasMessage("INVALID_HISTORY_CURSOR");
    }

    @Test void rejectsUnsafeMaintenanceIntervalsAndBatches() {
        var runs = mock(SyncRunStore.class);
        var manager = mock(PlatformTransactionManager.class);
        var metrics = new SimpleMeterRegistry();
        assertThatThrownBy(() -> new SyncMaintenance(runs, manager, Clock.systemUTC(), metrics,
                true, true, Duration.ofMillis(1), Duration.ofDays(1), Duration.ofDays(1), 4,
                Duration.ofDays(90), Duration.ofHours(1), 100)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SyncMaintenance(runs, manager, Clock.systemUTC(), metrics,
                true, true, Duration.ofMinutes(5), Duration.ofSeconds(1), Duration.ofDays(1), 4,
                Duration.ofDays(90), Duration.ofHours(1), 100)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SyncMaintenance(runs, manager, Clock.systemUTC(), metrics,
                true, true, Duration.ofMinutes(5), Duration.ofDays(1), Duration.ofDays(1), 4,
                Duration.ofSeconds(-1), Duration.ofHours(1), 100)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SyncMaintenance(runs, manager, Clock.systemUTC(), metrics,
                true, true, Duration.ofMinutes(5), Duration.ofDays(1), Duration.ofDays(1), 1_000_000,
                Duration.ofDays(90), Duration.ofHours(1), 100)).isInstanceOf(IllegalArgumentException.class);
        metrics.close();
    }
}
