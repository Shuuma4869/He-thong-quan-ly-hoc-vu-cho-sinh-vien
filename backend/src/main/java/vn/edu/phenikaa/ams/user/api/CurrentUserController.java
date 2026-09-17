package vn.edu.phenikaa.ams.user.api;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.edu.phenikaa.ams.auth.application.AccountPrincipal;
import vn.edu.phenikaa.ams.user.api.AccountDtos.*;
import vn.edu.phenikaa.ams.user.application.AccountService;

@RestController
@RequestMapping("/api/me")
public class CurrentUserController {
    private final AccountService accounts;
    public CurrentUserController(AccountService accounts) { this.accounts = accounts; }

    @GetMapping
    public UserView me(@AuthenticationPrincipal AccountPrincipal principal) {
        return accounts.currentUser(principal.getUserId());
    }

    @PutMapping("/settings")
    public SettingsView settings(@AuthenticationPrincipal AccountPrincipal principal,
                                 @Valid @RequestBody SettingsRequest request) {
        return accounts.updateSettings(principal.getUserId(), request);
    }
}
