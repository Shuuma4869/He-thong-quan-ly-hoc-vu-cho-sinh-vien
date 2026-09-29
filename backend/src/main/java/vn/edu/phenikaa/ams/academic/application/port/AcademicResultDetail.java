package vn.edu.phenikaa.ams.academic.application.port;

import java.util.List;
import java.math.BigDecimal;

/** Source links to a result, not proof of a persistent learning-attempt identity. */
public record AcademicResultDetail(String sourceResultId, List<ComponentLink> components) {
    public AcademicResultDetail { components = List.copyOf(components); }
    @Override public String toString() { return "AcademicResultDetail[redacted]"; }

    public record ComponentLink(String sourceEnrollmentId, String sourceComponentId,
                                String code, String name, int examAttempt, BigDecimal score) {
        @Override public String toString() { return "AcademicResultComponentLink[redacted]"; }
    }
}
