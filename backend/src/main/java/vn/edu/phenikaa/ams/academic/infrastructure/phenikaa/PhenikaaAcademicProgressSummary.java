package vn.edu.phenikaa.ams.academic.infrastructure.phenikaa;

import java.math.BigDecimal;
import tools.jackson.databind.JsonNode;
import vn.edu.phenikaa.ams.academic.application.port.AcademicProgram;
import vn.edu.phenikaa.ams.academic.application.port.AcademicProgressSummaryObservation;
import vn.edu.phenikaa.ams.academic.application.port.AcademicProgressSummaryUnavailable;
import static vn.edu.phenikaa.ams.academic.infrastructure.phenikaa.PhenikaaClientException.Code.*;

final class PhenikaaAcademicProgressSummary {
    private PhenikaaAcademicProgressSummary() {}

    static AcademicProgressSummaryObservation normalize(JsonNode data, AcademicProgram program) {
        var rows = PhenikaaAcademicRecords.table(data, "rsDiemTrungBinhChung");
        BigDecimal scale4 = null, scale10 = null, credits = null;
        for (var row : rows) {
            var kind = row.path("LOAIDIEMTRUNGBINH_MA");
            if (!kind.isString() || !"TRUNGBINHTICHLUY".equals(kind.stringValue())) continue;
            var period = row.path("DAOTAO_THOIGIANDAOTAO_ID");
            if (!period.isNull()) continue;
            var overall = row.path("THUOCTINHLANTINH");
            if (!overall.isNumber() || overall.decimalValue().compareTo(BigDecimal.ZERO) != 0) continue;
            var scale = row.path("THANGDIEM_MA");
            if (!scale.isString()) throw invalid();
            if ("4".equals(scale.stringValue())) {
                if (scale4 != null) throw invalid();
                scale4 = number(row, "DIEMTRUNGBINH", 4, 6);
            } else if ("10".equals(scale.stringValue())) {
                if (scale10 != null) throw invalid();
                scale10 = number(row, "DIEMTRUNGBINH", 10, 6);
                credits = number(row, "TONGSOTINCHI", 1_000_000, 4);
            }
        }
        if (scale4 == null || scale10 == null || credits == null)
            throw new AcademicProgressSummaryUnavailable();
        return new AcademicProgressSummaryObservation(program, scale4, scale10, credits);
    }

    private static BigDecimal number(JsonNode row, String field, int maximum, int maxScale) {
        var value = row.path(field);
        if (!value.isNumber()) throw invalid();
        var number = value.decimalValue();
        if (number.signum() < 0 || number.compareTo(BigDecimal.valueOf(maximum)) > 0
                || number.precision() > 10 || number.scale() > maxScale) throw invalid();
        return number;
    }

    private static PhenikaaClientException invalid() { return new PhenikaaClientException(UNEXPECTED_SCHEMA); }
}
