package vn.edu.phenikaa.ams.user.api;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;
import vn.edu.phenikaa.ams.user.domain.AppUser;
import vn.edu.phenikaa.ams.user.domain.UserPreferences;

public final class AccountDtos {
    private AccountDtos() {}

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 12, max = 64) String password,
            @NotBlank @Size(max = 80) String displayName) {
        @Override public String toString() { return "RegisterRequest[redacted]"; }
    }

    public record SettingsRequest(
            @Email @Size(max = 254) String notificationEmail,
            @NotBlank @Size(max = 64) String timezone,
            @NotBlank @Pattern(regexp = "vi-VN|en-US") String locale,
            @NotNull UserPreferences.Theme theme) {}

    public record SettingsView(String notificationEmail, String timezone, String locale, UserPreferences.Theme theme) {
        public static SettingsView from(UserPreferences settings) {
            return new SettingsView(settings.getNotificationEmail(), settings.getTimezone(), settings.getLocale(), settings.getTheme());
        }
    }

    public record UserView(UUID id, String email, String displayName, AppUser.Role role,
                           AppUser.Status status, Instant createdAt, SettingsView settings) {
        public static UserView from(AppUser user, UserPreferences settings) {
            return new UserView(user.getId(), user.getEmail(), user.getDisplayName(), user.getRole(),
                    user.getAccountStatus(), user.getCreatedAt(), SettingsView.from(settings));
        }
    }
}
