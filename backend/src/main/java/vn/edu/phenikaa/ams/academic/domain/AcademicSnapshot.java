package vn.edu.phenikaa.ams.academic.domain;

import java.time.Instant;
import java.util.List;

public record AcademicSnapshot(
        Instant capturedAt,
        List<CourseSnapshot> courses,
        List<ClassSessionSnapshot> classSessions,
        List<ExamSnapshot> exams) {

    public record CourseSnapshot(String sourceId, String code, String name, int credits, String status) {}

    public record ClassSessionSnapshot(
            String sourceId, String courseCode, Instant startsAt, Instant endsAt, String room, String lecturer) {}

    public record ExamSnapshot(
            String sourceId, String courseCode, Instant startsAt, String room, String format) {}
}
