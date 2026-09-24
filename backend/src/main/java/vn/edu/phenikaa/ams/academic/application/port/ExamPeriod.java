package vn.edu.phenikaa.ams.academic.application.port;

/** A source filter, not a verified AMS semester identifier. */
public record ExamPeriod(String sourceId, String label) {
    public ExamPeriod {
        if (sourceId == null || sourceId.isBlank() || sourceId.length() > 128
                || sourceId.chars().anyMatch(Character::isISOControl)
                || label == null || label.isBlank() || label.length() > 200
                || label.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Invalid exam period");
    }
    @Override public String toString() { return "ExamPeriod[redacted]"; }
}
