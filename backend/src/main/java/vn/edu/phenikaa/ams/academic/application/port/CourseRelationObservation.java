package vn.edu.phenikaa.ams.academic.application.port;

import java.util.List;

/** Descriptive conditions, not executable prerequisite rules or a verified AND/OR expression. */
public record CourseRelationObservation(String courseSourceId, List<Condition> conditions) {
    public CourseRelationObservation { conditions = List.copyOf(conditions); }
    @Override public String toString() { return "CourseRelationObservation[redacted]"; }
    public record Condition(String sourceId, String relatedCourseSourceId, String relationshipLabel,
                            String levelLabel, String operatorLabel, String threshold) {
        @Override public String toString() { return "CourseRelationCondition[redacted]"; }
    }
}
