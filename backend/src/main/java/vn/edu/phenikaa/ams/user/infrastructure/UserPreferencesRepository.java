package vn.edu.phenikaa.ams.user.infrastructure;

import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import vn.edu.phenikaa.ams.user.domain.UserPreferences;

public interface UserPreferencesRepository extends JpaRepository<UserPreferences, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from UserPreferences p where p.userId = :userId")
    java.util.Optional<UserPreferences> findLocked(UUID userId);
}
