package vn.edu.phenikaa.ams.academic.application.port;

/** A source lookup filter; its display label is not an AMS semester identifier. */
public record AcademicPeriod(String sourceId, String label) {
    public AcademicPeriod {
        if (sourceId == null || sourceId.isBlank() || sourceId.length() > 128
                || label == null || label.isBlank() || label.length() > 200
                || sourceId.chars().anyMatch(Character::isISOControl)
                || label.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Invalid academic period");
    }
    @Override public String toString() { return "AcademicPeriod[redacted]"; }
}
