package vn.edu.phenikaa.ams.user.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_preferences")
public class UserPreferences {
    public enum Theme { LIGHT, DARK, SYSTEM }

    @Id
    private UUID userId;
    @Column(length = 254)
    private String notificationEmail;
    @Column(nullable = false, length = 64)
    private String timezone = "Asia/Ho_Chi_Minh";
    @Column(nullable = false, length = 16)
    private String locale = "vi-VN";
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Theme theme = Theme.SYSTEM;
    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    protected UserPreferences() {}
    public UserPreferences(UUID userId) { this.userId = userId; }

    public void update(String notificationEmail, String timezone, String locale, Theme theme) {
        this.notificationEmail = notificationEmail;
        this.timezone = timezone;
        this.locale = locale;
        this.theme = theme;
        this.updatedAt = Instant.now();
    }

    public String getNotificationEmail() { return notificationEmail; }
    public String getTimezone() { return timezone; }
    public String getLocale() { return locale; }
    public Theme getTheme() { return theme; }
}
