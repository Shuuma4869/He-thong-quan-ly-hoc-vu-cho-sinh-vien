package vn.edu.phenikaa.ams.academic.application.port;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.HashSet;

/** Observed attempts with scores, not a complete inventory of all registrations. */
public record AcademicRecordObservation(AcademicProgram program, List<Entry> entries) {
    public enum Completeness { UNKNOWN }
    public enum Outcome { PASSED, FAILED, RETAKE_REQUIRED }
    public AcademicRecordObservation {
        Objects.requireNonNull(program);
        entries = List.copyOf(entries);
    }
    public Completeness completeness() { return Completeness.UNKNOWN; }
    public boolean hasAmbiguousLearningAttempts() {
        var seen = new HashSet<List<Object>>();
        for (var entry : entries)
            if (!seen.add(List.of(entry.sourceCourseId(), entry.reportedLearningAttempt()))) return true;
        return false;
    }
    @Override public String toString() { return "AcademicRecordObservation[redacted]"; }

    public record Entry(String sourceEnrollmentId, String sourceSectionId, String sourceCourseId,
                        String courseCode, String courseName, BigDecimal courseCredits,
                        String sourcePeriodId, int academicYearStart, int semesterNumber,
                        int reportedLearningAttempt, List<Component> components, FinalResult result) {
        public Entry { components = List.copyOf(components); }
        @Override public String toString() { return "AcademicRecordEntry[redacted]"; }
    }
    public record Component(String sourceId, String code, String name, int examAttempt, BigDecimal score) {
        @Override public String toString() { return "AcademicComponent[redacted]"; }
    }
    public record FinalResult(String sourceId, int examAttempt, Outcome outcome,
                              BigDecimal numericScore, BigDecimal gradePoints, String letterGrade) {
        @Override public String toString() { return "AcademicFinalResult[redacted]"; }
    }
}
