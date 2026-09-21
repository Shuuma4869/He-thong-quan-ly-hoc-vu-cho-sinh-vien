package vn.edu.phenikaa.ams;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.edu.phenikaa.ams.academic.domain.StudentProfile;
import static org.assertj.core.api.Assertions.*;

class ProfileImportTest {
    @Test void validatesAllFieldsBeforeUpdatingExistingProfile() {
        var profile = new StudentProfile(UUID.randomUUID(), "SYNTHETIC-001", "Trường giả định", "Ngành giả định", "Khóa mẫu");
        assertThatThrownBy(() -> profile.updateSourceProfile("SYNTHETIC-CHANGED", "x".repeat(201)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(profile.getStudentNumber()).isEqualTo("SYNTHETIC-001");
        assertThat(profile.getProgramName()).isEqualTo("Ngành giả định");
    }
}
