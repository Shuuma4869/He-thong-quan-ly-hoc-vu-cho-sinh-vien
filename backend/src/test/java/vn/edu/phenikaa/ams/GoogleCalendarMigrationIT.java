package vn.edu.phenikaa.ams;

import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;

class GoogleCalendarMigrationIT {
    @Test void upgradesV9ToV10WithoutChangingExistingRuns() throws Exception {
        try (var postgres = new PostgreSQLContainer("postgres:17-alpine")) {
            postgres.start();
            var config = Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            config.target("9").load().migrate();
            try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                 var sql = connection.createStatement()) {
                sql.execute("insert into app_user(id,email,password_hash,role,account_status) values "
                        + "('00000000-0000-0000-0000-000000000021','google-v10@example.test','!synthetic','STUDENT','ACTIVE')");
                sql.execute("insert into sync_run(id,user_id,trigger_type,status,requested_at,next_attempt_at,updated_at) values "
                        + "('00000000-0000-0000-0000-000000000022','00000000-0000-0000-0000-000000000021',"
                        + "'MANUAL','QUEUED',now(),now(),now())");
                var upgrade = config.target("10").load();
                assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);
                upgrade.validate();
                assertThat(upgrade.migrate().migrationsExecuted).isZero();
                try (var rows = sql.executeQuery("select count(*) from sync_run")) {
                    rows.next(); assertThat(rows.getInt(1)).isEqualTo(1);
                }
                try (var rows = sql.executeQuery("select count(*) from google_calendar_connection")) {
                    rows.next(); assertThat(rows.getInt(1)).isZero();
                }
                try (var rows = sql.executeQuery("select count(*) from information_schema.columns "
                        + "where table_name='google_calendar_connection' and column_name='encrypted_tokens'")) {
                    rows.next(); assertThat(rows.getInt(1)).isEqualTo(1);
                }
                sql.execute("insert into google_calendar_connection(id,user_id,status,encryption_key_version,created_at,updated_at) "
                        + "values ('00000000-0000-0000-0000-000000000023',"
                        + "'00000000-0000-0000-0000-000000000021','DISCONNECTED',1,now(),now())");
                assertThatThrownBy(() -> sql.execute("insert into google_calendar_connection"
                        + "(id,user_id,status,encryption_key_version,created_at,updated_at) values "
                        + "('00000000-0000-0000-0000-000000000024',"
                        + "'00000000-0000-0000-0000-000000000021','DISCONNECTED',1,now(),now())"))
                        .hasMessageContaining("duplicate key");
                assertThatThrownBy(() -> sql.execute("insert into google_calendar_connection"
                        + "(id,user_id,status,encryption_key_version,created_at,updated_at) values "
                        + "('00000000-0000-0000-0000-000000000025',"
                        + "'00000000-0000-0000-0000-000000000099','DISCONNECTED',1,now(),now())"))
                        .hasMessageContaining("foreign key");
                assertThatThrownBy(() -> sql.execute("update google_calendar_connection set status='CONNECTED' "
                        + "where id='00000000-0000-0000-0000-000000000023'"))
                        .hasMessageContaining("check constraint");
            }
        }
    }
}
