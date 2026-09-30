package vn.edu.phenikaa.ams;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import vn.edu.phenikaa.ams.academic.application.port.*;
import vn.edu.phenikaa.ams.academic.infrastructure.phenikaa.*;
import vn.edu.phenikaa.ams.auth.application.AccountPrincipal;
import vn.edu.phenikaa.ams.sync.application.SyncService;
import vn.edu.phenikaa.ams.sync.application.SyncWorker;
import vn.edu.phenikaa.ams.sync.application.port.SyncJobDispatcher;
import vn.edu.phenikaa.ams.sync.domain.SyncRun;
import vn.edu.phenikaa.ams.sync.infrastructure.RedisSyncLock;
import vn.edu.phenikaa.ams.sync.infrastructure.SyncRunStore;
import vn.edu.phenikaa.ams.user.domain.AppUser;
import vn.edu.phenikaa.ams.user.infrastructure.UserRepository;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {"spring.data.redis.password=", "ams.phenikaa.enabled=true",
        "ams.sync.worker-enabled=false", "ams.sync.lock-ttl=30s", "ams.sync.stale-timeout=40s",
        "ams.sync.backoff=1s", "ams.sync.manual-cooldown=5s"})
@AutoConfigureMockMvc
class SyncInfrastructureIT {
    @Autowired UserRepository users;
    @Autowired PhenikaaAcademicPortalClient portal;
    @Autowired SyncService sync;
    @Autowired SyncWorker worker;
    @Autowired SyncRunStore runs;
    @Autowired RedisSyncLock lock;
    @Autowired StringRedisTemplate redis;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired MockMvc mvc;
    @MockitoBean PhenikaaHttpClient http;
    private final JsonMapper json = JsonMapper.builder().build();
    private AppUser owner;
    private final CurriculumOption option = new CurriculumOption("synthetic-curriculum", "TEST-CT",
            "Chương trình giả định", null, new BigDecimal("3"));

    @DynamicPropertySource static void key(DynamicPropertyRegistry registry) {
        byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
        String encoded = Base64.getEncoder().encodeToString(bytes); Arrays.fill(bytes, (byte) 0);
        registry.add("ams.phenikaa.session-key", () -> encoded);
    }

    @BeforeEach void setup() {
        jdbc.update("delete from sync_run");
        owner = users.save(new AppUser(UUID.randomUUID() + "@example.test", "!synthetic-unusable", null));
        when(http.fetchProfile(any())).thenReturn(new ProfileObservation("SYNTHETIC-001", "Ngành giả định"));
        when(http.fetchCurricula(any())).thenReturn(List.of(option));
        when(http.fetchCurriculum(any(), any())).thenReturn(new CurriculumObservation(option,
                List.of(new CurriculumObservation.CourseEntry("synthetic-course", "TEST101", "Môn giả định",
                        new BigDecimal("3"), false)),
                List.of(new CurriculumObservation.Group("synthetic-group", "R", "Nhóm bắt buộc giả định",
                        CurriculumObservation.Requirement.REQUIRED, null, null, List.of("synthetic-course")))));
    }

    private void connect(AppUser user) {
        try (var material = new PhenikaaSessionMaterial("Bearer synthetic-secret", "", "synthetic-key",
                "synthetic-learner", "profile", "schedule", null, "academic", "curriculum")) {
            portal.connect(user.getId(), material);
        }
    }

    @Test void manualApiIsAsyncOwnedAndDeduplicated() throws Exception {
        mvc.perform(post("/api/me/sync").with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/me/sync").with(user(new AccountPrincipal(owner))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/me/sync/current").with(user(new AccountPrincipal(owner))))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/me/sync").with(user(new AccountPrincipal(owner))).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONNECTION_NOT_FOUND"));
        connect(owner);
        var first = mvc.perform(post("/api/me/sync").with(user(new AccountPrincipal(owner))).with(csrf()))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("QUEUED"))
                .andReturn().getResponse().getContentAsString();
        String id = json.readTree(first).path("runId").asText();
        assertThat(first).doesNotContain("Bearer", "synthetic-learner", "sourceId", "cookie");
        assertThatThrownBy(() -> jdbc.update("""
                insert into sync_run(id,user_id,trigger_type,status,requested_at,next_attempt_at,updated_at)
                values (?, ?, 'MANUAL', 'QUEUED', now(), now(), now())
                """, UUID.randomUUID(), owner.getId())).isInstanceOf(DataIntegrityViolationException.class);
        mvc.perform(post("/api/me/sync").with(user(new AccountPrincipal(owner))).with(csrf()))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.runId").value(id));
        assertThat(jdbc.queryForObject("select count(*) from sync_run where user_id = ?", Integer.class, owner.getId())).isEqualTo(1);
        var other = users.save(new AppUser(UUID.randomUUID() + "@example.test", "!synthetic", null));
        mvc.perform(get("/api/me/sync/runs/{id}", id).with(user(new AccountPrincipal(other))))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/me/sync/current").with(user(new AccountPrincipal(owner))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.runId").value(id));
        verify(http, never()).fetchCurricula(any());
    }

    @Test void manualCooldownAppliesAfterRunAndReconnectionIsRequiredAfterExpiry() throws Exception {
        connect(owner);
        UUID id = sync.dispatchAcademicSync(owner.getId(), SyncJobDispatcher.Trigger.MANUAL).jobId();
        worker.runOnce();
        assertThat(runs.owned(owner.getId(), id).orElseThrow().status()).isEqualTo(SyncRun.Status.SUCCEEDED);
        mvc.perform(post("/api/me/sync").with(user(new AccountPrincipal(owner))).with(csrf()))
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("RATE_LIMITED"));
        when(http.fetchProfile(any())).thenThrow(new PhenikaaClientException(PhenikaaClientException.Code.SESSION_EXPIRED));
        assertThatThrownBy(() -> portal.fetchProfile(owner.getId(), portal.currentConnection(owner.getId())))
                .hasMessage("SESSION_EXPIRED");
        mvc.perform(post("/api/me/sync").with(user(new AccountPrincipal(owner))).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RECONNECTION_REQUIRED"));
    }

    @Test void successfulWorkerRefreshesOnlySupportedDataWithoutNetworkTransaction() {
        connect(owner);
        when(http.fetchProfile(any())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return new ProfileObservation("SYNTHETIC-001", "Ngành giả định");
        });
        when(http.fetchCurricula(any())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return List.of(option);
        });
        var input = new CurriculumObservation(option,
                List.of(new CurriculumObservation.CourseEntry("synthetic-course", "TEST101", "Môn giả định", new BigDecimal("3"), false)),
                List.of(new CurriculumObservation.Group("synthetic-group", "R", "Nhóm bắt buộc giả định",
                        CurriculumObservation.Requirement.REQUIRED, null, null, List.of("synthetic-course"))));
        when(http.fetchCurriculum(any(), any())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return input;
        });
        UUID id = sync.dispatchAcademicSync(owner.getId(), SyncJobDispatcher.Trigger.MANUAL).jobId();
        assertThat(worker.runOnce()).isTrue();
        assertThat(runs.owned(owner.getId(), id).orElseThrow().status()).isEqualTo(SyncRun.Status.SUCCEEDED);
        UUID profile = jdbc.queryForObject("select id from student_profile where user_id = ?", UUID.class, owner.getId());
        assertThat(profile).isNotNull();
        assertThat(jdbc.queryForObject("select count(*) from curriculum where profile_id = ?", Integer.class, profile)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from course where profile_id = ?", Integer.class, profile)).isEqualTo(1);
        for (String table : List.of("student_course", "academic_result", "class_session", "exam"))
            assertThat(jdbc.queryForObject("select count(*) from " + table + " where profile_id = ?", Integer.class, profile)).isZero();
        sync.dispatchAcademicSync(owner.getId(), SyncJobDispatcher.Trigger.SCHEDULED);
        assertThat(worker.runOnce()).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from curriculum where profile_id = ?", Integer.class, profile)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from course where profile_id = ?", Integer.class, profile)).isEqualTo(1);
    }

    @Test void profileFailureStopsCurriculumAndSessionExpiryDoesNotRetry() {
        connect(owner);
        when(http.fetchProfile(any())).thenThrow(new PhenikaaClientException(PhenikaaClientException.Code.SESSION_EXPIRED));
        UUID id = sync.dispatchAcademicSync(owner.getId(), SyncJobDispatcher.Trigger.MANUAL).jobId();
        worker.runOnce();
        var result = runs.owned(owner.getId(), id).orElseThrow();
        assertThat(result.status()).isEqualTo(SyncRun.Status.FAILED);
        assertThat(result.failureCode()).isEqualTo(SyncRun.FailureCode.RECONNECTION_REQUIRED);
        assertThat(result.attemptCount()).isEqualTo(1);
        assertThat(portal.connectionInfo(owner.getId()).state()).isEqualTo(AcademicPortalClient.ConnectionInfo.State.RECONNECTION_REQUIRED);
        verify(http, never()).fetchCurricula(any());
    }

    @Test void curriculumFailureAfterProfileCommitIsPartialAndSchemaFailureDoesNotRetry() {
        connect(owner);
        when(http.fetchCurriculum(any(), any())).thenThrow(new PhenikaaClientException(PhenikaaClientException.Code.UNEXPECTED_SCHEMA));
        UUID id = sync.dispatchAcademicSync(owner.getId(), SyncJobDispatcher.Trigger.MANUAL).jobId();
        worker.runOnce();
        var result = runs.owned(owner.getId(), id).orElseThrow();
        assertThat(result.status()).isEqualTo(SyncRun.Status.PARTIAL);
        assertThat(result.failureCode()).isEqualTo(SyncRun.FailureCode.SOURCE_SCHEMA_CHANGED);
        assertThat(jdbc.queryForObject("select count(*) from student_profile where user_id = ?", Integer.class, owner.getId())).isEqualTo(1);
        assertThat(result.attemptCount()).isEqualTo(1);
    }

    @Test void timeoutRetriesSameRunWithDurableBackoffThenSucceeds() {
        connect(owner);
        when(http.fetchProfile(any())).thenThrow(new PhenikaaClientException(PhenikaaClientException.Code.TIMEOUT))
                .thenReturn(new ProfileObservation("SYNTHETIC-001", "Ngành giả định"));
        UUID id = sync.dispatchAcademicSync(owner.getId(), SyncJobDispatcher.Trigger.MANUAL).jobId();
        worker.runOnce();
        var pending = runs.owned(owner.getId(), id).orElseThrow();
        assertThat(pending.status()).isEqualTo(SyncRun.Status.QUEUED);
        assertThat(pending.failureCode()).isEqualTo(SyncRun.FailureCode.SOURCE_TIMEOUT);
        assertThat(pending.nextAttemptAt()).isAfter(Instant.now().minusSeconds(1));
        jdbc.update("update sync_run set next_attempt_at = now() - interval '1 second' where id = ?", id);
        worker.runOnce();
        var result = runs.owned(owner.getId(), id).orElseThrow();
        assertThat(result.status()).isEqualTo(SyncRun.Status.SUCCEEDED);
        assertThat(result.attemptCount()).isEqualTo(2);
    }

    @Test void curriculumTimeoutRetriesAfterProfileCommitWithoutDuplicatingProfile() {
        connect(owner);
        var input = new CurriculumObservation(option, List.of(), List.of());
        when(http.fetchCurriculum(any(), any()))
                .thenThrow(new PhenikaaClientException(PhenikaaClientException.Code.TIMEOUT)).thenReturn(input);
        UUID id = sync.dispatchAcademicSync(owner.getId(), SyncJobDispatcher.Trigger.MANUAL).jobId();
        worker.runOnce();
        var pending = runs.owned(owner.getId(), id).orElseThrow();
        assertThat(pending.status()).isEqualTo(SyncRun.Status.QUEUED);
        assertThat(pending.profileStepStatus()).isEqualTo(SyncRun.StepStatus.SUCCEEDED);
        UUID profile = jdbc.queryForObject("select id from student_profile where user_id = ?", UUID.class, owner.getId());
        jdbc.update("update sync_run set next_attempt_at = now() - interval '1 second' where id = ?", id);
        worker.runOnce();
        assertThat(runs.owned(owner.getId(), id).orElseThrow().status()).isEqualTo(SyncRun.Status.SUCCEEDED);
        assertThat(jdbc.queryForObject("select id from student_profile where user_id = ?", UUID.class, owner.getId())).isEqualTo(profile);
        assertThat(jdbc.queryForObject("select count(*) from curriculum where profile_id = ?", Integer.class, profile)).isEqualTo(1);
    }

    @Test void networkFailureStopsAfterBoundedAttemptsAndKeepsOldData() {
        connect(owner);
        when(http.fetchProfile(any())).thenThrow(new PhenikaaClientException(PhenikaaClientException.Code.NETWORK_ERROR));
        UUID id = sync.dispatchAcademicSync(owner.getId(), SyncJobDispatcher.Trigger.MANUAL).jobId();
        for (int attempt = 1; attempt <= 3; attempt++) {
            assertThat(worker.runOnce()).isTrue();
            if (attempt < 3) jdbc.update("update sync_run set next_attempt_at = now() - interval '1 second' where id = ?", id);
        }
        var result = runs.owned(owner.getId(), id).orElseThrow();
        assertThat(result.status()).isEqualTo(SyncRun.Status.FAILED);
        assertThat(result.failureCode()).isEqualTo(SyncRun.FailureCode.SOURCE_UNAVAILABLE);
        assertThat(result.attemptCount()).isEqualTo(3);
        assertThat(jdbc.queryForObject("select count(*) from student_profile where user_id = ?", Integer.class, owner.getId())).isZero();
    }

    @Test void busyUserLockReschedulesInsteadOfFailingImmediately() {
        connect(owner);
        String otherOwner = lock.acquire(owner.getId());
        UUID id = sync.dispatchAcademicSync(owner.getId(), SyncJobDispatcher.Trigger.MANUAL).jobId();
        worker.runOnce();
        var pending = runs.owned(owner.getId(), id).orElseThrow();
        assertThat(pending.status()).isEqualTo(SyncRun.Status.QUEUED);
        assertThat(pending.failureCode()).isEqualTo(SyncRun.FailureCode.LOCK_UNAVAILABLE);
        assertThat(lock.release(owner.getId(), otherOwner)).isTrue();
        jdbc.update("update sync_run set next_attempt_at = now() - interval '1 second' where id = ?", id);
        worker.runOnce();
        assertThat(runs.owned(owner.getId(), id).orElseThrow().status()).isEqualTo(SyncRun.Status.SUCCEEDED);
    }

    @Test void disconnectedOrDisabledOwnerCannotRefreshQueuedRun() {
        connect(owner);
        UUID disconnected = sync.dispatchAcademicSync(owner.getId(), SyncJobDispatcher.Trigger.MANUAL).jobId();
        portal.disconnect(owner.getId());
        worker.runOnce();
        assertThat(runs.owned(owner.getId(), disconnected).orElseThrow().status()).isEqualTo(SyncRun.Status.FAILED);
        verify(http, never()).fetchCurricula(any());

        var another = users.save(new AppUser(UUID.randomUUID() + "@example.test", "!synthetic", null));
        connect(another);
        UUID disabled = sync.dispatchAcademicSync(another.getId(), SyncJobDispatcher.Trigger.MANUAL).jobId();
        jdbc.update("update app_user set account_status = 'DISABLED' where id = ?", another.getId());
        worker.runOnce();
        assertThat(runs.owned(another.getId(), disabled).orElseThrow().status()).isEqualTo(SyncRun.Status.FAILED);
    }

    @Test void atomicClaimAndStaleRecoveryHandleCrashBeforeLock() throws Exception {
        connect(owner);
        UUID id = sync.dispatchAcademicSync(owner.getId(), SyncJobDispatcher.Trigger.MANUAL).jobId();
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var gate = new CountDownLatch(1);
            var a = pool.submit(() -> { gate.await(); return runs.claim(Instant.now()); });
            var b = pool.submit(() -> { gate.await(); return runs.claim(Instant.now()); });
            gate.countDown();
            assertThat(List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS)).stream().filter(java.util.Optional::isPresent)).hasSize(1);
        }
        var claimed = runs.owned(owner.getId(), id).orElseThrow();
        assertThat(claimed.status()).isEqualTo(SyncRun.Status.RUNNING);
        assertThatThrownBy(() -> runs.finish(id, claimed.attemptCount(), SyncRun.Status.SUCCEEDED, null, Instant.now()))
                .isInstanceOf(DataIntegrityViolationException.class);
        runs.step(id, claimed.attemptCount(), SyncRun.Step.PROFILE, SyncRun.StepStatus.SUCCEEDED, Instant.now());
        runs.step(id, claimed.attemptCount(), SyncRun.Step.CURRICULUM, SyncRun.StepStatus.SUCCEEDED, Instant.now());
        assertThat(runs.finish(id, claimed.attemptCount(), SyncRun.Status.SUCCEEDED, null, Instant.now())).isTrue();
        assertThat(runs.finish(id, claimed.attemptCount(), SyncRun.Status.FAILED, SyncRun.FailureCode.INTERNAL_ERROR, Instant.now())).isFalse();

        UUID second = sync.dispatchAcademicSync(owner.getId(), SyncJobDispatcher.Trigger.SCHEDULED).jobId();
        var stale = runs.claim(Instant.now()).orElseThrow();
        assertThat(stale.id()).isEqualTo(second);
        jdbc.update("update sync_run set heartbeat_at = now() - interval '2 minutes' where id = ?", second);
        assertThat(worker.recoverStale()).isEqualTo(1);
        assertThat(runs.owned(owner.getId(), second).orElseThrow().status()).isEqualTo(SyncRun.Status.QUEUED);
        assertThat(runs.finish(second, stale.attemptCount(), SyncRun.Status.SUCCEEDED, null, Instant.now())).isFalse();
        assertThatThrownBy(() -> new TransactionTemplate(transactions).execute(status -> {
            runs.assertClaimForWrite(second, stale.attemptCount()); return null;
        })).hasMessage("SYNC_LEASE_LOST");
        jdbc.update("update sync_run set status = 'RUNNING', attempt_count = 3, heartbeat_at = now() - interval '2 minutes' where id = ?", second);
        assertThat(worker.recoverStale()).isEqualTo(1);
        assertThat(runs.owned(owner.getId(), second).orElseThrow().status()).isEqualTo(SyncRun.Status.FAILED);
    }

    @Test void twoWorkersCannotProcessTheSameRun() throws Exception {
        connect(owner);
        clearInvocations(http);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(http.fetchProfile(any())).thenAnswer(invocation -> {
            entered.countDown();
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            return new ProfileObservation("SYNTHETIC-001", "Ngành giả định");
        });
        UUID id = sync.dispatchAcademicSync(owner.getId(), SyncJobDispatcher.Trigger.MANUAL).jobId();
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = pool.submit(worker::runOnce);
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            var second = pool.submit(worker::runOnce);
            assertThat(second.get(5, TimeUnit.SECONDS)).isFalse();
            release.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(runs.owned(owner.getId(), id).orElseThrow().status()).isEqualTo(SyncRun.Status.SUCCEEDED);
        verify(http, times(1)).fetchProfile(any());
    }

    @Test void redisLockRejectsWrongOwnerAndExpires() throws Exception {
        UUID user = UUID.randomUUID();
        String first = lock.acquire(user);
        assertThat(first).isNotNull();
        assertThat(lock.acquire(user)).isNull();
        assertThat(lock.release(user, "wrong-owner")).isFalse();
        assertThat(lock.renew(user, first)).isTrue();
        assertThat(lock.release(user, first)).isTrue();
        String second = lock.acquire(user);
        assertThat(second).isNotNull();
        assertThat(lock.release(user, second)).isTrue();
        UUID expiring = UUID.randomUUID();
        var shortLock = new RedisSyncLock(redis, Duration.ofSeconds(1));
        assertThat(shortLock.acquire(expiring)).isNotNull();
        Thread.sleep(1200);
        assertThat(shortLock.acquire(expiring)).isNotNull();
    }
}
