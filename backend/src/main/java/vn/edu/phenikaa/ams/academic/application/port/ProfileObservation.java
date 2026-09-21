package vn.edu.phenikaa.ams.academic.application.port;

public record ProfileObservation(String studentNumber, String programName) {
    public ProfileObservation {
        studentNumber = text(studentNumber, 80, true);
        programName = text(programName, 200, false);
    }

    private static String text(String value, int limit, boolean required) {
        if (value == null && !required) return null;
        if (value == null || value.isBlank() || value.length() > limit
                || value.chars().anyMatch(c -> c < 32 || c == 127))
            throw new IllegalArgumentException("Invalid profile observation");
        return value.strip();
    }

    @Override public String toString() { return "ProfileObservation[redacted]"; }
}
