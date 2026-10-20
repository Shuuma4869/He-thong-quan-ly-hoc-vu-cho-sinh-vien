package vn.edu.phenikaa.ams.auth.api;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

public final class SecurityDtos {
    private SecurityDtos() {}

    public record PasswordRequest(@NotBlank String currentPassword, @NotBlank String newPassword) {
        @Override public String toString() { return "PasswordRequest[redacted]"; }
    }

    public record SessionView(String sessionRef, boolean current, Instant createdAt,
                              Instant lastAccessedAt, Instant expiresAt) {}
}
