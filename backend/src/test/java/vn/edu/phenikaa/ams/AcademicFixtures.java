package vn.edu.phenikaa.ams;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import vn.edu.phenikaa.ams.academic.domain.*;
import vn.edu.phenikaa.ams.sync.domain.*;
import vn.edu.phenikaa.ams.user.domain.AppUser;

final class AcademicFixtures {
    static final Instant START = Instant.parse("2026-09-01T01:00:00Z");
    static final BigDecimal CREDITS = new BigDecimal("3.50");
    final AppUser user = new AppUser("academic-" + UUID.randomUUID() + "@example.test", "!test-only-unusable-hash", "Test student");
    final StudentProfile profile = new StudentProfile(user.getId(), "TEST-001", "Test institution", "Test program", "TEST-2026");
    final Course course = new Course(profile.getId(), "CS101", "Test programming", CREDITS);
    final Course prerequisite = new Course(profile.getId(), "MATH101", "Test mathematics", new BigDecimal("3"));
    final Semester semester = new Semester(profile.getId(), new Semester.Identifier(2026, "T1"), "Test term",
            LocalDate.of(2026, 9, 1), LocalDate.of(2027, 1, 31));
    final Curriculum curriculum = new Curriculum(profile.getId(), "TEST", "v1", "Test curriculum", "TEST-2026", new BigDecimal("120"));
    final CurriculumGroup group = new CurriculumGroup(curriculum, "ELECTIVE", "Test elective group", new BigDecimal("6"));
    final CurriculumCourse required = new CurriculumCourse(curriculum, prerequisite, CurriculumCourse.Requirement.REQUIRED, null, prerequisite.getCredits(), 1);
    final CurriculumCourse elective = new CurriculumCourse(curriculum, course, CurriculumCourse.Requirement.ELECTIVE, group, CREDITS, 2);
    final CoursePrerequisite dependency = new CoursePrerequisite(elective, required, CoursePrerequisite.Kind.PREREQUISITE);
    final GradingPolicy policy = new GradingPolicy(profile.getId(), "TEST", "v1", "Test scale", new BigDecimal("20"),
            new BigDecimal("5"), GradingPolicy.RepeatStrategy.HIGHEST, Map.of("Test classification", new BigDecimal("4.25")));
    final ClassSection section = new ClassSection(semester, course, "TEST-SECTION-01");
    final StudentCourse attempt = new StudentCourse(course, semester, section, 1, CREDITS);
    final AcademicResult result = new AcademicResult(attempt, policy, AcademicResult.Status.PASSED,
            new BigDecimal("17.50"), "TEST-A", new BigDecimal("4.50"), CREDITS, true, START);
    final ClassSession session = new ClassSession(section, "lesson-001", START, START.plusSeconds(5400), "Test room A", null);
    final Exam exam = new Exam(attempt, "final-01", START.plusSeconds(86400), null, null, "Test written exam");
    final AcademicSnapshotMetadata previous = new AcademicSnapshotMetadata(profile.getId(), "test-source", START, 1,
            AcademicSnapshotMetadata.Status.COMPLETE, "a".repeat(64));
    final AcademicSnapshotMetadata current = new AcademicSnapshotMetadata(profile.getId(), "test-source", START.plusSeconds(60), 1,
            AcademicSnapshotMetadata.Status.COMPLETE, "b".repeat(64));
    final ScheduleChange sessionChange = ScheduleChange.forSession(session, previous, current,
            new DetectedAcademicChange(AcademicChangeType.ROOM_CHANGED, session.getId(),
                    Map.of("room", "Test room A"), Map.of("room", "Test room B"), START.plusSeconds(60)));
    final ScheduleChange examChange = ScheduleChange.forExam(exam, null, current,
            new DetectedAcademicChange(AcademicChangeType.EXAM_ADDED, exam.getId(),
                    Map.of(), Map.of("startsAt", exam.getStartsAt().toString()), START.plusSeconds(60)));

    List<Object> persistenceOrder() {
        return List.of(user, profile, course, prerequisite, semester, curriculum, group, required, elective,
                dependency, policy, section, attempt, result, session, exam, previous, current, sessionChange, examChange);
    }
}
