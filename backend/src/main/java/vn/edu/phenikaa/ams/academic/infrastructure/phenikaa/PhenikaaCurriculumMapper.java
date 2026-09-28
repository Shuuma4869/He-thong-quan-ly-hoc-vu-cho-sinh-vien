package vn.edu.phenikaa.ams.academic.infrastructure.phenikaa;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import tools.jackson.databind.JsonNode;
import vn.edu.phenikaa.ams.academic.application.port.*;
import static vn.edu.phenikaa.ams.academic.infrastructure.phenikaa.PhenikaaAcademicRecords.text;
import static vn.edu.phenikaa.ams.academic.infrastructure.phenikaa.PhenikaaClientException.Code.*;

final class PhenikaaCurriculumMapper {
    private PhenikaaCurriculumMapper() {}
    record GroupData(JsonNode row, boolean elective, List<JsonNode> members) {
        @Override public String toString() { return "CurriculumSourceGroup[redacted]"; }
    }

    static List<CurriculumOption> options(List<JsonNode> rows, String learner) {
        if (rows.size() > 64) throw new PhenikaaClientException(RESPONSE_TOO_LARGE);
        var ids = new HashSet<String>(); var result = new ArrayList<CurriculumOption>();
        for (var row : rows) {
            if (!learner.equals(text(row, "QLSV_NGUOIHOC_ID", 256))) throw invalid();
            String id = text(row, "DAOTAO_TOCHUCCHUONGTRINH_ID", 128);
            if (!ids.add(id)) throw invalid();
            result.add(new CurriculumOption(id, text(row, "DAOTAO_TOCHUCCHUONGTRINH_MA", 40),
                    text(row, "DAOTAO_TOCHUCCHUONGTRINH_TEN", 240), optional(row, "DAOTAO_KHOADAOTAO_MA", 40),
                    decimal(row, "TONGSOTINCHIQUYDINH", 6)));
        }
        return List.copyOf(result);
    }

    static List<CurriculumObservation.CourseEntry> courses(CurriculumOption option, List<JsonNode> rows) {
        var ids = new HashSet<String>(); var rowIds = new HashSet<String>(); var codes = new HashSet<String>();
        var courses = new ArrayList<CurriculumObservation.CourseEntry>();
        for (var row : rows) {
            requireProgram(row, option);
            String id = text(row, "DAOTAO_HOCPHAN_ID", 128), code = text(row, "DAOTAO_HOCPHAN_MA", 40);
            if (!ids.add(id) || !rowIds.add(text(row, "ID", 128)) || !codes.add(code)
                    || !code.equals(code.trim().toUpperCase(Locale.ROOT))) throw invalid();
            courses.add(new CurriculumObservation.CourseEntry(id, code, text(row, "DAOTAO_HOCPHAN_TEN", 240),
                    decimal(row, "HOCTRINHAPDUNGHOCTAP", 5), optional(row, "THONGTINQUANHEHOCPHAN", 16000) != null));
        }
        return List.copyOf(courses);
    }

    static CurriculumObservation normalize(CurriculumOption option, List<JsonNode> rows, List<GroupData> sourceGroups) {
        var courses = courses(option, rows);
        var byId = new HashMap<String, CurriculumObservation.CourseEntry>();
        courses.forEach(c -> byId.put(c.sourceId(), c));
        var groups = new ArrayList<CurriculumObservation.Group>();
        var groupIds = new HashSet<String>(); var groupCodes = new HashSet<String>(); var assigned = new HashSet<String>();
        for (var data : sourceGroups) {
            var row = data.row(); requireProgram(row, option);
            String id = text(row, "ID", 128), code = text(row, "KYHIEU", 40);
            if (!groupIds.add(id) || !groupCodes.add(code)) throw invalid();
            // Only flat groups have been verified; do not discard an unknown parent relationship.
            if (optional(row, data.elective() ? "DAOTAO_KHOITUCHON_DON_CHA_ID" : "DAOTAO_KHOIBATBUOC_CHA_ID", 128) != null) throw invalid();
            var members = new ArrayList<String>(); var memberIds = new HashSet<String>();
            BigDecimal totalCredits = BigDecimal.ZERO;
            for (var member : data.members()) {
                requireProgram(member, option);
                if (!id.equals(text(member, data.elective() ? "DAOTAO_KHOITUCHON_DON_ID" : "DAOTAO_KHOIBATBUOC_ID", 128))) throw invalid();
                String courseId = text(member, "DAOTAO_HOCPHAN_ID", 128);
                var course = byId.get(courseId);
                if (course == null || !memberIds.add(text(member, "ID", 128)) || !assigned.add(courseId)
                        || !course.code().equals(text(member, "DAOTAO_HOCPHAN_MA", 40))
                        || course.credits().compareTo(decimal(member, "HOCTRINHAPDUNGHOCTAP", 5)) != 0) throw invalid();
                // A non-null per-member override needs its own semantics before import.
                if (data.elective() && !member.path("LAHOCPHANBATBUOC").isNull()
                        && !member.path("LAHOCPHANBATBUOC").isMissingNode()) throw invalid();
                members.add(courseId); totalCredits = totalCredits.add(course.credits());
            }
            if (count(row, data.elective() ? "TONGSOHP" : "TONGSOHOCPHAN", false) != members.size()
                    || decimal(row, data.elective() ? "TONGSOTC" : "TONGSOTINCHI", 6).compareTo(totalCredits) != 0) throw invalid();
            BigDecimal minimum = data.elective() ? decimal(row, "SOTINCHIQUYDINH", 6) : null;
            Integer courseCount = data.elective() ? count(row, "SOHOCPHANQUYDINH", true) : null;
            if (minimum != null && minimum.compareTo(totalCredits) > 0 || courseCount != null && courseCount > members.size()) throw invalid();
            groups.add(new CurriculumObservation.Group(id, code, text(row, "TEN", 200), data.elective()
                    ? CurriculumObservation.Requirement.ELECTIVE : CurriculumObservation.Requirement.REQUIRED,
                    minimum, courseCount, members));
        }
        return new CurriculumObservation(option, courses, groups);
    }

    static CourseRelationObservation relations(CurriculumOption option, String courseId, List<JsonNode> rows) {
        var conditions = new ArrayList<CourseRelationObservation.Condition>(); var ids = new HashSet<String>();
        for (var row : rows) {
            requireProgram(row, option);
            String id = text(row, "ID", 128), related = text(row, "DAOTAO_HOCPHAN_QUANHE_ID", 128);
            if (!courseId.equals(text(row, "DAOTAO_HOCPHAN_ID", 128)) || related.equals(courseId) || !ids.add(id)) throw invalid();
            conditions.add(new CourseRelationObservation.Condition(id, related, text(row, "LOAIQUANHE_TEN", 200),
                    optional(row, "MUCDIEUKIEN_TEN", 200), optional(row, "TOANTU_TEN", 80), optional(row, "GIATRIDIEUKIEN", 80)));
        }
        return new CourseRelationObservation(courseId, conditions);
    }

    static void requireProgram(JsonNode row, CurriculumOption option) {
        if (!option.sourceId().equals(text(row, "DAOTAO_TOCHUCCHUONGTRINH_ID", 128))) throw invalid();
    }
    private static String optional(JsonNode row, String key, int length) {
        var value = row.path(key);
        if (value.isNull() || value.isMissingNode()) return null;
        if (!value.isString() || value.stringValue().length() > length
                || value.stringValue().chars().anyMatch(Character::isISOControl)) throw invalid();
        return value.stringValue().isBlank() ? null : value.stringValue();
    }
    private static BigDecimal decimal(JsonNode row, String key, int precision) {
        if (!row.path(key).isNumber()) throw invalid();
        try {
            var value = row.path(key).decimalValue().setScale(2, RoundingMode.UNNECESSARY);
            if (value.signum() < 0 || value.precision() > precision) throw invalid();
            return value;
        } catch (ArithmeticException ex) { throw invalid(); }
    }
    private static Integer count(JsonNode row, String key, boolean optional) {
        if (optional && (row.path(key).isNull() || row.path(key).isMissingNode())) return null;
        if (!row.path(key).isNumber()) throw invalid();
        try {
            int value = row.path(key).decimalValue().intValueExact();
            if (value < 0) throw invalid();
            return value;
        } catch (ArithmeticException ex) { throw invalid(); }
    }
    private static PhenikaaClientException invalid() { return new PhenikaaClientException(UNEXPECTED_SCHEMA); }
}
