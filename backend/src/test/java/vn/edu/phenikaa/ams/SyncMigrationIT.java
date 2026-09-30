package vn.edu.phenikaa.ams;

import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;

class SyncMigrationIT {
    @Test void upgradesV7ToV8WithoutChangingExistingAccountOrCurriculum() throws Exception {
        try (var postgres = new PostgreSQLContainer("postgres:17-alpine")) {
            postgres.start();
            var config = Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            config.target("7").load().migrate();
            try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                 var sql = connection.createStatement()) {
                sql.execute("insert into app_user(id,email,password_hash,role,account_status) values "
                        + "('00000000-0000-0000-0000-000000000001','sync-migration@example.test','!synthetic','STUDENT','ACTIVE')");
                sql.execute("insert into student_profile(id,user_id) values "
                        + "('00000000-0000-0000-0000-000000000002','00000000-0000-0000-0000-000000000001')");
                var upgrade = config.target("8").load();
                assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);
                upgrade.validate();
                assertThat(upgrade.migrate().migrationsExecuted).isZero();
                try (var rows = sql.executeQuery("select count(*) from student_profile")) {
                    rows.next(); assertThat(rows.getInt(1)).isEqualTo(1);
                }
                try (var rows = sql.executeQuery("select count(*) from sync_run")) {
                    rows.next(); assertThat(rows.getInt(1)).isZero();
                }
            }
        }
    }
}
