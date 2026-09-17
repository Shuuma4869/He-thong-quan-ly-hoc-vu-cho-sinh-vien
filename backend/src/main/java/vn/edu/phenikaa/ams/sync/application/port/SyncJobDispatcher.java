package vn.edu.phenikaa.ams.sync.application.port;

import java.util.UUID;

public interface SyncJobDispatcher {

    DispatchResult dispatchAcademicSync(UUID userId, Trigger trigger);

    record DispatchResult(UUID jobId, Status status) {}

    enum Trigger {
        SCHEDULED,
        MANUAL
    }

    enum Status {
        QUEUED,
        ALREADY_QUEUED
    }
}
