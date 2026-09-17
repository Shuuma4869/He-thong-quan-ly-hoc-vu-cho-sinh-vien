package vn.edu.phenikaa.ams;

import static org.assertj.core.api.Assertions.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.phenikaa.ams.academic.domain.*;
import vn.edu.phenikaa.ams.sync.domain.*;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.data.redis.password=")
@Transactional
class AcademicPersistenceIT {
    @Autowired private EntityManager entities;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void allAcademicMappingsRoundTripThroughPostgresql() {
        var f = persistFixture();
        f.profile.selectCurriculum(f.curriculum);
        f.profile.selectGradingPolicy(f.policy);
        entities.flush();
        entities.clear();
        var profile = entities.find(StudentProfile.class, f.profile.getId());
        assertThat(profile.getUserId()).isEqualTo(f.user.getId());
        assertThat(profile.getCurriculumId()).isEqualTo(f.curriculum.getId());
        assertThat(profile.getGradingPolicyId()).isEqualTo(f.policy.getId());
        assertThat(entities.find(Course.class, f.course.getId()).getCredits()).isEqualByComparingTo("3.50");
        assertThat(entities.find(Semester.class, f.semester.getId()).getIdentifier().canonicalCode()).isEqualTo("2026-2027:T1");
        assertThat(entities.find(Curriculum.class, f.curriculum.getId()).getMinimumCredits()).isEqualByComparingTo("120");
        assertThat(entities.find(CurriculumGroup.class, f.group.getId()).getMinimumCredits()).isEqualByComparingTo("6");
        assertThat(entities.find(CurriculumCourse.class, f.elective.getId()).getGroupId()).isEqualTo(f.group.getId());
        assertThat(entities.find(CoursePrerequisite.class, f.dependency.getId()).getPrerequisiteCourseId()).isEqualTo(f.prerequisite.getId());
        assertThat(entities.find(GradingPolicy.class, f.policy.getId()).getClassifications())
                .containsEntry("Test classification", new BigDecimal("4.25"));
        assertThat(entities.find(ClassSection.class, f.section.getId()).getCourseId()).isEqualTo(f.course.getId());
        assertThat(entities.find(StudentCourse.class, f.attempt.getId()).getAttemptNumber()).isEqualTo(1);
        var result = entities.find(AcademicResult.class, f.result.getId());
        assertThat(result.getStatus()).isEqualTo(AcademicResult.Status.PASSED);
        assertThat(result.getNumericScore()).isEqualByComparingTo("17.50");
        assertThat(result.getGradePoints()).isEqualByComparingTo("4.50");
        assertThat(result.getCreditsEarned()).isEqualByComparingTo("3.50");
        assertThat(result.isIncludedInGpa()).isTrue();
        assertThat(entities.find(ClassSession.class, f.session.getId()).getStartsAt()).isEqualTo(AcademicFixtures.START);
        assertThat(entities.find(Exam.class, f.exam.getId()).getEndsAt()).isNull();
        assertThat(entities.find(AcademicSnapshotMetadata.class, f.current.getId()).getContentHash()).isEqualTo("b".repeat(64));
        var change = entities.find(ScheduleChange.class, f.sessionChange.getId());
        assertThat(change.getClassSessionId()).isEqualTo(f.session.getId());
        assertThat(change.getBeforeValues()).containsEntry("room", "Test room A");
        assertThat(change.getAfterValues()).containsEntry("room", "Test room B");
        assertThat(entities.find(ScheduleChange.class, f.examChange.getId()).getExamId()).isEqualTo(f.exam.getId());
    }

    @Test
    void stableSessionLookupSurvivesReschedulingAndRejectsStaleUpdates() {
        var f = persistFixture();
        entities.clear();
        var stale = entities.find(ClassSession.class, f.session.getId());
        entities.detach(stale);
        var session = entities.createQuery("select s from ClassSession s where s.profileId = :profile and s.sectionId = :section and s.occurrenceKey = :key", ClassSession.class)
                .setParameter("profile", f.profile.getId()).setParameter("section", f.section.getId()).setParameter("key", "lesson-001")
                .getSingleResult();
        session.reschedule(AcademicFixtures.START.plusSeconds(86400), AcademicFixtures.START.plusSeconds(91800), "Test room B", null);
        entities.flush();
        assertThat(session.getId()).isEqualTo(stale.getId());
        assertThat(session.getVersion()).isEqualTo(stale.getVersion() + 1);
        assertThat(jdbc.queryForObject("select count(*) from class_session where profile_id = ? and occurrence_key = ?",
                Integer.class, f.profile.getId(), "lesson-001")).isEqualTo(1);
        stale.reschedule(AcademicFixtures.START, AcademicFixtures.START.plusSeconds(3600), "Stale test room", null);
        assertThatThrownBy(() -> { entities.merge(stale); entities.flush(); }).isInstanceOf(OptimisticLockException.class);
    }

    @Test
    void repeatsAndUnknownValuesDoNotOverwritePreviousResults() {
        var f = persistFixture();
        var second = new StudentCourse(f.course, f.semester, null, 2, AcademicFixtures.CREDITS);
        entities.persist(second);
        var result = new AcademicResult(second, null, AcademicResult.Status.IN_PROGRESS, null, null, null,
                BigDecimal.ZERO, false, AcademicFixtures.START.plusSeconds(60));
        entities.persist(result);
        entities.flush();
        entities.clear();
        assertThat(entities.find(AcademicResult.class, result.getId()).getGradePoints()).isNull();
        assertThat(entities.find(AcademicResult.class, f.result.getId()).getStatus()).isEqualTo(AcademicResult.Status.PASSED);
        assertThat(jdbc.queryForObject("select count(*) from student_course where profile_id = ? and course_id = ?",
                Integer.class, f.profile.getId(), f.course.getId())).isEqualTo(2);
    }

    @Test
    void catalogCodesCanRepeatAcrossProfilesAndRevisionsRemainDistinct() {
        var first = persistFixture();
        var second = persistFixture();
        entities.persist(new Curriculum(first.profile.getId(), "TEST", "v2", "Revised test curriculum", null, BigDecimal.TEN));
        entities.flush();
        assertThat(first.course.getCode()).isEqualTo(second.course.getCode());
        assertThat(first.course.getId()).isNotEqualTo(second.course.getId());
        assertThat(jdbc.queryForObject("select count(*) from curriculum where profile_id = ?", Integer.class, first.profile.getId())).isEqualTo(2);
    }

    enum InvalidData {
        DUPLICATE_COURSE, DUPLICATE_SEMESTER, DUPLICATE_ATTEMPT, DUPLICATE_SESSION, DUPLICATE_EXAM,
        DUPLICATE_CURRICULUM, DUPLICATE_POLICY, DUPLICATE_CHANGE, CROSS_PROFILE_SECTION, CROSS_PROFILE_SNAPSHOT,
        CROSS_PROFILE_POLICY, MISSING_COURSE, WRONG_SECTION_COURSE, SELF_PREREQUISITE, ELECTIVE_WITHOUT_GROUP,
        NEGATIVE_CREDITS, TOO_MANY_EARNED_CREDITS, FAILED_WITH_EARNED_CREDITS, GPA_WITHOUT_POLICY,
        WRONG_ATTEMPT_CREDITS, INVALID_SESSION_TIME, NULL_SESSION_IDENTITY, INVALID_RESULT_STATUS,
        CHANGE_WITH_TWO_TARGETS, CHANGE_WITH_WRONG_TARGET_TYPE, SNAPSHOT_WITHOUT_HASH
    }

    @ParameterizedTest
    @EnumSource(InvalidData.class)
    void databaseRejectsInvalidRelationshipsAndValues(InvalidData scenario) {
        var f = persistFixture();
        var other = persistFixture();
        assertThatThrownBy(() -> {
            switch (scenario) {
                case DUPLICATE_COURSE -> jdbc.update("insert into course (id, profile_id, code, name, credits) values (?, ?, 'CS101', 'Test duplicate', 3)", UUID.randomUUID(), f.profile.getId());
                case DUPLICATE_SEMESTER -> jdbc.update("insert into semester (id, profile_id, academic_year_start, term_code, name) values (?, ?, 2026, 'T1', 'Test duplicate')", UUID.randomUUID(), f.profile.getId());
                case DUPLICATE_ATTEMPT -> jdbc.update("insert into student_course (id, profile_id, course_id, semester_id, attempt_number, credits_attempted) values (?, ?, ?, ?, 1, 3.5)", UUID.randomUUID(), f.profile.getId(), f.course.getId(), f.semester.getId());
                case DUPLICATE_SESSION -> jdbc.update("insert into class_session (id, profile_id, section_id, occurrence_key, starts_at, ends_at, status) select ?, profile_id, section_id, occurrence_key, starts_at + interval '1 day', ends_at + interval '1 day', status from class_session where id = ?", UUID.randomUUID(), f.session.getId());
                case DUPLICATE_EXAM -> jdbc.update("insert into exam (id, profile_id, student_course_id, occurrence_key, starts_at, status) select ?, profile_id, student_course_id, occurrence_key, starts_at, status from exam where id = ?", UUID.randomUUID(), f.exam.getId());
                case DUPLICATE_CURRICULUM -> jdbc.update("insert into curriculum (id, profile_id, code, revision, name, minimum_credits) select ?, profile_id, code, revision, name, minimum_credits from curriculum where id = ?", UUID.randomUUID(), f.curriculum.getId());
                case DUPLICATE_POLICY -> jdbc.update("insert into grading_policy (id, profile_id, code, revision, name, max_numeric_score, max_grade_points, repeat_strategy) select ?, profile_id, code, revision, name, max_numeric_score, max_grade_points, repeat_strategy from grading_policy where id = ?", UUID.randomUUID(), f.policy.getId());
                case DUPLICATE_CHANGE -> jdbc.update("insert into schedule_change (id, profile_id, current_snapshot_id, class_session_id, change_type, before_values, after_values, detected_at) select ?, profile_id, current_snapshot_id, class_session_id, change_type, before_values, after_values, detected_at from schedule_change where id = ?", UUID.randomUUID(), f.sessionChange.getId());
                case CROSS_PROFILE_SECTION -> jdbc.update("update class_session set section_id = ? where id = ?", other.section.getId(), f.session.getId());
                case CROSS_PROFILE_SNAPSHOT -> jdbc.update("update schedule_change set previous_snapshot_id = ? where id = ?", other.previous.getId(), f.sessionChange.getId());
                case CROSS_PROFILE_POLICY -> jdbc.update("update student_profile set grading_policy_id = ? where id = ?", other.policy.getId(), f.profile.getId());
                case MISSING_COURSE -> jdbc.update("insert into class_section (id, profile_id, semester_id, course_id, section_code) values (?, ?, ?, ?, 'TEST-MISSING')", UUID.randomUUID(), f.profile.getId(), f.semester.getId(), UUID.randomUUID());
                case WRONG_SECTION_COURSE -> jdbc.update("update student_course set course_id = ? where id = ?", f.prerequisite.getId(), f.attempt.getId());
                case SELF_PREREQUISITE -> jdbc.update("update course_prerequisite set prerequisite_course_id = course_id where id = ?", f.dependency.getId());
                case ELECTIVE_WITHOUT_GROUP -> jdbc.update("update curriculum_course set group_id = null where id = ?", f.elective.getId());
                case NEGATIVE_CREDITS -> jdbc.update("update course set credits = -1 where id = ?", f.course.getId());
                case TOO_MANY_EARNED_CREDITS -> jdbc.update("update academic_result set credits_earned = 4 where id = ?", f.result.getId());
                case FAILED_WITH_EARNED_CREDITS -> jdbc.update("update academic_result set status = 'FAILED' where id = ?", f.result.getId());
                case GPA_WITHOUT_POLICY -> jdbc.update("update academic_result set grading_policy_id = null where id = ?", f.result.getId());
                case WRONG_ATTEMPT_CREDITS -> jdbc.update("update academic_result set credits_attempted = 4 where id = ?", f.result.getId());
                case INVALID_SESSION_TIME -> jdbc.update("update class_session set ends_at = starts_at where id = ?", f.session.getId());
                case NULL_SESSION_IDENTITY -> jdbc.update("update class_session set occurrence_key = null where id = ?", f.session.getId());
                case INVALID_RESULT_STATUS -> jdbc.update("update academic_result set status = 'UNKNOWN' where id = ?", f.result.getId());
                case CHANGE_WITH_TWO_TARGETS -> jdbc.update("update schedule_change set exam_id = ? where id = ?", f.exam.getId(), f.sessionChange.getId());
                case CHANGE_WITH_WRONG_TARGET_TYPE -> jdbc.update("update schedule_change set change_type = 'EXAM_ADDED' where id = ?", f.sessionChange.getId());
                case SNAPSHOT_WITHOUT_HASH -> jdbc.update("update academic_snapshot set content_hash = null where id = ?", f.current.getId());
            }
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    private AcademicFixtures persistFixture() {
        var fixture = new AcademicFixtures();
        fixture.persistenceOrder().forEach(entities::persist);
        entities.flush();
        return fixture;
    }
}
