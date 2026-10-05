package vn.edu.phenikaa.ams.academic.api;

import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.phenikaa.ams.auth.application.AccountPrincipal;
import vn.edu.phenikaa.ams.academic.application.CatalogPageRequest;
import vn.edu.phenikaa.ams.academic.application.CurriculumQueryService;
import vn.edu.phenikaa.ams.academic.application.CurriculumQueryService.*;

@RestController
@RequestMapping("/api/me/academic")
public class CurriculumController {
    private final CurriculumQueryService queries;
    public CurriculumController(CurriculumQueryService queries) { this.queries = queries; }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail invalidParameter() {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_CATALOG_QUERY", "Tham số truy vấn không hợp lệ.");
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    public ProblemDetail unavailable() {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "CATALOG_UNAVAILABLE", "Chưa thể đọc dữ liệu đã lưu. Vui lòng thử lại.");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail queryError(ResponseStatusException exception) {
        return exception.getStatusCode().value() == 404
                ? problem(HttpStatus.NOT_FOUND, "CURRICULUM_NOT_FOUND", "Không tìm thấy chương trình đào tạo.")
                : problem(HttpStatus.BAD_REQUEST, "INVALID_CATALOG_QUERY", "Giới hạn trang, từ khóa hoặc con trỏ không hợp lệ.");
    }

    private static ProblemDetail problem(HttpStatus status, String code, String message) {
        var result = ProblemDetail.forStatusAndDetail(status, message);
        result.setProperty("code", code);
        return result;
    }

    @GetMapping("/curricula")
    public Page<CurriculumView> curricula(@AuthenticationPrincipal AccountPrincipal principal,
            @RequestParam(required = false) Integer limit, @RequestParam(required = false) String cursor) {
        return queries.curricula(principal.getUserId(), CatalogPageRequest.parse(limit, null, cursor));
    }

    @GetMapping("/curricula/{curriculumId}")
    public CurriculumDetail detail(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID curriculumId,
            @RequestParam(required = false) Integer limit, @RequestParam(required = false) String cursor) {
        return queries.detail(principal.getUserId(), curriculumId, CatalogPageRequest.parse(limit, null, cursor));
    }

    @GetMapping("/curricula/{curriculumId}/courses")
    public Page<CurriculumCourseView> courses(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID curriculumId,
            @RequestParam(required = false) Integer limit, @RequestParam(required = false) String cursor,
            @RequestParam(required = false) String search) {
        return queries.courses(principal.getUserId(), curriculumId, CatalogPageRequest.parse(limit, search, cursor));
    }

    @GetMapping("/catalog/courses")
    public Page<CatalogCourseView> catalog(@AuthenticationPrincipal AccountPrincipal principal,
            @RequestParam(required = false) Integer limit, @RequestParam(required = false) String cursor,
            @RequestParam(required = false) String search) {
        return queries.catalog(principal.getUserId(), CatalogPageRequest.parse(limit, search, cursor));
    }
}
