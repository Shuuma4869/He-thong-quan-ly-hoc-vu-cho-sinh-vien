package vn.edu.phenikaa.ams.academic.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.edu.phenikaa.ams.academic.application.port.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AcademicSourceQueryServiceTest {
    @Mock AcademicPortalClient portal;
    @Mock AcademicReadGate gate;
    private AcademicSourceQueryService queries;
    private final UUID owner = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();
    private final AcademicPortalClient.StudentConnectionId connection =
            new AcademicPortalClient.StudentConnectionId(UUID.randomUUID());
    private final AcademicProgram program = new AcademicProgram("private-program-id", "Chương trình giả định");

    @BeforeEach void setup() { queries = new AcademicSourceQueryService(portal, gate); }

    private void connected(UUID user) {
        when(gate.tryAcquire(eq(user), any())).thenReturn(true);
        when(portal.connectionInfo(user)).thenReturn(new AcademicPortalClient.ConnectionInfo(
                AcademicPortalClient.ConnectionInfo.State.CONNECTED, null));
        when(portal.currentConnection(user)).thenReturn(connection);
    }

    private AcademicRecordObservation observation() {
        var component = new AcademicRecordObservation.Component("private-component-id", "QT", "Quá trình", 1,
                new BigDecimal("8.00"));
        var result = new AcademicRecordObservation.FinalResult("private-result-id", 1,
                AcademicRecordObservation.Outcome.RETAKE_REQUIRED, new BigDecimal("4.00"),
                new BigDecimal("1.00"), "D");
        var entry = new AcademicRecordObservation.Entry("private-registration-id", "private-section-id",
                "private-course-id", "TEST101", "Môn giả định", new BigDecimal("3.00"),
                "private-period-id", 2026, 1, 2, List.of(component), result);
        return new AcademicRecordObservation(program, List.of(entry));
    }

    @Test void mapsLiveRecordsWithoutInventingCreditGpaOrSourceIdentity() {
        connected(owner);
        when(portal.fetchAcademicPrograms(owner, connection)).thenReturn(List.of(program));
        when(portal.fetchAcademicRecords(owner, connection, program)).thenReturn(observation());
        var programRef = queries.programs(owner).programs().getFirst().programRef();
        var view = queries.records(owner, programRef);
        assertThat(view.completeness()).isEqualTo("UNKNOWN");
        assertThat(view.unknownSemantics()).isEqualTo(new AcademicSourceQueryService.UnknownSemantics(
                "UNKNOWN", "UNKNOWN", "UNKNOWN"));
        assertThat(view.records()).hasSize(1);
        var row = view.records().getFirst();
        assertThat(row.semester().code()).isEqualTo("2026-2027:T1");
        assertThat(row.reportedLearningAttempt()).isEqualTo(2);
        assertThat(row.finalResult().outcome()).isEqualTo("RETAKE_REQUIRED");
        assertThat(row.registrationRef()).startsWith("rg_").doesNotContain("private-registration-id");
        assertThat(row.finalResult().detailRef()).startsWith("dt_").doesNotContain("private-result-id");
        verify(portal).fetchAcademicRecords(owner, connection, program);
    }

    @Test void detailRechecksOwnedResultAndReturnsOnlyNormalizedComponents() {
        connected(owner);
        when(portal.fetchAcademicPrograms(owner, connection)).thenReturn(List.of(program));
        when(portal.fetchAcademicRecords(owner, connection, program)).thenReturn(observation());
        when(portal.fetchAcademicResultDetail(owner, connection, program, "private-result-id"))
                .thenReturn(new AcademicResultDetail("private-result-id", List.of(
                        new AcademicResultDetail.ComponentLink("private-registration-id", "private-component-id",
                                "QT", "Quá trình", 1, new BigDecimal("8.00")))));
        var programRef = queries.programs(owner).programs().getFirst().programRef();
        var detailRef = queries.records(owner, programRef).records().getFirst().finalResult().detailRef();
        var detail = queries.detail(owner, programRef, detailRef);
        assertThat(detail.completeness()).isEqualTo("UNKNOWN");
        assertThat(detail.components()).hasSize(1);
        assertThat(detail.components().getFirst().code()).isEqualTo("QT");
        assertThat(detail.components().getFirst().registrationRef()).startsWith("rg_");
    }

    @Test void refusesToMixComponentsFromChangedSourceReads() {
        connected(owner);
        when(portal.fetchAcademicPrograms(owner, connection)).thenReturn(List.of(program));
        when(portal.fetchAcademicRecords(owner, connection, program)).thenReturn(observation());
        when(portal.fetchAcademicResultDetail(owner, connection, program, "private-result-id"))
                .thenReturn(new AcademicResultDetail("private-result-id", List.of(
                        new AcademicResultDetail.ComponentLink("private-registration-id", "private-component-id",
                                "QT", "Quá trình", 1, new BigDecimal("9.00")))));
        var programRef = queries.programs(owner).programs().getFirst().programRef();
        var detailRef = queries.records(owner, programRef).records().getFirst().finalResult().detailRef();
        assertThatThrownBy(() -> queries.detail(owner, programRef, detailRef))
                .hasMessage("SOURCE_DATA_INCOMPLETE");
    }

    @Test void anotherUsersReferenceCannotResolveEvenWhenSourceIdsMatch() {
        connected(owner);
        connected(other);
        when(portal.fetchAcademicPrograms(owner, connection)).thenReturn(List.of(program));
        when(portal.fetchAcademicPrograms(other, connection)).thenReturn(List.of(program));
        when(portal.fetchAcademicRecords(owner, connection, program)).thenReturn(observation());
        var ownProgram = queries.programs(owner).programs().getFirst().programRef();
        var ownDetail = queries.records(owner, ownProgram).records().getFirst().finalResult().detailRef();
        var otherProgram = queries.programs(other).programs().getFirst().programRef();
        assertThatThrownBy(() -> queries.records(other, ownProgram))
                .isInstanceOf(AcademicSourceQueryException.class).hasMessage("INVALID_SOURCE_REFERENCE");
        when(portal.fetchAcademicRecords(other, connection, program)).thenReturn(observation());
        assertThatThrownBy(() -> queries.detail(other, otherProgram, ownDetail))
                .isInstanceOf(AcademicSourceQueryException.class).hasMessage("INVALID_SOURCE_REFERENCE");
        verify(portal, never()).fetchAcademicResultDetail(eq(other), any(), any(), any());
    }

    @Test void rejectsForgedOrMissingReferencesBeforeDetailSourceCall() {
        assertThatThrownBy(() -> queries.detail(owner, "pr_bad", "dt_bad"))
                .hasMessage("INVALID_SOURCE_REFERENCE");
        verifyNoInteractions(portal);
    }

    @Test void emptySourceRemainsUnknownNotComplete() {
        connected(owner);
        when(portal.fetchAcademicPrograms(owner, connection)).thenReturn(List.of(program));
        when(portal.fetchAcademicRecords(owner, connection, program))
                .thenReturn(new AcademicRecordObservation(program, List.of()));
        var ref = queries.programs(owner).programs().getFirst().programRef();
        var view = queries.records(owner, ref);
        assertThat(view.records()).isEmpty();
        assertThat(view.completeness()).isEqualTo("UNKNOWN");
    }

    @Test void distinguishesMissingConnectionReconnectionAndTimeout() {
        when(gate.tryAcquire(eq(owner), any())).thenReturn(true);
        when(portal.connectionInfo(owner)).thenReturn(new AcademicPortalClient.ConnectionInfo(
                AcademicPortalClient.ConnectionInfo.State.NOT_CONNECTED, null));
        assertThatThrownBy(() -> queries.programs(owner)).hasMessage("CONNECTION_NOT_FOUND");
        when(portal.connectionInfo(owner)).thenReturn(new AcademicPortalClient.ConnectionInfo(
                AcademicPortalClient.ConnectionInfo.State.RECONNECTION_REQUIRED, null));
        assertThatThrownBy(() -> queries.programs(owner)).hasMessage("RECONNECTION_REQUIRED");
        when(portal.connectionInfo(owner)).thenReturn(new AcademicPortalClient.ConnectionInfo(
                AcademicPortalClient.ConnectionInfo.State.CONNECTED, null));
        when(portal.currentConnection(owner)).thenReturn(connection);
        when(portal.fetchAcademicPrograms(owner, connection))
                .thenThrow(new AcademicPortalException(AcademicPortalException.Code.TIMEOUT));
        assertThatThrownBy(() -> queries.programs(owner)).hasMessage("SOURCE_TIMEOUT");
    }

    @Test void sourceFailureDoesNotBecomeEmptyAndCooldownAvoidsRepeatedReads() {
        connected(owner);
        when(portal.fetchAcademicPrograms(owner, connection))
                .thenThrow(new AcademicPortalException(AcademicPortalException.Code.UNEXPECTED_SCHEMA));
        assertThatThrownBy(() -> queries.programs(owner)).hasMessage("SOURCE_SCHEMA_CHANGED");
        when(gate.tryAcquire(owner, AcademicReadGate.Capability.PROGRAMS)).thenReturn(false);
        assertThatThrownBy(() -> queries.programs(owner)).hasMessage("RATE_LIMITED");
        verify(portal, times(1)).fetchAcademicPrograms(owner, connection);
    }

    @Test void statusDescribesSupportWithoutMakingSourceRequest() {
        when(portal.connectionInfo(owner)).thenReturn(new AcademicPortalClient.ConnectionInfo(
                AcademicPortalClient.ConnectionInfo.State.NOT_CONNECTED, null));
        var status = queries.status(owner);
        assertThat(status.connectionState()).isEqualTo("NOT_CONNECTED");
        assertThat(status.capabilities()).anyMatch(item -> item.capability().equals("ACADEMIC_RECORDS")
                && item.mode().equals("LIVE_READ_ONLY"));
        assertThat(status.capabilities()).anyMatch(item -> item.capability().equals("STUDENT_COURSE")
                && item.mode().equals("BLOCKED_SOURCE_LIMIT"));
        assertThat(status.capabilities()).anyMatch(item -> item.capability().equals("SCHEDULE")
                && item.mode().equals("LIVE_READ_ONLY") && item.completeness().equals("UNKNOWN"));
        assertThat(status.capabilities()).anyMatch(item -> item.capability().equals("EXAMS")
                && item.mode().equals("LIVE_READ_ONLY") && item.completeness().equals("UNKNOWN"));
        assertThat(status.capabilities()).anyMatch(item -> item.capability().equals("CLASS_SESSION")
                && item.mode().equals("BLOCKED_SOURCE_LIMIT"));
        assertThat(status.capabilities()).anyMatch(item -> item.capability().equals("EXAM_PERSISTENCE")
                && item.mode().equals("BLOCKED_SOURCE_LIMIT"));
        verify(portal, never()).fetchAcademicRecords(any(), any(), any());
    }

    @Test void schedulePreservesDuplicateObservationsNullsAndSourceLimits() {
        connected(owner);
        var from = LocalDate.of(2026, 10, 1);
        var entry = new ScheduleObservation.Entry(new ScheduleObservation.CandidateIdentity(
                "private-schedule-id", "private-section-id", "private-enrollment-id"), null, from,
                null, null, null, null, ScheduleObservation.Kind.UNKNOWN);
        when(portal.fetchSchedule(owner, connection, from, from)).thenReturn(new ScheduleObservation(
                from, from, ZoneId.of("Asia/Ho_Chi_Minh"), List.of(entry, entry)));
        var view = queries.schedule(owner, "2026-10-01", "2026-10-01");
        assertThat(view.completeness()).isEqualTo("UNKNOWN");
        assertThat(view.identityScope()).isEqualTo("UNVERIFIED");
        assertThat(view.zone()).isEqualTo("Asia/Ho_Chi_Minh");
        assertThat(view.entries()).hasSize(2);
        assertThat(view.entries().getFirst().courseName()).isNull();
        assertThat(view.entries().getFirst().startsAt()).isNull();
        assertThat(view.entries().getFirst().room()).isNull();
        assertThat(view.entries().getFirst().lecturer()).isNull();
        assertThat(view.entries().getFirst().kind()).isEqualTo("UNKNOWN");
        assertThat(view.toString()).doesNotContain("private-schedule-id");
    }

    @Test void scheduleAccepts31DaysButRejectsInvalidRangesBeforeSourceRead() {
        connected(owner);
        var from = LocalDate.of(2026, 10, 1);
        var through = from.plusDays(30);
        when(portal.fetchSchedule(owner, connection, from, through)).thenReturn(new ScheduleObservation(
                from, through, ZoneId.of("Asia/Ho_Chi_Minh"), List.of()));
        assertThat(queries.schedule(owner, "2026-10-01", "2026-10-31").entries()).isEmpty();
        for (var range : List.of("2026-11-01", "2026-09-30", "2026-02-30", "not-a-date"))
            assertThatThrownBy(() -> queries.schedule(owner, "2026-10-01", range))
                    .hasMessage("INVALID_SOURCE_RANGE");
        verify(portal, times(1)).fetchSchedule(any(), any(), any(), any());
    }

    @Test void scheduleRejectsMismatchedSourceRangeAndRespectsCapabilityCooldown() {
        connected(owner);
        var from = LocalDate.of(2026, 10, 1);
        when(portal.fetchSchedule(owner, connection, from, from)).thenReturn(new ScheduleObservation(
                from, from.plusDays(1), ZoneId.of("Asia/Ho_Chi_Minh"), List.of()));
        assertThatThrownBy(() -> queries.schedule(owner, "2026-10-01", "2026-10-01"))
                .hasMessage("SOURCE_DATA_INCOMPLETE");
        when(gate.tryAcquire(owner, AcademicReadGate.Capability.SCHEDULE)).thenReturn(false);
        assertThatThrownBy(() -> queries.schedule(owner, "2026-10-01", "2026-10-01"))
                .hasMessage("RATE_LIMITED");
        verify(portal, times(1)).fetchSchedule(any(), any(), any(), any());
    }

    @Test void examPeriodsUseOwnedOpaqueReferencesAndRejectDuplicateSourceIds() {
        connected(owner);
        var period = new ExamPeriod("private-period-id", "Kỳ nguồn giả định");
        when(portal.fetchExamPeriods(owner, connection)).thenReturn(List.of(period));
        var view = queries.examPeriods(owner);
        assertThat(view.completeness()).isEqualTo("UNKNOWN");
        assertThat(view.periods().getFirst().label()).isEqualTo("Kỳ nguồn giả định");
        assertThat(view.periods().getFirst().periodRef()).matches("ep_[0-9a-f]{64}")
                .doesNotContain(period.sourceId());
        when(portal.fetchExamPeriods(owner, connection)).thenReturn(List.of(period, period));
        assertThatThrownBy(() -> queries.examPeriods(owner)).hasMessage("SOURCE_SCHEMA_CHANGED");
    }

    @Test void examsResolveCurrentUsersPeriodAndKeepDuplicateRowsWithoutCandidateIdentity() {
        connected(owner);
        var period = new ExamPeriod("private-period-id", "Kỳ nguồn giả định");
        var start = Instant.parse("2026-10-01T01:30:00Z");
        var entry = new ExamObservation.Entry(new ExamObservation.CandidateIdentity("private-exam-id"),
                "TEST101", "Môn kiểm thử A", 2, null, start, null, null);
        when(portal.fetchExamPeriods(owner, connection)).thenReturn(List.of(period));
        var ref = queries.examPeriods(owner).periods().getFirst().periodRef();
        when(portal.fetchExams(owner, connection, period)).thenReturn(new ExamObservation(period,
                ZoneId.of("Asia/Ho_Chi_Minh"), List.of(entry, entry)));
        var view = queries.exams(owner, ref);
        assertThat(view.completeness()).isEqualTo("UNKNOWN");
        assertThat(view.identityScope()).isEqualTo("UNVERIFIED");
        assertThat(view.entries()).hasSize(2);
        assertThat(view.entries().getFirst().date()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(view.entries().getFirst().startsAt()).isEqualTo(LocalTime.of(8, 30));
        assertThat(view.entries().getFirst().endsAt()).isNull();
        assertThat(view.entries().getFirst().examSession()).isNull();
        assertThat(view.entries().getFirst().room()).isNull();
        assertThat(view.entries().getFirst().examAttempt()).isEqualTo(2);
        assertThat(view.toString()).doesNotContain("private-exam-id", period.sourceId());
        assertThatThrownBy(() -> queries.exams(owner, "ep_" + "f".repeat(64)))
                .hasMessage("INVALID_SOURCE_REFERENCE");
        assertThatThrownBy(() -> queries.exams(owner, "private-period-id"))
                .hasMessage("INVALID_SOURCE_REFERENCE");
        verify(portal, times(1)).fetchExams(owner, connection, period);
    }

    @Test void examsRejectForeignReferenceAndMismatchedObservation() {
        connected(owner);
        connected(other);
        var period = new ExamPeriod("private-period-id", "Kỳ nguồn giả định");
        when(portal.fetchExamPeriods(owner, connection)).thenReturn(List.of(period));
        when(portal.fetchExamPeriods(other, connection)).thenReturn(List.of(period));
        var ref = queries.examPeriods(owner).periods().getFirst().periodRef();
        assertThatThrownBy(() -> queries.exams(other, ref)).hasMessage("INVALID_SOURCE_REFERENCE");
        verify(portal, never()).fetchExams(eq(other), any(), any());
        when(portal.fetchExams(owner, connection, period)).thenReturn(new ExamObservation(
                new ExamPeriod("other-period", "Khác"), ZoneId.of("Asia/Ho_Chi_Minh"), List.of()));
        assertThatThrownBy(() -> queries.exams(owner, ref)).hasMessage("SOURCE_DATA_INCOMPLETE");
    }
}
