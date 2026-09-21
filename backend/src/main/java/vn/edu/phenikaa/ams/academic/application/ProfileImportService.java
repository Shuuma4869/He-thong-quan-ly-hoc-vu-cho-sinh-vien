package vn.edu.phenikaa.ams.academic.application;

import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.phenikaa.ams.academic.application.port.AcademicPortalClient;
import vn.edu.phenikaa.ams.academic.application.port.AcademicPortalException;
import vn.edu.phenikaa.ams.academic.domain.StudentProfile;
import vn.edu.phenikaa.ams.academic.infrastructure.StudentProfileRepository;

public class ProfileImportService {
    private final AcademicPortalClient portal;
    private final StudentProfileRepository profiles;

    public ProfileImportService(AcademicPortalClient portal, StudentProfileRepository profiles) {
        this.portal = portal;
        this.profiles = profiles;
    }

    @Transactional(noRollbackFor = AcademicPortalException.class)
    public UUID importCurrentProfile(UUID currentUserId) {
        // The adapter locks the active account until this transaction ends, including the first insert.
        var connection = portal.currentConnection(currentUserId);
        var input = portal.fetchProfile(currentUserId, connection);
        var existing = profiles.findByUserId(currentUserId);
        if (existing.isPresent()) {
            var profile = existing.orElseThrow();
            profile.updateSourceProfile(input.studentNumber(), input.programName());
            return profile.getId();
        }
        return profiles.save(new StudentProfile(currentUserId, input.studentNumber(), null, input.programName(), null)).getId();
    }
}
