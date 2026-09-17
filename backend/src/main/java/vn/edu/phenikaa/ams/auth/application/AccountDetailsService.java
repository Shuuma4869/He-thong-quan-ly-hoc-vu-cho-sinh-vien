package vn.edu.phenikaa.ams.auth.application;

import java.util.Locale;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import vn.edu.phenikaa.ams.user.infrastructure.UserRepository;

@Service
public class AccountDetailsService implements UserDetailsService {
    private final UserRepository users;

    public AccountDetailsService(UserRepository users) { this.users = users; }

    @Override
    public UserDetails loadUserByUsername(String email) {
        return users.findByEmail(email.trim().toLowerCase(Locale.ROOT))
                .map(AccountPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
    }
}
