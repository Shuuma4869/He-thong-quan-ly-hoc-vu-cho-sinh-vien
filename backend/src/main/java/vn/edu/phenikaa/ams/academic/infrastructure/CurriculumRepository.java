package vn.edu.phenikaa.ams.academic.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.phenikaa.ams.academic.domain.Curriculum;

public interface CurriculumRepository extends JpaRepository<Curriculum, UUID> {
    Optional<Curriculum> findByIdAndProfileId(UUID id, UUID profileId);
}
