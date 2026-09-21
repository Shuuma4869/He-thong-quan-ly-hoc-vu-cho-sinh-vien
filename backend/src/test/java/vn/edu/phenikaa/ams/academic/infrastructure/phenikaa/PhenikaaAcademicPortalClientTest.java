package vn.edu.phenikaa.ams.academic.infrastructure.phenikaa;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PhenikaaAcademicPortalClientTest {

    @Test
    void restrictsPortalBaseUriToExpectedHost() {
        assertThat(PhenikaaHttpTransport.PORTAL.getScheme()).isEqualTo("https");
        assertThat(PhenikaaHttpTransport.PORTAL.getHost())
                .isEqualTo("qldtbeta.phenikaa-uni.edu.vn");
    }
}
