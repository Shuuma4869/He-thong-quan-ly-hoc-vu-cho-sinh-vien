package vn.edu.phenikaa.ams.academic.infrastructure.phenikaa;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import tools.jackson.databind.JsonNode;
import vn.edu.phenikaa.ams.academic.application.port.*;
import static vn.edu.phenikaa.ams.academic.infrastructure.phenikaa.PhenikaaClientException.Code.*;

final class PhenikaaAcademicRecords {
    private PhenikaaAcademicRecords() {}

    static AcademicRecordObservation normalize(JsonNode data, JsonNode registrations, String learner,
                                                 AcademicProgram program) {
        var owners = table(data, "rsThongTinNguoiHoc");
        if (owners.size() != 1 || !learner.equals(text(owners.get(0), "QLSV_NGUOIHOC_ID", 256))) throw invalid();
        var bySection = new HashMap<String, JsonNode>();
        var registrationIds = new HashSet<String>();
        for (var row : table(registrations, "rsKetQuaDangKy")) {
            if (!learner.equals(text(row, "QLSV_NGUOIHOC_ID", 256))) throw invalid();
            String id = text(row, "ID", 128);
            if (!registrationIds.add(id)) throw invalid();
            String section = text(row, "DANGKY_LOPHOCPHAN_ID", 128);
            String ownerProgram = text(row, "DAOTAO_TOCHUCCHUONGTRINH_ID", 128);
            if (ownerProgram.equals(program.sourceId()) && bySection.putIfAbsent(section, row) != null) throw invalid();
        }
        var attempts = new LinkedHashMap<String, Attempt>();
        var componentIds = new HashSet<String>();
        var finalIds = new HashSet<String>();
        for (var row : table(data, "rsDiemThanhPhan")) {
            var attempt = attempt(row, false, bySection, attempts);
            String id = text(row, "ID", 128);
            if (!componentIds.add(id)) throw invalid();
            int examAttempt = positive(row, "LANTHI");
            String code = text(row, "DIEM_THANHPHANDIEM_MA", 80);
            if (!attempt.componentKeys.add(examAttempt + ":" + code)) throw invalid();
            attempt.components.add(new AcademicRecordObservation.Component(id, code,
                    text(row, "DIEM_THANHPHANDIEM_TEN", 200), examAttempt, decimal(row, "DIEM", 6, false)));
        }
        for (var row : table(data, "rsDiemKetThucHocPhan")) {
            var attempt = attempt(row, true, bySection, attempts);
            String id = text(row, "ID", 128);
            // There is no verified rule for selecting one final result among multiple exam attempts.
            if (!finalIds.add(id) || attempt.result != null) throw invalid();
            var outcome = switch (text(row, "DANHGIA_MA", 80)) {
                case "DAT" -> AcademicRecordObservation.Outcome.PASSED;
                case "KHONGDAT" -> AcademicRecordObservation.Outcome.FAILED;
                case "HOCLAI" -> AcademicRecordObservation.Outcome.RETAKE_REQUIRED;
                default -> throw invalid();
            };
            attempt.result = new AcademicRecordObservation.FinalResult(id, positive(row, "LANTHI"), outcome,
                    decimal(row, "DIEM", 6, true), decimal(row, "DIEMQUYDOI", 6, true),
                    optionalText(row, "DIEMQUYDOI_TEN", 16));
        }
        var entries = attempts.values().stream().map(Attempt::entry).toList();
        var courses = new HashMap<String, List<Object>>();
        var codes = new HashMap<String, String>();
        var periods = new HashMap<String, List<Integer>>();
        for (var entry : entries) {
            requireConsistent(courses, entry.sourceCourseId(), List.of(entry.courseCode(), entry.courseName(), entry.courseCredits()));
            requireConsistent(codes, entry.courseCode(), entry.sourceCourseId());
            requireConsistent(periods, entry.sourcePeriodId(), List.of(entry.academicYearStart(), entry.semesterNumber()));
        }
        return new AcademicRecordObservation(program, entries);
    }

    private static Attempt attempt(JsonNode row, boolean finalResult, Map<String, JsonNode> registrations,
                                   Map<String, Attempt> attempts) {
        String section = text(row, "DIEM_DANHSACHHOC_ID", 128);
        var registration = registrations.get(section);
        if (registration == null) throw invalid();
        String registrationId = text(registration, "ID", 128);
        String courseId = text(registration, "DAOTAO_HOCPHAN_ID", 128);
        String code = text(registration, "DAOTAO_HOCPHAN_MA", 40);
        String name = text(registration, "DAOTAO_HOCPHAN_TEN", 240);
        String period = text(registration, "DAOTAO_THOIGIANDAOTAO_ID", 128);
        BigDecimal credits = decimal(registration, "DAOTAO_HOCPHAN_HOCTRINH", 5, false);
        if (!code.equals(text(row, "DAOTAO_HOCPHAN_MA", 40))
                || !period.equals(text(row, "DAOTAO_THOIGIANDAOTAO_ID", 128))
                || credits.compareTo(decimal(row, "DAOTAO_HOCPHAN_HOCTRINH", 5, false)) != 0) throw invalid();
        if (!finalResult && !courseId.equals(text(row, "DAOTAO_HOCPHAN_ID", 128))) throw invalid();
        if (finalResult) text(row, "DAOTAO_HOCPHAN_TEN", 240);
        int year;
        if (finalResult) {
            String range = text(row, "NAMHOC", 9);
            if (!range.matches("[0-9]{4}_[0-9]{4}")) throw invalid();
            year = Integer.parseInt(range.substring(0, 4));
            if (Integer.parseInt(range.substring(5)) != year + 1) throw invalid();
        } else year = positive(row, "NAMHOC");
        if (year < 1900 || year > 9998) throw invalid();
        int semester = positive(row, "HOCKY"), learningAttempt = positive(row, "LANHOC");
        var candidate = new Attempt(registrationId, section, courseId, code, name, credits, period, year, semester, learningAttempt);
        var existing = attempts.putIfAbsent(registrationId, candidate);
        if (existing == null) return candidate;
        if (!existing.context.equals(candidate.context)) throw invalid();
        return existing;
    }

    private static final class Attempt {
        private final Context context;
        private final List<AcademicRecordObservation.Component> components = new ArrayList<>();
        private final Set<String> componentKeys = new HashSet<>();
        private AcademicRecordObservation.FinalResult result;
        Attempt(String enrollment, String section, String course, String code, String name, BigDecimal credits,
                String period, int year, int semester, int learningAttempt) {
            context = new Context(enrollment, section, course, code, name, credits, period, year, semester, learningAttempt);
        }
        AcademicRecordObservation.Entry entry() {
            return new AcademicRecordObservation.Entry(context.enrollment(), context.section(), context.course(),
                    context.code(), context.name(), context.credits(), context.period(), context.year(),
                    context.semester(), context.learningAttempt(), components, result);
        }
    }

    private record Context(String enrollment, String section, String course, String code, String name,
                           BigDecimal credits, String period, int year, int semester, int learningAttempt) {
        @Override public String toString() { return "AcademicSourceContext[redacted]"; }
    }

    static JsonNode table(JsonNode data, String name) {
        if (data == null || !data.isObject() || !data.path(name).isArray()) throw invalid();
        return boundedArray(data.path(name), 10000);
    }
    static JsonNode boundedArray(JsonNode data, int limit) {
        if (data == null || !data.isArray()) throw invalid();
        if (data.size() > limit) throw new PhenikaaClientException(RESPONSE_TOO_LARGE);
        for (var item : data) if (!item.isObject()) throw invalid();
        return data;
    }
    static String text(JsonNode row, String field, int limit) {
        String value = optionalText(row, field, limit);
        if (value == null || value.isBlank()) throw invalid();
        return value;
    }
    private static String optionalText(JsonNode row, String field, int limit) {
        var value = row.path(field);
        if (value.isNull() || value.isMissingNode()) return null;
        if (!value.isString() || value.stringValue().length() > limit
                || value.stringValue().chars().anyMatch(Character::isISOControl)) throw invalid();
        return value.stringValue().isBlank() ? null : value.stringValue();
    }
    private static int positive(JsonNode row, String field) {
        var value = row.path(field);
        if (!value.isNumber()) throw invalid();
        try {
            int number = value.decimalValue().intValueExact();
            if (number <= 0) throw invalid();
            return number;
        } catch (ArithmeticException ex) { throw invalid(); }
    }
    private static BigDecimal decimal(JsonNode row, String field, int precision, boolean optional) {
        var value = row.path(field);
        if (optional && (value.isMissingNode() || value.isNull())) return null;
        if (!value.isNumber()) throw invalid();
        try {
            var number = value.decimalValue().setScale(2, RoundingMode.UNNECESSARY);
            if (number.signum() < 0 || number.precision() > precision) throw invalid();
            return number;
        } catch (ArithmeticException ex) { throw invalid(); }
    }
    private static <K, V> void requireConsistent(Map<K, V> values, K key, V value) {
        var old = values.putIfAbsent(key, value);
        if (old != null && !old.equals(value)) throw invalid();
    }
    private static PhenikaaClientException invalid() { return new PhenikaaClientException(UNEXPECTED_SCHEMA); }
}
