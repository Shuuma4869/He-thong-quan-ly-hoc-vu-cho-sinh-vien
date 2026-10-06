package vn.edu.phenikaa.ams.user.application;

import java.time.ZoneId;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.phenikaa.ams.auth.application.AccountPasswordEncoder;
import vn.edu.phenikaa.ams.user.api.AccountDtos.*;
import vn.edu.phenikaa.ams.user.domain.AppUser;
import vn.edu.phenikaa.ams.user.domain.UserPreferences;
import vn.edu.phenikaa.ams.user.infrastructure.UserRepository;
import vn.edu.phenikaa.ams.user.infrastructure.UserPreferencesRepository;

@Service
@Transactional
public class AccountService {
    private final UserRepository users;
    private final UserPreferencesRepository preferences;
    private final PasswordEncoder encoder;
    private final JdbcTemplate jdbc;

    public AccountService(UserRepository users, UserPreferencesRepository preferences, PasswordEncoder encoder,
                          JdbcTemplate jdbc) {
        this.users = users;
        this.preferences = preferences;
        this.encoder = encoder;
        this.jdbc = jdbc;
    }

    public void register(RegisterRequest request) {
        if (!AccountPasswordEncoder.hasValidLength(request.password())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mật khẩu cần 12–64 ký tự và không quá 72 byte UTF-8.");
        }
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        var user = new AppUser(email, encoder.encode(request.password()), request.displayName().trim());
        int inserted = users.insertAccount(user.getId(), user.getEmail(), user.getPasswordHash(), user.getDisplayName(), user.getCreatedAt());
        if (inserted == 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "Không thể đăng ký với email này.");
        preferences.save(new UserPreferences(user.getId()));
    }

    @Transactional(readOnly = true)
    public UserView currentUser(UUID userId) {
        return UserView.from(requireActiveUser(userId), preferences.findById(userId).orElseThrow());
    }

    public SettingsView updateSettings(UUID userId, SettingsRequest request) {
        requireActiveUser(userId);
        if (!ZoneId.getAvailableZoneIds().contains(request.timezone())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Múi giờ không hợp lệ.");
        }
        var settings = preferences.findLocked(userId).orElseThrow();
        String email = request.notificationEmail();
        String normalizedEmail = email == null || email.isBlank() ? null : email.trim().toLowerCase(Locale.ROOT);
        try {
            boolean alerts = request.syncEmailAlertsEnabled() == null
                    ? java.util.Objects.equals(settings.getNotificationEmail(), normalizedEmail)
                        && settings.isSyncEmailAlertsEnabled()
                    : request.syncEmailAlertsEnabled();
            boolean changed = settings.update(normalizedEmail, request.timezone(), request.locale(), request.theme(), alerts);
            if (changed) jdbc.update("""
                    update notification_email_verification set code_hash = repeat('0', 64),
                        email_hash = repeat('0', 64), expires_at = now(), attempt_count = 5
                    where user_id = ?
                    """, userId);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cần xác minh email trước khi bật cảnh báo đồng bộ.");
        }
        return SettingsView.from(settings);
    }

    private AppUser requireActiveUser(UUID id) {
        return users.findById(id).filter(user -> user.getAccountStatus() == AppUser.Status.ACTIVE)
                .orElseThrow(() -> new AccessDeniedException("Account unavailable"));
    }
}
