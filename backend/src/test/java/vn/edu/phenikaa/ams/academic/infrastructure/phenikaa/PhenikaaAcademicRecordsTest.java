package vn.edu.phenikaa.ams.academic.infrastructure.phenikaa;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;
import vn.edu.phenikaa.ams.academic.application.port.*;
import static org.assertj.core.api.Assertions.*;

class PhenikaaAcademicRecordsTest {
    private final JsonMapper json = JsonMapper.builder().build();
    private final AcademicProgram program = new AcademicProgram("synthetic-program", "Giả định");
    private AcademicRecordObservation map(String data, String registrations) {
        return PhenikaaAcademicRecords.normalize(json.readTree(data), json.readTree(registrations), "synthetic-learner", program);
    }
    @Test void joinsRealRegistrationIdentityAndKeepsLearningAttemptSeparateFromExamAttempt() {
        var observation = map(AcademicSourceFixtures.records(), AcademicSourceFixtures.registrations());
        assertThat(observation.entries()).hasSize(2);
        var first = observation.entries().getFirst();
        var second = observation.entries().getLast();
        assertThat(first.sourceEnrollmentId()).isEqualTo("synthetic-registration-1");
        assertThat(first.sourceSectionId()).isEqualTo("synthetic-section-1");
        assertThat(first.sourceCourseId()).isEqualTo(second.sourceCourseId());
        assertThat(first.reportedLearningAttempt()).isEqualTo(1);
        assertThat(second.reportedLearningAttempt()).isEqualTo(2);
        assertThat(observation.hasAmbiguousLearningAttempts()).isFalse();
        assertThat(first.result().examAttempt()).isEqualTo(2);
        assertThat(first.academicYearStart()).isEqualTo(2026);
        assertThat(first.semesterNumber()).isEqualTo(1);
        assertThat(first.courseCredits()).isEqualByComparingTo("3");
        assertThat(observation.completeness()).isEqualTo(AcademicRecordObservation.Completeness.UNKNOWN);
        assertThat(observation.toString()).isEqualTo("AcademicRecordObservation[redacted]");
        assertThat(first.toString()).doesNotContain("TEST101");
        assertThat(first.result().toString()).doesNotContain("7.25");
        assertThat(first.components().getFirst().toString()).doesNotContain("6.25");
        assertThatThrownBy(() -> observation.entries().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> first.components().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
    @Test void componentAloneNeverBecomesACompletedResult() {
        var value = map(AcademicSourceFixtures.records(AcademicSourceFixtures.component(1), ""), AcademicSourceFixtures.registrations());
        assertThat(value.entries().getFirst().result()).isNull();
    }
    @Test void retainsDistinctRegistrationsWithSameReportedLearningAttemptAndFlagsAmbiguity() {
        var data = AcademicSourceFixtures.records().replace("\"LANHOC\":2.0", "\"LANHOC\":1.0");
        var observation = map(data, AcademicSourceFixtures.registrations());
        assertThat(observation.entries()).hasSize(2);
        assertThat(observation.entries()).extracting(AcademicRecordObservation.Entry::sourceEnrollmentId).doesNotHaveDuplicates();
        assertThat(observation.hasAmbiguousLearningAttempts()).isTrue();
    }
    @Test void optionalGradesRemainUnknownInsteadOfBeingCalculated() {
        var result = AcademicSourceFixtures.result(1).replace("\"DIEMQUYDOI\":2.5", "\"DIEMQUYDOI\":null")
                .replace("\"DIEMQUYDOI_TEN\":\"TEST-B\"", "\"DIEMQUYDOI_TEN\":null");
        var value = map(AcademicSourceFixtures.records(AcademicSourceFixtures.component(1), result), AcademicSourceFixtures.registrations());
        assertThat(value.entries().getFirst().result().gradePoints()).isNull();
        assertThat(value.entries().getFirst().result().letterGrade()).isNull();
    }
    @Test void explicitRetakeRequirementIsNotGuessedFromNumericScore() {
        var data = AcademicSourceFixtures.records().replace("\"DANHGIA_MA\":\"DAT\"", "\"DANHGIA_MA\":\"HOCLAI\"");
        assertThat(map(data, AcademicSourceFixtures.registrations()).entries().getFirst().result().outcome())
                .isEqualTo(AcademicRecordObservation.Outcome.RETAKE_REQUIRED);
    }
    @Test void emptyObservationHasUnknownCompleteness() {
        var observation = map(AcademicSourceFixtures.records("", ""), "{\"rsKetQuaDangKy\":[]}");
        assertThat(observation.entries()).isEmpty();
        assertThat(observation.completeness()).isEqualTo(AcademicRecordObservation.Completeness.UNKNOWN);
    }
    @ParameterizedTest @ValueSource(strings = {"0", "-1", "1.5", "null", "\"1\""})
    void rejectsInvalidLearningAttempt(String value) {
        invalid(AcademicSourceFixtures.records().replace("\"LANHOC\":1.0", "\"LANHOC\":" + value), AcademicSourceFixtures.registrations());
    }
    @Test void rejectsMissingOrAmbiguousRegistrationAndWrongOwner() {
        invalid(AcademicSourceFixtures.records(), "{\"rsKetQuaDangKy\":[]}");
        invalid(AcademicSourceFixtures.records(), AcademicSourceFixtures.registrations().replace("synthetic-learner", "other-learner"));
        invalid(AcademicSourceFixtures.records(), AcademicSourceFixtures.registrations().replace("synthetic-registration-2", "synthetic-registration-1"));
        invalid(AcademicSourceFixtures.records(), AcademicSourceFixtures.registrations().replace("synthetic-section-2", "synthetic-section-1"));
        invalid(AcademicSourceFixtures.records(), AcademicSourceFixtures.registrations().replace("synthetic-course", "other-course"));
    }
    @Test void rejectsDuplicateScoresAndDoesNotSelectLatestOrHighestFinal() {
        invalid(AcademicSourceFixtures.records(AcademicSourceFixtures.component(1) + "," + AcademicSourceFixtures.component(1), ""), AcademicSourceFixtures.registrations());
        invalid(AcademicSourceFixtures.records(AcademicSourceFixtures.component(1), AcademicSourceFixtures.result(1) + ","
                + AcademicSourceFixtures.result(1).replace("synthetic-final-1", "synthetic-final-other").replace("\"LANTHI\":2.0", "\"LANTHI\":3.0")), AcademicSourceFixtures.registrations());
    }
    @Test void rejectsInconsistentSemesterCreditsCourseAndUnknownStatusWithoutSkippingRows() {
        for (String data : new String[]{AcademicSourceFixtures.records().replace("2026_2027", "2026_2028"),
                AcademicSourceFixtures.records().replace("\"NAMHOC\":2026.0", "\"NAMHOC\":2025.0"),
                AcademicSourceFixtures.records().replace("\"DANHGIA_MA\":\"DAT\"", "\"DANHGIA_MA\":\"UNKNOWN\""),
                AcademicSourceFixtures.records().replace("\"DAOTAO_HOCPHAN_HOCTRINH\":3.0", "\"DAOTAO_HOCPHAN_HOCTRINH\":4.0"),
                AcademicSourceFixtures.records().replace("synthetic-period-1", "unknown-period"),
                AcademicSourceFixtures.records().replace("\"DAOTAO_HOCPHAN_MA\":\"TEST101\"", "\"DAOTAO_HOCPHAN_MA\":null")})
            invalid(data, AcademicSourceFixtures.registrations());
    }
    private void invalid(String data, String registrations) {
        assertThatThrownBy(() -> map(data, registrations)).isInstanceOf(PhenikaaClientException.class)
                .hasMessage("UNEXPECTED_SCHEMA").hasNoCause();
    }
}
