package vn.edu.phenikaa.ams.sync.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.edu.phenikaa.ams.sync.domain.SyncRun;
import vn.edu.phenikaa.ams.sync.domain.SyncRun.*;

public class SyncRunStore {
    private final JdbcTemplate jdbc;
    public SyncRunStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<SyncRun> active(UUID userId) {
        return jdbc.query("select * from sync_run where user_id = ? and status in ('QUEUED','RUNNING')",
                SyncRunStore::map, userId).stream().findFirst();
    }

    public Optional<SyncRun> latest(UUID userId) {
        return jdbc.query("select * from sync_run where user_id = ? order by requested_at desc, id desc limit 1",
                SyncRunStore::map, userId).stream().findFirst();
    }

    public Optional<SyncRun> owned(UUID userId, UUID runId) {
        return jdbc.query("select * from sync_run where user_id = ? and id = ?", SyncRunStore::map,
                userId, runId).stream().findFirst();
    }

    public SyncRun enqueue(UUID userId, Trigger trigger, Instant now) {
        UUID id = UUID.randomUUID();
        int inserted = jdbc.update("""
                insert into sync_run(id,user_id,trigger_type,status,requested_at,next_attempt_at,updated_at)
                values (?, ?, ?, 'QUEUED', ?, ?, ?) on conflict do nothing
                """, id, userId, trigger.name(), time(now), time(now), time(now));
        if (inserted == 0) return active(userId).orElseThrow();
        return owned(userId, id).orElseThrow();
    }

    public Optional<SyncRun> claim(Instant now) {
        return jdbc.query("""
                update sync_run set status = 'RUNNING', started_at = ?, heartbeat_at = ?,
                    attempt_count = attempt_count + 1, updated_at = ?, failure_code = null
                where id = (select id from sync_run where status = 'QUEUED' and next_attempt_at <= ?
                    order by next_attempt_at, requested_at for update skip locked limit 1)
                returning *
                """, SyncRunStore::map, time(now), time(now), time(now), time(now)).stream().findFirst();
    }

    public boolean heartbeat(UUID id, int attempt, Instant now) {
        return jdbc.update("update sync_run set heartbeat_at = ?, updated_at = ? where id = ? and status = 'RUNNING' and attempt_count = ?",
                time(now), time(now), id, attempt) == 1;
    }

    public void assertClaimForWrite(UUID id, int attempt) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new SyncLeaseLostException();
        var rows = jdbc.query("select status, attempt_count from sync_run where id = ? for update",
                (rs, row) -> rs.getString(1).equals("RUNNING") && rs.getInt(2) == attempt, id);
        if (rows.size() != 1 || !rows.getFirst()) throw new SyncLeaseLostException();
    }

    public boolean step(UUID id, int attempt, Step step, StepStatus result, Instant now) {
        String column = step == Step.PROFILE ? "profile_step_status" : "curriculum_step_status";
        return jdbc.update("update sync_run set current_step = ?, " + column
                + " = case when ? = 'PENDING' then " + column + " else ? end, updated_at = ? "
                + "where id = ? and status = 'RUNNING' and attempt_count = ?",
                step.name(), result.name(), result.name(), time(now), id, attempt) == 1;
    }

    public boolean finish(UUID id, int attempt, Status status, FailureCode failure, Instant now) {
        if (status != Status.SUCCEEDED && status != Status.PARTIAL && status != Status.FAILED)
            throw new IllegalArgumentException("Not a final sync status");
        return jdbc.update("""
                update sync_run set status = ?, failure_code = ?, finished_at = ?, updated_at = ?, current_step = null
                where id = ? and status = 'RUNNING' and attempt_count = ?
                """, status.name(), failure == null ? null : failure.name(), time(now), time(now), id, attempt) == 1;
    }

    public boolean retry(UUID id, int attempt, FailureCode failure, Instant next, Instant now) {
        return jdbc.update("""
                update sync_run set status = 'QUEUED', failure_code = ?, next_attempt_at = ?,
                    updated_at = ?, current_step = null
                where id = ? and status = 'RUNNING' and attempt_count = ?
                """, failure.name(), time(next), time(now), id, attempt) == 1;
    }

    public List<SyncRun> staleBefore(Instant cutoff, int limit) {
        return jdbc.query("select * from sync_run where status = 'RUNNING' and heartbeat_at < ? "
                + "order by heartbeat_at limit ?", SyncRunStore::map, time(cutoff), limit);
    }

    public boolean recover(SyncRun run, Instant cutoff, Instant next, Instant now, int maxAttempts) {
        boolean exhausted = run.attemptCount() >= maxAttempts;
        Status target = !exhausted ? Status.QUEUED
                : run.profileStepStatus() == StepStatus.SUCCEEDED && run.curriculumStepStatus() == StepStatus.SUCCEEDED
                    ? Status.SUCCEEDED
                    : run.profileStepStatus() == StepStatus.SUCCEEDED ? Status.PARTIAL : Status.FAILED;
        return jdbc.update("""
                update sync_run set status = ?, failure_code = ?,
                    curriculum_step_status = case when ? = 'PARTIAL' then 'FAILED' else curriculum_step_status end,
                    finished_at = ?, next_attempt_at = ?, updated_at = ?, current_step = null
                where id = ? and status = 'RUNNING' and attempt_count = ? and heartbeat_at < ?
                """, target.name(), target == Status.SUCCEEDED ? null : FailureCode.LOCK_UNAVAILABLE.name(),
                target.name(), exhausted ? time(now) : null, time(next), time(now),
                run.id(), run.attemptCount(), time(cutoff)) == 1;
    }

    private static Timestamp time(Instant instant) { return Timestamp.from(instant); }
    private static Instant instant(ResultSet rs, String field) throws SQLException {
        var value = rs.getTimestamp(field);
        return value == null ? null : value.toInstant();
    }
    private static SyncRun map(ResultSet rs, int row) throws SQLException {
        String step = rs.getString("current_step"), failure = rs.getString("failure_code");
        return new SyncRun(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class),
                Trigger.valueOf(rs.getString("trigger_type")), Status.valueOf(rs.getString("status")),
                instant(rs, "requested_at"), instant(rs, "started_at"), instant(rs, "finished_at"),
                instant(rs, "next_attempt_at"), step == null ? null : Step.valueOf(step),
                StepStatus.valueOf(rs.getString("profile_step_status")),
                StepStatus.valueOf(rs.getString("curriculum_step_status")),
                failure == null ? null : FailureCode.valueOf(failure), rs.getInt("attempt_count"));
    }
}
