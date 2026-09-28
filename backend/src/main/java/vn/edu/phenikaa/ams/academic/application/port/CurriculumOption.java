package vn.edu.phenikaa.ams.academic.application.port;

import java.math.BigDecimal;
import java.util.Objects;

public record CurriculumOption(String sourceId, String code, String name, String cohort, BigDecimal requiredCredits) {
    public CurriculumOption {
        if (sourceId == null || sourceId.isBlank() || sourceId.length() > 128
                || code == null || code.isBlank() || code.length() > 40
                || name == null || name.isBlank() || name.length() > 240
                || (cohort != null && (cohort.isBlank() || cohort.length() > 40)))
            throw new IllegalArgumentException("Invalid curriculum option");
        Objects.requireNonNull(requiredCredits);
        if (requiredCredits.signum() < 0) throw new IllegalArgumentException("Invalid curriculum credits");
    }
    @Override public String toString() { return "CurriculumOption[redacted]"; }
}
