package vn.edu.phenikaa.ams.academic.infrastructure.phenikaa;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import vn.edu.phenikaa.ams.academic.application.port.AcademicPortalException;

@Entity
@Table(name = "phenikaa_connection")
public class PhenikaaConnection {
    public enum Status { CONNECTED, RECONNECTION_REQUIRED, DISCONNECTED }
    @Id private UUID id;
    @Column(nullable = false, unique = true, updatable = false) private UUID userId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private Status status;
    @Column(columnDefinition = "bytea") private byte[] encryptedSession;
    @Column(nullable = false, columnDefinition = "bytea") private byte[] encryptedSubject;
    @Column(nullable = false) private int encryptionKeyVersion;
    private Instant sessionExpiresAt;
    @Column(nullable = false) private Instant lastAuthenticatedAt;
    private Instant lastSuccessfulAccessAt;
    private Instant lastFailedAccessAt;
    @Enumerated(EnumType.STRING) @Column(length = 40) private AcademicPortalException.Code lastFailureCode;
    @Column(nullable = false, updatable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
    @Version private long version;

    protected PhenikaaConnection() {}
    PhenikaaConnection(UUID id, UUID userId, byte[] session, byte[] subject, int keyVersion, Instant now) {
        this.id = id;
        this.userId = userId;
        createdAt = now;
        reconnect(session, subject, keyVersion, now);
    }

    void reconnect(byte[] session, byte[] subject, int keyVersion, Instant now) {
        encryptedSession = session.clone();
        encryptedSubject = subject.clone();
        encryptionKeyVersion = keyVersion;
        status = Status.CONNECTED;
        sessionExpiresAt = null;
        lastAuthenticatedAt = now;
        successful(now);
    }

    void successful(Instant now) { lastSuccessfulAccessAt = now; updatedAt = now; }
    void failed(AcademicPortalException.Code code, Instant now) {
        lastFailureCode = code;
        lastFailedAccessAt = now;
        updatedAt = now;
        if (code == AcademicPortalException.Code.SESSION_EXPIRED && status != Status.DISCONNECTED)
            status = Status.RECONNECTION_REQUIRED;
    }
    void disconnect(Instant now) {
        encryptedSession = null;
        sessionExpiresAt = null;
        status = Status.DISCONNECTED;
        updatedAt = now;
    }

    UUID id() { return id; }
    byte[] encryptedSession() { return encryptedSession == null ? null : encryptedSession.clone(); }
    byte[] encryptedSubject() { return encryptedSubject.clone(); }
    int keyVersion() { return encryptionKeyVersion; }
    Status status() { return status; }
    public ConnectionView view() {
        return new ConnectionView(status, lastAuthenticatedAt, lastSuccessfulAccessAt,
                lastFailedAccessAt, lastFailureCode, status == Status.RECONNECTION_REQUIRED);
    }
    public record ConnectionView(Status status, Instant lastAuthenticatedAt, Instant lastSuccessfulAccessAt,
                                 Instant lastFailedAccessAt, AcademicPortalException.Code lastFailureCode,
                                 boolean reconnectionRequired) {}
    @Override public String toString() { return "PhenikaaConnection[redacted]"; }
}
