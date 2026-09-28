package vn.edu.phenikaa.ams;

import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;

class CurriculumMigrationIT {
    @Test void upgradesV6AndPreservesExistingRequiredAndElectiveRows() throws Exception {
        try (var postgres = new PostgreSQLContainer("postgres:17-alpine")) {
            postgres.start();
            var config = Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            config.target("6").load().migrate();
            try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()); var sql = connection.createStatement()) {
                sql.execute("insert into app_user(id,email,password_hash,role,account_status) values ('00000000-0000-0000-0000-000000000001','curriculum-migration@example.test','!synthetic','STUDENT','ACTIVE')");
                sql.execute("insert into student_profile(id,user_id) values ('00000000-0000-0000-0000-000000000002','00000000-0000-0000-0000-000000000001')");
                sql.execute("insert into curriculum(id,profile_id,code,revision,name,minimum_credits) values ('00000000-0000-0000-0000-000000000003','00000000-0000-0000-0000-000000000002','TEST','v1','Chương trình giả định',6)");
                sql.execute("insert into curriculum_group(id,profile_id,curriculum_id,code,name,minimum_credits) values ('00000000-0000-0000-0000-000000000004','00000000-0000-0000-0000-000000000002','00000000-0000-0000-0000-000000000003','E','Nhóm giả định',3)");
                for (int i = 5; i <= 6; i++) sql.execute("insert into course(id,profile_id,code,name,credits) values ('00000000-0000-0000-0000-00000000000" + i + "','00000000-0000-0000-0000-000000000002','TEST" + i + "','Môn giả định',3)");
                sql.execute("insert into curriculum_course(id,profile_id,curriculum_id,course_id,requirement,group_id,credits) values ('00000000-0000-0000-0000-000000000007','00000000-0000-0000-0000-000000000002','00000000-0000-0000-0000-000000000003','00000000-0000-0000-0000-000000000005','ELECTIVE','00000000-0000-0000-0000-000000000004',3)");
                sql.execute("insert into curriculum_course(id,profile_id,curriculum_id,course_id,requirement,credits) values ('00000000-0000-0000-0000-000000000008','00000000-0000-0000-0000-000000000002','00000000-0000-0000-0000-000000000003','00000000-0000-0000-0000-000000000006','REQUIRED',3)");
                var flyway = config.target("7").load(); assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1); flyway.validate();
                assertThat(flyway.migrate().migrationsExecuted).isZero();
                try (var result = sql.executeQuery("select requirement,minimum_credits,minimum_course_count from curriculum_group")) {
                    assertThat(result.next()).isTrue(); assertThat(result.getString(1)).isEqualTo("ELECTIVE"); assertThat(result.getInt(2)).isEqualTo(3); assertThat(result.getObject(3)).isNull();
                }
                try (var result = sql.executeQuery("select count(*) from curriculum_course")) { result.next(); assertThat(result.getInt(1)).isEqualTo(2); }
                try (var result = sql.executeQuery("select count(*) from phenikaa_course_mapping")) { result.next(); assertThat(result.getInt(1)).isZero(); }
                assertThatThrownBy(() -> sql.execute("update curriculum_course set requirement = 'REQUIRED' where requirement = 'ELECTIVE'")).isInstanceOf(java.sql.SQLException.class);
            }
        }
    }
}
