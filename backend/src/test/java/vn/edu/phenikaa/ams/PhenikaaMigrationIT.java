package vn.edu.phenikaa.ams;

import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.assertThat;

class PhenikaaMigrationIT {
    @Test void upgradesPhaseTwoSchemaWithoutChangingExistingProfile() throws Exception {
        try (var postgres = new PostgreSQLContainer("postgres:17-alpine")) {
            postgres.start();
            var config = Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            config.target("5").load().migrate();
            UUID user = UUID.randomUUID();
            UUID profile = UUID.randomUUID();
            try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
                try (var statement = connection.prepareStatement("insert into app_user(id,email,password_hash,role,account_status) values (?, 'phase4b@example.test', '!synthetic-unusable-hash','STUDENT','ACTIVE')")) {
                    statement.setObject(1, user); statement.executeUpdate();
                }
                try (var statement = connection.prepareStatement("insert into student_profile(id,user_id,student_number) values (?,?,'SYNTHETIC-001')")) {
                    statement.setObject(1, profile); statement.setObject(2, user); statement.executeUpdate();
                }
                var flyway = config.target("6").load();
                assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
                flyway.validate();
                assertThat(flyway.migrate().migrationsExecuted).isZero();
                try (var statement = connection.createStatement(); var rows = statement.executeQuery("select id,user_id,student_number from student_profile")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getObject(1, UUID.class)).isEqualTo(profile);
                    assertThat(rows.getObject(2, UUID.class)).isEqualTo(user);
                    assertThat(rows.getString(3)).isEqualTo("SYNTHETIC-001");
                }
                try (var statement = connection.createStatement(); var rows = statement.executeQuery("select count(*) from phenikaa_connection")) {
                    rows.next(); assertThat(rows.getInt(1)).isZero();
                }
            }
        }
    }
}
