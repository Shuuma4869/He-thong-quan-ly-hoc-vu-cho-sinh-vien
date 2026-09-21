package vn.edu.phenikaa.ams.academic.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.phenikaa.ams.academic.application.port.AcademicPortalException;
import vn.edu.phenikaa.ams.academic.infrastructure.phenikaa.PhenikaaAcademicPortalClient;
import vn.edu.phenikaa.ams.academic.infrastructure.phenikaa.PhenikaaConnection;
import vn.edu.phenikaa.ams.auth.application.AccountPrincipal;

@RestController
@ConditionalOnProperty(name = "ams.phenikaa.enabled", havingValue = "true")
public class PhenikaaConnectionController {
    private final PhenikaaAcademicPortalClient portal;
    public PhenikaaConnectionController(PhenikaaAcademicPortalClient portal) { this.portal = portal; }

    @GetMapping("/api/me/connections/phenikaa")
    public PhenikaaConnection.ConnectionView status(@AuthenticationPrincipal AccountPrincipal principal) {
        try { return portal.status(principal.getUserId()); }
        catch (AcademicPortalException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Không thể đọc trạng thái kết nối của tài khoản này");
        }
    }
}
