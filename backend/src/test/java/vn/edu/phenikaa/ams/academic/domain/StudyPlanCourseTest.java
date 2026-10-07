package vn.edu.phenikaa.ams.academic.domain;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class StudyPlanCourseTest {
    private final Instant first = Instant.parse("2026-01-01T00:00:00Z");

    private StudyPlanCourse course(int term) {
        return new StudyPlanCourse(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), term, first);
    }

    @Test void acceptsBoundaryTermsAndMovesWithoutChangingIdentity() {
        var planned = course(1);
        var id = planned.id();
        planned.moveToTerm(99, first.plusSeconds(1));
        assertThat(planned.id()).isEqualTo(id);
        assertThat(planned.plannedTerm()).isEqualTo(99);
        assertThat(planned.updatedAt()).isAfter(planned.createdAt());
        planned.moveToTerm(99, first.plusSeconds(2));
        assertThat(planned.updatedAt()).isEqualTo(first.plusSeconds(1));
        assertThat(course(99).plannedTerm()).isEqualTo(99);
    }

    @Test void rejectsInvalidTermsAndKeepsExistingState() {
        assertThatThrownBy(() -> course(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> course(100)).isInstanceOf(IllegalArgumentException.class);
        var planned = course(1);
        assertThatThrownBy(() -> planned.moveToTerm(0, first.plusSeconds(1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> planned.moveToTerm(100, first.plusSeconds(1))).isInstanceOf(IllegalArgumentException.class);
        assertThat(planned.plannedTerm()).isEqualTo(1);
    }
}
