package vn.edu.phenikaa.ams.auth.application;

import java.util.List;
import java.util.UUID;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import vn.edu.phenikaa.ams.user.domain.AppUser;

public class AccountPrincipal extends User {
    private static final long serialVersionUID = 1L;
    private final UUID userId;
    private final long sessionGeneration;
    private final boolean versionedSession;

    public AccountPrincipal(AppUser user) {
        super(user.getEmail(), user.getPasswordHash(), user.getAccountStatus() == AppUser.Status.ACTIVE,
                true, true, true, List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
        this.userId = user.getId();
        this.sessionGeneration = user.getSessionGeneration();
        this.versionedSession = true;
    }

    public UUID getUserId() { return userId; }
    public long getSessionGeneration() { return sessionGeneration; }
    public boolean isVersionedSession() { return versionedSession; }
}
