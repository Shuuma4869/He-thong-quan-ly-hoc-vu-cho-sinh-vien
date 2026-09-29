package vn.edu.phenikaa.ams.academic.infrastructure.phenikaa;

import java.util.*;
import org.junit.jupiter.api.Test;
import vn.edu.phenikaa.ams.academic.application.port.*;
import static org.assertj.core.api.Assertions.*;
import static vn.edu.phenikaa.ams.academic.infrastructure.phenikaa.CurriculumSourceFixtures.*;

class PhenikaaCurriculumMapperTest {
    private final CurriculumOption option = PhenikaaCurriculumMapper.options(rows(OPTIONS), "synthetic-learner").getFirst();
    private CurriculumObservation map(String catalog, String required, String elective, String requiredMembers, String electiveMembers) {
        return PhenikaaCurriculumMapper.normalize(option, rows(catalog), List.of(
                new PhenikaaCurriculumMapper.GroupData(rows(required).getFirst(), false, rows(requiredMembers)),
                new PhenikaaCurriculumMapper.GroupData(rows(elective).getFirst(), true, rows(electiveMembers))));
    }
    private String catalog() { return "[" + course(1) + "," + course(2) + "," + course(3) + "]"; }

    @Test void keepsSourceOrganizationDistinctFromAssociationAndAllowsMultipleVersionsWithoutGuessingRevision() {
        assertThat(option.sourceId()).isEqualTo("synthetic-curriculum");
        var second = OPTIONS.replace("synthetic-curriculum", "synthetic-curriculum-v2");
        var combined = rows(OPTIONS); combined.addAll(rows(second));
        assertThat(PhenikaaCurriculumMapper.options(combined, "synthetic-learner")).hasSize(2);
        assertThat(option.toString()).doesNotContain("TEST-CT");
        assertThatThrownBy(() -> PhenikaaCurriculumMapper.options(rows(OPTIONS), "other-learner")).hasMessage("UNEXPECTED_SCHEMA");
        combined.addAll(rows(OPTIONS));
        assertThatThrownBy(() -> PhenikaaCurriculumMapper.options(combined, "synthetic-learner")).hasMessage("UNEXPECTED_SCHEMA");
    }
    @Test void mapsVerifiedGroupsAndLeavesUnassignedCourseWithoutInventingRequirement() {
        var value = map(catalog(), group(false), group(true), member(false), member(true));
        assertThat(value.courses()).hasSize(3);
        assertThat(value.groups()).hasSize(2);
        assertThat(value.groups().getFirst().requirement()).isEqualTo(CurriculumObservation.Requirement.REQUIRED);
        assertThat(value.groups().getFirst().minimumCredits()).isNull();
        assertThat(value.groups().getLast().minimumCourseCount()).isEqualTo(1);
        assertThat(value.groups().stream().flatMap(g -> g.courseSourceIds().stream())).doesNotContain("synthetic-course-3");
        assertThat(value.completeness()).isEqualTo(CurriculumObservation.Completeness.UNKNOWN);
        assertThatThrownBy(() -> value.courses().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> value.groups().getFirst().courseSourceIds().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(value.toString()).isEqualTo("CurriculumObservation[redacted]");
    }
    @Test void optionalCohortAndRelationshipSummaryMayBeUnknown() {
        var input = OPTIONS.replace("\"TEST-COHORT\"", "null");
        assertThat(PhenikaaCurriculumMapper.options(rows(input), "synthetic-learner").getFirst().cohort()).isNull();
        assertThat(PhenikaaCurriculumMapper.courses(option, rows(catalog()))).allMatch(c -> !c.relationshipDetailsAvailable());
    }
    @Test void rejectsDuplicateIdsCodesAndCrossProgramRows() {
        for (String input : List.of(catalog().replace("synthetic-course-2", "synthetic-course-1"),
                catalog().replace("TEST2", "TEST1"), catalog().replace("synthetic-row-2", "synthetic-row-1"),
                catalog().replace("synthetic-curriculum", "other-curriculum"), catalog().replace("3.0", "3.001")))
            assertThatThrownBy(() -> PhenikaaCurriculumMapper.courses(option, rows(input))).hasMessage("UNEXPECTED_SCHEMA");
    }
    @Test void rejectsUnknownGroupHierarchyAndMandatoryOverrideAndMissingCourse() {
        assertThatThrownBy(() -> map(catalog(), group(false), group(true).replace("\"KYHIEU\"", "\"DAOTAO_KHOITUCHON_DON_CHA_ID\":\"synthetic-parent\",\"KYHIEU\""), member(false), member(true)))
                .hasMessage("UNEXPECTED_SCHEMA");
        assertThatThrownBy(() -> map(catalog(), group(false), group(true), member(false), member(true).replace("BATBUOC\":null", "BATBUOC\":1")))
                .hasMessage("UNEXPECTED_SCHEMA");
        assertThatThrownBy(() -> map("[" + course(1) + "]", group(false), group(true), member(false), member(true))).hasMessage("UNEXPECTED_SCHEMA");
    }
    @Test void rejectsMultipleMembershipsAndIncorrectCreditsOrTotals() {
        for (String input : List.of(member(true).replace("synthetic-course-2", "synthetic-course-1").replace("TEST2", "TEST1"),
                member(true).replace("3.0", "4.0"), member(true).replace("synthetic-group-E", "other-group")))
            assertThatThrownBy(() -> map(catalog(), group(false), group(true), member(false), input)).hasMessage("UNEXPECTED_SCHEMA");
        assertThatThrownBy(() -> map(catalog(), group(false), group(true).replace("\"TONGSOHP\":1.0", "\"TONGSOHP\":2.0"), member(false), member(true)))
                .hasMessage("UNEXPECTED_SCHEMA");
    }
    @Test void descriptiveRelationsPreserveThresholdAndDoNotInventAndOrOrCorequisiteRules() {
        var value = PhenikaaCurriculumMapper.relations(option, "synthetic-course-2", rows(RELATIONS));
        assertThat(value.conditions().getFirst().threshold()).isEqualTo("12");
        assertThat(value.conditions().getFirst().operatorLabel()).isEqualTo(">=");
        assertThat(value.conditions().getFirst().toString()).doesNotContain("12");
        assertThatThrownBy(() -> value.conditions().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
    @Test void rejectsSelfRelationWrongOwnerAndDuplicateRelation() {
        for (String data : List.of(RELATIONS.replace("synthetic-course-1", "synthetic-course-2"), RELATIONS.replace("synthetic-curriculum", "other")))
            assertThatThrownBy(() -> PhenikaaCurriculumMapper.relations(option, "synthetic-course-2", rows(data))).hasMessage("UNEXPECTED_SCHEMA");
        var duplicate = rows(RELATIONS); duplicate.addAll(rows(RELATIONS));
        assertThatThrownBy(() -> PhenikaaCurriculumMapper.relations(option, "synthetic-course-2", duplicate)).hasMessage("UNEXPECTED_SCHEMA");
    }
}
