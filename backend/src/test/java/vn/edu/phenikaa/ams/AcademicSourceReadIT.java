package vn.edu.phenikaa.ams;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;
import vn.edu.phenikaa.ams.academic.application.port.*;
import vn.edu.phenikaa.ams.academic.infrastructure.phenikaa.*;
import vn.edu.phenikaa.ams.auth.application.AccountPrincipal;
import vn.edu.phenikaa.ams.user.domain.AppUser;
import vn.edu.phenikaa.ams.user.infrastructure.UserRepository;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {"spring.data.redis.password=", "ams.phenikaa.enabled=true"})
@AutoConfigureMockMvc
class AcademicSourceReadIT {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PhenikaaAcademicPortalClient portal;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean PhenikaaHttpClient http;
    private final JsonMapper json = JsonMapper.builder().build();
    private AppUser owner;
    private final AcademicProgram program = new AcademicProgram("private-program-id", "Chương trình giả định");

    @DynamicPropertySource static void key(DynamicPropertyRegistry registry) {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        String encoded = Base64.getEncoder().encodeToString(bytes);
        java.util.Arrays.fill(bytes, (byte) 0);
        registry.add("ams.phenikaa.session-key", () -> encoded);
    }

    @BeforeEach void setup() {
        owner = users.save(new AppUser(UUID.randomUUID() + "@example.test", "!synthetic-unusable-hash", null));
        when(http.fetchProfile(any())).thenReturn(new ProfileObservation("SYNTHETIC-001", "Ngành giả định"));
        connect(owner, "synthetic-learner-one");
        when(http.fetchAcademicPrograms(any())).thenReturn(List.of(program));
    }

    private void connect(AppUser user, String learner) {
        try (var material = new PhenikaaSessionMaterial("Bearer synthetic-secret", "", "synthetic-response-key",
                learner, "synthetic-profile-function", "synthetic-schedule-function", null, "synthetic-academic-function")) {
            portal.connect(user.getId(), material);
        }
    }

    private AcademicRecordObservation observation() {
        return new AcademicRecordObservation(program, List.of(new AcademicRecordObservation.Entry(
                "private-registration-id", "private-section-id", "private-course-id", "TEST101", "Môn giả định",
                new BigDecimal("3.00"), "private-period-id", 2026, 1, 1,
                List.of(new AcademicRecordObservation.Component("private-component-id", "QT", "Quá trình", 1,
                        new BigDecimal("8.00"))),
                new AcademicRecordObservation.FinalResult("private-result-id", 1,
                        AcademicRecordObservation.Outcome.PASSED, new BigDecimal("8.00"),
                        new BigDecimal("3.50"), "B+"))));
    }

    private String programRef(AppUser account) throws Exception {
        var response = mvc.perform(get("/api/me/academic/source/programs").with(user(new AccountPrincipal(account))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).path("programs").get(0).path("programRef").asText();
    }

    @Test void ownUserCanReadRecordsAndDetailWithoutAcademicPersistenceOrRawIds() throws Exception {
        when(http.fetchAcademicRecords(any(), eq(program))).thenReturn(observation());
        when(http.fetchAcademicResultDetail(any(), eq(program), eq("private-result-id")))
                .thenReturn(new AcademicResultDetail("private-result-id", List.of(new AcademicResultDetail.ComponentLink(
                        "private-registration-id", "private-component-id", "QT", "Quá trình", 1, new BigDecimal("8.00")))));
        String ref = programRef(owner);
        var recordJson = mvc.perform(get("/api/me/academic/source/records").param("programRef", ref)
                        .with(user(new AccountPrincipal(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completeness").value("UNKNOWN"))
                .andExpect(jsonPath("$.unknownSemantics.creditsEarned").value("UNKNOWN"))
                .andReturn().getResponse().getContentAsString();
        assertThat(recordJson).doesNotContain("private-registration-id", "private-result-id", "private-program-id",
                "sourceEnrollmentId", "learnerId", "Bearer", "Data.B");
        String detailRef = json.readTree(recordJson).path("records").get(0).path("finalResult").path("detailRef").asText();
        mvc.perform(get("/api/me/academic/source/records/{detailRef}/detail", detailRef)
                        .param("programRef", ref).with(user(new AccountPrincipal(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components[0].code").value("QT"));
        assertThat(jdbc.queryForObject("select count(*) from student_course where profile_id in "
                + "(select id from student_profile where user_id = ?)", Integer.class, owner.getId())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from academic_result where profile_id in "
                + "(select id from student_profile where user_id = ?)", Integer.class, owner.getId())).isZero();
    }

    @Test void referenceFromAnotherUserCannotRequestTheOwnersRecords() throws Exception {
        String ownRef = programRef(owner);
        var other = users.save(new AppUser(UUID.randomUUID() + "@example.test", "!synthetic-unusable-hash", null));
        connect(other, "synthetic-learner-two");
        mvc.perform(get("/api/me/academic/source/records").param("programRef", ownRef)
                        .with(user(new AccountPrincipal(other))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INVALID_SOURCE_REFERENCE"));
        verify(http, never()).fetchAcademicRecords(any(), any());
    }

    @Test void expiredSessionRequiresReconnectionAndRepeatedRefreshIsThrottled() throws Exception {
        String ref = programRef(owner);
        mvc.perform(get("/api/me/academic/source/programs").with(user(new AccountPrincipal(owner))))
                .andExpect(status().isTooManyRequests());
        when(http.fetchAcademicRecords(any(), eq(program)))
                .thenThrow(new PhenikaaClientException(PhenikaaClientException.Code.SESSION_EXPIRED));
        mvc.perform(get("/api/me/academic/source/records").param("programRef", ref)
                        .with(user(new AccountPrincipal(owner))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RECONNECTION_REQUIRED"));
        mvc.perform(get("/api/me/academic/source/status").with(user(new AccountPrincipal(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connectionState").value("RECONNECTION_REQUIRED"));
    }

    @Test void ownedScheduleAndExamsStayReadOnlyWithOpaquePeriodReferences() throws Exception {
        try (var material = new PhenikaaSessionMaterial("Bearer synthetic-secret", "", "synthetic-response-key",
                "synthetic-learner-one", "synthetic-profile-function", "synthetic-schedule-function",
                "synthetic-exam-function", "synthetic-academic-function")) {
            portal.connect(owner.getId(), material);
        }
        var day = LocalDate.of(2026, 10, 1);
        var scheduleRow = new ScheduleObservation.Entry(new ScheduleObservation.CandidateIdentity(
                "private-schedule-id", "private-section-id", "private-enrollment-id"), null, day,
                null, null, null, null, ScheduleObservation.Kind.UNKNOWN);
        when(http.fetchSchedule(any(), eq(day), eq(day))).thenReturn(new ScheduleObservation(
                day, day, ZoneId.of("Asia/Ho_Chi_Minh"), List.of(scheduleRow, scheduleRow)));
        String schedule = mvc.perform(get("/api/me/academic/source/schedule")
                        .param("from", "2026-10-01").param("through", "2026-10-01")
                        .with(user(new AccountPrincipal(owner))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.entries.length()").value(2))
                .andExpect(jsonPath("$.completeness").value("UNKNOWN"))
                .andExpect(jsonPath("$.identityScope").value("UNVERIFIED"))
                .andReturn().getResponse().getContentAsString();
        assertThat(schedule).doesNotContain("private-schedule-id", "private-section-id", "private-enrollment-id");

        var period = new ExamPeriod("private-period-id", "Kỳ nguồn giả định");
        when(http.fetchExamPeriods(any())).thenReturn(List.of(period));
        String periods = mvc.perform(get("/api/me/academic/source/exams/periods")
                        .with(user(new AccountPrincipal(owner))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.completeness").value("UNKNOWN"))
                .andReturn().getResponse().getContentAsString();
        String ref = json.readTree(periods).path("periods").get(0).path("periodRef").asText();
        assertThat(ref).matches("ep_[0-9a-f]{64}");
        assertThat(periods).doesNotContain(period.sourceId());
        var examRow = new ExamObservation.Entry(new ExamObservation.CandidateIdentity("private-exam-id"),
                "TEST101", "Môn kiểm thử A", 2, null, Instant.parse("2026-10-01T01:30:00Z"), null, null);
        when(http.fetchExams(any(), eq(period))).thenReturn(new ExamObservation(period,
                ZoneId.of("Asia/Ho_Chi_Minh"), List.of(examRow, examRow)));
        String exams = mvc.perform(get("/api/me/academic/source/exams").param("periodRef", ref)
                        .with(user(new AccountPrincipal(owner))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.entries.length()").value(2))
                .andExpect(jsonPath("$.entries[0].startsAt").value("08:30:00"))
                .andReturn().getResponse().getContentAsString();
        assertThat(exams).doesNotContain("private-exam-id", period.sourceId());
        assertThat(jdbc.queryForObject("select count(*) from class_session", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from exam", Integer.class)).isZero();
    }
}
