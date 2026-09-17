package vn.edu.phenikaa.ams.user.infrastructure;

import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.edu.phenikaa.ams.user.domain.AppUser;

public interface UserRepository extends JpaRepository<AppUser, UUID> {
    Optional<AppUser> findByEmail(String email);
    @Modifying
    @Query(value = """
            INSERT INTO app_user (id, email, password_hash, display_name, role, account_status, created_at, updated_at)
            VALUES (:id, :email, :hash, :name, 'STUDENT', 'ACTIVE', :created, :created)
            ON CONFLICT (email) DO NOTHING
            """, nativeQuery = true)
    int insertAccount(@Param("id") UUID id, @Param("email") String email, @Param("hash") String hash,
                      @Param("name") String name, @Param("created") Instant created);
}
