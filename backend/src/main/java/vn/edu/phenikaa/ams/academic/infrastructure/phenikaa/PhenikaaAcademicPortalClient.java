package vn.edu.phenikaa.ams.academic.infrastructure.phenikaa;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.List;
import java.util.function.Function;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.phenikaa.ams.academic.application.port.*;
import vn.edu.phenikaa.ams.user.domain.AppUser;
import vn.edu.phenikaa.ams.user.infrastructure.UserRepository;
import static vn.edu.phenikaa.ams.academic.application.port.AcademicPortalException.Code.*;

@Transactional(noRollbackFor = AcademicPortalException.class)
public class PhenikaaAcademicPortalClient implements AcademicPortalClient {
    private final PhenikaaConnectionRepository connections;
    private final UserRepository users;
    private final PhenikaaSessionCipher cipher;
    private final PhenikaaHttpClient http;

    public PhenikaaAcademicPortalClient(PhenikaaConnectionRepository connections, UserRepository users,
                                       PhenikaaSessionCipher cipher, PhenikaaHttpClient http) {
        this.connections = connections;
        this.users = users;
        this.cipher = cipher;
        this.http = http;
    }

    /** Trusted local provisioning only; not exposed by a credential-submission endpoint. */
    public StudentConnectionId connect(UUID currentUserId, PhenikaaSessionMaterial material) {
        lockActiveUser(currentUserId);
        var existing = connections.findByUserId(currentUserId);
        UUID id = existing.map(PhenikaaConnection::id).orElseGet(UUID::randomUUID);
        if (existing.isPresent()) {
            var connection = existing.orElseThrow();
            boolean matches;
            try {
                matches = cipher.matchesSubject(connection.encryptedSubject(), connection.keyVersion(), material, id, currentUserId);
            } catch (IllegalStateException ex) { throw new AcademicPortalException(SESSION_INTEGRITY_FAILURE); }
            if (!matches) throw new AcademicPortalException(SOURCE_ACCOUNT_MISMATCH);
        }
        try { http.fetchProfile(material.profile()); }
        catch (PhenikaaClientException ex) {
            var failure = translate(ex);
            existing.ifPresent(connection -> connection.failed(failure.code(), Instant.now()));
            throw failure;
        }
        byte[] encrypted = cipher.encrypt(material, id, currentUserId);
        byte[] subject = cipher.encryptSubject(material, id, currentUserId);
        Instant now = Instant.now();
        if (existing.isPresent()) existing.orElseThrow().reconnect(encrypted, subject, cipher.keyVersion(), now);
        else connections.save(new PhenikaaConnection(id, currentUserId, encrypted, subject, cipher.keyVersion(), now));
        return new StudentConnectionId(id);
    }

    @Override public StudentConnectionId currentConnection(UUID currentUserId) {
        lockActiveUser(currentUserId);
        var connection = connections.findByUserId(currentUserId)
                .orElseThrow(() -> new AcademicPortalException(CONNECTION_UNAVAILABLE));
        return new StudentConnectionId(connection.id());
    }

    @Override public ProfileObservation fetchProfile(UUID currentUserId, StudentConnectionId connectionId) {
        return access(currentUserId, connectionId, material -> http.fetchProfile(material.profile()));
    }

    @Override public ScheduleObservation fetchSchedule(UUID currentUserId, StudentConnectionId connectionId,
                                                       LocalDate from, LocalDate through) {
        return access(currentUserId, connectionId, material -> http.fetchSchedule(material.schedule(), from, through));
    }

    @Override public List<ExamPeriod> fetchExamPeriods(UUID currentUserId, StudentConnectionId connectionId) {
        return access(currentUserId, connectionId, material -> http.fetchExamPeriods(examSession(material)));
    }

    @Override public ExamObservation fetchExams(UUID currentUserId, StudentConnectionId connectionId, ExamPeriod period) {
        return access(currentUserId, connectionId, material -> http.fetchExams(examSession(material), period));
    }

    private static PhenikaaSession examSession(PhenikaaSessionMaterial material) {
        if (material.exam() == null) throw new AcademicPortalException(CONNECTION_UNAVAILABLE);
        return material.exam();
    }

    @Override public List<AcademicProgram> fetchAcademicPrograms(UUID userId, StudentConnectionId connectionId) {
        return access(userId, connectionId, material -> http.fetchAcademicPrograms(academicSession(material)));
    }

    @Override public List<AcademicPeriod> fetchAcademicPeriods(UUID userId, StudentConnectionId connectionId) {
        return access(userId, connectionId, material -> http.fetchAcademicPeriods(academicSession(material)));
    }

    @Override public AcademicRecordObservation fetchAcademicRecords(UUID userId, StudentConnectionId connectionId,
                                                                    AcademicProgram program) {
        return access(userId, connectionId, material -> http.fetchAcademicRecords(academicSession(material), program));
    }

    private static PhenikaaSession academicSession(PhenikaaSessionMaterial material) {
        if (material.academic() == null) throw new AcademicPortalException(CONNECTION_UNAVAILABLE);
        return material.academic();
    }

    private <T> T access(UUID userId, StudentConnectionId id, Function<PhenikaaSessionMaterial, T> operation) {
        lockActiveUser(userId);
        var connection = connections.findByIdAndUserId(id.value(), userId)
                .orElseThrow(() -> new AcademicPortalException(CONNECTION_UNAVAILABLE));
        if (connection.status() != PhenikaaConnection.Status.CONNECTED)
            throw new AcademicPortalException(CONNECTION_UNAVAILABLE);
        PhenikaaSessionMaterial decrypted;
        try { decrypted = cipher.decrypt(connection.encryptedSession(), connection.keyVersion(), id.value(), userId); }
        catch (IllegalStateException ex) {
            connection.failed(SESSION_INTEGRITY_FAILURE, Instant.now());
            throw new AcademicPortalException(SESSION_INTEGRITY_FAILURE);
        }
        try (decrypted) {
            T result = operation.apply(decrypted);
            connection.successful(Instant.now());
            return result;
        } catch (PhenikaaClientException ex) {
            var failure = translate(ex);
            connection.failed(failure.code(), Instant.now());
            throw failure;
        }
    }

    public void disconnect(UUID currentUserId) {
        lockActiveUser(currentUserId);
        connections.findByUserId(currentUserId).ifPresent(connection -> connection.disconnect(Instant.now()));
    }

    public PhenikaaConnection.ConnectionView status(UUID currentUserId) {
        lockActiveUser(currentUserId);
        return connections.findByUserId(currentUserId).map(PhenikaaConnection::view)
                .orElseGet(() -> new PhenikaaConnection.ConnectionView(PhenikaaConnection.Status.DISCONNECTED,
                        null, null, null, null, false));
    }

    private void lockActiveUser(UUID userId) {
        var user = users.lockById(userId).orElseThrow(() -> new AcademicPortalException(CONNECTION_UNAVAILABLE));
        if (user.getAccountStatus() != AppUser.Status.ACTIVE) throw new AcademicPortalException(CONNECTION_UNAVAILABLE);
    }

    private static AcademicPortalException translate(PhenikaaClientException ex) {
        return new AcademicPortalException(AcademicPortalException.Code.valueOf(ex.code().name()));
    }
}
