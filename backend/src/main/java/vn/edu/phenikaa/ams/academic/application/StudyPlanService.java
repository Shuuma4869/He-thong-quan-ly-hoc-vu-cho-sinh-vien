package vn.edu.phenikaa.ams.academic.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.phenikaa.ams.academic.domain.CurriculumCourse.Requirement;
import vn.edu.phenikaa.ams.academic.domain.StudyPlanCourse;
import vn.edu.phenikaa.ams.academic.infrastructure.StudyPlanRepository;
import vn.edu.phenikaa.ams.academic.infrastructure.StudyPlanRepository.PlannedCourse;
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
    public enum CourseChange { UNCHANGED, MOVED, ONLY_LEFT, ONLY_RIGHT }
    public record TermComparisonView(int plannedTerm, int leftCourseCount, BigDecimal leftPlannedCredits,
                                     int rightCourseCount, BigDecimal rightPlannedCredits) {}
    public record CourseComparisonView(UUID courseId, String code, String name, BigDecimal credits,
                                       Requirement requirement, String groupName, Integer leftPlannedTerm,
                                       Integer rightPlannedTerm, CourseChange change) {}
    public record ComparisonView(String mode, CurriculumView curriculum, int leftScenario, int rightScenario,
                                 ScenarioView left, ScenarioView right, List<TermComparisonView> terms,
                                 List<CourseComparisonView> courses) {}
    private record TermTotals(int count, BigDecimal credits) {
        TermTotals plus(TermTotals other) {
            return new TermTotals(count + other.count, credits.add(other.credits));
        }
    }

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

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ComparisonView compare(UUID userId, int leftScenario, int rightScenario) {
        comparisonScenarios(leftScenario, rightScenario);
        active(userId);
        var selected = plans.selected(userId).orElse(null);
        if (selected == null) return compareRows(null, leftScenario, rightScenario, List.of(), List.of());
        var left = plans.courses(userId, selected.curriculumId(), leftScenario, MAX_ITEMS + 1);
        if (left.size() > MAX_ITEMS) throw new StudyPlanException(STUDY_PLAN_TOO_LARGE);
        var right = plans.courses(userId, selected.curriculumId(), rightScenario, MAX_ITEMS + 1);
        if (right.size() > MAX_ITEMS) throw new StudyPlanException(STUDY_PLAN_TOO_LARGE);
        return compareRows(new CurriculumView(selected.curriculumId(), selected.code(), selected.name()),
                leftScenario, rightScenario, left, right);
    }

    static ComparisonView compareRows(CurriculumView curriculum, int leftScenario, int rightScenario,
                                      List<PlannedCourse> leftRows, List<PlannedCourse> rightRows) {
        comparisonScenarios(leftScenario, rightScenario);
        if (leftRows.size() > MAX_ITEMS || rightRows.size() > MAX_ITEMS)
            throw new StudyPlanException(STUDY_PLAN_TOO_LARGE);
        var leftById = index(leftRows);
        var rightById = index(rightRows);
        var leftTerms = totalsByTerm(leftRows);
        var rightTerms = totalsByTerm(rightRows);
        var terms = new TreeSet<>(leftTerms.keySet());
        terms.addAll(rightTerms.keySet());
        var termViews = terms.stream().map(term -> {
            var left = leftTerms.getOrDefault(term, new TermTotals(0, BigDecimal.ZERO));
            var right = rightTerms.getOrDefault(term, new TermTotals(0, BigDecimal.ZERO));
            return new TermComparisonView(term, left.count(), left.credits(), right.count(), right.credits());
        }).toList();
        var courseIds = new HashSet<>(leftById.keySet());
        courseIds.addAll(rightById.keySet());
        var courseViews = courseIds.stream().map(courseId -> {
            var left = leftById.get(courseId);
            var right = rightById.get(courseId);
            var details = left == null ? right : left;
            Integer leftTerm = left == null ? null : left.plannedTerm();
            Integer rightTerm = right == null ? null : right.plannedTerm();
            var change = left == null ? CourseChange.ONLY_RIGHT
                    : right == null ? CourseChange.ONLY_LEFT
                    : leftTerm.equals(rightTerm) ? CourseChange.UNCHANGED : CourseChange.MOVED;
            return new CourseComparisonView(courseId, details.code(), details.name(), details.credits(),
                    details.requirement(), details.groupName(), leftTerm, rightTerm, change);
        }).sorted(Comparator.comparing(CourseComparisonView::code).thenComparing(CourseComparisonView::courseId))
                .toList();
        return new ComparisonView("USER_PLANNED_AMS", curriculum, leftScenario, rightScenario,
                summary(leftScenario, leftById.size(), leftTerms), summary(rightScenario, rightById.size(), rightTerms),
                termViews, courseViews);
    }

    private static Map<UUID, PlannedCourse> index(List<PlannedCourse> rows) {
        var byId = new HashMap<UUID, PlannedCourse>();
        for (var row : rows)
            if (byId.putIfAbsent(row.courseId(), row) != null)
                throw new IllegalStateException("Duplicate study-plan course");
        return byId;
    }

    private static TreeMap<Integer, TermTotals> totalsByTerm(List<PlannedCourse> rows) {
        var totals = new TreeMap<Integer, TermTotals>();
        for (var row : rows)
            totals.merge(row.plannedTerm(), new TermTotals(1, row.credits()), TermTotals::plus);
        return totals;
    }

    private static ScenarioView summary(int number, int count, Map<Integer, TermTotals> terms) {
        var credits = terms.values().stream().map(TermTotals::credits).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ScenarioView(number, count, terms.size(), credits);
    }

    private static void comparisonScenarios(int left, int right) {
        if (left == right || left < 1 || left > 5 || right < 1 || right > 5)
            throw new StudyPlanException(INVALID_STUDY_PLAN_COMPARISON);
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
