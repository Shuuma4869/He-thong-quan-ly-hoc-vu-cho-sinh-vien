package vn.edu.phenikaa.ams.academic.domain;

import java.time.Instant;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record AcademicSnapshot(
        Instant capturedAt,
        List<SemesterSnapshot> semesters,
        List<CourseSnapshot> courses,
        List<SectionSnapshot> sections,
        List<StudentCourseSnapshot> studentCourses,
        List<ClassSessionSnapshot> classSessions,
        List<ExamSnapshot> exams) {

    public AcademicSnapshot {
        java.util.Objects.requireNonNull(capturedAt);
        semesters = List.copyOf(semesters);
        courses = List.copyOf(courses);
        sections = List.copyOf(sections);
        studentCourses = List.copyOf(studentCourses);
        classSessions = List.copyOf(classSessions);
        exams = List.copyOf(exams);
    }

    public record SemesterSnapshot(Semester.Identifier identifier, String name, LocalDate startsOn, LocalDate endsOn) {}

    public record CourseSnapshot(String code, String name, BigDecimal credits) {}

    public record SectionSnapshot(String sectionKey, Semester.Identifier semester, String courseCode, String sectionCode) {}

    public record StudentCourseSnapshot(String attemptKey, String courseCode, Semester.Identifier semester,
            String sectionKey, int attemptNumber, BigDecimal creditsAttempted, AcademicResult.Status status,
            BigDecimal numericScore, String letterGrade, BigDecimal gradePoints, BigDecimal creditsEarned) {}

    public record ClassSessionSnapshot(
            String occurrenceKey, String sectionKey, Instant startsAt, Instant endsAt,
            String room, String lecturer, ClassSession.Status status) {}

    public record ExamSnapshot(
            String occurrenceKey, String attemptKey, Instant startsAt, Instant endsAt,
            String room, String format, Exam.Status status) {}
}
