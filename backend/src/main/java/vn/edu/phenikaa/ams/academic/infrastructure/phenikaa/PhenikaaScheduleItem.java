package vn.edu.phenikaa.ams.academic.infrastructure.phenikaa;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.Locale;
import tools.jackson.databind.JsonNode;
import vn.edu.phenikaa.ams.academic.application.port.ScheduleObservation;
import static vn.edu.phenikaa.ams.academic.infrastructure.phenikaa.PhenikaaClientException.Code.UNEXPECTED_SCHEMA;

record PhenikaaScheduleItem(String scheduleId, String sectionId, String registeredSectionId,
                            String courseName, String date, Integer startHour, Integer startMinute,
                            Integer endHour, Integer endMinute, String room, String lecturer, String classification) {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT);

    static PhenikaaScheduleItem from(JsonNode node) {
        if (!node.isObject()) throw new PhenikaaClientException(UNEXPECTED_SCHEMA);
        return new PhenikaaScheduleItem(text(node, "IDLICHHOC"), text(node, "IDLOPHOCPHAN"),
                text(node, "DANGKY_LOPHOCPHAN_ID"), text(node, "TENHOCPHAN"), text(node, "NGAYHOC"),
                number(node, "GIOBATDAU"), number(node, "PHUTBATDAU"),
                number(node, "GIOKETTHUC"), number(node, "PHUTKETTHUC"),
                text(node, "TENPHONGHOC"), text(node, "GIANGVIEN"), text(node, "PHANLOAI"));
    }

    ScheduleObservation.Entry normalize() {
        if (date == null || date.isBlank()) throw new PhenikaaClientException(UNEXPECTED_SCHEMA);
        try {
            LocalDate day = LocalDate.parse(date, DATE);
            LocalTime start = time(startHour, startMinute);
            LocalTime end = time(endHour, endMinute);
            if (start != null && end != null && !end.isAfter(start)) throw new PhenikaaClientException(UNEXPECTED_SCHEMA);
            var kind = "LICHHOC".equals(classification) ? ScheduleObservation.Kind.CLASS
                    : "LICHTHI".equals(classification) ? ScheduleObservation.Kind.EXAM : ScheduleObservation.Kind.UNKNOWN;
            // Neither the mutable response ID nor date/time/room is an identity key.
            var identity = new ScheduleObservation.CandidateIdentity(scheduleId, sectionId, registeredSectionId);
            return new ScheduleObservation.Entry(identity, courseName, day, start, end, room, lecturer, kind);
        } catch (DateTimeException ex) {
            throw new PhenikaaClientException(UNEXPECTED_SCHEMA);
        }
    }

    private static LocalTime time(Integer hour, Integer minute) {
        if (hour == null && minute == null) return null;
        if (hour == null || minute == null) throw new PhenikaaClientException(UNEXPECTED_SCHEMA);
        return LocalTime.of(hour, minute);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) return null;
        if (!value.isString() || value.stringValue().length() > 2048) throw new PhenikaaClientException(UNEXPECTED_SCHEMA);
        return value.stringValue();
    }

    private static Integer number(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) return null;
        if (!value.isNumber()) throw new PhenikaaClientException(UNEXPECTED_SCHEMA);
        try {
            // The portal serializes whole hours/minutes as decimal JSON numbers too.
            return value.decimalValue().intValueExact();
        } catch (ArithmeticException ex) {
            throw new PhenikaaClientException(UNEXPECTED_SCHEMA);
        }
    }

    @Override public String toString() { return "PhenikaaScheduleItem[redacted]"; }
}
