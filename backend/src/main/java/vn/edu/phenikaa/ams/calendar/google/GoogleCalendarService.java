package vn.edu.phenikaa.ams.calendar.google;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import vn.edu.phenikaa.ams.calendar.google.GoogleCalendarConnectionStore.Connection;
import vn.edu.phenikaa.ams.calendar.google.GoogleCalendarConnectionStore.Status;
import vn.edu.phenikaa.ams.calendar.google.GoogleTokenCipher.TokenMaterial;
import vn.edu.phenikaa.ams.user.domain.AppUser;
import vn.edu.phenikaa.ams.user.infrastructure.UserRepository;

final class GoogleCalendarService {
    private final GoogleCalendarConnectionStore store;
    private final GoogleTokenCipher cipher;
    private final GoogleOAuthStateStore states;
    private final GoogleOAuthGateway oauth;
    private final GoogleCalendarGateway calendar;
    private final GoogleAccessTokenProvider tokens;
    private final GoogleOperationLock locks;
    private final UserRepository users;
    private final Clock clock;

    GoogleCalendarService(GoogleCalendarConnectionStore store, GoogleTokenCipher cipher,
                          GoogleOAuthStateStore states, GoogleOAuthGateway oauth,
                          GoogleCalendarGateway calendar, GoogleAccessTokenProvider tokens,
                          GoogleOperationLock locks, UserRepository users, Clock clock) {
        this.store = store; this.cipher = cipher; this.states = states; this.oauth = oauth;
        this.calendar = calendar; this.tokens = tokens; this.locks = locks; this.users = users; this.clock = clock;
    }

    ConnectionView status(UUID userId) {
        requireActive(userId);
        return store.find(userId).map(c -> new ConnectionView(true, c.status(),
                c.status() == Status.CONNECTED, c.connectedAt(), c.lastSuccessfulAccessAt()))
                .orElse(new ConnectionView(true, Status.DISCONNECTED, false, null, null));
    }

    String authorize(UUID userId) {
        requireActive(userId);
        var connection = store.find(userId);
        long expectedVersion = connection.map(Connection::version).orElse(-1L);
        boolean renewConsent = connection.map(c -> c.status() == Status.RECONNECTION_REQUIRED
                || c.status() == Status.DISCONNECTED).orElse(false);
        var started = states.start(userId, expectedVersion);
        return oauth.authorizationUrl(started.state(), started.challenge(), renewConsent);
    }

    String callback(UUID userId, String state, String code, String error) {
        var pending = states.consume(state, userId);
        requireActive(userId);
        var existing = store.find(userId);
        if (existing.map(Connection::version).orElse(-1L) != pending.expectedVersion())
            throw new GoogleCalendarException(GoogleCalendarException.Code.GOOGLE_STATE_INVALID);
        if ("access_denied".equals(error))
            throw new GoogleCalendarException(GoogleCalendarException.Code.GOOGLE_AUTH_DENIED);
        if (error != null) return "error";
        if (code == null || code.isBlank() || code.length() > 2048)
            throw new GoogleCalendarException(GoogleCalendarException.Code.GOOGLE_STATE_INVALID);

        var granted = oauth.exchange(code, state, pending.verifier());
        if (granted.refreshToken() == null || granted.refreshToken().isBlank()) {
            if (existing.isEmpty()) store.createNeedsReconnect(UUID.randomUUID(), userId, cipher.keyVersion(), clock.instant());
            throw new GoogleCalendarException(GoogleCalendarException.Code.GOOGLE_RECONNECTION_REQUIRED);
        }
        UUID connectionId = existing.map(Connection::id).orElseGet(UUID::randomUUID);
        byte[] encrypted = cipher.encrypt(new TokenMaterial(granted.accessToken(), granted.refreshToken()),
                connectionId, userId);
        int updated = existing.isEmpty()
                ? store.create(connectionId, userId, encrypted, cipher.keyVersion(), granted.expiresAt(), clock.instant())
                : store.replaceTokens(userId, pending.expectedVersion(), encrypted, cipher.keyVersion(),
                        granted.expiresAt(), clock.instant());
        if (updated != 1) throw new GoogleCalendarException(GoogleCalendarException.Code.GOOGLE_STATE_INVALID);
        ensureCalendar(userId);
        return "connected";
    }

    ConnectionView setup(UUID userId) {
        requireActive(userId);
        ensureCalendar(userId);
        return status(userId);
    }

    DisconnectView disconnect(UUID userId) {
        requireActive(userId);
        var current = store.find(userId);
        if (current.isEmpty() || current.get().status() == Status.DISCONNECTED)
            return new DisconnectView(false);
        var connection = current.get();
        boolean revoked = false;
        if (connection.encryptedTokens() != null) {
            try {
                var material = cipher.decrypt(connection.encryptedTokens(), connection.keyVersion(),
                        connection.id(), userId);
                revoked = oauth.revoke(material.refreshToken());
            } catch (IllegalStateException ex) {
                // A lost/rotated key must not prevent removal of the local connection.
            }
        }
        if (store.disconnect(userId, connection.version(), clock.instant()) != 1)
            throw new GoogleCalendarException(GoogleCalendarException.Code.GOOGLE_UNAVAILABLE);
        return new DisconnectView(revoked);
    }

    private void ensureCalendar(UUID userId) {
        var lease = locks.acquire(userId, "setup");
        if (lease == null) throw new GoogleCalendarException(GoogleCalendarException.Code.GOOGLE_UNAVAILABLE);
        try (lease) {
            var current = store.find(userId).orElseThrow(() ->
                    new GoogleCalendarException(GoogleCalendarException.Code.GOOGLE_RECONNECTION_REQUIRED));
            if (current.status() == Status.CONNECTED) return;
            if (current.status() != Status.SETUP_REQUIRED)
                throw new GoogleCalendarException(GoogleCalendarException.Code.GOOGLE_RECONNECTION_REQUIRED);
            String access = tokens.accessToken(userId);
            current = store.find(userId).orElseThrow();
            if (current.status() != Status.SETUP_REQUIRED)
                throw new GoogleCalendarException(GoogleCalendarException.Code.GOOGLE_RECONNECTION_REQUIRED);
            String calendarId;
            try {
                calendarId = current.calendarId() != null && calendar.exists(access, current.calendarId())
                        ? current.calendarId() : calendar.create(access);
            } catch (GoogleCalendarException ex) {
                if (ex.code() == GoogleCalendarException.Code.GOOGLE_RECONNECTION_REQUIRED)
                    store.requireReconnect(userId, current.version(), clock.instant());
                throw ex;
            }
            if (calendarId.isBlank() || calendarId.length() > 1024
                    || store.ready(userId, current.version(), calendarId, clock.instant()) != 1)
                throw new GoogleCalendarException(GoogleCalendarException.Code.GOOGLE_CALENDAR_SETUP_FAILED);
        }
    }

    private void requireActive(UUID userId) {
        if (users.findById(userId).filter(u -> u.getAccountStatus() == AppUser.Status.ACTIVE).isEmpty())
            throw new AccessDeniedException("Account unavailable");
    }

    record ConnectionView(boolean available, Status status, boolean calendarReady,
                          Instant connectedAt, Instant lastSuccessfulAccessAt) {}
    record DisconnectView(boolean remoteRevocationConfirmed) {}
}
