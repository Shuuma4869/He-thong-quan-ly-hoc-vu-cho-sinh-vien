package vn.edu.phenikaa.ams.academic.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.phenikaa.ams.academic.application.CurriculumSelectionService;
import vn.edu.phenikaa.ams.academic.application.CurriculumSelectionService.SelectionView;
import vn.edu.phenikaa.ams.auth.application.AccountPrincipal;

@RestController
@RequestMapping("/api/me/academic/curriculum-selection")
public class CurriculumSelectionController {
    public record SelectionRequest(@NotNull UUID curriculumId) {}

    private final CurriculumSelectionService selections;
    public CurriculumSelectionController(CurriculumSelectionService selections) { this.selections = selections; }

    @GetMapping
    public SelectionView current(@AuthenticationPrincipal AccountPrincipal principal) {
        return selections.current(principal.getUserId());
    }

    @PutMapping
    public SelectionView select(@AuthenticationPrincipal AccountPrincipal principal, @Valid @RequestBody SelectionRequest request) {
        return selections.select(principal.getUserId(), request.curriculumId());
    }

    @DeleteMapping
    public SelectionView clear(@AuthenticationPrincipal AccountPrincipal principal) {
        return selections.clear(principal.getUserId());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail notFound() {
        return problem(HttpStatus.NOT_FOUND, "CURRICULUM_NOT_FOUND", "Không tìm thấy chương trình trong dữ liệu đã lưu.");
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    public ProblemDetail unavailable() {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "CURRICULUM_SELECTION_UNAVAILABLE", "Chưa thể truy cập chương trình theo dõi. Vui lòng thử lại.");
    }

    private static ProblemDetail problem(HttpStatus status, String code, String message) {
        var result = ProblemDetail.forStatusAndDetail(status, message);
        result.setProperty("code", code);
        return result;
    }
}
