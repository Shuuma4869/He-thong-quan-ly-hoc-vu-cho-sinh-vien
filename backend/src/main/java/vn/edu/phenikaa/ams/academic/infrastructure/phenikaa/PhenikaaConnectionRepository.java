package vn.edu.phenikaa.ams.academic.infrastructure.phenikaa;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PhenikaaConnectionRepository extends JpaRepository<PhenikaaConnection, UUID> {
    Optional<PhenikaaConnection> findByUserId(UUID userId);
    Optional<PhenikaaConnection> findByIdAndUserId(UUID id, UUID userId);
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from PhenikaaConnection c where c.id = :id and c.userId = :userId")
    Optional<PhenikaaConnection> lockByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);
}
