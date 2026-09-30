package vn.edu.phenikaa.ams.academic.application;

import java.util.UUID;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.edu.phenikaa.ams.academic.application.port.*;
import vn.edu.phenikaa.ams.academic.infrastructure.StudentProfileRepository;
import vn.edu.phenikaa.ams.academic.infrastructure.phenikaa.PhenikaaCurriculumStore;
import static vn.edu.phenikaa.ams.academic.application.port.AcademicPortalException.Code.CONNECTION_UNAVAILABLE;

public class CurriculumImportService {
    private final AcademicPortalClient portal;
    private final StudentProfileRepository profiles;
    private final PhenikaaCurriculumStore store;
    private final TransactionTemplate writes;
    public CurriculumImportService(AcademicPortalClient portal, StudentProfileRepository profiles,
                                   PhenikaaCurriculumStore store, PlatformTransactionManager transactions) {
        this.portal = portal; this.profiles = profiles; this.store = store;
        this.writes = new TransactionTemplate(transactions);
    }

    public UUID importCurriculum(UUID currentUserId, CurriculumOption curriculum) {
        return importCurriculum(currentUserId, curriculum, () -> {});
    }

    public UUID importCurriculum(UUID currentUserId, CurriculumOption curriculum, Runnable beforeWrite) {
        var connection = portal.currentConnection(currentUserId);
        var generation = portal.connectionInfo(currentUserId).authenticatedAt();
        if (profiles.findByUserId(currentUserId).isEmpty())
            throw new CurriculumImportException(CurriculumImportException.Code.PROFILE_REQUIRED);
        var observation = portal.fetchCurriculum(currentUserId, connection, curriculum);
        return writes.execute(status -> {
            var owned = portal.currentConnection(currentUserId);
            var current = portal.connectionInfo(currentUserId);
            if (!owned.equals(connection)
                    || current.state() != AcademicPortalClient.ConnectionInfo.State.CONNECTED
                    || !java.util.Objects.equals(current.authenticatedAt(), generation))
                throw new AcademicPortalException(CONNECTION_UNAVAILABLE);
            beforeWrite.run();
            var profile = profiles.findByUserId(currentUserId)
                    .orElseThrow(() -> new CurriculumImportException(CurriculumImportException.Code.PROFILE_REQUIRED));
            return store.upsert(profile.getId(), observation);
        });
    }

    public int refreshAvailableCurricula(UUID currentUserId, Runnable beforeWrite) {
        var connection = portal.currentConnection(currentUserId);
        if (profiles.findByUserId(currentUserId).isEmpty())
            throw new CurriculumImportException(CurriculumImportException.Code.PROFILE_REQUIRED);
        var options = portal.fetchCurricula(currentUserId, connection);
        if (options.isEmpty() || options.size() > 8)
            throw new CurriculumImportException(CurriculumImportException.Code.INVALID_OBSERVATION);
        for (var option : options) importCurriculum(currentUserId, option, beforeWrite);
        return options.size();
    }
}
