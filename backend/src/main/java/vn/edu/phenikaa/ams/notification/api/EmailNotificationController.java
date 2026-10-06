package vn.edu.phenikaa.ams.notification.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.phenikaa.ams.auth.application.AccountPrincipal;
import vn.edu.phenikaa.ams.notification.application.EmailVerificationService;
import vn.edu.phenikaa.ams.notification.application.EmailVerificationService.EmailStatus;

@RestController
@RequestMapping("/api/me/notifications/email")
public class EmailNotificationController {
    private final EmailVerificationService service;

    public EmailNotificationController(EmailVerificationService service) { this.service = service; }

    @GetMapping
    public EmailStatus status(@AuthenticationPrincipal AccountPrincipal principal) {
        return service.status(principal.getUserId());
    }

    @PostMapping("/verification")
    public ResponseEntity<Void> request(@AuthenticationPrincipal AccountPrincipal principal) {
        service.request(principal.getUserId());
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @PostMapping("/verification/confirm")
    public EmailStatus confirm(@AuthenticationPrincipal AccountPrincipal principal,
                               @Valid @RequestBody ConfirmRequest request) {
        return service.confirm(principal.getUserId(), request.code());
    }

    public record ConfirmRequest(@NotBlank String code) {}
}
