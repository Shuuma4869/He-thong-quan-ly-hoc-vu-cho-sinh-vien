package vn.edu.phenikaa.ams.academic.application.port;

import java.util.Optional;
import java.util.UUID;

public interface TrackedCurriculumResolver {
    Optional<TrackedCurriculumSource> resolve(UUID userId);

    record TrackedCurriculumSource(UUID curriculumId, String code, String name, String sourceProgramId) {
        @Override public String toString() { return "TrackedCurriculumSource[redacted]"; }
    }
}
