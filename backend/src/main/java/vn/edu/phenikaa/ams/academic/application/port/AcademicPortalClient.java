package vn.edu.phenikaa.ams.academic.application.port;

import java.util.UUID;
import vn.edu.phenikaa.ams.academic.domain.AcademicSnapshot;

public interface AcademicPortalClient {

    AcademicSnapshot fetchSnapshot(StudentConnectionId connectionId);

    record StudentConnectionId(UUID value) {}
}
