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
    public record CopyRequest(BigDecimal sourceScenario) {}

    private final StudyPlanService plans;
    public StudyPlanController(StudyPlanService plans) { this.plans = plans; }

    @GetMapping
    public PlanView current(@AuthenticationPrincipal AccountPrincipal principal,
                            @RequestParam(required = false) String scenario) {
        return plans.current(principal.getUserId(), scenario(scenario));
    }

    @GetMapping("/scenarios")
    public StudyPlanService.ScenariosView scenarios(@AuthenticationPrincipal AccountPrincipal principal) {
        return plans.scenarios(principal.getUserId());
    }

    @GetMapping("/compare")
    public StudyPlanService.ComparisonView compare(@AuthenticationPrincipal AccountPrincipal principal,
                                                    @RequestParam(required = false) String left,
                                                    @RequestParam(required = false) String right) {
        return plans.compare(principal.getUserId(), comparisonScenario(left), comparisonScenario(right));
    }

    @PutMapping("/curricula/{curriculumId}/courses/{courseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void put(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID curriculumId,
                    @PathVariable UUID courseId, @RequestParam(required = false) String scenario,
                    @RequestBody PlanCourseRequest request) {
        int term;
        try { term = request.plannedTerm() == null ? 0 : request.plannedTerm().intValueExact(); }
        catch (ArithmeticException ex) { throw new StudyPlanException(StudyPlanException.Code.INVALID_PLANNED_TERM); }
        plans.put(principal.getUserId(), curriculumId, courseId, term, scenario(scenario));
    }

    @DeleteMapping("/curricula/{curriculumId}/courses/{courseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID curriculumId,
                       @PathVariable UUID courseId, @RequestParam(required = false) String scenario) {
        plans.remove(principal.getUserId(), curriculumId, courseId, scenario(scenario));
    }

    @PostMapping("/scenarios/{targetScenario}/copy")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void copy(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable String targetScenario,
                     @RequestBody CopyRequest request) {
        int source;
        try {
            if (request.sourceScenario() == null || request.sourceScenario().scale() != 0)
                throw new ArithmeticException();
            source = request.sourceScenario().intValueExact();
        } catch (ArithmeticException ex) {
            throw new StudyPlanException(StudyPlanException.Code.INVALID_STUDY_PLAN_SCENARIO);
        }
        plans.copy(principal.getUserId(), source, scenario(targetScenario));
    }

    @DeleteMapping("/scenarios/{scenarioNo}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable String scenarioNo) {
        plans.clear(principal.getUserId(), scenario(scenarioNo));
    }

    private static int scenario(String value) {
        if (value == null) return 1;
        if (!value.matches("[1-5]"))
            throw new StudyPlanException(StudyPlanException.Code.INVALID_STUDY_PLAN_SCENARIO);
        return Integer.parseInt(value);
    }

    private static int comparisonScenario(String value) {
        if (value == null || !value.matches("[1-5]"))
            throw new StudyPlanException(StudyPlanException.Code.INVALID_STUDY_PLAN_COMPARISON);
        return Integer.parseInt(value);
    }
}
