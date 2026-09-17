package vn.edu.phenikaa.ams.academic.infrastructure.phenikaa;

import java.net.URI;
import vn.edu.phenikaa.ams.academic.application.port.AcademicPortalClient;
import vn.edu.phenikaa.ams.academic.domain.AcademicSnapshot;

public final class PhenikaaAcademicPortalClient implements AcademicPortalClient {

    static final URI ALLOWED_PORTAL = URI.create("https://qldtbeta.phenikaa-uni.edu.vn/");

    @Override
    public AcademicSnapshot fetchSnapshot(StudentConnectionId connectionId) {
        throw new UnsupportedOperationException(
                "Phenikaa integration is not implemented in the bootstrap phase");
    }
}
