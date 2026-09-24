package vn.edu.phenikaa.ams.academic.infrastructure.phenikaa;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.Locale;
import tools.jackson.databind.JsonNode;
import vn.edu.phenikaa.ams.academic.application.port.ExamObservation;
import static vn.edu.phenikaa.ams.academic.infrastructure.phenikaa.PhenikaaClientException.Code.UNEXPECTED_SCHEMA;

record PhenikaaExamItem(String sourceId, String courseCode, String courseName, String date,
                       int startHour, int startMinute, Integer endHour, Integer endMinute,
                       int examAttempt, String examSession, String room) {
    static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT);

    static PhenikaaExamItem from(JsonNode node, String learnerId) {
        if (!node.isObject() || !learnerId.equals(text(node, "QLSV_NGUOIHOC_ID", true))) throw invalid();
        return new PhenikaaExamItem(text(node, "IDLICHHOC", true), text(node, "MAHOCPHAN", true),
                text(node, "TENHOCPHAN", true), text(node, "NGAYHOC", true),
                requiredNumber(node, "GIOBATDAU"), requiredNumber(node, "PHUTBATDAU"),
                number(node, "GIOKETTHUC"), number(node, "PHUTKETTHUC"),
                requiredNumber(node, "LANTHI"), text(node, "CATHI", false), text(node, "PHONGHOC_TEN", false));
    }

    ExamObservation.Entry normalize() {
        try {
            LocalDate day = LocalDate.parse(date, DATE);
            Instant start = day.atTime(LocalTime.of(startHour, startMinute)).atZone(ZONE).toInstant();
            if ((endHour == null) != (endMinute == null) || examAttempt < 1) throw invalid();
            Instant end = endHour == null ? null : day.atTime(LocalTime.of(endHour, endMinute)).atZone(ZONE).toInstant();
            if (end != null && !end.isAfter(start)) throw invalid();
            return new ExamObservation.Entry(new ExamObservation.CandidateIdentity(sourceId), courseCode,
                    courseName, examAttempt, examSession, start, end, room);
        } catch (DateTimeException ex) { throw invalid(); }
    }

    static String text(JsonNode node, String field, boolean required) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            if (required) throw invalid();
            return null;
        }
        if (!value.isString() || value.stringValue().length() > 2048
                || value.stringValue().chars().anyMatch(Character::isISOControl)
                || (required && value.stringValue().isBlank())) throw invalid();
        return value.stringValue();
    }

    private static int requiredNumber(JsonNode node, String field) {
        Integer value = number(node, field);
        if (value == null) throw invalid();
        return value;
    }

    private static Integer number(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) return null;
        if (!value.isNumber()) throw invalid();
        try { return value.decimalValue().intValueExact(); }
        catch (ArithmeticException ex) { throw invalid(); }
    }

    private static PhenikaaClientException invalid() { return new PhenikaaClientException(UNEXPECTED_SCHEMA); }
    @Override public String toString() { return "PhenikaaExamItem[redacted]"; }
}
