package vn.edu.phenikaa.ams.academic.infrastructure.phenikaa;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface PhenikaaConnectionRepository extends JpaRepository<PhenikaaConnection, UUID> {
    Optional<PhenikaaConnection> findByUserId(UUID userId);
    Optional<PhenikaaConnection> findByIdAndUserId(UUID id, UUID userId);
}
