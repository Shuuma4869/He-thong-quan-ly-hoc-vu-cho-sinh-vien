package vn.edu.phenikaa.ams.sync.domain;

import java.time.Instant;
import java.util.UUID;

public record SyncRun(UUID id, UUID userId, Trigger trigger, Status status, Instant requestedAt,
                      Instant startedAt, Instant finishedAt, Instant nextAttemptAt, Step currentStep,
                      StepStatus profileStepStatus, StepStatus curriculumStepStatus,
                      FailureCode failureCode, int attemptCount) {
    public enum Trigger { MANUAL, SCHEDULED }
    public enum Status { QUEUED, RUNNING, SUCCEEDED, PARTIAL, FAILED }
    public enum Step { PROFILE, CURRICULUM }
    public enum StepStatus { PENDING, SUCCEEDED, FAILED }
    public enum FailureCode {
        CONNECTION_NOT_FOUND, RECONNECTION_REQUIRED, SOURCE_TIMEOUT, SOURCE_UNAVAILABLE,
        SOURCE_SCHEMA_CHANGED, PROFILE_REFRESH_FAILED, CURRICULUM_REFRESH_FAILED,
        LOCK_UNAVAILABLE, INTERNAL_ERROR
    }
    public boolean active() { return status == Status.QUEUED || status == Status.RUNNING; }
}
