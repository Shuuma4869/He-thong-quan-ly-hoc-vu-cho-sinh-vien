package vn.edu.phenikaa.ams.academic.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.phenikaa.ams.academic.domain.CurriculumCourse.Requirement;
import vn.edu.phenikaa.ams.academic.domain.StudyPlanCourse;
import vn.edu.phenikaa.ams.academic.infrastructure.StudyPlanRepository;
import vn.edu.phenikaa.ams.academic.infrastructure.StudyPlanRepository.ProfileSelection;
import vn.edu.phenikaa.ams.user.domain.AppUser;
import vn.edu.phenikaa.ams.user.infrastructure.UserRepository;
import static vn.edu.phenikaa.ams.academic.application.StudyPlanException.Code.*;

@Service
public class StudyPlanService {
    private static final int MAX_ITEMS = 1000;
    public record CurriculumView(UUID id, String code, String name) {}
    public record CourseView(UUID courseId, String code, String name, BigDecimal credits,
                             Requirement requirement, String groupName) {}
    public record TermView(int plannedTerm, int courseCount, BigDecimal plannedCredits, List<CourseView> courses) {}
    public record PlanView(String mode, int scenarioNo, CurriculumView curriculum, List<TermView> terms) {}
    public record ScenarioView(int scenarioNo, long courseCount, long termCount, BigDecimal plannedCredits) {}
    public record ScenariosView(String mode, CurriculumView curriculum, List<ScenarioView> scenarios) {}

    private final UserRepository users;
    private final StudyPlanRepository plans;
    public StudyPlanService(UserRepository users, StudyPlanRepository plans) {
        this.users = users;
        this.plans = plans;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PlanView current(UUID userId, int scenarioNo) {
        scenario(scenarioNo);
        active(userId);
        var selected = plans.selected(userId).orElse(null);
        if (selected == null) return new PlanView("USER_PLANNED_AMS", scenarioNo, null, List.of());
        var rows = plans.courses(userId, selected.curriculumId(), scenarioNo, MAX_ITEMS + 1);
        if (rows.size() > MAX_ITEMS) throw new StudyPlanException(STUDY_PLAN_TOO_LARGE);
        var grouped = new TreeMap<Integer, List<CourseView>>();
        for (var row : rows) grouped.computeIfAbsent(row.plannedTerm(), ignored -> new ArrayList<>())
                .add(new CourseView(row.courseId(), row.code(), row.name(), row.credits(),
                        row.requirement(), row.groupName()));
        var terms = grouped.entrySet().stream().map(entry -> {
            var courses = List.copyOf(entry.getValue());
            var credits = courses.stream().map(CourseView::credits).reduce(BigDecimal.ZERO, BigDecimal::add);
            return new TermView(entry.getKey(), courses.size(), credits, courses);
        }).toList();
        return new PlanView("USER_PLANNED_AMS", scenarioNo,
                new CurriculumView(selected.curriculumId(), selected.code(), selected.name()), terms);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ScenariosView scenarios(UUID userId) {
        active(userId);
        var selected = plans.selected(userId).orElse(null);
        if (selected == null) return new ScenariosView("USER_PLANNED_AMS", null, List.of());
        var totals = plans.totals(userId, selected.curriculumId());
        if (totals.stream().anyMatch(row -> row.courseCount() > MAX_ITEMS))
            throw new StudyPlanException(STUDY_PLAN_TOO_LARGE);
        var scenarios = new ArrayList<ScenarioView>();
        for (int number = 1; number <= 5; number++) {
            int current = number;
            var row = totals.stream().filter(item -> item.scenarioNo() == current).findFirst().orElse(null);
            scenarios.add(row == null ? new ScenarioView(number, 0, 0, BigDecimal.ZERO)
                    : new ScenarioView(number, row.courseCount(), row.termCount(), row.plannedCredits()));
        }
        return new ScenariosView("USER_PLANNED_AMS",
                new CurriculumView(selected.curriculumId(), selected.code(), selected.name()), List.copyOf(scenarios));
    }

    @Transactional
    public void put(UUID userId, UUID curriculumId, UUID courseId, Integer plannedTerm, int scenarioNo) {
        scenario(scenarioNo);
        int term;
        try { term = StudyPlanCourse.term(plannedTerm == null ? 0 : plannedTerm); }
        catch (IllegalArgumentException ex) { throw new StudyPlanException(INVALID_PLANNED_TERM); }
        var profile = selectedForMutation(userId, curriculumId);
        membership(profile, curriculumId, courseId);
        var existing = plans.assignment(profile.profileId(), curriculumId, scenarioNo, courseId);
        if (existing.isEmpty() && plans.count(profile.profileId(), curriculumId, scenarioNo) >= MAX_ITEMS)
            throw new StudyPlanException(STUDY_PLAN_TOO_LARGE);
        var now = Instant.now();
        var assignment = existing.orElseGet(() -> new StudyPlanCourse(profile.profileId(), curriculumId, scenarioNo, courseId, term, now));
        assignment.moveToTerm(term, now);
        plans.upsert(assignment);
    }

    @Transactional
    public void remove(UUID userId, UUID curriculumId, UUID courseId, int scenarioNo) {
        scenario(scenarioNo);
        var profile = selectedForMutation(userId, curriculumId);
        membership(profile, curriculumId, courseId);
        plans.remove(profile.profileId(), curriculumId, scenarioNo, courseId);
    }

    @Transactional
    public void copy(UUID userId, int sourceScenario, int targetScenario) {
        scenario(sourceScenario);
        scenario(targetScenario);
        if (sourceScenario == targetScenario) throw new StudyPlanException(INVALID_STUDY_PLAN_SCENARIO);
        var profile = selectedForScenarioMutation(userId);
        UUID curriculumId = profile.curriculumId();
        if (plans.count(profile.profileId(), curriculumId, targetScenario) != 0)
            throw new StudyPlanException(STUDY_PLAN_SCENARIO_NOT_EMPTY);
        var source = plans.assignments(profile.profileId(), curriculumId, sourceScenario, MAX_ITEMS + 1);
        if (source.size() > MAX_ITEMS) throw new StudyPlanException(STUDY_PLAN_TOO_LARGE);
        var now = Instant.now();
        plans.insertCopies(source.stream().map(item -> new StudyPlanCourse(profile.profileId(), curriculumId,
                targetScenario, item.courseId(), item.plannedTerm(), now)).toList());
    }

    @Transactional
    public void clear(UUID userId, int scenarioNo) {
        scenario(scenarioNo);
        var profile = selectedForScenarioMutation(userId);
        plans.clear(profile.profileId(), profile.curriculumId(), scenarioNo);
    }

    private ProfileSelection selectedForMutation(UUID userId, UUID curriculumId) {
        active(userId);
        var profile = plans.lockProfile(userId).orElseThrow(() -> new StudyPlanException(CURRICULUM_SELECTION_REQUIRED));
        if (profile.curriculumId() == null) throw new StudyPlanException(CURRICULUM_SELECTION_REQUIRED);
        if (curriculumId != null && !profile.curriculumId().equals(curriculumId))
            throw new StudyPlanException(STUDY_PLAN_SELECTION_CHANGED);
        return profile;
    }

    private ProfileSelection selectedForScenarioMutation(UUID userId) {
        active(userId);
        var selected = plans.selected(userId).orElseThrow(() -> new StudyPlanException(CURRICULUM_SELECTION_REQUIRED));
        return selectedForMutation(userId, selected.curriculumId());
    }

    private void membership(ProfileSelection profile, UUID curriculumId, UUID courseId) {
        if (!plans.hasCourse(profile.profileId(), curriculumId, courseId))
            throw new StudyPlanException(STUDY_PLAN_COURSE_NOT_FOUND);
    }

    private static void scenario(int value) {
        try { StudyPlanCourse.scenario(value); }
        catch (IllegalArgumentException ex) { throw new StudyPlanException(INVALID_STUDY_PLAN_SCENARIO); }
    }

    private void active(UUID userId) {
        users.findById(userId).filter(user -> user.getAccountStatus() == AppUser.Status.ACTIVE)
                .orElseThrow(() -> new AccessDeniedException("Account unavailable"));
    }
}
