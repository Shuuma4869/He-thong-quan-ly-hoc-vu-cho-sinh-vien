package vn.edu.phenikaa.ams.calendar.google;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import vn.edu.phenikaa.ams.calendar.google.GoogleCalendarConnectionStore.Connection;
import vn.edu.phenikaa.ams.calendar.google.GoogleCalendarConnectionStore.Status;
import vn.edu.phenikaa.ams.calendar.google.GoogleTokenCipher.TokenMaterial;

final class GoogleAccessTokenProvider {
    private static final Duration REFRESH_EARLY = Duration.ofMinutes(1);
    private final GoogleCalendarConnectionStore store;
    private final GoogleTokenCipher cipher;
    private final GoogleOAuthGateway oauth;
    private final GoogleOperationLock locks;
    private final Clock clock;

    GoogleAccessTokenProvider(GoogleCalendarConnectionStore store, GoogleTokenCipher cipher,
                              GoogleOAuthGateway oauth, GoogleOperationLock locks, Clock clock) {
        this.store = store; this.cipher = cipher; this.oauth = oauth; this.locks = locks; this.clock = clock;
    }

    String accessToken(UUID userId) {
        Connection current = active(userId);
        if (current.tokenExpiresAt().isAfter(clock.instant().plus(REFRESH_EARLY)))
            return decrypt(current).accessToken();
        var lease = locks.acquire(userId, "refresh");
        if (lease == null) return awaitWinner(userId, current.version());
        try (lease) {
            current = active(userId);
            if (current.tokenExpiresAt().isAfter(clock.instant().plus(REFRESH_EARLY)))
                return decrypt(current).accessToken();
            TokenMaterial previous = decrypt(current);
            GoogleOAuthGateway.Tokens renewed;
            try {
                renewed = oauth.refresh(previous.accessToken(), previous.refreshToken());
            } catch (GoogleCalendarException ex) {
                if (ex.code() == GoogleCalendarException.Code.GOOGLE_RECONNECTION_REQUIRED)
                    store.requireReconnect(userId, current.version(), clock.instant());
                throw ex;
            }
            String refreshToken = renewed.refreshToken() == null ? previous.refreshToken() : renewed.refreshToken();
            byte[] encrypted = cipher.encrypt(new TokenMaterial(renewed.accessToken(), refreshToken),
                    current.id(), userId);
            if (store.refresh(userId, current.version(), encrypted, renewed.expiresAt(), clock.instant()) != 1)
                return awaitWinner(userId, current.version());
            return renewed.accessToken();
        }
    }

    private String awaitWinner(UUID userId, long oldVersion) {
        for (int attempt = 0; attempt < 50; attempt++) {
            var latest = active(userId);
            if (latest.version() != oldVersion && latest.tokenExpiresAt().isAfter(clock.instant().plus(REFRESH_EARLY)))
                return decrypt(latest).accessToken();
            try { Thread.sleep(100); }
            catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new GoogleCalendarException(GoogleCalendarException.Code.GOOGLE_UNAVAILABLE);
    }

    private Connection active(UUID userId) {
        return store.find(userId).filter(c -> c.status() == Status.CONNECTED || c.status() == Status.SETUP_REQUIRED)
                .orElseThrow(() -> new GoogleCalendarException(GoogleCalendarException.Code.GOOGLE_RECONNECTION_REQUIRED));
    }

    private TokenMaterial decrypt(Connection connection) {
        return cipher.decrypt(connection.encryptedTokens(), connection.keyVersion(),
                connection.id(), connection.userId());
    }
}
