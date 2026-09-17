package vn.edu.phenikaa.ams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

class AccountMigrationIT {
    @Test
    void upgradesLegacyAccountsWithoutInventingCredentialsAndRollsBackEmailCollisions() throws Exception {
        try (var postgres = new PostgreSQLContainer("postgres:17-alpine")) {
            postgres.start();
            var config = Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            config.target("1").load().migrate();
            UUID userId = UUID.randomUUID();
            UUID duplicateId = UUID.randomUUID();
            try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
                try (var insert = connection.prepareStatement("insert into app_user (id, email, role) values (?, ?, 'STUDENT')")) {
                    insert.setObject(1, userId);
                    insert.setString(2, "Legacy@Example.test");
                    insert.executeUpdate();
                    insert.setObject(1, duplicateId);
                    insert.setString(2, "legacy@example.test");
                    insert.executeUpdate();
                }
                var upgrade = config.target("2").load();
                assertThatThrownBy(upgrade::migrate).isInstanceOf(FlywayException.class);
                try (var statement = connection.createStatement(); var rows = statement.executeQuery("select count(*) from app_user")) {
                    rows.next();
                    assertThat(rows.getInt(1)).isEqualTo(2);
                }
                try (var delete = connection.prepareStatement("delete from app_user where id = ?")) {
                    delete.setObject(1, duplicateId);
                    delete.executeUpdate();
                }
                upgrade.migrate();
                try (var statement = connection.createStatement(); var rows = statement.executeQuery(
                        "select u.id, u.email, u.password_hash, u.account_status, p.timezone, p.locale, p.theme "
                                + "from app_user u join user_preferences p on p.user_id = u.id")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getObject("id", UUID.class)).isEqualTo(userId);
                    assertThat(rows.getString("email")).isEqualTo("legacy@example.test");
                    assertThat(rows.getString("password_hash")).isEqualTo("!");
                    assertThat(rows.getString("account_status")).isEqualTo("DISABLED");
                    assertThat(rows.getString("timezone")).isEqualTo("Asia/Ho_Chi_Minh");
                    assertThat(rows.getString("locale")).isEqualTo("vi-VN");
                    assertThat(rows.getString("theme")).isEqualTo("SYSTEM");
                    assertThat(rows.next()).isFalse();
                }
            }
        }
    }
}
