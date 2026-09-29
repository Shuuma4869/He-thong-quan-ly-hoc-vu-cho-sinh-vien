package vn.edu.phenikaa.ams.academic.application;

import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.phenikaa.ams.academic.application.port.*;
import vn.edu.phenikaa.ams.academic.infrastructure.StudentProfileRepository;
import vn.edu.phenikaa.ams.academic.infrastructure.phenikaa.PhenikaaCurriculumStore;

public class CurriculumImportService {
    private final AcademicPortalClient portal;
    private final StudentProfileRepository profiles;
    private final PhenikaaCurriculumStore store;
    public CurriculumImportService(AcademicPortalClient portal, StudentProfileRepository profiles, PhenikaaCurriculumStore store) {
        this.portal = portal; this.profiles = profiles; this.store = store;
    }

    @Transactional(noRollbackFor = AcademicPortalException.class)
    public UUID importCurriculum(UUID currentUserId, CurriculumOption curriculum) {
        // Retains the existing per-user database lock until this transaction commits.
        var connection = portal.currentConnection(currentUserId);
        var profile = profiles.findByUserId(currentUserId)
                .orElseThrow(() -> new CurriculumImportException(CurriculumImportException.Code.PROFILE_REQUIRED));
        var observation = portal.fetchCurriculum(currentUserId, connection, curriculum);
        // An available curriculum is not necessarily the user's active/current curriculum.
        return store.upsert(profile.getId(), observation);
    }
}
