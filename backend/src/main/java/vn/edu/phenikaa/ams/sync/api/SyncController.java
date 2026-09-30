package vn.edu.phenikaa.ams.sync.api;

import java.time.Instant;
import java.util.UUID;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.edu.phenikaa.ams.auth.application.AccountPrincipal;
import vn.edu.phenikaa.ams.sync.application.SyncService;
import vn.edu.phenikaa.ams.sync.application.port.SyncJobDispatcher;
import vn.edu.phenikaa.ams.sync.domain.SyncRun;

@RestController
@RequestMapping("/api/me/sync")
@ConditionalOnProperty(name = "ams.phenikaa.enabled", havingValue = "true")
public class SyncController {
    private final SyncService sync;
    public SyncController(SyncService sync) { this.sync = sync; }

    @Operation(summary = "Yêu cầu làm mới hồ sơ và chương trình đã hỗ trợ")
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public RunView enqueue(@AuthenticationPrincipal AccountPrincipal principal) {
        var result = sync.dispatchAcademicSync(principal.getUserId(), SyncJobDispatcher.Trigger.MANUAL);
        return view(sync.owned(principal.getUserId(), result.jobId()));
    }

    @Operation(summary = "Lượt đồng bộ gần nhất")
    @GetMapping("/current")
    public RunView current(@AuthenticationPrincipal AccountPrincipal principal) {
        return view(sync.current(principal.getUserId()));
    }

    @Operation(summary = "Trạng thái một lượt đồng bộ của tài khoản hiện tại")
    @GetMapping("/runs/{runId}")
    public RunView run(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID runId) {
        return view(sync.owned(principal.getUserId(), runId));
    }

    private static RunView view(SyncRun run) {
        return new RunView(run.id(), run.status().name(), run.trigger().name(), run.requestedAt(),
                run.startedAt(), run.finishedAt(), run.nextAttemptAt(),
                run.currentStep() == null ? null : run.currentStep().name(),
                run.profileStepStatus().name(), run.curriculumStepStatus().name(),
                run.failureCode() == null ? null : run.failureCode().name(), run.attemptCount());
    }

    public record RunView(UUID runId, String status, String trigger, Instant requestedAt, Instant startedAt,
                          Instant finishedAt, Instant nextAttemptAt, String currentStep,
                          String profileStepStatus, String curriculumStepStatus, String failureCode,
                          int attemptCount) {}
}
