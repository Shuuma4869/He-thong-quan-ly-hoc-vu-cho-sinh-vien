package vn.edu.phenikaa.ams;

import io.micrometer.core.instrument.MeterRegistry;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.json.JsonMapper;
import vn.edu.phenikaa.ams.academic.infrastructure.phenikaa.PhenikaaAcademicPortalClient;
import vn.edu.phenikaa.ams.academic.infrastructure.phenikaa.PhenikaaHttpClient;
import vn.edu.phenikaa.ams.academic.infrastructure.phenikaa.PhenikaaSessionMaterial;
import vn.edu.phenikaa.ams.academic.application.port.ProfileObservation;
import vn.edu.phenikaa.ams.auth.application.AccountPrincipal;
import vn.edu.phenikaa.ams.sync.application.SyncMaintenance;
import vn.edu.phenikaa.ams.sync.application.SyncService;
import vn.edu.phenikaa.ams.sync.application.port.SyncJobDispatcher;
import vn.edu.phenikaa.ams.sync.domain.SyncRun;
import vn.edu.phenikaa.ams.sync.infrastructure.SyncRunStore;
import vn.edu.phenikaa.ams.user.domain.AppUser;
import vn.edu.phenikaa.ams.user.infrastructure.UserRepository;
import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {"spring.data.redis.password=", "ams.phenikaa.enabled=true",
        "ams.sync.worker-enabled=false", "ams.sync.auto-enabled=false"})
@AutoConfigureMockMvc
class SyncMaintenanceIT {
    @Autowired UserRepository users;
    @Autowired PhenikaaAcademicPortalClient portal;
    @Autowired SyncRunStore runs;
    @Autowired SyncService sync;
    @Autowired SyncMaintenance defaultMaintenance;
    @Autowired JdbcTemplate jdbc;
    @Autowired StringRedisTemplate redis;
    @Autowired PlatformTransactionManager manager;
    @Autowired MeterRegistry metrics;
    @Autowired MockMvc mvc;
    @MockitoBean PhenikaaHttpClient http;
    private final JsonMapper json = JsonMapper.builder().build();
    private Instant now;

    @DynamicPropertySource static void key(DynamicPropertyRegistry registry) {
        byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
        String encoded = Base64.getEncoder().encodeToString(bytes); Arrays.fill(bytes, (byte) 0);
        registry.add("ams.phenikaa.session-key", () -> encoded);
    }

    @BeforeEach void setup() {
        jdbc.update("delete from sync_run");
        jdbc.update("update phenikaa_connection set status = 'DISCONNECTED', encrypted_session = null, session_expires_at = null");
        now = Instant.now();
        when(http.fetchProfile(any())).thenReturn(new ProfileObservation("SYNTHETIC-001", "Ngành giả định"));
    }

    private AppUser account() {
        return users.save(new AppUser(UUID.randomUUID() + "@example.test", "!synthetic", null));
    }

    private void connect(AppUser user) {
        try (var material = new PhenikaaSessionMaterial("Bearer synthetic-secret", "", "synthetic-key",
                "synthetic-learner", "profile", "schedule", null, "academic", "curriculum")) {
            portal.connect(user.getId(), material);
        }
    }

    private void oldConnection(AppUser user) {
        jdbc.update("update phenikaa_connection set created_at = ? where user_id = ?",
                Timestamp.from(now.minus(Duration.ofDays(3))), user.getId());
    }

    private SyncMaintenance maintenance(int autoBatch, int cleanupBatch) {
        return maintenance(runs, autoBatch, cleanupBatch);
    }

    private SyncMaintenance maintenance(SyncRunStore store, int autoBatch, int cleanupBatch) {
        return new SyncMaintenance(store, manager, Clock.fixed(now, ZoneOffset.UTC), metrics,
                true, true, Duration.ofMinutes(5), Duration.ofDays(1), Duration.ofDays(1), autoBatch,
                Duration.ofDays(90), Duration.ofHours(1), cleanupBatch);
    }

    private UUID insertRun(AppUser user, String status, Instant requested, Instant finished) {
        UUID id = UUID.randomUUID();
        String profile = status.equals("SUCCEEDED") || status.equals("PARTIAL") ? "SUCCEEDED" : "PENDING";
        String curriculum = status.equals("SUCCEEDED") ? "SUCCEEDED" : status.equals("PARTIAL") ? "FAILED" : "PENDING";
        jdbc.update("""
                insert into sync_run(id,user_id,trigger_type,status,requested_at,started_at,heartbeat_at,
                    finished_at,next_attempt_at,profile_step_status,curriculum_step_status,updated_at)
                values (?,?, 'SCHEDULED', ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, user.getId(), status, Timestamp.from(requested),
                status.equals("QUEUED") ? null : Timestamp.from(requested),
                status.equals("RUNNING") ? Timestamp.from(requested) : null,
                finished == null ? null : Timestamp.from(finished), Timestamp.from(requested),
                profile, curriculum, Timestamp.from(requested));
        return id;
    }

    @Test void autoSelectsOnlyConnectedActiveDueUsersAndBoundsBatch() {
        var due = account(); connect(due); oldConnection(due);
        var extra = account(); connect(extra); oldConnection(extra);
        var notDue = account(); connect(notDue);
        var disconnected = account(); connect(disconnected); oldConnection(disconnected); portal.disconnect(disconnected.getId());
        var reconnect = account(); connect(reconnect); oldConnection(reconnect);
        jdbc.update("update phenikaa_connection set status = 'RECONNECTION_REQUIRED' where user_id = ?", reconnect.getId());
        var disabled = account(); connect(disabled); oldConnection(disabled);
        jdbc.update("update app_user set account_status = 'DISABLED' where id = ?", disabled.getId());
        var active = account(); connect(active); oldConnection(active);
        runs.enqueue(active.getId(), SyncRun.Trigger.MANUAL, now);

        assertThat(defaultMaintenance.enqueueDue()).isZero();
        assertThat(maintenance(1, 100).enqueueDue()).isEqualTo(1);
        assertThat(maintenance(1, 100).enqueueDue()).isEqualTo(1);
        assertThat(maintenance(1, 100).enqueueDue()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from sync_run where trigger_type = 'SCHEDULED'", Integer.class)).isEqualTo(2);
        for (var user : new AppUser[]{notDue, disconnected, reconnect, disabled})
            assertThat(runs.latest(user.getId())).isEmpty();
        assertThat(runs.active(active.getId()).orElseThrow().trigger()).isEqualTo(SyncRun.Trigger.MANUAL);
        verify(http, never()).fetchCurricula(any());
    }

    @Test void concurrentSchedulersDoNotCreateTwoActiveRuns() throws Exception {
        var owner = account(); connect(owner); oldConnection(owner);
        var first = maintenance(1, 100);
        var second = maintenance(1, 100);
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int attempt = 0; attempt < 20; attempt++) {
                int currentAttempt = attempt;
                jdbc.update("delete from sync_run where user_id = ?", owner.getId());
                double before = metrics.counter("sync.auto.enqueue.total", "result", "ENQUEUED").count();
                var gate = new CountDownLatch(1);
                var a = pool.submit(() -> { gate.await(); return first.enqueueDue(); });
                var b = pool.submit(() -> { gate.await(); return second.enqueueDue(); });
                gate.countDown();
                int reported = a.get(10, TimeUnit.SECONDS) + b.get(10, TimeUnit.SECONDS);
                int rows = jdbc.queryForObject("select count(*) from sync_run where user_id = ?",
                        Integer.class, owner.getId());
                int activeRows = jdbc.queryForObject("select count(*) from sync_run where user_id = ? "
                        + "and status in ('QUEUED','RUNNING')", Integer.class, owner.getId());
                var active = runs.active(owner.getId());
                double recorded = metrics.counter("sync.auto.enqueue.total", "result", "ENQUEUED").count() - before;
                assertSoftly(softly -> {
                    softly.assertThat(reported).as("reported new runs, attempt %s", currentAttempt).isEqualTo(1);
                    softly.assertThat(rows).as("stored runs, attempt %s", currentAttempt).isEqualTo(1);
                    softly.assertThat(activeRows).as("active runs, attempt %s", currentAttempt).isEqualTo(1);
                    softly.assertThat(active.map(SyncRun::trigger)).as("active run trigger, attempt %s", currentAttempt)
                            .contains(SyncRun.Trigger.SCHEDULED);
                    softly.assertThat(recorded).as("enqueue metric, attempt %s", currentAttempt).isEqualTo(1);
                });
            }
        }
    }

    @Test void staleSelectionDoesNotCountAnExistingRun() {
        var owner = account();
        var staleStore = new SyncRunStore(jdbc) {
            @Override public List<UUID> dueUsers(Instant ignored, long intervalSeconds,
                                                  long failureCooldownSeconds, int limit) {
                return List.of(owner.getId());
            }
        };
        var scheduler = maintenance(staleStore, 1, 100);
        double before = metrics.counter("sync.auto.enqueue.total", "result", "ENQUEUED").count();
        int first = scheduler.enqueueDue();
        int second = scheduler.enqueueDue();
        int rows = jdbc.queryForObject("select count(*) from sync_run where user_id = ?",
                Integer.class, owner.getId());
        int activeRows = jdbc.queryForObject("select count(*) from sync_run where user_id = ? "
                + "and status in ('QUEUED','RUNNING')", Integer.class, owner.getId());
        double recorded = metrics.counter("sync.auto.enqueue.total", "result", "ENQUEUED").count() - before;
        assertSoftly(softly -> {
            softly.assertThat(first).isEqualTo(1);
            softly.assertThat(second).isZero();
            softly.assertThat(rows).isEqualTo(1);
            softly.assertThat(activeRows).isEqualTo(1);
            softly.assertThat(runs.active(owner.getId()).map(SyncRun::trigger))
                    .contains(SyncRun.Trigger.SCHEDULED);
            softly.assertThat(recorded).isEqualTo(1);
        });
    }

    @Test void terminalFailureWaitsForCooldownAndManualCooldownIsSeparate() {
        var owner = account(); connect(owner); oldConnection(owner);
        UUID failure = insertRun(owner, "FAILED", now.minus(Duration.ofHours(2)), now.minus(Duration.ofHours(1)));
        assertThat(maintenance(4, 100).enqueueDue()).isZero();
        jdbc.update("update sync_run set requested_at = ?, finished_at = ? where id = ?",
                Timestamp.from(now.minus(Duration.ofHours(26))), Timestamp.from(now.minus(Duration.ofHours(25))), failure);
        assertThat(maintenance(4, 100).enqueueDue()).isEqualTo(1);
        var scheduled = runs.active(owner.getId()).orElseThrow();
        assertThat(scheduled.trigger()).isEqualTo(SyncRun.Trigger.SCHEDULED);
        assertThat(Boolean.TRUE.equals(redis.hasKey("ams:sync:manual:" + owner.getId()))).isFalse();
        assertThat(sync.dispatchAcademicSync(owner.getId(), SyncJobDispatcher.Trigger.MANUAL).jobId())
                .isEqualTo(scheduled.id());
        assertThat(Boolean.TRUE.equals(redis.hasKey("ams:sync:manual:" + owner.getId()))).isFalse();
        var claimed = runs.claim(now).orElseThrow();
        runs.step(claimed.id(), claimed.attemptCount(), SyncRun.Step.PROFILE, SyncRun.StepStatus.SUCCEEDED, now);
        runs.step(claimed.id(), claimed.attemptCount(), SyncRun.Step.CURRICULUM, SyncRun.StepStatus.SUCCEEDED, now);
        runs.finish(claimed.id(), claimed.attemptCount(), SyncRun.Status.SUCCEEDED, null, now);
        assertThat(maintenance(4, 100).enqueueDue()).isZero();
        assertThat(sync.dispatchAcademicSync(owner.getId(), SyncJobDispatcher.Trigger.MANUAL).status())
                .isEqualTo(SyncJobDispatcher.Status.QUEUED);
        assertThat(Boolean.TRUE.equals(redis.hasKey("ams:sync:manual:" + owner.getId()))).isTrue();
    }

    @Test void cleanupDeletesOnlyOldTerminalRunsInSmallBatches() throws Exception {
        var owner = account();
        insertRun(owner, "SUCCEEDED", now.minus(Duration.ofDays(100)), now.minus(Duration.ofDays(99)));
        insertRun(owner, "FAILED", now.minus(Duration.ofDays(100)), now.minus(Duration.ofDays(98)));
        insertRun(owner, "PARTIAL", now.minus(Duration.ofDays(100)), now.minus(Duration.ofDays(97)));
        UUID recent = insertRun(owner, "SUCCEEDED", now.minus(Duration.ofDays(2)), now.minus(Duration.ofDays(1)));
        var oldOnlyOwner = account();
        insertRun(oldOnlyOwner, "FAILED", now.minus(Duration.ofDays(100)), now.minus(Duration.ofDays(96)));
        var queuedOwner = account();
        UUID queued = insertRun(queuedOwner, "QUEUED", now.minus(Duration.ofDays(100)), null);
        var runningOwner = account();
        UUID running = insertRun(runningOwner, "RUNNING", now.minus(Duration.ofDays(100)), null);
        var cleaner = maintenance(4, 2);
        assertThat(cleaner.cleanupExpired()).isEqualTo(2);
        assertThat(cleaner.cleanupExpired()).isEqualTo(2);
        assertThat(cleaner.cleanupExpired()).isZero();
        assertThat(runs.latest(owner.getId()).orElseThrow().id()).isEqualTo(recent);
        assertThat(runs.owned(queuedOwner.getId(), queued)).isPresent();
        assertThat(runs.owned(runningOwner.getId(), running)).isPresent();
        mvc.perform(get("/api/me/sync/current").with(user(new AccountPrincipal(owner))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.runId").value(recent.toString()));
        mvc.perform(get("/api/me/sync/current").with(user(new AccountPrincipal(oldOnlyOwner))))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RUN_NOT_FOUND"));
    }

    @Test void historyUsesOwnedKeysetCursorAndRejectsInvalidInput() throws Exception {
        var owner = account();
        mvc.perform(get("/api/me/sync/runs").with(user(new AccountPrincipal(owner))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(get("/api/me/sync/runs")).andExpect(status().isUnauthorized());
        Instant stamp = now.minus(Duration.ofDays(2));
        for (int i = 1; i <= 3; i++) {
            UUID id = UUID.fromString("00000000-0000-0000-0000-00000000000" + i);
            jdbc.update("""
                    insert into sync_run(id,user_id,trigger_type,status,requested_at,finished_at,next_attempt_at,updated_at)
                    values (?,?,'MANUAL','FAILED',?,?,?,?)
                    """, id, owner.getId(), Timestamp.from(stamp), Timestamp.from(stamp.plusSeconds(1)),
                    Timestamp.from(stamp), Timestamp.from(stamp));
        }
        var first = mvc.perform(get("/api/me/sync/runs").param("limit", "2")
                        .with(user(new AccountPrincipal(owner))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        var firstJson = json.readTree(first);
        assertThat(firstJson.path("items").get(0).path("runId").asText()).endsWith("0003");
        assertThat(firstJson.path("items").get(1).path("runId").asText()).endsWith("0002");
        String cursor = firstJson.path("nextCursor").asText();
        mvc.perform(get("/api/me/sync/runs").param("limit", "2").param("cursor", cursor)
                        .with(user(new AccountPrincipal(owner))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].runId").value("00000000-0000-0000-0000-000000000001"))
                .andExpect(jsonPath("$.nextCursor").isEmpty());
        var other = account();
        mvc.perform(get("/api/me/sync/runs").param("cursor", cursor)
                        .with(user(new AccountPrincipal(other))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_HISTORY_CURSOR"));
        mvc.perform(get("/api/me/sync/runs").param("cursor", "bad!")
                        .with(user(new AccountPrincipal(owner))))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/me/sync/runs").param("limit", "101")
                        .with(user(new AccountPrincipal(owner))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_HISTORY_LIMIT"));
        mvc.perform(get("/api/me/sync/runs").param("limit", "0")
                        .with(user(new AccountPrincipal(owner))))
                .andExpect(status().isBadRequest());
    }
}
