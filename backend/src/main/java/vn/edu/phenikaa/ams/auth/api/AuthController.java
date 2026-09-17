package vn.edu.phenikaa.ams.auth.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import vn.edu.phenikaa.ams.user.api.AccountDtos.RegisterRequest;
import vn.edu.phenikaa.ams.user.application.AccountService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AccountService accounts;
    public AuthController(AccountService accounts) { this.accounts = accounts; }

    @GetMapping("/csrf")
    public CsrfToken csrf(CsrfToken token) { return token; }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public void register(@Valid @RequestBody RegisterRequest request) { accounts.register(request); }
}
