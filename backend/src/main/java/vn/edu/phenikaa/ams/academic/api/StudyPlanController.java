package vn.edu.phenikaa.ams.academic.api;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.edu.phenikaa.ams.academic.application.StudyPlanException;
import vn.edu.phenikaa.ams.academic.application.StudyPlanService;
import vn.edu.phenikaa.ams.academic.application.StudyPlanService.PlanView;
import vn.edu.phenikaa.ams.auth.application.AccountPrincipal;

@RestController
@RequestMapping("/api/me/academic/study-plan")
public class StudyPlanController {
    public record PlanCourseRequest(BigDecimal plannedTerm) {}

    private final StudyPlanService plans;
    public StudyPlanController(StudyPlanService plans) { this.plans = plans; }

    @GetMapping
    public PlanView current(@AuthenticationPrincipal AccountPrincipal principal) {
        return plans.current(principal.getUserId());
    }

    @PutMapping("/curricula/{curriculumId}/courses/{courseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void put(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID curriculumId,
                    @PathVariable UUID courseId, @RequestBody PlanCourseRequest request) {
        int term;
        try { term = request.plannedTerm() == null ? 0 : request.plannedTerm().intValueExact(); }
        catch (ArithmeticException ex) { throw new StudyPlanException(StudyPlanException.Code.INVALID_PLANNED_TERM); }
        plans.put(principal.getUserId(), curriculumId, courseId, term);
    }

    @DeleteMapping("/curricula/{curriculumId}/courses/{courseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID curriculumId,
                       @PathVariable UUID courseId) {
        plans.remove(principal.getUserId(), curriculumId, courseId);
    }
}
