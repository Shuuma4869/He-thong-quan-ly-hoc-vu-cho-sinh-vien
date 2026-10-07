package vn.edu.phenikaa.ams.academic.application;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.edu.phenikaa.ams.academic.domain.CurriculumCourse.Requirement;
import vn.edu.phenikaa.ams.academic.infrastructure.StudyPlanRepository;
import vn.edu.phenikaa.ams.user.domain.AppUser;
import vn.edu.phenikaa.ams.user.infrastructure.UserRepository;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class StudyPlanServiceTest {
    @Test void rejectsOversizedPlanInsteadOfTruncating() {
        var users = mock(UserRepository.class);
        var plans = mock(StudyPlanRepository.class);
        var account = new AppUser(UUID.randomUUID() + "@example.test", "!synthetic", null);
        UUID userId = account.getId(), profile = UUID.randomUUID(), curriculum = UUID.randomUUID();
        when(users.findById(userId)).thenReturn(Optional.of(account));
        when(plans.selected(userId)).thenReturn(Optional.of(new StudyPlanRepository.Selected(profile, curriculum, "CURR-A", "Chương trình kiểm thử")));
        var row = new StudyPlanRepository.PlannedCourse(UUID.randomUUID(), "TEST101", "Môn kiểm thử",
                new BigDecimal("3.00"), Requirement.REQUIRED, null, 1);
        when(plans.courses(userId, curriculum, 1001)).thenReturn(Collections.nCopies(1001, row));
        assertThatThrownBy(() -> new StudyPlanService(users, plans).current(userId))
                .isInstanceOf(StudyPlanException.class).hasMessage("STUDY_PLAN_TOO_LARGE");
    }
}
