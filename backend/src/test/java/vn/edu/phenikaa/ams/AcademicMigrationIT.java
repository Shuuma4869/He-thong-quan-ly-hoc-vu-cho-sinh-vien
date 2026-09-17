package vn.edu.phenikaa.ams;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

class AcademicMigrationIT {
    @Test
    void upgradesPhaseOneWithoutChangingAccountsOrCreatingAcademicSeedData() throws Exception {
        try (var postgres = new PostgreSQLContainer("postgres:17-alpine")) {
            postgres.start();
            var config = Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            config.target("2").load().migrate();
            UUID userId = UUID.randomUUID();
            try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
                try (var insert = connection.prepareStatement("insert into app_user (id, email, password_hash, role, account_status) values (?, 'migration@example.test', '!test-only-unusable-hash', 'STUDENT', 'ACTIVE')")) {
                    insert.setObject(1, userId);
                    insert.executeUpdate();
                }
                try (var insert = connection.prepareStatement("insert into user_preferences (user_id, timezone) values (?, 'Asia/Tokyo')")) {
                    insert.setObject(1, userId);
                    insert.executeUpdate();
                }
                var flyway = config.target("5").load();
                assertThat(flyway.migrate().migrationsExecuted).isEqualTo(3);
                flyway.validate();
                assertThat(flyway.migrate().migrationsExecuted).isZero();
                try (var statement = connection.createStatement(); var rows = statement.executeQuery("select u.id, u.account_status, p.timezone from app_user u join user_preferences p on p.user_id = u.id")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getObject("id", UUID.class)).isEqualTo(userId);
                    assertThat(rows.getString("account_status")).isEqualTo("ACTIVE");
                    assertThat(rows.getString("timezone")).isEqualTo("Asia/Tokyo");
                }
                try (var statement = connection.createStatement(); var rows = statement.executeQuery("select count(*) from student_profile")) {
                    rows.next();
                    assertThat(rows.getInt(1)).isZero();
                }
            }
        }
    }
}
