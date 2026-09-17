package vn.edu.phenikaa.ams;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.data.redis.password=")
class AmsApplicationIT {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private StringRedisTemplate redis;

    @Test
    void migrationCreatesBaselineSchema() {
        assertThat(jdbc.queryForObject(
                "select count(*) from flyway_schema_history where version in ('1', '2', '3', '4', '5') and success = true",
                Integer.class)).isEqualTo(5);
        assertThat(jdbc.queryForObject("select count(*) from app_user", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from user_preferences", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from security_audit_event", Integer.class)).isZero();
    }

    @Test
    void redisAcceptsConnections() {
        try (var connection = redis.getConnectionFactory().getConnection()) {
            assertThat(connection.ping()).isEqualTo("PONG");
        }
    }

}
