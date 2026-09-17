package vn.edu.phenikaa.ams;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import vn.edu.phenikaa.ams.academic.domain.*;
import vn.edu.phenikaa.ams.sync.domain.*;

class AcademicDomainTest {
    @Test
    void semesterIdentifierSeparatesAcademicYearAndTermWithoutParsingProviderCodes() {
        var id = new Semester.Identifier(2026, "SUMMER");
        assertThat(id.canonicalCode()).isEqualTo("2026-2027:SUMMER");
        assertThatThrownBy(() -> new Semester.Identifier(2026, "2026/1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Semester(UUID.randomUUID(), id, "Test", LocalDate.now(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reschedulingAndCancellationKeepSessionAndExamIdentity() {
        var f = new AcademicFixtures();
        UUID sessionId = f.session.getId();
        UUID examId = f.exam.getId();
        f.session.reschedule(AcademicFixtures.START.plusSeconds(3600), AcademicFixtures.START.plusSeconds(9000), "Test room B", "Test teacher");
        f.exam.reschedule(AcademicFixtures.START.plusSeconds(172800), null, "Test room C");
        f.session.cancel();
        f.exam.cancel();
        assertThat(f.session.getId()).isEqualTo(sessionId);
        assertThat(f.session.getOccurrenceKey()).isEqualTo("lesson-001");
        assertThat(f.session.getStatus()).isEqualTo(ClassSession.Status.CANCELLED);
        assertThat(f.exam.getId()).isEqualTo(examId);
        assertThat(f.exam.getOccurrenceKey()).isEqualTo("final-01");
        assertThat(f.exam.getStatus()).isEqualTo(Exam.Status.CANCELLED);
        assertThatThrownBy(() -> f.session.reschedule(AcademicFixtures.START, AcademicFixtures.START, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void referencesCannotCrossProfilesOrMismatchCourseAndSemester() {
        var f = new AcademicFixtures();
        var other = new AcademicFixtures();
        assertThatThrownBy(() -> new ClassSection(f.semester, other.course, "TEST")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> f.profile.selectCurriculum(other.curriculum)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> f.profile.selectGradingPolicy(other.policy)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StudentCourse(f.prerequisite, f.semester, f.section, 1, BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void electiveGroupsAndPrerequisitesHaveExplicitRules() {
        var f = new AcademicFixtures();
        assertThatThrownBy(() -> new CurriculumCourse(f.curriculum, f.course, CurriculumCourse.Requirement.ELECTIVE, null, BigDecimal.ONE, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CoursePrerequisite(f.required, f.required, CoursePrerequisite.Kind.PREREQUISITE))
                .isInstanceOf(IllegalArgumentException.class);
        CoursePrerequisite.validateAcyclic(List.of(f.dependency));
        var reverse = new CoursePrerequisite(f.required, f.elective, CoursePrerequisite.Kind.PREREQUISITE);
        assertThatThrownBy(() -> CoursePrerequisite.validateAcyclic(List.of(f.dependency, reverse)))
                .isInstanceOf(IllegalArgumentException.class);
        CoursePrerequisite.validateAcyclic(List.of(f.dependency,
                new CoursePrerequisite(f.required, f.elective, CoursePrerequisite.Kind.COREQUISITE)));
    }

    @Test
    void resultsRepresentUnfinishedFailedPassedAndExemptedAttemptsWithoutGuessingGpa() {
        var f = new AcademicFixtures();
        for (var status : List.of(AcademicResult.Status.IN_PROGRESS, AcademicResult.Status.FAILED, AcademicResult.Status.WITHDRAWN)) {
            var result = new AcademicResult(f.attempt, null, status, null, null, null, BigDecimal.ZERO, false, AcademicFixtures.START);
            assertThat(result.isIncludedInGpa()).isFalse();
            assertThat(result.getGradePoints()).isNull();
            assertThat(result.getCreditsEarned()).isZero();
        }
        var exemption = new AcademicResult(f.attempt, null, AcademicResult.Status.EXEMPTED, null, null, null,
                AcademicFixtures.CREDITS, false, AcademicFixtures.START);
        assertThat(exemption.getCreditsEarned()).isEqualByComparingTo("3.5");
        assertThat(f.result.getNumericScore()).isEqualByComparingTo("17.5");
        assertThat(f.result.getGradePoints()).isEqualByComparingTo("4.5");
        assertThatThrownBy(() -> new AcademicResult(f.attempt, null, AcademicResult.Status.PASSED, null, null,
                BigDecimal.ONE, BigDecimal.ONE, true, AcademicFixtures.START)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AcademicResult(f.attempt, f.policy, AcademicResult.Status.FAILED, null, null,
                BigDecimal.ZERO, BigDecimal.ONE, true, AcademicFixtures.START)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AcademicResult(f.attempt, f.policy, AcademicResult.Status.PASSED, new BigDecimal("21"), null,
                BigDecimal.ONE, BigDecimal.ONE, true, AcademicFixtures.START)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void gradingPolicyUsesVersionedConfigurableScalesAndClassifications() {
        var f = new AcademicFixtures();
        assertThat(f.policy.getMaxGradePoints()).isEqualByComparingTo("5");
        assertThat(f.policy.getClassifications()).containsEntry("Test classification", new BigDecimal("4.25"));
        assertThatThrownBy(() -> f.policy.getClassifications().put("Changed", BigDecimal.ONE))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new GradingPolicy(f.profile.getId(), "TEST", "v2", "Test", BigDecimal.TEN, new BigDecimal("4"),
                GradingPolicy.RepeatStrategy.LATEST, Map.of("Test classification", new BigDecimal("4.25"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Course(f.profile.getId(), "CS2", "Test", new BigDecimal("1.125")))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void changeMetadataUsesInternalIdentityAndRejectsIncompleteOrUnrelatedSnapshots() {
        var f = new AcademicFixtures();
        var payload = new HashMap<>(Map.of("room", "Test room B"));
        var change = new DetectedAcademicChange(AcademicChangeType.ROOM_CHANGED, f.session.getId(),
                Map.of("room", "Test room A"), payload, AcademicFixtures.START);
        payload.put("room", "Changed outside record");
        assertThat(change.after()).containsEntry("room", "Test room B");
        var partial = new AcademicSnapshotMetadata(f.profile.getId(), "test-source", AcademicFixtures.START, 1,
                AcademicSnapshotMetadata.Status.PARTIAL, "c".repeat(64));
        assertThatThrownBy(() -> ScheduleChange.forSession(f.session, f.previous, partial, change))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ScheduleChange.forExam(f.exam, f.previous, f.current, change))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AcademicSnapshotMetadata(f.profile.getId(), "test-source", AcademicFixtures.START, 1,
                AcademicSnapshotMetadata.Status.COMPLETE, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void normalizedSnapshotSeparatesCatalogSectionAttemptSessionAndExam() {
        var f = new AcademicFixtures();
        var courses = new ArrayList<>(List.of(new AcademicSnapshot.CourseSnapshot("CS101", "Test", AcademicFixtures.CREDITS)));
        var snapshot = new AcademicSnapshot(AcademicFixtures.START, List.of(), courses,
                List.of(new AcademicSnapshot.SectionSnapshot("section-01", f.semester.getIdentifier(), "CS101", "TEST-SECTION-01")),
                List.of(), List.of(new AcademicSnapshot.ClassSessionSnapshot("lesson-001", "section-01", f.session.getStartsAt(),
                f.session.getEndsAt(), null, null, ClassSession.Status.SCHEDULED)), List.of());
        courses.clear();
        assertThat(snapshot.courses()).hasSize(1);
        assertThat(snapshot.classSessions().getFirst().sectionKey()).isEqualTo(snapshot.sections().getFirst().sectionKey());
        assertThatThrownBy(() -> snapshot.courses().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
