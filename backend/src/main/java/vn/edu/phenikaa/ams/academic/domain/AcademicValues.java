package vn.edu.phenikaa.ams.academic.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.UUID;

final class AcademicValues {
    private AcademicValues() {}

    static String text(String value, int max) {
        if (value == null || value.isBlank() || value.trim().length() > max)
            throw new IllegalArgumentException("Invalid text length");
        return value.trim();
    }

    static String optionalText(String value, int max) {
        return value == null ? null : text(value, max);
    }

    static BigDecimal decimal(BigDecimal value, int precision) {
        Objects.requireNonNull(value);
        BigDecimal normalized = value.setScale(2, RoundingMode.UNNECESSARY);
        if (normalized.signum() < 0 || normalized.precision() > precision)
            throw new IllegalArgumentException("Value outside numeric range");
        return normalized;
    }

    static void sameProfile(UUID profileId, AcademicEntity other) {
        if (!profileId.equals(other.getProfileId()))
            throw new IllegalArgumentException("References must belong to the same student profile");
    }
}
