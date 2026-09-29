package vn.edu.phenikaa.ams;

import java.math.BigDecimal;
import java.security.SecureRandom;
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
}
