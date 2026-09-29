package vn.edu.phenikaa.ams.academic.api;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import vn.edu.phenikaa.ams.academic.application.AcademicSourceQueryException;
import vn.edu.phenikaa.ams.academic.application.AcademicSourceQueryService;
import vn.edu.phenikaa.ams.academic.application.AcademicSourceQueryService.*;
import vn.edu.phenikaa.ams.auth.application.AccountPrincipal;
import vn.edu.phenikaa.ams.config.SecurityConfiguration;
import vn.edu.phenikaa.ams.user.domain.AppUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AcademicSourceController.class)
@Import(SecurityConfiguration.class)
@TestPropertySource(properties = "ams.phenikaa.enabled=true")
class AcademicSourceControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean AcademicSourceQueryService queries;
    @MockitoBean UserDetailsService users;
    private final AccountPrincipal principal = new AccountPrincipal(
            new AppUser("student@example.test", "!synthetic-unusable-hash", "Người dùng giả định"));

    @Test void requiresAmsAuthenticationForAllReadEndpoints() throws Exception {
        mvc.perform(get("/api/me/academic/source/status")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me/academic/source/programs")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me/academic/source/records").param("programRef", "pr_test"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me/academic/source/records/dt_test/detail").param("programRef", "pr_test"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(queries);
    }

    @Test void returnsOwnReadOnlyObservationWithoutInternalFields() throws Exception {
        var row = new RecordView("rg_opaque", new CourseView("TEST101", "Môn giả định", new BigDecimal("3.00")),
                new SemesterView(2026, "T1", "2026-2027:T1"), 2,
                List.of(new ComponentView("QT", "Quá trình", 1, new BigDecimal("8.00"))),
                new FinalResultView("dt_opaque", 1, "RETAKE_REQUIRED", new BigDecimal("4.00"), "D",
                        new BigDecimal("1.00")));
        when(queries.records(principal.getUserId(), "pr_opaque"))
                .thenReturn(new RecordsView("UNKNOWN", new UnknownSemantics("UNKNOWN", "UNKNOWN", "UNKNOWN"), List.of(row)));
        var response = mvc.perform(get("/api/me/academic/source/records").param("programRef", "pr_opaque")
                        .with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completeness").value("UNKNOWN"))
                .andExpect(jsonPath("$.unknownSemantics.includedInGpa").value("UNKNOWN"))
                .andExpect(jsonPath("$.records[0].reportedLearningAttempt").value(2))
                .andExpect(jsonPath("$.records[0].finalResult.outcome").value("RETAKE_REQUIRED"))
                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("sourceEnrollmentId", "sourceResultId", "learnerId",
                "authorization", "cookie", "iM", "Data.B", "DAOTAO_", "private-id");
    }

    @Test void exposesSafeDetailAndStatusMetadata() throws Exception {
        when(queries.status(principal.getUserId())).thenReturn(new SourceStatus("CONNECTED", null,
                List.of(new CapabilitySupport("ACADEMIC_RECORDS", "LIVE_READ_ONLY", "UNKNOWN"))));
        when(queries.detail(principal.getUserId(), "pr_opaque", "dt_opaque"))
                .thenReturn(new DetailView("dt_opaque", "UNKNOWN", List.of(
                        new DetailComponent("rg_opaque", "QT", "Quá trình", 1, new BigDecimal("8.00")))));
        mvc.perform(get("/api/me/academic/source/status").with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capabilities[0].mode").value("LIVE_READ_ONLY"));
        mvc.perform(get("/api/me/academic/source/records/dt_opaque/detail")
                        .param("programRef", "pr_opaque").with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components[0].registrationRef").value("rg_opaque"));
    }

    @Test void distinguishesReconnectionTimeoutSchemaAndInvalidReferenceWithoutInternalDetails() throws Exception {
        for (var sample : new Object[][] {
                { AcademicSourceQueryException.Code.RECONNECTION_REQUIRED, 409 },
                { AcademicSourceQueryException.Code.SOURCE_TIMEOUT, 504 },
                { AcademicSourceQueryException.Code.SOURCE_SCHEMA_CHANGED, 502 },
                { AcademicSourceQueryException.Code.INVALID_SOURCE_REFERENCE, 404 },
                { AcademicSourceQueryException.Code.RATE_LIMITED, 429 } }) {
            var code = (AcademicSourceQueryException.Code) sample[0];
            doThrow(new AcademicSourceQueryException(code)).when(queries).programs(principal.getUserId());
            var response = mvc.perform(get("/api/me/academic/source/programs").with(user(principal)))
                    .andExpect(status().is((int) sample[1]))
                    .andExpect(jsonPath("$.code").value(code.name()))
                    .andReturn().getResponse().getContentAsString();
            assertThat(response).doesNotContain("phenikaa-uni.edu.vn", "pkg_", "Bearer", "private-id");
        }
    }

    @Test void emptyProgramListIsStillUnknown() throws Exception {
        when(queries.programs(principal.getUserId())).thenReturn(new ProgramsView("UNKNOWN", List.of()));
        mvc.perform(get("/api/me/academic/source/programs").with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completeness").value("UNKNOWN"))
                .andExpect(jsonPath("$.programs").isEmpty());
    }
}
