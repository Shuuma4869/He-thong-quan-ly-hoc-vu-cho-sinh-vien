package vn.edu.phenikaa.ams.user.infrastructure;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.phenikaa.ams.user.domain.UserPreferences;

public interface UserPreferencesRepository extends JpaRepository<UserPreferences, UUID> {}
