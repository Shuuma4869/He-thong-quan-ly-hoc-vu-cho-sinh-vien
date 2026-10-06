package vn.edu.phenikaa.ams.academic.infrastructure.phenikaa;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.List;
import java.util.function.Function;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
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
    private final TransactionTemplate readTransactions;

    public PhenikaaAcademicPortalClient(PhenikaaConnectionRepository connections, UserRepository users,
                                       PhenikaaSessionCipher cipher, PhenikaaHttpClient http,
                                       PlatformTransactionManager transactions) {
        this.connections = connections;
        this.users = users;
        this.cipher = cipher;
        this.http = http;
        this.readTransactions = new TransactionTemplate(transactions);
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

    @Override public ConnectionInfo connectionInfo(UUID currentUserId) {
        requireActiveUser(currentUserId);
        return connections.findByUserId(currentUserId)
                .map(connection -> new ConnectionInfo(switch (connection.status()) {
                    case CONNECTED -> ConnectionInfo.State.CONNECTED;
                    case RECONNECTION_REQUIRED -> ConnectionInfo.State.RECONNECTION_REQUIRED;
                    case DISCONNECTED -> ConnectionInfo.State.DISCONNECTED;
                }, connection.view().lastSuccessfulAccessAt(), connection.authenticatedAt()))
                .orElseGet(() -> new ConnectionInfo(ConnectionInfo.State.NOT_CONNECTED, null, null));
    }

    @Override @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ProfileObservation fetchProfile(UUID currentUserId, StudentConnectionId connectionId) {
        return accessRead(currentUserId, connectionId, material -> http.fetchProfile(material.profile()));
    }

    @Override @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ScheduleObservation fetchSchedule(UUID currentUserId, StudentConnectionId connectionId,
                                                       LocalDate from, LocalDate through) {
        return accessRead(currentUserId, connectionId, material -> http.fetchSchedule(material.schedule(), from, through));
    }

    @Override @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<CurriculumOption> fetchCurricula(UUID user, StudentConnectionId connection) {
        return accessRead(user, connection, material -> http.fetchCurricula(curriculumSession(material)));
    }

    @Override @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CurriculumObservation fetchCurriculum(UUID user, StudentConnectionId connection, CurriculumOption curriculum) {
        return accessRead(user, connection, material -> http.fetchCurriculum(curriculumSession(material), curriculum));
    }

    @Override public CourseRelationObservation fetchCourseRelations(UUID user, StudentConnectionId connection,
                                                                   CurriculumOption curriculum, String courseSourceId) {
        return access(user, connection, material -> http.fetchCourseRelations(curriculumSession(material), curriculum, courseSourceId));
    }

    private static PhenikaaSession curriculumSession(PhenikaaSessionMaterial material) {
        if (material.curriculum() == null) throw new AcademicPortalException(CONNECTION_UNAVAILABLE);
        return material.curriculum();
    }

    @Override @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<ExamPeriod> fetchExamPeriods(UUID currentUserId, StudentConnectionId connectionId) {
        return accessRead(currentUserId, connectionId, material -> http.fetchExamPeriods(examSession(material)));
    }

    @Override @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ExamObservation fetchExams(UUID currentUserId, StudentConnectionId connectionId, ExamPeriod period) {
        return accessRead(currentUserId, connectionId, material -> http.fetchExams(examSession(material), period));
    }

    private static PhenikaaSession examSession(PhenikaaSessionMaterial material) {
        if (material.exam() == null) throw new AcademicPortalException(CONNECTION_UNAVAILABLE);
        return material.exam();
    }

    @Override @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<AcademicProgram> fetchAcademicPrograms(UUID userId, StudentConnectionId connectionId) {
        return accessRead(userId, connectionId, material -> http.fetchAcademicPrograms(academicSession(material)));
    }

    @Override public List<AcademicPeriod> fetchAcademicPeriods(UUID userId, StudentConnectionId connectionId) {
        return access(userId, connectionId, material -> http.fetchAcademicPeriods(academicSession(material)));
    }

    @Override @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AcademicRecordObservation fetchAcademicRecords(UUID userId, StudentConnectionId connectionId,
                                                          AcademicProgram program) {
        return accessRead(userId, connectionId, material -> http.fetchAcademicRecords(academicSession(material), program));
    }

    @Override @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AcademicProgressSummaryObservation fetchAcademicProgressSummary(UUID userId, StudentConnectionId connectionId,
                                                                          AcademicProgram program) {
        return accessRead(userId, connectionId,
                material -> http.fetchAcademicProgressSummary(academicSession(material), program));
    }

    private static PhenikaaSession academicSession(PhenikaaSessionMaterial material) {
        if (material.academic() == null) throw new AcademicPortalException(CONNECTION_UNAVAILABLE);
        return material.academic();
    }

    @Override @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AcademicResultDetail fetchAcademicResultDetail(UUID userId, StudentConnectionId connectionId,
                                                           AcademicProgram program, String sourceResultId) {
        return accessRead(userId, connectionId,
                material -> http.fetchAcademicResultDetail(academicSession(material), program, sourceResultId));
    }

    private record ReadSession(PhenikaaSessionMaterial material, Instant authenticatedAt) {}

    private <T> T accessRead(UUID userId, StudentConnectionId id, Function<PhenikaaSessionMaterial, T> operation) {
        var opened = readTransactions.execute(status -> {
            requireActiveUser(userId);
            var connection = connections.findByIdAndUserId(id.value(), userId)
                    .orElseThrow(() -> new AcademicPortalException(CONNECTION_UNAVAILABLE));
            if (connection.status() != PhenikaaConnection.Status.CONNECTED)
                throw new AcademicPortalException(CONNECTION_UNAVAILABLE);
            try { return new ReadSession(cipher.decrypt(connection.encryptedSession(), connection.keyVersion(), id.value(), userId),
                    connection.authenticatedAt()); }
            catch (IllegalStateException ex) { return new ReadSession(null, connection.authenticatedAt()); }
        });
        if (opened.material() == null) {
            recordRead(userId, id, opened.authenticatedAt(), SESSION_INTEGRITY_FAILURE);
            throw new AcademicPortalException(SESSION_INTEGRITY_FAILURE);
        }
        try (var material = opened.material()) {
            T result = operation.apply(material);
            if (!recordRead(userId, id, opened.authenticatedAt(), null))
                throw new AcademicPortalException(CONNECTION_UNAVAILABLE);
            return result;
        } catch (PhenikaaClientException ex) {
            var failure = translate(ex);
            recordRead(userId, id, opened.authenticatedAt(), failure.code());
            throw failure;
        } catch (AcademicProgressSummaryUnavailable ex) {
            if (!recordRead(userId, id, opened.authenticatedAt(), null))
                throw new AcademicPortalException(CONNECTION_UNAVAILABLE);
            throw ex;
        }
    }

    private boolean recordRead(UUID userId, StudentConnectionId id, Instant authenticatedAt,
                               AcademicPortalException.Code failure) {
        return Boolean.TRUE.equals(readTransactions.execute(status -> {
            if (users.findById(userId).filter(user -> user.getAccountStatus() == AppUser.Status.ACTIVE).isEmpty()) return false;
            var connection = connections.lockByIdAndUserId(id.value(), userId).orElse(null);
            if (connection == null || connection.status() != PhenikaaConnection.Status.CONNECTED
                    || !connection.authenticatedAt().equals(authenticatedAt)) return false;
            if (failure == null) connection.successful(Instant.now());
            else connection.failed(failure, Instant.now());
            return true;
        }));
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

    private void requireActiveUser(UUID userId) {
        var user = users.findById(userId).orElseThrow(() -> new AcademicPortalException(CONNECTION_UNAVAILABLE));
        if (user.getAccountStatus() != AppUser.Status.ACTIVE) throw new AcademicPortalException(CONNECTION_UNAVAILABLE);
    }

    private static AcademicPortalException translate(PhenikaaClientException ex) {
        return new AcademicPortalException(AcademicPortalException.Code.valueOf(ex.code().name()));
    }
}
