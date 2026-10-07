package vn.edu.phenikaa.ams.academic.api;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.phenikaa.ams.academic.application.AcademicSourceQueryService;
import vn.edu.phenikaa.ams.academic.application.AcademicSourceQueryService.*;
import vn.edu.phenikaa.ams.auth.application.AccountPrincipal;

@RestController
@RequestMapping("/api/me/academic/source")
@ConditionalOnProperty(name = "ams.phenikaa.enabled", havingValue = "true")
public class AcademicSourceController {
    private final AcademicSourceQueryService queries;

    public AcademicSourceController(AcademicSourceQueryService queries) { this.queries = queries; }

    @Operation(summary = "Trạng thái hỗ trợ dữ liệu học vụ", description = "Cho biết phần đã lưu, phần chỉ đọc trực tiếp và giới hạn nguồn.")
    @GetMapping("/status")
    public SourceStatus status(@AuthenticationPrincipal AccountPrincipal principal) {
        return queries.status(principal.getUserId());
    }

    @Operation(summary = "Các chương trình học có thể tra cứu", description = "Danh sách từ nguồn; độ đầy đủ chưa được xác nhận.")
    @GetMapping("/programs")
    public ProgramsView programs(@AuthenticationPrincipal AccountPrincipal principal) {
        return queries.programs(principal.getUserId());
    }

    @Operation(summary = "Đọc tổng hợp tích lũy do nguồn báo", description = "Chỉ đọc trực tiếp cho chương trình theo dõi đã ánh xạ; AMS không tự tính điểm hay tín chỉ.")
    @GetMapping("/progress-summary")
    public ProgressSummaryView progressSummary(@AuthenticationPrincipal AccountPrincipal principal) {
        return queries.progressSummary(principal.getUserId());
    }

    @Operation(summary = "Đọc kết quả học tập trực tiếp", description = "Chỉ là dữ liệu quan sát; không tạo lần học hay kết quả đã lưu.")
    @GetMapping("/records")
    public RecordsView records(@AuthenticationPrincipal AccountPrincipal principal,
                               @RequestParam String programRef) {
        return queries.records(principal.getUserId(), programRef);
    }

    @Operation(summary = "Đọc các điểm thành phần của kết quả", description = "Reference được đối chiếu lại với dữ liệu của tài khoản hiện tại.")
    @GetMapping("/records/{detailRef}/detail")
    public DetailView detail(@AuthenticationPrincipal AccountPrincipal principal,
                             @RequestParam String programRef, @PathVariable String detailRef) {
        return queries.detail(principal.getUserId(), programRef, detailRef);
    }

    @Operation(summary = "Đọc lịch cá nhân trực tiếp", description = "Chỉ đọc một khoảng 1–31 ngày; độ đầy đủ và định danh từng buổi chưa xác minh.")
    @GetMapping("/schedule")
    public ScheduleView schedule(@AuthenticationPrincipal AccountPrincipal principal,
                                 @RequestParam String from, @RequestParam String through) {
        return queries.schedule(principal.getUserId(), from, through);
    }

    @Operation(summary = "Bộ lọc kỳ thi từ nguồn", description = "Các lựa chọn chỉ dùng để đọc lịch thi, không phải học kỳ AMS.")
    @GetMapping("/exams/periods")
    public ExamPeriodsView examPeriods(@AuthenticationPrincipal AccountPrincipal principal) {
        return queries.examPeriods(principal.getUserId());
    }

    @Operation(summary = "Đọc lịch thi trực tiếp", description = "Reference kỳ thi được đối chiếu lại với danh sách của tài khoản hiện tại.")
    @GetMapping("/exams")
    public ExamsView exams(@AuthenticationPrincipal AccountPrincipal principal, @RequestParam String periodRef) {
        return queries.exams(principal.getUserId(), periodRef);
    }
}
