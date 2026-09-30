package vn.edu.phenikaa.ams.academic.application;

import java.util.UUID;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.edu.phenikaa.ams.academic.application.port.AcademicPortalClient;
import vn.edu.phenikaa.ams.academic.application.port.AcademicPortalException;
import vn.edu.phenikaa.ams.academic.domain.StudentProfile;
import vn.edu.phenikaa.ams.academic.infrastructure.StudentProfileRepository;
import static vn.edu.phenikaa.ams.academic.application.port.AcademicPortalException.Code.CONNECTION_UNAVAILABLE;

public class ProfileImportService {
    private final AcademicPortalClient portal;
    private final StudentProfileRepository profiles;
    private final TransactionTemplate writes;

    public ProfileImportService(AcademicPortalClient portal, StudentProfileRepository profiles,
                                PlatformTransactionManager transactions) {
        this.portal = portal;
        this.profiles = profiles;
        this.writes = new TransactionTemplate(transactions);
    }

    public UUID importCurrentProfile(UUID currentUserId) {
        return importCurrentProfile(currentUserId, () -> {});
    }

    public UUID importCurrentProfile(UUID currentUserId, Runnable beforeWrite) {
        var connection = portal.currentConnection(currentUserId);
        var generation = portal.connectionInfo(currentUserId).authenticatedAt();
        var input = portal.fetchProfile(currentUserId, connection);
        return writes.execute(status -> {
            var owned = portal.currentConnection(currentUserId);
            var current = portal.connectionInfo(currentUserId);
            if (!owned.equals(connection)
                    || current.state() != AcademicPortalClient.ConnectionInfo.State.CONNECTED
                    || !java.util.Objects.equals(current.authenticatedAt(), generation))
                throw new AcademicPortalException(CONNECTION_UNAVAILABLE);
            beforeWrite.run();
            var existing = profiles.findByUserId(currentUserId);
            if (existing.isPresent()) {
                var profile = existing.orElseThrow();
                profile.updateSourceProfile(input.studentNumber(), input.programName());
                return profile.getId();
            }
            return profiles.save(new StudentProfile(currentUserId, input.studentNumber(), null, input.programName(), null)).getId();
        });
    }
}
