package vn.edu.phenikaa.ams.auth.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.phenikaa.ams.auth.application.AccountPrincipal;
import vn.edu.phenikaa.ams.auth.application.AccountSecurityService;
import vn.edu.phenikaa.ams.auth.api.SecurityDtos.PasswordRequest;
import vn.edu.phenikaa.ams.auth.api.SecurityDtos.SessionView;

@RestController
@RequestMapping("/api/me/security")
public class AccountSecurityController {
    private final AccountSecurityService security;

    public AccountSecurityController(AccountSecurityService security) {
        this.security = security;
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<SessionView>> sessions(@AuthenticationPrincipal AccountPrincipal principal,
            HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(security.list(principal, request.getSession(false)));
    }

    @DeleteMapping("/sessions/others")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeOthers(@AuthenticationPrincipal AccountPrincipal principal, HttpServletRequest request) {
        security.revokeOthers(principal, request.getSession(false));
    }

    @DeleteMapping("/sessions/{sessionRef}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeOne(@AuthenticationPrincipal AccountPrincipal principal, HttpServletRequest request,
            @PathVariable String sessionRef) {
        security.revokeOne(principal, request.getSession(false), sessionRef);
    }

    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal AccountPrincipal principal, HttpServletRequest request,
            @Valid @RequestBody PasswordRequest body) {
        security.changePassword(principal, request.getSession(false), body);
    }
}
