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
        UUID first = null, second = null;
        for (String code : new String[] {"CURR-A", "CURR-B"}) {
            UUID curriculum = UUID.randomUUID();
            jdbc.update("insert into curriculum(id,profile_id,code,name,minimum_credits) values (?,?,?,?,?)",
                    curriculum, profile, code, "Chương trình kiểm thử " + code.substring(5), 132);
            if (first == null) first = curriculum; else second = curriculum;
        }
        String[] codes = {"TEST101", "TEST102", "TEST103", "TEST201"};
        String[] names = {"Môn kiểm thử A", "Môn kiểm thử B", "Môn kiểm thử C", "Môn kiểm thử D"};
        int[] credits = {3, 4, 2, 3};
        for (int i = 0; i < codes.length; i++) {
            UUID course = UUID.randomUUID();
            jdbc.update("insert into course(id,profile_id,code,name,credits) values (?,?,?,?,?)",
                    course, profile, codes[i], names[i], credits[i]);
            jdbc.update("""
                    insert into curriculum_course(id,profile_id,curriculum_id,course_id,requirement,credits)
                    values (?,?,?,?, 'REQUIRED', ?)
                    """, UUID.randomUUID(), profile, i == 3 ? second : first, course, credits[i]);
        }
    }
}
