package vn.edu.phenikaa.ams.academic.application.port;

import java.math.BigDecimal;
import java.util.Objects;

public record AcademicProgressSummaryObservation(AcademicProgram program, BigDecimal cumulativeAverageScale4,
                                                  BigDecimal cumulativeAverageScale10, BigDecimal sourceAccumulatedCredits) {
    public AcademicProgressSummaryObservation {
        Objects.requireNonNull(program);
        Objects.requireNonNull(cumulativeAverageScale4);
        Objects.requireNonNull(cumulativeAverageScale10);
        Objects.requireNonNull(sourceAccumulatedCredits);
    }
    @Override public String toString() { return "AcademicProgressSummaryObservation[redacted]"; }
}
