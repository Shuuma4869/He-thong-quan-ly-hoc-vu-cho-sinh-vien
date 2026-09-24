package vn.edu.phenikaa.ams.academic.application.port;

public record AcademicProgram(String sourceId, String label) {
    public AcademicProgram {
        if (sourceId == null || sourceId.isBlank() || sourceId.length() > 128
                || label == null || label.isBlank() || label.length() > 240
                || sourceId.chars().anyMatch(Character::isISOControl)
                || label.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Invalid academic program");
    }
    @Override public String toString() { return "AcademicProgram[redacted]"; }
}
