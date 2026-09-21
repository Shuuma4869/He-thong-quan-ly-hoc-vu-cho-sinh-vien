package vn.edu.phenikaa.ams.academic.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.phenikaa.ams.academic.domain.StudentProfile;

public interface StudentProfileRepository extends JpaRepository<StudentProfile, UUID> {
    Optional<StudentProfile> findByUserId(UUID userId);
}
