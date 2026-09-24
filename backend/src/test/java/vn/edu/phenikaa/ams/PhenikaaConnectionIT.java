package vn.edu.phenikaa.ams;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import vn.edu.phenikaa.ams.academic.application.ProfileImportService;
import vn.edu.phenikaa.ams.academic.application.port.*;
import vn.edu.phenikaa.ams.academic.domain.StudentProfile;
import vn.edu.phenikaa.ams.academic.infrastructure.StudentProfileRepository;
import vn.edu.phenikaa.ams.academic.infrastructure.phenikaa.*;
import vn.edu.phenikaa.ams.auth.application.AccountPrincipal;
import vn.edu.phenikaa.ams.user.domain.AppUser;
import vn.edu.phenikaa.ams.user.infrastructure.UserRepository;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {"spring.data.redis.password=", "ams.phenikaa.enabled=true"})
@AutoConfigureMockMvc
class PhenikaaConnectionIT {
    @Autowired UserRepository users;
    @Autowired PhenikaaAcademicPortalClient portal;
    @Autowired ProfileImportService imports;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @MockitoBean PhenikaaHttpClient http;
    @MockitoSpyBean StudentProfileRepository profiles;
    private AppUser owner;
    private AppUser other;

    @DynamicPropertySource static void key(DynamicPropertyRegistry registry) {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        String encoded = Base64.getEncoder().encodeToString(bytes);
        java.util.Arrays.fill(bytes, (byte) 0);
        registry.add("ams.phenikaa.session-key", () -> encoded);
    }

    @BeforeEach void setup() {
        owner = users.save(new AppUser(UUID.randomUUID() + "@example.test", "!synthetic-unusable-hash", null));
        other = users.save(new AppUser(UUID.randomUUID() + "@example.test", "!synthetic-unusable-hash", null));
        when(http.fetchProfile(any())).thenReturn(new ProfileObservation("SYNTHETIC-001", "Ngành giả định"));
    }

    private PhenikaaSessionMaterial material(String learner) {
        return new PhenikaaSessionMaterial("Bearer synthetic-secret", "fixture=synthetic-cookie", "synthetic-response-key",
                learner, "synthetic-profile-function", "synthetic-schedule-function");
    }
    private AcademicPortalClient.StudentConnectionId connect() {
        try (var material = material("synthetic-learner")) { return portal.connect(owner.getId(), material); }
    }

    private AcademicPortalClient.StudentConnectionId connectWithExams() {
        try (var material = new PhenikaaSessionMaterial("Bearer synthetic-secret", "", "synthetic-response-key",
                "synthetic-learner", "synthetic-profile-function", "synthetic-schedule-function", "synthetic-exam-function")) {
            return portal.connect(owner.getId(), material);
        }
    }

    private AcademicPortalClient.StudentConnectionId connectWithAcademics() {
        try (var material = new PhenikaaSessionMaterial("Bearer synthetic-secret", "", "synthetic-response-key",
                "synthetic-learner", "synthetic-profile-function", "synthetic-schedule-function", null, "synthetic-academic-function")) {
            return portal.connect(owner.getId(), material);
        }
    }

    @Test void academicCapabilityChecksOwnerBeforeReadingAndNeverPersistsObservations() {
        var connection = connectWithAcademics();
        var program = new AcademicProgram("synthetic-program", "Chương trình giả định");
        var period = new AcademicPeriod("synthetic-period", "Kỳ giả định");
        var observation = new AcademicRecordObservation(program, java.util.List.of());
        when(http.fetchAcademicPrograms(any())).thenReturn(java.util.List.of(program));
        when(http.fetchAcademicPeriods(any())).thenReturn(java.util.List.of(period));
        when(http.fetchAcademicRecords(any(), eq(program))).thenReturn(observation);
        clearInvocations(http);
        assertThatThrownBy(() -> portal.fetchAcademicPrograms(other.getId(), connection)).hasMessage("CONNECTION_UNAVAILABLE");
        assertThatThrownBy(() -> portal.fetchAcademicPeriods(other.getId(), connection)).hasMessage("CONNECTION_UNAVAILABLE");
        assertThatThrownBy(() -> portal.fetchAcademicRecords(other.getId(), connection, program)).hasMessage("CONNECTION_UNAVAILABLE");
        verifyNoInteractions(http);
        assertThat(portal.fetchAcademicPrograms(owner.getId(), connection)).containsExactly(program);
        assertThat(portal.fetchAcademicPeriods(owner.getId(), connection)).containsExactly(period);
        assertThat(portal.fetchAcademicRecords(owner.getId(), connection, program)).isEqualTo(observation);
        assertThat(profiles.findByUserId(owner.getId())).isEmpty();
        jdbc.update("update app_user set account_status = 'DISABLED' where id = ?", owner.getId());
        clearInvocations(http);
        assertThatThrownBy(() -> portal.fetchAcademicRecords(owner.getId(), connection, program)).hasMessage("CONNECTION_UNAVAILABLE");
        verifyNoInteractions(http);
    }

    @Test void legacySessionDoesNotGainAcademicCapabilityOrLoseItsProfileCapability() {
        var connection = connect();
        clearInvocations(http);
        assertThatThrownBy(() -> portal.fetchAcademicPeriods(owner.getId(), connection)).hasMessage("CONNECTION_UNAVAILABLE");
        verifyNoInteractions(http);
        assertThat(portal.status(owner.getId()).status()).isEqualTo(PhenikaaConnection.Status.CONNECTED);
        assertThat(imports.importCurrentProfile(owner.getId())).isNotNull();
    }

    @ParameterizedTest @EnumSource(PhenikaaClientException.Code.class)
    void academicFailurePreservesExistingCourseAndOnlyExpiryRequiresReconnection(PhenikaaClientException.Code code) {
        var connection = connectWithAcademics();
        UUID profile = imports.importCurrentProfile(owner.getId());
        UUID course = UUID.randomUUID();
        jdbc.update("insert into course(id,profile_id,code,name,credits) values (?,?,'TEST101','Môn giả định',3)", course, profile);
        var program = new AcademicProgram("synthetic-program", "Chương trình giả định");
        when(http.fetchAcademicRecords(any(), eq(program))).thenThrow(new PhenikaaClientException(code));
        assertThatThrownBy(() -> portal.fetchAcademicRecords(owner.getId(), connection, program)).hasMessage(code.name()).hasNoCause();
        assertThat(jdbc.queryForObject("select id from course where profile_id = ?", UUID.class, profile)).isEqualTo(course);
        assertThat(portal.status(owner.getId()).reconnectionRequired()).isEqualTo(code == PhenikaaClientException.Code.SESSION_EXPIRED);
        assertThat(portal.status(owner.getId()).lastFailureCode().name()).isEqualTo(code.name());
    }

    @Test void examCapabilityUsesOwnedEncryptedContextAndDoesNotCreateAcademicRecords() {
        var connection = connectWithExams();
        var period = new ExamPeriod("synthetic-period", "Kỳ giả định");
        var observation = new ExamObservation(period, java.time.ZoneId.of("Asia/Ho_Chi_Minh"), java.util.List.of());
        when(http.fetchExamPeriods(any())).thenReturn(java.util.List.of(period));
        when(http.fetchExams(any(), eq(period))).thenReturn(observation);
        clearInvocations(http);
        assertThatThrownBy(() -> portal.fetchExamPeriods(other.getId(), connection)).hasMessage("CONNECTION_UNAVAILABLE");
        assertThatThrownBy(() -> portal.fetchExams(other.getId(), connection, period)).hasMessage("CONNECTION_UNAVAILABLE");
        verifyNoInteractions(http);
        assertThat(portal.fetchExamPeriods(owner.getId(), connection)).containsExactly(period);
        assertThat(portal.fetchExams(owner.getId(), connection, period)).isEqualTo(observation);
        assertThat(profiles.findByUserId(owner.getId())).isEmpty();
        assertThat(portal.status(owner.getId()).lastSuccessfulAccessAt()).isNotNull();
        jdbc.update("update app_user set account_status = 'DISABLED' where id = ?", owner.getId());
        clearInvocations(http);
        assertThatThrownBy(() -> portal.fetchExams(owner.getId(), connection, period)).hasMessage("CONNECTION_UNAVAILABLE");
        verifyNoInteractions(http);
    }

    @Test void missingExamContextPreservesWorkingProfileConnection() {
        var connection = connect();
        clearInvocations(http);
        assertThatThrownBy(() -> portal.fetchExamPeriods(owner.getId(), connection)).hasMessage("CONNECTION_UNAVAILABLE");
        verifyNoInteractions(http);
        assertThat(portal.status(owner.getId()).status()).isEqualTo(PhenikaaConnection.Status.CONNECTED);
        assertThat(imports.importCurrentProfile(owner.getId())).isNotNull();
    }

    @ParameterizedTest @EnumSource(PhenikaaClientException.Code.class)
    void examFailurePreservesProfileAndCiphertextAndOnlyExpiredSessionRequiresReconnect(PhenikaaClientException.Code code) {
        var connection = connectWithExams();
        UUID profile = imports.importCurrentProfile(owner.getId());
        byte[] previous = jdbc.queryForObject("select encrypted_session from phenikaa_connection where id = ?", byte[].class, connection.value());
        var period = new ExamPeriod("synthetic-period", "Kỳ giả định");
        when(http.fetchExams(any(), eq(period))).thenThrow(new PhenikaaClientException(code));
        assertThatThrownBy(() -> portal.fetchExams(owner.getId(), connection, period)).hasMessage(code.name()).hasNoCause();
        assertThat(portal.status(owner.getId()).reconnectionRequired()).isEqualTo(code == PhenikaaClientException.Code.SESSION_EXPIRED);
        assertThat(portal.status(owner.getId()).lastFailureCode().name()).isEqualTo(code.name());
        assertThat(profiles.findById(profile)).isPresent();
        assertThat(jdbc.queryForObject("select encrypted_session from phenikaa_connection where id = ?", byte[].class, connection.value()))
                .isEqualTo(previous);
    }

    @Test void persistsOnlyCiphertextAndEnforcesOwnerAndUniqueConnection() {
        var connection = connect();
        var row = jdbc.queryForMap("select * from phenikaa_connection where id = ?", connection.value());
        assertThat(row.get("user_id")).isEqualTo(owner.getId());
        assertThat(row.get("status")).isEqualTo("CONNECTED");
        assertThat(row.get("session_expires_at")).isNull();
        assertThat(row.get("encryption_key_version")).isEqualTo(1);
        for (String column : new String[]{"encrypted_session", "encrypted_subject"})
            assertThat(new String((byte[]) row.get(column), java.nio.charset.StandardCharsets.ISO_8859_1))
                    .doesNotContain("synthetic-secret", "synthetic-cookie", "synthetic-response-key", "synthetic-learner");
        byte[] first = (byte[]) row.get("encrypted_session");
        assertThat(connect()).isEqualTo(connection);
        assertThat(jdbc.queryForObject("select encrypted_session from phenikaa_connection where id = ?", byte[].class, connection.value()))
                .isNotEqualTo(first);
        assertThatThrownBy(() -> jdbc.update("""
                insert into phenikaa_connection (id,user_id,status,encrypted_session,encrypted_subject,encryption_key_version,
                    last_authenticated_at,created_at,updated_at)
                select ?,user_id,status,encrypted_session,encrypted_subject,encryption_key_version,last_authenticated_at,created_at,updated_at
                from phenikaa_connection where id = ?
                """, UUID.randomUUID(), connection.value())).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update phenikaa_connection set user_id = ? where id = ?", UUID.randomUUID(), connection.value()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update phenikaa_connection set encrypted_session = null where id = ?", connection.value()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void repeatedImportRetainsUuidAndOnlyUpdatesConfirmedFields() {
        connect();
        UUID first = imports.importCurrentProfile(owner.getId());
        assertThat(imports.importCurrentProfile(owner.getId())).isEqualTo(first);
        jdbc.update("update student_profile set institution_name = 'Trường giả định', cohort = 'Khóa giả định' where id = ?", first);
        when(http.fetchProfile(any())).thenReturn(new ProfileObservation("SYNTHETIC-001", "Ngành cập nhật"));
        assertThat(imports.importCurrentProfile(owner.getId())).isEqualTo(first);
        var result = profiles.findById(first).orElseThrow();
        assertThat(result.getProgramName()).isEqualTo("Ngành cập nhật");
        assertThat(result.getInstitutionName()).isEqualTo("Trường giả định");
        assertThat(result.getCohort()).isEqualTo("Khóa giả định");
        when(http.fetchProfile(any())).thenReturn(new ProfileObservation("SYNTHETIC-001", null));
        imports.importCurrentProfile(owner.getId());
        assertThat(profiles.findById(first).orElseThrow().getProgramName()).isEqualTo("Ngành cập nhật");
        assertThat(jdbc.queryForObject("select count(*) from student_profile where user_id = ?", Long.class, owner.getId())).isEqualTo(1L);
    }

    @Test void concurrentFirstImportsDoNotCreateTwoProfiles() throws Exception {
        connect();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var gate = new CountDownLatch(1);
            Callable<UUID> action = () -> { gate.await(); return imports.importCurrentProfile(owner.getId()); };
            var first = executor.submit(action);
            var second = executor.submit(action);
            gate.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(second.get(10, TimeUnit.SECONDS));
        }
        assertThat(jdbc.queryForObject("select count(*) from student_profile where user_id = ?", Long.class, owner.getId())).isEqualTo(1L);
    }

    @Test void wrongUserCannotReadConnectionOrModifyAnotherProfile() {
        var connection = connect();
        var untouched = profiles.save(new StudentProfile(other.getId(), "SYNTHETIC-OTHER", null, "Ngành khác", null));
        clearInvocations(http);
        assertThatThrownBy(() -> portal.fetchProfile(other.getId(), connection)).hasMessage("CONNECTION_UNAVAILABLE");
        assertThatThrownBy(() -> portal.fetchSchedule(other.getId(), connection, LocalDate.now(), LocalDate.now()))
                .hasMessage("CONNECTION_UNAVAILABLE");
        assertThatThrownBy(() -> imports.importCurrentProfile(other.getId())).hasMessage("CONNECTION_UNAVAILABLE");
        verifyNoInteractions(http);
        imports.importCurrentProfile(owner.getId());
        assertThat(profiles.findById(untouched.getId()).orElseThrow().getStudentNumber()).isEqualTo("SYNTHETIC-OTHER");
        jdbc.update("update app_user set account_status = 'DISABLED' where id = ?", owner.getId());
        assertThatThrownBy(() -> imports.importCurrentProfile(owner.getId())).hasMessage("CONNECTION_UNAVAILABLE");
    }

    @ParameterizedTest @EnumSource(PhenikaaClientException.Code.class)
    void sourceFailurePreservesAcademicDataAndOnlyAuthFailureRequiresReconnect(PhenikaaClientException.Code code) {
        var connection = connect();
        UUID profile = imports.importCurrentProfile(owner.getId());
        byte[] before = jdbc.queryForObject("select encrypted_session from phenikaa_connection where id = ?", byte[].class, connection.value());
        when(http.fetchProfile(any())).thenThrow(new PhenikaaClientException(code));
        assertThatThrownBy(() -> imports.importCurrentProfile(owner.getId())).hasMessage(code.name()).hasNoCause();
        assertThat(profiles.findById(profile).orElseThrow().getStudentNumber()).isEqualTo("SYNTHETIC-001");
        var status = portal.status(owner.getId());
        assertThat(status.lastFailureCode().name()).isEqualTo(code.name());
        assertThat(status.lastFailedAccessAt()).isNotNull();
        assertThat(status.reconnectionRequired()).isEqualTo(code == PhenikaaClientException.Code.SESSION_EXPIRED);
        assertThat(jdbc.queryForObject("select encrypted_session from phenikaa_connection where id = ?", byte[].class, connection.value()))
                .isEqualTo(before);
    }

    @Test void expiredConnectionCanReconnectButCannotSwitchSourceAccountAfterDisconnect() {
        var connection = connect();
        UUID profile = imports.importCurrentProfile(owner.getId());
        when(http.fetchProfile(any())).thenThrow(new PhenikaaClientException(PhenikaaClientException.Code.SESSION_EXPIRED));
        assertThatThrownBy(() -> imports.importCurrentProfile(owner.getId())).hasMessage("SESSION_EXPIRED");
        assertThatThrownBy(() -> imports.importCurrentProfile(owner.getId())).hasMessage("CONNECTION_UNAVAILABLE");
        doReturn(new ProfileObservation("SYNTHETIC-001", "Ngành giả định")).when(http).fetchProfile(any());
        assertThat(connect()).isEqualTo(connection);
        assertThat(portal.status(owner.getId()).status()).isEqualTo(PhenikaaConnection.Status.CONNECTED);
        portal.disconnect(owner.getId());
        assertThat(jdbc.queryForObject("select encrypted_session from phenikaa_connection where id = ?", byte[].class, connection.value())).isNull();
        assertThat(portal.status(owner.getId()).status()).isEqualTo(PhenikaaConnection.Status.DISCONNECTED);
        try (var different = material("different-synthetic-learner")) {
            assertThatThrownBy(() -> portal.connect(owner.getId(), different)).hasMessage("SOURCE_ACCOUNT_MISMATCH");
        }
        assertThat(connect()).isEqualTo(connection);
        assertThat(imports.importCurrentProfile(owner.getId())).isEqualTo(profile);
    }

    @Test void tamperingFailsBeforeHttpAndPreservesOldProfile() {
        var connection = connect();
        UUID profile = imports.importCurrentProfile(owner.getId());
        byte[] bytes = jdbc.queryForObject("select encrypted_session from phenikaa_connection where id = ?", byte[].class, connection.value());
        bytes[bytes.length - 1] ^= 1;
        jdbc.update("update phenikaa_connection set encrypted_session = ? where id = ?", bytes, connection.value());
        clearInvocations(http);
        assertThatThrownBy(() -> imports.importCurrentProfile(owner.getId())).hasMessage("SESSION_INTEGRITY_FAILURE");
        verifyNoInteractions(http);
        assertThat(profiles.findById(profile)).isPresent();
        assertThat(portal.status(owner.getId()).reconnectionRequired()).isFalse();
    }

    @Test void failedReconnectDoesNotReactivateDisconnectedConnectionWithoutASession() {
        connect();
        UUID profile = imports.importCurrentProfile(owner.getId());
        portal.disconnect(owner.getId());
        when(http.fetchProfile(any())).thenThrow(new PhenikaaClientException(PhenikaaClientException.Code.SESSION_EXPIRED));
        assertThatThrownBy(this::connect).hasMessage("SESSION_EXPIRED");
        assertThat(portal.status(owner.getId()).status()).isEqualTo(PhenikaaConnection.Status.DISCONNECTED);
        assertThat(portal.status(owner.getId()).lastFailureCode()).isEqualTo(AcademicPortalException.Code.SESSION_EXPIRED);
        assertThat(profiles.findById(profile)).isPresent();
    }

    @Test void databaseWriteFailureRollsBackImportAndSuccessMetadata() {
        var connection = connect();
        var previous = portal.status(owner.getId()).lastSuccessfulAccessAt();
        doThrow(new DataIntegrityViolationException("synthetic database failure")).when(profiles).save(any(StudentProfile.class));
        assertThatThrownBy(() -> imports.importCurrentProfile(owner.getId())).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(profiles.findByUserId(owner.getId())).isEmpty();
        assertThat(portal.status(owner.getId()).lastSuccessfulAccessAt()).isEqualTo(previous);
        assertThat(portal.currentConnection(owner.getId())).isEqualTo(connection);
    }

    @Test void statusApiUsesPrincipalAndNeverReturnsSessionOrSourceIds() throws Exception {
        connect();
        mvc.perform(get("/api/me/connections/phenikaa")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me/connections/phenikaa").with(user(new AccountPrincipal(owner))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONNECTED"))
                .andExpect(jsonPath("$.userId").doesNotExist()).andExpect(jsonPath("$.encryptedSession").doesNotExist())
                .andExpect(jsonPath("$.learnerId").doesNotExist()).andExpect(jsonPath("$.authorization").doesNotExist())
                .andExpect(jsonPath("$.length()").value(6));
        mvc.perform(get("/api/me/connections/phenikaa").param("userId", owner.getId().toString())
                        .with(user(new AccountPrincipal(other))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DISCONNECTED"));
        mvc.perform(post("/api/me/connections/phenikaa").with(user(new AccountPrincipal(owner))))
                .andExpect(status().isForbidden());
    }
}
