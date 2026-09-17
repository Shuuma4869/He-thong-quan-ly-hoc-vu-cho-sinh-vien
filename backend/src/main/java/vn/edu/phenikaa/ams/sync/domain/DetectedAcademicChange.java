package vn.edu.phenikaa.ams.sync.domain;

import java.time.Instant;
import java.util.Map;

public record DetectedAcademicChange(
        AcademicChangeType type,
        String sourceEntityId,
        Map<String, String> before,
        Map<String, String> after,
        Instant detectedAt) {}
