package vn.edu.phenikaa.ams;

import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.phenikaa.ams.auth.application.AccountPrincipal;

@RestController
@ConditionalOnProperty(name = "ams.e2e-fixtures.enabled", havingValue = "true")
public class E2eCurriculumFixtures {
    private final JdbcTemplate jdbc;
    E2eCurriculumFixtures(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @PostMapping("/api/me/test-fixtures/curricula")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void create(@AuthenticationPrincipal AccountPrincipal principal) {
        UUID profile = UUID.randomUUID();
        jdbc.update("insert into student_profile(id,user_id) values (?,?)", profile, principal.getUserId());
        for (String code : new String[] {"CURR-A", "CURR-B"}) {
            jdbc.update("insert into curriculum(id,profile_id,code,name,minimum_credits) values (?,?,?,?,?)",
                    UUID.randomUUID(), profile, code, "Chương trình kiểm thử " + code.substring(5), 132);
        }
        jdbc.update("insert into course(id,profile_id,code,name,credits) values (?,?,?,?,?)",
                UUID.randomUUID(), profile, "TEST101", "Môn kiểm thử", 3);
    }
}
