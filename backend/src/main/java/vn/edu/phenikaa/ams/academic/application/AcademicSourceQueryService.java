package vn.edu.phenikaa.ams.academic.application;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import vn.edu.phenikaa.ams.academic.application.port.*;
import static vn.edu.phenikaa.ams.academic.application.AcademicSourceQueryException.Code.*;

public class AcademicSourceQueryService {
    private static final ZoneId SOURCE_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final AcademicPortalClient portal;
    private final AcademicReadGate gate;
    private final TrackedCurriculumResolver trackedCurricula;

    public AcademicSourceQueryService(AcademicPortalClient portal, AcademicReadGate gate,
                                      TrackedCurriculumResolver trackedCurricula) {
        this.portal = portal;
        this.gate = gate;
        this.trackedCurricula = trackedCurricula;
    }

    public SourceStatus status(UUID userId) {
        try {
            var connection = portal.connectionInfo(userId);
            return new SourceStatus(connection.state().name(), connection.lastSuccessfulAccessAt(), List.of(
                    new CapabilitySupport("PROFILE", "PERSISTED", "SOURCE_VERIFIED"),
                    new CapabilitySupport("CURRICULUM", "PERSISTED_PARTIAL", "UNKNOWN"),
                    new CapabilitySupport("COURSE_CATALOG", "PERSISTED_PARTIAL", "UNKNOWN"),
                    new CapabilitySupport("ACADEMIC_RECORDS", "LIVE_READ_ONLY", "UNKNOWN"),
                    new CapabilitySupport("ACADEMIC_RESULT_DETAIL", "LIVE_READ_ONLY", "UNKNOWN"),
                    new CapabilitySupport("ACADEMIC_PROGRESS_SUMMARY", "LIVE_READ_ONLY", "UNKNOWN"),
                    new CapabilitySupport("SCHEDULE", "LIVE_READ_ONLY", "UNKNOWN"),
                    new CapabilitySupport("EXAMS", "LIVE_READ_ONLY", "UNKNOWN"),
                    new CapabilitySupport("STUDENT_COURSE", "BLOCKED_SOURCE_LIMIT", "UNKNOWN"),
                    new CapabilitySupport("ACADEMIC_RESULT", "BLOCKED_SOURCE_LIMIT", "UNKNOWN"),
                    new CapabilitySupport("CLASS_SESSION", "BLOCKED_SOURCE_LIMIT", "UNKNOWN"),
                    new CapabilitySupport("EXAM_PERSISTENCE", "BLOCKED_SOURCE_LIMIT", "UNKNOWN"),
                    new CapabilitySupport("PREREQUISITE", "BLOCKED_PARTIAL", "UNKNOWN")));
        } catch (AcademicPortalException ex) { throw map(userId, ex); }
    }

    public ProgramsView programs(UUID userId) {
        return read(userId, AcademicReadGate.Capability.PROGRAMS, connection -> new ProgramsView("UNKNOWN",
                portal.fetchAcademicPrograms(userId, connection).stream()
                        .map(program -> new ProgramView(reference("program", userId, program.sourceId()), program.label()))
                        .toList()));
    }

    public ScheduleView schedule(UUID userId, String fromText, String throughText) {
        LocalDate from = parseDate(fromText);
        LocalDate through = parseDate(throughText);
        long days = ChronoUnit.DAYS.between(from, through);
        if (days < 0 || days > 30) throw new AcademicSourceQueryException(INVALID_SOURCE_RANGE);
        return read(userId, AcademicReadGate.Capability.SCHEDULE, connection -> {
            var observation = portal.fetchSchedule(userId, connection, from, through);
            if (observation == null || !from.equals(observation.from()) || !through.equals(observation.through())
                    || !SOURCE_ZONE.equals(observation.zone()) || observation.entries().size() > 10000
                    || observation.entries().stream().anyMatch(entry -> entry == null || entry.date() == null
                        || entry.date().isBefore(from) || entry.date().isAfter(through) || entry.kind() == null))
                throw new AcademicSourceQueryException(SOURCE_DATA_INCOMPLETE);
            return new ScheduleView("UNKNOWN", "UNVERIFIED", observation.zone().getId(), from, through,
                    observation.entries().stream().map(entry -> new ScheduleEntryView(entry.courseName(), entry.date(),
                            entry.startsAt(), entry.endsAt(), entry.room(), entry.lecturer(), entry.kind().name())).toList());
        });
    }

    public ExamPeriodsView examPeriods(UUID userId) {
        return read(userId, AcademicReadGate.Capability.EXAM_PERIODS, connection -> {
            var periods = checkedExamPeriods(userId, connection);
            return new ExamPeriodsView("UNKNOWN", periods.stream()
                    .map(period -> new ExamPeriodView(reference("exam-period", userId, period.sourceId()), period.label()))
                    .toList());
        });
    }

    public ExamsView exams(UUID userId, String periodRef) {
        validateReference(periodRef, "exam-period");
        return read(userId, AcademicReadGate.Capability.EXAMS, connection -> {
            var period = checkedExamPeriods(userId, connection).stream()
                    .filter(candidate -> reference("exam-period", userId, candidate.sourceId()).equals(periodRef))
                    .findFirst().orElseThrow(() -> new AcademicSourceQueryException(INVALID_SOURCE_REFERENCE));
            var observation = portal.fetchExams(userId, connection, period);
            if (observation == null || !period.equals(observation.requestedPeriod())
                    || !SOURCE_ZONE.equals(observation.zone()) || observation.entries().size() > 10000
                    || observation.entries().stream().anyMatch(entry -> entry == null || entry.startsAt() == null
                        || entry.examAttempt() < 1 || entry.endsAt() != null
                        && (!entry.endsAt().isAfter(entry.startsAt())
                            || !entry.endsAt().atZone(SOURCE_ZONE).toLocalDate()
                                .equals(entry.startsAt().atZone(SOURCE_ZONE).toLocalDate()))))
                throw new AcademicSourceQueryException(SOURCE_DATA_INCOMPLETE);
            var entries = observation.entries().stream().map(entry -> {
                var start = entry.startsAt().atZone(observation.zone());
                var end = entry.endsAt() == null ? null : entry.endsAt().atZone(observation.zone());
                return new ExamEntryView(entry.courseCode(), entry.courseName(), entry.examAttempt(),
                        entry.examSession(), start.toLocalDate(), start.toLocalTime(),
                        end == null ? null : end.toLocalTime(), entry.room());
            }).toList();
            return new ExamsView("UNKNOWN", "UNVERIFIED", observation.zone().getId(),
                    new ExamPeriodView(periodRef, period.label()), entries);
        });
    }

    private List<ExamPeriod> checkedExamPeriods(UUID userId, AcademicPortalClient.StudentConnectionId connection) {
        var periods = portal.fetchExamPeriods(userId, connection);
        if (periods == null || periods.size() > 256) throw new AcademicSourceQueryException(SOURCE_DATA_INCOMPLETE);
        var ids = new HashSet<String>();
        for (var period : periods) {
            if (period == null || !ids.add(period.sourceId()))
                throw new AcademicSourceQueryException(SOURCE_SCHEMA_CHANGED);
        }
        return periods;
    }

    private static LocalDate parseDate(String value) {
        if (value == null || !value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))
            throw new AcademicSourceQueryException(INVALID_SOURCE_RANGE);
        try { return LocalDate.parse(value); }
        catch (DateTimeParseException ex) { throw new AcademicSourceQueryException(INVALID_SOURCE_RANGE); }
    }

    public RecordsView records(UUID userId, String programRef) {
        validateReference(programRef, "program");
        return read(userId, AcademicReadGate.Capability.RECORDS, connection -> {
            var program = resolveProgram(userId, connection, programRef);
            var observation = portal.fetchAcademicRecords(userId, connection, program);
            if (!observation.program().equals(program) || observation.entries().size() > 10000)
                throw new AcademicSourceQueryException(SOURCE_DATA_INCOMPLETE);
            return new RecordsView(observation.completeness().name(), new UnknownSemantics("UNKNOWN", "UNKNOWN", "UNKNOWN"),
                    observation.entries().stream().map(entry -> entryView(userId, program, entry)).toList());
        });
    }

    public ProgressSummaryView progressSummary(UUID userId) {
        return read(userId, AcademicReadGate.Capability.PROGRESS_SUMMARY, connection -> {
            var generation = portal.connectionInfo(userId).authenticatedAt();
            var selected = trackedCurricula.resolve(userId)
                    .orElseThrow(() -> new AcademicSourceQueryException(CURRICULUM_SELECTION_REQUIRED));
            if (selected.sourceProgramId() == null)
                throw new AcademicSourceQueryException(SOURCE_PROGRESS_UNAVAILABLE);
            var matches = portal.fetchAcademicPrograms(userId, connection).stream()
                    .filter(program -> selected.sourceProgramId().equals(program.sourceId())).toList();
            if (matches.isEmpty()) throw new AcademicSourceQueryException(SOURCE_PROGRESS_UNAVAILABLE);
            if (matches.size() != 1) throw new AcademicSourceQueryException(SOURCE_DATA_INCOMPLETE);
            var program = matches.getFirst();
            var observation = portal.fetchAcademicProgressSummary(userId, connection, program);
            if (observation == null || !program.sourceId().equals(observation.program().sourceId()))
                throw new AcademicSourceQueryException(SOURCE_DATA_INCOMPLETE);
            var current = trackedCurricula.resolve(userId)
                    .orElseThrow(() -> new AcademicSourceQueryException(SOURCE_PROGRESS_UNAVAILABLE));
            if (!selected.curriculumId().equals(current.curriculumId())
                    || !selected.sourceProgramId().equals(current.sourceProgramId()))
                throw new AcademicSourceQueryException(SOURCE_PROGRESS_UNAVAILABLE);
            var connectionNow = portal.connectionInfo(userId);
            if (connectionNow.state() != AcademicPortalClient.ConnectionInfo.State.CONNECTED
                    || !java.util.Objects.equals(generation, connectionNow.authenticatedAt()))
                throw new AcademicSourceQueryException(RECONNECTION_REQUIRED);
            return new ProgressSummaryView("SOURCE_REPORTED_LIVE_READ_ONLY", "UNKNOWN", "USER_SELECTED_AMS",
                    new TrackedCurriculumView(selected.curriculumId(), selected.code(), selected.name()),
                    new AccumulatedSummaryView(observation.cumulativeAverageScale4(),
                            observation.cumulativeAverageScale10(), observation.sourceAccumulatedCredits()));
        });
    }

    public DetailView detail(UUID userId, String programRef, String detailRef) {
        validateReference(programRef, "program");
        validateReference(detailRef, "detail");
        return read(userId, AcademicReadGate.Capability.DETAIL, connection -> {
            var program = resolveProgram(userId, connection, programRef);
            var observation = portal.fetchAcademicRecords(userId, connection, program);
            if (!observation.program().equals(program) || observation.entries().size() > 10000)
                throw new AcademicSourceQueryException(SOURCE_DATA_INCOMPLETE);
            var result = observation.entries().stream()
                    .filter(entry -> entry.result() != null
                            && reference("detail", userId, program.sourceId(), entry.result().sourceId()).equals(detailRef))
                    .findFirst().orElseThrow(() -> new AcademicSourceQueryException(INVALID_SOURCE_REFERENCE));
            var sourceDetail = portal.fetchAcademicResultDetail(userId, connection, program, result.result().sourceId());
            if (!result.result().sourceId().equals(sourceDetail.sourceResultId()) || sourceDetail.components().size() > 10000)
                throw new AcademicSourceQueryException(SOURCE_DATA_INCOMPLETE);
            var components = new HashMap<String, DetailComponent>();
            for (var entry : observation.entries()) for (var component : entry.components()) {
                var view = new DetailComponent(reference("registration", userId, program.sourceId(), entry.sourceEnrollmentId()),
                        component.code(), component.name(), component.examAttempt(), component.score());
                if (components.putIfAbsent(entry.sourceEnrollmentId() + "\0" + component.sourceId(), view) != null)
                    throw new AcademicSourceQueryException(SOURCE_DATA_INCOMPLETE);
            }
            var views = new ArrayList<DetailComponent>();
            for (var link : sourceDetail.components()) {
                var view = components.get(link.sourceEnrollmentId() + "\0" + link.sourceComponentId());
                if (view == null || !view.code().equals(link.code()) || !view.name().equals(link.name())
                        || view.examAttempt() != link.examAttempt() || view.score().compareTo(link.score()) != 0)
                    throw new AcademicSourceQueryException(SOURCE_DATA_INCOMPLETE);
                views.add(view);
            }
            return new DetailView(detailRef, observation.completeness().name(), List.copyOf(views));
        });
    }

    private AcademicProgram resolveProgram(UUID userId, AcademicPortalClient.StudentConnectionId connection, String ref) {
        return portal.fetchAcademicPrograms(userId, connection).stream()
                .filter(program -> reference("program", userId, program.sourceId()).equals(ref))
                .findFirst().orElseThrow(() -> new AcademicSourceQueryException(INVALID_SOURCE_REFERENCE));
    }

    private RecordView entryView(UUID userId, AcademicProgram program, AcademicRecordObservation.Entry entry) {
        var semester = entry.semesterIdentifier();
        var result = entry.result() == null ? null : new FinalResultView(
                reference("detail", userId, program.sourceId(), entry.result().sourceId()),
                entry.result().examAttempt(), entry.result().outcome().name(),
                entry.result().numericScore(), entry.result().letterGrade(), entry.result().gradePoints());
        return new RecordView(reference("registration", userId, program.sourceId(), entry.sourceEnrollmentId()),
                new CourseView(entry.courseCode(), entry.courseName(), entry.courseCredits()),
                new SemesterView(semester.academicYearStart(), semester.termCode(), semester.canonicalCode()),
                entry.reportedLearningAttempt(), entry.components().stream()
                        .map(component -> new ComponentView(component.code(), component.name(),
                                component.examAttempt(), component.score())).toList(), result);
    }

    private <T> T read(UUID userId, AcademicReadGate.Capability capability,
                       Function<AcademicPortalClient.StudentConnectionId, T> operation) {
        boolean permitted;
        try { permitted = gate.tryAcquire(userId, capability); }
        catch (RuntimeException ex) { throw new AcademicSourceQueryException(SOURCE_UNAVAILABLE); }
        if (!permitted) throw new AcademicSourceQueryException(RATE_LIMITED);
        try {
            var info = portal.connectionInfo(userId);
            if (info.state() == AcademicPortalClient.ConnectionInfo.State.RECONNECTION_REQUIRED)
                throw new AcademicSourceQueryException(RECONNECTION_REQUIRED);
            if (info.state() != AcademicPortalClient.ConnectionInfo.State.CONNECTED)
                throw new AcademicSourceQueryException(CONNECTION_NOT_FOUND);
            return operation.apply(portal.currentConnection(userId));
        } catch (AcademicProgressSummaryUnavailable ex) {
            throw new AcademicSourceQueryException(SOURCE_PROGRESS_UNAVAILABLE);
        } catch (AcademicPortalException ex) { throw map(userId, ex); }
    }

    private AcademicSourceQueryException map(UUID userId, AcademicPortalException ex) {
        return switch (ex.code()) {
            case SESSION_EXPIRED -> new AcademicSourceQueryException(RECONNECTION_REQUIRED);
            case TIMEOUT -> new AcademicSourceQueryException(SOURCE_TIMEOUT);
            case UNEXPECTED_SCHEMA, DECODE_ERROR -> new AcademicSourceQueryException(SOURCE_SCHEMA_CHANGED);
            case RESPONSE_TOO_LARGE -> new AcademicSourceQueryException(SOURCE_DATA_INCOMPLETE);
            case CONNECTION_UNAVAILABLE -> {
                try {
                    var info = portal.connectionInfo(userId);
                    yield new AcademicSourceQueryException(switch (info.state()) {
                        case RECONNECTION_REQUIRED -> RECONNECTION_REQUIRED;
                        case CONNECTED -> SOURCE_UNAVAILABLE;
                        default -> CONNECTION_NOT_FOUND;
                    });
                } catch (AcademicPortalException ignored) { yield new AcademicSourceQueryException(CONNECTION_NOT_FOUND); }
            }
            default -> new AcademicSourceQueryException(SOURCE_UNAVAILABLE);
        };
    }

    private static void validateReference(String ref, String kind) {
        if (ref == null || !ref.matches(referencePrefix(kind) + "[0-9a-f]{64}"))
            throw new AcademicSourceQueryException(INVALID_SOURCE_REFERENCE);
    }

    private static String referencePrefix(String kind) {
        return switch (kind) {
            case "program" -> "pr_";
            case "detail" -> "dt_";
            case "registration" -> "rg_";
            case "exam-period" -> "ep_";
            default -> throw new IllegalArgumentException("Unsupported source reference kind");
        };
    }

    private static String reference(String kind, UUID userId, String... sourceParts) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            digest.update(kind.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(userId.toString().getBytes(StandardCharsets.UTF_8));
            for (String part : sourceParts) {
                digest.update((byte) 0);
                digest.update(part.getBytes(StandardCharsets.UTF_8));
            }
            return referencePrefix(kind) + HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 unavailable"); }
    }

    public record SourceStatus(String connectionState, Instant lastSuccessfulAccessAt, List<CapabilitySupport> capabilities) {}
    public record CapabilitySupport(String capability, String mode, String completeness) {}
    public record ProgramsView(String completeness, List<ProgramView> programs) {}
    public record ProgramView(String programRef, String label) {}
    public record ProgressSummaryView(String mode, String completeness, String selectionMode,
                                      TrackedCurriculumView curriculum, AccumulatedSummaryView summary) {
        @Override public String toString() { return "ProgressSummaryView[redacted]"; }
    }
    public record TrackedCurriculumView(UUID id, String code, String name) {
        @Override public String toString() { return "TrackedCurriculumView[redacted]"; }
    }
    public record AccumulatedSummaryView(BigDecimal cumulativeAverageScale4, BigDecimal cumulativeAverageScale10,
                                         BigDecimal sourceAccumulatedCredits) {
        @Override public String toString() { return "AccumulatedSummaryView[redacted]"; }
    }
    public record ScheduleView(String completeness, String identityScope, String zone, LocalDate from,
                               LocalDate through, List<ScheduleEntryView> entries) {}
    public record ScheduleEntryView(String courseName, LocalDate date, LocalTime startsAt, LocalTime endsAt,
                                    String room, String lecturer, String kind) {}
    public record ExamPeriodsView(String completeness, List<ExamPeriodView> periods) {}
    public record ExamPeriodView(String periodRef, String label) {}
    public record ExamsView(String completeness, String identityScope, String zone, ExamPeriodView period,
                            List<ExamEntryView> entries) {}
    public record ExamEntryView(String courseCode, String courseName, int examAttempt, String examSession,
                                LocalDate date, LocalTime startsAt, LocalTime endsAt, String room) {}
    public record UnknownSemantics(String creditsEarned, String includedInGpa, String currentResult) {}
    public record RecordsView(String completeness, UnknownSemantics unknownSemantics, List<RecordView> records) {
        @Override public String toString() { return "RecordsView[redacted]"; }
    }
    public record CourseView(String code, String name, BigDecimal credits) {}
    public record SemesterView(int academicYearStart, String termCode, String code) {}
    public record ComponentView(String code, String name, int examAttempt, BigDecimal score) {
        @Override public String toString() { return "ComponentView[redacted]"; }
    }
    public record FinalResultView(String detailRef, int examAttempt, String outcome,
                                  BigDecimal numericScore, String letterGrade, BigDecimal gradePoints) {
        @Override public String toString() { return "FinalResultView[redacted]"; }
    }
    public record RecordView(String registrationRef, CourseView course, SemesterView semester,
                             int reportedLearningAttempt, List<ComponentView> components, FinalResultView finalResult) {
        @Override public String toString() { return "RecordView[redacted]"; }
    }
    public record DetailComponent(String registrationRef, String code, String name, int examAttempt, BigDecimal score) {
        @Override public String toString() { return "DetailComponent[redacted]"; }
    }
    public record DetailView(String detailRef, String completeness, List<DetailComponent> components) {
        @Override public String toString() { return "DetailView[redacted]"; }
    }
}
