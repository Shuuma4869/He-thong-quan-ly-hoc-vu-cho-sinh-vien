package vn.edu.phenikaa.ams.academic.infrastructure.phenikaa;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import vn.edu.phenikaa.ams.academic.application.port.AcademicProgram;
import static org.assertj.core.api.Assertions.*;

class PhenikaaAcademicProgressSummaryTest {
    private final JsonMapper json = JsonMapper.builder().build();
    private final AcademicProgram program = new AcademicProgram("synthetic-program", "Chương trình kiểm thử");

    private String row(String scale, String average, String credits) {
        return """
                {"DAOTAO_THOIGIANDAOTAO_ID":null,"LOAIDIEMTRUNGBINH_MA":"TRUNGBINHTICHLUY",
                 "THUOCTINHLANTINH":0,"THANGDIEM_MA":%s,"DIEMTRUNGBINH":%s,"TONGSOTINCHI":%s}
                """.formatted(scale, average, credits);
    }

    private String first() { return row("\"4\"", "3.25", "72"); }
    private String second() { return row("\"10\"", "8.10", "72"); }
    private String data(String rows) { return "{\"rsDiemTrungBinhChung\":[" + rows + "]}"; }
    private void invalid(String value, String code) {
        assertThatThrownBy(() -> PhenikaaAcademicProgressSummary.normalize(json.readTree(value), program))
                .hasMessage(code);
    }

    @Test void readsExactlyOneRowPerScaleAndCreditsOnlyFromScale10() {
        var source = data(first().replace("\"TONGSOTINCHI\":72", "\"TONGSOTINCHI\":999")
                + "," + second());
        var result = PhenikaaAcademicProgressSummary.normalize(json.readTree(source), program);
        assertThat(result.cumulativeAverageScale4()).isEqualByComparingTo("3.25");
        assertThat(result.cumulativeAverageScale10()).isEqualByComparingTo("8.10");
        assertThat(result.sourceAccumulatedCredits()).isEqualByComparingTo("72");
        assertThat(result.toString()).isEqualTo("AcademicProgressSummaryObservation[redacted]");
    }

    @Test void duplicatesFailRatherThanChoosingFirst() {
        invalid(data(first() + "," + first() + "," + second()), "UNEXPECTED_SCHEMA");
        invalid(data(first() + "," + second() + "," + second()), "UNEXPECTED_SCHEMA");
    }

    @Test void missingRowsAreUnavailableNotZero() {
        invalid(data(first()), "Progress summary unavailable");
        invalid(data(second()), "Progress summary unavailable");
        invalid(data(""), "Progress summary unavailable");
    }

    @Test void unrelatedAndSemesterRowsCannotBecomeGlobalSummary() {
        var semester = first().replace("\"DAOTAO_THOIGIANDAOTAO_ID\":null", "\"DAOTAO_THOIGIANDAOTAO_ID\":\"semester\"");
        var otherType = first().replace("TRUNGBINHTICHLUY", "TRUNGBINHCHUNG");
        var otherFlag = first().replace("\"THUOCTINHLANTINH\":0", "\"THUOCTINHLANTINH\":1");
        var result = PhenikaaAcademicProgressSummary.normalize(
                json.readTree(data(semester + "," + otherType + "," + first() + "," + second())), program);
        assertThat(result.cumulativeAverageScale4()).isEqualByComparingTo("3.25");
    }

    @Test void rejectsNumericStringsWrongScaleTypesNegativeAndOversizedNumbers() {
        invalid(data(first().replace("\"DIEMTRUNGBINH\":3.25", "\"DIEMTRUNGBINH\":\"3.25\"")
                + "," + second()), "UNEXPECTED_SCHEMA");
        invalid(data(first() + "," + second().replace("\"TONGSOTINCHI\":72", "\"TONGSOTINCHI\":-1")), "UNEXPECTED_SCHEMA");
        invalid(data(first().replace("3.25", "12345678901234567890") + "," + second()), "UNEXPECTED_SCHEMA");
        invalid(data(first().replace("3.25", "3.1234567") + "," + second()), "UNEXPECTED_SCHEMA");
        invalid(data(first().replace("\"THANGDIEM_MA\":\"4\"", "\"THANGDIEM_MA\":4")
                + "," + second()), "UNEXPECTED_SCHEMA");
        invalid(data(first().replace("3.25", "4.01") + "," + second()), "UNEXPECTED_SCHEMA");
    }

    @Test void requiresBoundedArrayAndObjectRows() {
        invalid("{}", "UNEXPECTED_SCHEMA");
        invalid("{\"rsDiemTrungBinhChung\":{}}", "UNEXPECTED_SCHEMA");
        invalid(data(first() + ",null," + second()), "UNEXPECTED_SCHEMA");
        invalid(data((first() + ",").repeat(10000) + second()), "RESPONSE_TOO_LARGE");
    }
}
