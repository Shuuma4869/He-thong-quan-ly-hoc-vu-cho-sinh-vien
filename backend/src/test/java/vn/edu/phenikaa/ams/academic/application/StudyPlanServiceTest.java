package vn.edu.phenikaa.ams.academic.application;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.phenikaa.ams.academic.domain.CurriculumCourse.Requirement;
import vn.edu.phenikaa.ams.academic.infrastructure.StudyPlanRepository;
import vn.edu.phenikaa.ams.user.domain.AppUser;
import vn.edu.phenikaa.ams.user.infrastructure.UserRepository;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class StudyPlanServiceTest {
    private StudyPlanRepository.PlannedCourse row(UUID id, String code, String credits, int term) {
        return new StudyPlanRepository.PlannedCourse(id, code, "Môn kiểm thử " + code,
                new BigDecimal(credits), Requirement.REQUIRED, null, term);
    }

    @Test void classifiesCoursesAndBuildsSortedExactTotals() {
        UUID unchanged = UUID.randomUUID(), moved = UUID.randomUUID();
        UUID onlyLeft = UUID.randomUUID(), onlyRight = UUID.randomUUID();
        var result = StudyPlanService.compareRows(
                new StudyPlanService.CurriculumView(UUID.randomUUID(), "CURR-A", "Chương trình kiểm thử"),
                1, 2,
                List.of(row(moved, "TEST102", "4.10", 2), row(onlyLeft, "TEST103", "2.00", 1),
                        row(unchanged, "TEST101", "3.25", 1)),
                List.of(row(onlyRight, "TEST104", "1.50", 3), row(unchanged, "TEST101", "3.25", 1),
                        row(moved, "TEST102", "4.10", 3)));
        assertThat(result.mode()).isEqualTo("USER_PLANNED_AMS");
        assertThat(result.courses()).extracting(StudyPlanService.CourseComparisonView::code)
                .containsExactly("TEST101", "TEST102", "TEST103", "TEST104");
        assertThat(result.courses()).extracting(StudyPlanService.CourseComparisonView::change)
                .containsExactly(StudyPlanService.CourseChange.UNCHANGED, StudyPlanService.CourseChange.MOVED,
                        StudyPlanService.CourseChange.ONLY_LEFT, StudyPlanService.CourseChange.ONLY_RIGHT);
        assertThat(result.courses().get(1).leftPlannedTerm()).isEqualTo(2);
        assertThat(result.courses().get(1).rightPlannedTerm()).isEqualTo(3);
        assertThat(result.terms()).extracting(StudyPlanService.TermComparisonView::plannedTerm)
                .containsExactly(1, 2, 3);
        assertThat(result.terms().get(1).leftPlannedCredits()).isEqualByComparingTo("4.10");
        assertThat(result.terms().get(1).rightPlannedCredits()).isEqualByComparingTo("0");
        assertThat(result.left().courseCount()).isEqualTo(3);
        assertThat(result.left().termCount()).isEqualTo(2);
        assertThat(result.left().plannedCredits()).isEqualByComparingTo("9.35");
        assertThat(result.right().courseCount()).isEqualTo(3);
        assertThat(result.right().termCount()).isEqualTo(2);
        assertThat(result.right().plannedCredits()).isEqualByComparingTo("8.85");
    }

    @Test void handlesEmptySidesAndRejectsDuplicateIdentityOrInvalidPair() {
        var course = row(UUID.randomUUID(), "TEST101", "3.00", 2);
        var empty = StudyPlanService.compareRows(null, 1, 2, List.of(), List.of());
        assertThat(empty.left().courseCount()).isZero();
        assertThat(empty.right().plannedCredits()).isEqualByComparingTo("0");
        assertThat(empty.terms()).isEmpty();
        assertThat(empty.courses()).isEmpty();
        var oneSide = StudyPlanService.compareRows(null, 1, 2, List.of(), List.of(course));
        assertThat(oneSide.courses().getFirst().change()).isEqualTo(StudyPlanService.CourseChange.ONLY_RIGHT);
        assertThat(oneSide.terms().getFirst().leftCourseCount()).isZero();
        assertThatThrownBy(() -> StudyPlanService.compareRows(null, 1, 2, List.of(course, course), List.of()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> StudyPlanService.compareRows(null, 2, 2, List.of(), List.of()))
                .isInstanceOf(StudyPlanException.class).hasMessage("INVALID_STUDY_PLAN_COMPARISON");
    }

    @Test void ordersMatchingCourseCodesByInternalId() {
        var first = UUID.fromString("00000000-0000-4000-8000-000000000001");
        var second = UUID.fromString("00000000-0000-4000-8000-000000000002");
        var result = StudyPlanService.compareRows(null, 1, 2,
                List.of(row(second, "TEST101", "2", 1), row(first, "TEST101", "3", 2)), List.of());
        assertThat(result.courses()).extracting(StudyPlanService.CourseComparisonView::courseId)
                .containsExactly(first, second);
    }

    @Test void compareUsesOneRepeatableReadTransactionAndBoundsEachScenario() throws Exception {
        var transaction = StudyPlanService.class.getMethod("compare", UUID.class, int.class, int.class)
                .getAnnotation(Transactional.class);
        assertThat(transaction.readOnly()).isTrue();
        assertThat(transaction.isolation()).isEqualTo(Isolation.REPEATABLE_READ);
        var users = mock(UserRepository.class);
        var plans = mock(StudyPlanRepository.class);
        var account = new AppUser(UUID.randomUUID() + "@example.test", "!synthetic", null);
        UUID userId = account.getId(), profile = UUID.randomUUID(), curriculum = UUID.randomUUID();
        when(users.findById(userId)).thenReturn(Optional.of(account));
        when(plans.selected(userId)).thenReturn(Optional.of(new StudyPlanRepository.Selected(
                profile, curriculum, "CURR-A", "Chương trình kiểm thử")));
        var row = row(UUID.randomUUID(), "TEST101", "3", 1);
        when(plans.courses(userId, curriculum, 1, 1001)).thenReturn(List.of(row));
        when(plans.courses(userId, curriculum, 2, 1001)).thenReturn(Collections.nCopies(1001, row));
        var service = new StudyPlanService(users, plans);
        assertThatThrownBy(() -> service.compare(userId, 1, 2))
                .isInstanceOf(StudyPlanException.class).hasMessage("STUDY_PLAN_TOO_LARGE");
        verify(plans).selected(userId);
        verify(plans).courses(userId, curriculum, 1, 1001);
        verify(plans).courses(userId, curriculum, 2, 1001);
        when(plans.courses(userId, curriculum, 1, 1001)).thenReturn(Collections.nCopies(1001, row));
        clearInvocations(plans);
        assertThatThrownBy(() -> service.compare(userId, 1, 2))
                .isInstanceOf(StudyPlanException.class).hasMessage("STUDY_PLAN_TOO_LARGE");
        verify(plans, never()).courses(userId, curriculum, 2, 1001);
    }
    @Test void rejectsOversizedPlanInsteadOfTruncating() {
        var users = mock(UserRepository.class);
        var plans = mock(StudyPlanRepository.class);
        var account = new AppUser(UUID.randomUUID() + "@example.test", "!synthetic", null);
        UUID userId = account.getId(), profile = UUID.randomUUID(), curriculum = UUID.randomUUID();
        when(users.findById(userId)).thenReturn(Optional.of(account));
        when(plans.selected(userId)).thenReturn(Optional.of(new StudyPlanRepository.Selected(profile, curriculum, "CURR-A", "Chương trình kiểm thử")));
        var row = new StudyPlanRepository.PlannedCourse(UUID.randomUUID(), "TEST101", "Môn kiểm thử",
                new BigDecimal("3.00"), Requirement.REQUIRED, null, 1);
        when(plans.courses(userId, curriculum, 1, 1001)).thenReturn(Collections.nCopies(1001, row));
        assertThatThrownBy(() -> new StudyPlanService(users, plans).current(userId, 1))
                .isInstanceOf(StudyPlanException.class).hasMessage("STUDY_PLAN_TOO_LARGE");
    }

    @Test void copyRejectsMoreThanOneThousandItemsWithoutWritingTarget() {
        var users = mock(UserRepository.class);
        var plans = mock(StudyPlanRepository.class);
        var account = new AppUser(UUID.randomUUID() + "@example.test", "!synthetic", null);
        UUID userId = account.getId(), profile = UUID.randomUUID(), curriculum = UUID.randomUUID();
        when(users.findById(userId)).thenReturn(Optional.of(account));
        when(plans.selected(userId)).thenReturn(Optional.of(new StudyPlanRepository.Selected(
                profile, curriculum, "CURR-A", "Chương trình kiểm thử")));
        when(plans.lockProfile(userId)).thenReturn(Optional.of(new StudyPlanRepository.ProfileSelection(profile, curriculum)));
        when(plans.assignments(profile, curriculum, 1, 1001)).thenReturn(Collections.nCopies(
                1001, new StudyPlanRepository.Assignment(UUID.randomUUID(), 1)));
        assertThatThrownBy(() -> new StudyPlanService(users, plans).copy(userId, 1, 2))
                .isInstanceOf(StudyPlanException.class).hasMessage("STUDY_PLAN_TOO_LARGE");
        verify(plans, never()).insertCopies(anyList());
    }

    @Test void itemLimitIsCheckedPerScenario() {
        var users = mock(UserRepository.class);
        var plans = mock(StudyPlanRepository.class);
        var account = new AppUser(UUID.randomUUID() + "@example.test", "!synthetic", null);
        UUID userId = account.getId(), profile = UUID.randomUUID(), curriculum = UUID.randomUUID();
        UUID course = UUID.randomUUID();
        when(users.findById(userId)).thenReturn(Optional.of(account));
        when(plans.lockProfile(userId)).thenReturn(Optional.of(new StudyPlanRepository.ProfileSelection(profile, curriculum)));
        when(plans.hasCourse(profile, curriculum, course)).thenReturn(true);
        new StudyPlanService(users, plans).put(userId, curriculum, course, 1, 2);
        verify(plans).count(profile, curriculum, 2);
        verify(plans, never()).count(profile, curriculum, 1);
        verify(plans).upsert(argThat(item -> item.scenarioNo() == 2 && item.courseId().equals(course)));
    }
}
