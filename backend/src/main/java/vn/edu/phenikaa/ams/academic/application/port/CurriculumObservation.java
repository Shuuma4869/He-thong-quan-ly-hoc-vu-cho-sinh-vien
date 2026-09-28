package vn.edu.phenikaa.ams.academic.application.port;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

public record CurriculumObservation(CurriculumOption curriculum, List<CourseEntry> courses, List<Group> groups) {
    public enum Completeness { UNKNOWN }
    public enum Requirement { REQUIRED, ELECTIVE }
    public CurriculumObservation {
        Objects.requireNonNull(curriculum);
        courses = List.copyOf(courses);
        groups = List.copyOf(groups);
    }
    public Completeness completeness() { return Completeness.UNKNOWN; }
    @Override public String toString() { return "CurriculumObservation[redacted]"; }

    public record CourseEntry(String sourceId, String code, String name, BigDecimal credits,
                              boolean relationshipDetailsAvailable) {
        @Override public String toString() { return "CurriculumCourseEntry[redacted]"; }
    }
    public record Group(String sourceId, String code, String name, Requirement requirement,
                        BigDecimal minimumCredits, Integer minimumCourseCount, List<String> courseSourceIds) {
        public Group { courseSourceIds = List.copyOf(courseSourceIds); }
        @Override public String toString() { return "CurriculumGroupObservation[redacted]"; }
    }
}
