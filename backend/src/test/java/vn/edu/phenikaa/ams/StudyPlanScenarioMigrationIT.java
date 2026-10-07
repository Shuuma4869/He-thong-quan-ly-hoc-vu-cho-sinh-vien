package vn.edu.phenikaa.ams;

import java.sql.DriverManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;

class StudyPlanScenarioMigrationIT {
    @Test void preservesV12AssignmentsAsScenarioOne() throws Exception {
        try (var postgres = new PostgreSQLContainer("postgres:17-alpine")) {
            postgres.start();
            var config = Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            config.target("12").load().migrate();
            UUID user = UUID.randomUUID(), profile = UUID.randomUUID(), curriculum = UUID.randomUUID();
            UUID course = UUID.randomUUID(), assignment = UUID.randomUUID();
            Instant created = Instant.parse("2026-01-01T00:00:00Z");
            Instant updated = Instant.parse("2026-01-02T00:00:00Z");
            try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
                try (var sql = connection.prepareStatement("insert into app_user(id,email,password_hash,role,account_status) values (?,?,'!synthetic','STUDENT','ACTIVE')")) {
                    sql.setObject(1, user); sql.setString(2, user + "@example.test"); sql.executeUpdate();
                }
                try (var sql = connection.prepareStatement("insert into student_profile(id,user_id) values (?,?)")) {
                    sql.setObject(1, profile); sql.setObject(2, user); sql.executeUpdate();
                }
                try (var sql = connection.prepareStatement("insert into curriculum(id,profile_id,code,name,minimum_credits) values (?,?,'CURR-A','Chương trình kiểm thử',3)")) {
                    sql.setObject(1, curriculum); sql.setObject(2, profile); sql.executeUpdate();
                }
                try (var sql = connection.prepareStatement("insert into course(id,profile_id,code,name,credits) values (?,?,'TEST101','Môn kiểm thử',3)")) {
                    sql.setObject(1, course); sql.setObject(2, profile); sql.executeUpdate();
                }
                try (var sql = connection.prepareStatement("""
                        insert into curriculum_course(id,profile_id,curriculum_id,course_id,requirement,credits)
                        values (?,?,?,?,'REQUIRED',3)
                        """)) {
                    sql.setObject(1, UUID.randomUUID()); sql.setObject(2, profile);
                    sql.setObject(3, curriculum); sql.setObject(4, course); sql.executeUpdate();
                }
                try (var sql = connection.prepareStatement("""
                        insert into study_plan_course(id,profile_id,curriculum_id,course_id,planned_term,created_at,updated_at)
                        values (?,?,?,?,3,?,?)
                        """)) {
                    sql.setObject(1, assignment); sql.setObject(2, profile); sql.setObject(3, curriculum);
                    sql.setObject(4, course); sql.setTimestamp(5, Timestamp.from(created));
                    sql.setTimestamp(6, Timestamp.from(updated)); sql.executeUpdate();
                }
                var upgraded = config.target("13").load();
                assertThat(upgraded.migrate().migrationsExecuted).isEqualTo(1);
                upgraded.validate();
                try (var sql = connection.createStatement();
                     var rows = sql.executeQuery("select id,profile_id,curriculum_id,course_id,scenario_no,planned_term,created_at,updated_at from study_plan_course")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getObject("id", UUID.class)).isEqualTo(assignment);
                    assertThat(rows.getObject("profile_id", UUID.class)).isEqualTo(profile);
                    assertThat(rows.getObject("curriculum_id", UUID.class)).isEqualTo(curriculum);
                    assertThat(rows.getObject("course_id", UUID.class)).isEqualTo(course);
                    assertThat(rows.getInt("scenario_no")).isEqualTo(1);
                    assertThat(rows.getInt("planned_term")).isEqualTo(3);
                    assertThat(rows.getTimestamp("created_at").toInstant()).isEqualTo(created);
                    assertThat(rows.getTimestamp("updated_at").toInstant()).isEqualTo(updated);
                    assertThat(rows.next()).isFalse();
                }
                try (var sql = connection.createStatement();
                     var row = sql.executeQuery("""
                             select column_default, is_nullable from information_schema.columns
                             where table_name = 'study_plan_course' and column_name = 'scenario_no'
                             """)) {
                    assertThat(row.next()).isTrue();
                    assertThat(row.getString("column_default")).isNull();
                    assertThat(row.getString("is_nullable")).isEqualTo("NO");
                }
            }
        }
    }
}
