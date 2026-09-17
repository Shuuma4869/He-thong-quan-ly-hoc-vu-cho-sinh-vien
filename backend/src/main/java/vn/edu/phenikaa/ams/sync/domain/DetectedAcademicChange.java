package vn.edu.phenikaa.ams.sync.domain;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record DetectedAcademicChange(
        AcademicChangeType type,
        UUID entityId,
        Map<String, String> before,
        Map<String, String> after,
        Instant detectedAt) {
    public DetectedAcademicChange {
        Objects.requireNonNull(type);
        Objects.requireNonNull(entityId);
        Objects.requireNonNull(detectedAt);
        before = Map.copyOf(before);
        after = Map.copyOf(after);
    }
}
