package vn.edu.phenikaa.ams.academic.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
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
        mvc.perform(get("/api/me/academic/source/progress-summary")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me/academic/source/records").param("programRef", "pr_test"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me/academic/source/records/dt_test/detail").param("programRef", "pr_test"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me/academic/source/schedule").param("from", "2026-10-01")
                .param("through", "2026-10-01")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me/academic/source/exams/periods")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me/academic/source/exams").param("periodRef", "ep_test"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(queries);
    }

    @Test void progressSummaryExposesOnlyAmsSelectionAndSourceReportedMetrics() throws Exception {
        var id = java.util.UUID.randomUUID();
        when(queries.progressSummary(principal.getUserId())).thenReturn(new ProgressSummaryView(
                "SOURCE_REPORTED_LIVE_READ_ONLY", "UNKNOWN", "USER_SELECTED_AMS",
                new TrackedCurriculumView(id, "TEST", "Chương trình kiểm thử"),
                new AccumulatedSummaryView(new BigDecimal("3.25"), new BigDecimal("8.10"), new BigDecimal("72"))));
        String body = mvc.perform(get("/api/me/academic/source/progress-summary").with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("SOURCE_REPORTED_LIVE_READ_ONLY"))
                .andExpect(jsonPath("$.curriculum.id").value(id.toString()))
                .andExpect(jsonPath("$.summary.sourceAccumulatedCredits").value(72))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("sourceProgramId", "profileId", "learnerId", "rsDiemTrungBinhChung");
    }

    @Test void progressSummaryUsesSafe409Errors() throws Exception {
        for (var code : new AcademicSourceQueryException.Code[] {
                AcademicSourceQueryException.Code.CURRICULUM_SELECTION_REQUIRED,
                AcademicSourceQueryException.Code.SOURCE_PROGRESS_UNAVAILABLE }) {
            doThrow(new AcademicSourceQueryException(code)).when(queries).progressSummary(principal.getUserId());
            mvc.perform(get("/api/me/academic/source/progress-summary").with(user(principal)))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value(code.name()));
        }
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

    @Test void scheduleAndExamsExposeOnlySourceSafeFields() throws Exception {
        var day = LocalDate.of(2026, 10, 1);
        when(queries.schedule(principal.getUserId(), "2026-10-01", "2026-10-01"))
                .thenReturn(new ScheduleView("UNKNOWN", "UNVERIFIED", "Asia/Ho_Chi_Minh", day, day,
                        List.of(new ScheduleEntryView(null, day, null, null, null, null, "UNKNOWN"))));
        when(queries.examPeriods(principal.getUserId()))
                .thenReturn(new ExamPeriodsView("UNKNOWN", List.of(new ExamPeriodView("ep_opaque", "Kỳ nguồn"))));
        when(queries.exams(principal.getUserId(), "ep_opaque"))
                .thenReturn(new ExamsView("UNKNOWN", "UNVERIFIED", "Asia/Ho_Chi_Minh",
                        new ExamPeriodView("ep_opaque", "Kỳ nguồn"), List.of(
                        new ExamEntryView("TEST101", "Môn kiểm thử", 2, null, day, LocalTime.of(8, 30), null, null))));
        String schedule = mvc.perform(get("/api/me/academic/source/schedule")
                        .param("from", "2026-10-01").param("through", "2026-10-01").with(user(principal)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.identityScope").value("UNVERIFIED"))
                .andExpect(jsonPath("$.entries.length()").value(1))
                .andExpect(jsonPath("$.entries[0].startsAt").isEmpty())
                .andReturn().getResponse().getContentAsString();
        String periods = mvc.perform(get("/api/me/academic/source/exams/periods").with(user(principal)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.periods[0].periodRef").value("ep_opaque"))
                .andReturn().getResponse().getContentAsString();
        String exams = mvc.perform(get("/api/me/academic/source/exams").param("periodRef", "ep_opaque")
                        .with(user(principal)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.entries[0].examAttempt").value(2))
                .andExpect(jsonPath("$.entries[0].endsAt").isEmpty())
                .andReturn().getResponse().getContentAsString();
        assertThat(schedule + periods + exams).doesNotContain("scheduleId", "sectionId", "sourceId",
                "learnerId", "studentCourseId", "examId", "classSessionId", "IDLICHHOC");
    }

    @Test void invalidScheduleRangeUsesSafe400Code() throws Exception {
        when(queries.schedule(principal.getUserId(), "bad", "2026-10-01"))
                .thenThrow(new AcademicSourceQueryException(AcademicSourceQueryException.Code.INVALID_SOURCE_RANGE));
        mvc.perform(get("/api/me/academic/source/schedule").param("from", "bad")
                        .param("through", "2026-10-01").with(user(principal)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SOURCE_RANGE"));
    }
}
