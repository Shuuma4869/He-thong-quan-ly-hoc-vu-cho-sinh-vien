package vn.edu.phenikaa.ams.calendar.google;

import java.net.URI;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.phenikaa.ams.auth.application.AccountPrincipal;
import vn.edu.phenikaa.ams.calendar.google.GoogleCalendarConnectionStore.Status;

@RestController
class GoogleCalendarController {
    private final ObjectProvider<GoogleCalendarService> services;

    GoogleCalendarController(ObjectProvider<GoogleCalendarService> services) { this.services = services; }

    @GetMapping("/api/me/connections/google-calendar")
    GoogleCalendarService.ConnectionView status(@AuthenticationPrincipal AccountPrincipal principal) {
        var service = services.getIfAvailable();
        return service == null
                ? new GoogleCalendarService.ConnectionView(false, Status.DISCONNECTED, false, null, null)
                : service.status(principal.getUserId());
    }

    @PostMapping("/api/me/connections/google-calendar/authorize")
    AuthorizationView authorize(@AuthenticationPrincipal AccountPrincipal principal) {
        return new AuthorizationView(required().authorize(principal.getUserId()));
    }

    @PostMapping("/api/me/connections/google-calendar/setup")
    GoogleCalendarService.ConnectionView setup(@AuthenticationPrincipal AccountPrincipal principal) {
        return required().setup(principal.getUserId());
    }

    @PostMapping("/api/me/connections/google-calendar/disconnect")
    GoogleCalendarService.DisconnectView disconnect(@AuthenticationPrincipal AccountPrincipal principal) {
        return required().disconnect(principal.getUserId());
    }

    @GetMapping("/api/integrations/google-calendar/callback")
    ResponseEntity<Void> callback(@AuthenticationPrincipal AccountPrincipal principal,
                                  @RequestParam(required = false) String state,
                                  @RequestParam(required = false) String code,
                                  @RequestParam(required = false) String error) {
        String result;
        try { result = required().callback(principal.getUserId(), state, code, error); }
        catch (GoogleCalendarException ex) {
            result = switch (ex.code()) {
                case GOOGLE_AUTH_DENIED -> "cancelled";
                case GOOGLE_CALENDAR_SETUP_FAILED -> "setup";
                default -> "error";
            };
        }
        return ResponseEntity.status(HttpStatus.SEE_OTHER).location(URI.create("/settings?google=" + result))
                .cacheControl(CacheControl.noStore()).header("Referrer-Policy", "no-referrer").build();
    }

    private GoogleCalendarService required() {
        var service = services.getIfAvailable();
        if (service == null) throw new GoogleCalendarException(GoogleCalendarException.Code.GOOGLE_NOT_CONFIGURED);
        return service;
    }

    record AuthorizationView(String authorizationUrl) {}
}
