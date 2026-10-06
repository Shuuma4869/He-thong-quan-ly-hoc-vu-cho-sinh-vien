package vn.edu.phenikaa.ams;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import vn.edu.phenikaa.ams.academic.application.port.*;
import vn.edu.phenikaa.ams.academic.infrastructure.phenikaa.*;
import vn.edu.phenikaa.ams.auth.application.AccountPrincipal;
import vn.edu.phenikaa.ams.user.domain.AppUser;
import vn.edu.phenikaa.ams.user.infrastructure.UserRepository;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {"spring.data.redis.password=", "ams.phenikaa.enabled=true"})
@AutoConfigureMockMvc
class AcademicProgressSummaryIT {
    private static final String PATH = "/api/me/academic/source/progress-summary";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired PhenikaaAcademicPortalClient portal;
    @MockitoBean PhenikaaHttpClient http;

    @DynamicPropertySource static void key(DynamicPropertyRegistry registry) {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        String encoded = Base64.getEncoder().encodeToString(bytes);
        java.util.Arrays.fill(bytes, (byte) 0);
        registry.add("ams.phenikaa.session-key", () -> encoded);
    }

    private AppUser account(String learner) {
        var account = users.save(new AppUser(UUID.randomUUID() + "@example.test", "!synthetic", null));
        when(http.fetchProfile(any())).thenReturn(new ProfileObservation("SYNTHETIC", "Giả định"));
        try (var material = new PhenikaaSessionMaterial("Bearer synthetic-secret", "", "synthetic-key",
                learner, "synthetic-profile-function", "synthetic-schedule-function", null, "synthetic-academic-function")) {
            portal.connect(account.getId(), material);
        }
        return account;
    }

    private UUID select(AppUser account, String sourceId) {
        UUID profile = UUID.randomUUID(), curriculum = UUID.randomUUID();
        jdbc.update("insert into student_profile(id,user_id) values (?,?)", profile, account.getId());
        jdbc.update("insert into curriculum(id,profile_id,code,name,minimum_credits) values (?,?,?,?,?)",
                curriculum, profile, "TEST", "Chương trình kiểm thử", 100);
        jdbc.update("update student_profile set curriculum_id = ? where id = ?", curriculum, profile);
        if (sourceId != null) jdbc.update("""
                insert into phenikaa_curriculum_mapping(id,profile_id,source_curriculum_id,curriculum_id,first_seen_at,last_seen_at)
                values (?,?,?,?,?,?)
                """, UUID.randomUUID(), profile, sourceId, curriculum,
                java.sql.Timestamp.from(Instant.now()), java.sql.Timestamp.from(Instant.now()));
        return curriculum;
    }

    @Test void requiresSelectionAndExactMappingWithoutCallingProgressSource() throws Exception {
        var account = account("synthetic-learner-a");
        mvc.perform(get(PATH).with(user(new AccountPrincipal(account))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CURRICULUM_SELECTION_REQUIRED"));
        var unselected = account("synthetic-learner-unselected");
        jdbc.update("insert into student_profile(id,user_id) values (?,?)", UUID.randomUUID(), unselected.getId());
        mvc.perform(get(PATH).with(user(new AccountPrincipal(unselected))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CURRICULUM_SELECTION_REQUIRED"));
        var withoutMapping = account("synthetic-learner-b");
        select(withoutMapping, null);
        mvc.perform(get(PATH).with(user(new AccountPrincipal(withoutMapping))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SOURCE_PROGRESS_UNAVAILABLE"));
        verify(http, never()).fetchAcademicPrograms(any());
        verify(http, never()).fetchAcademicProgressSummary(any(), any());
    }

    @Test void readsOnlyOwnExactProgramWithoutChangingAcademicTables() throws Exception {
        var a = account("synthetic-learner-a");
        var b = account("synthetic-learner-b");
        UUID selectedA = select(a, "synthetic-program-a");
        select(b, "synthetic-program-b");
        var programA = new AcademicProgram("synthetic-program-a", "Chương trình A");
        var programB = new AcademicProgram("synthetic-program-b", "Chương trình B");
        when(http.fetchAcademicPrograms(any())).thenReturn(List.of(programA));
        when(http.fetchAcademicProgressSummary(any(), any())).thenAnswer(invocation -> {
            AcademicProgram requested = invocation.getArgument(1);
            return new AcademicProgressSummaryObservation(requested, new BigDecimal("3.25"),
                    new BigDecimal("8.10"), new BigDecimal("72"));
        });
        String[] unchanged = {"student_course", "academic_result", "semester", "class_session", "exam",
                "schedule_change", "academic_snapshot", "notification_outbox"};
        long[] before = new long[unchanged.length];
        for (int i = 0; i < unchanged.length; i++)
            before[i] = jdbc.queryForObject("select count(*) from " + unchanged[i], Long.class);
        String body = mvc.perform(get(PATH).with(user(new AccountPrincipal(a))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("SOURCE_REPORTED_LIVE_READ_ONLY"))
                .andExpect(jsonPath("$.completeness").value("UNKNOWN"))
                .andExpect(jsonPath("$.selectionMode").value("USER_SELECTED_AMS"))
                .andExpect(jsonPath("$.curriculum.id").value(selectedA.toString()))
                .andExpect(jsonPath("$.summary.cumulativeAverageScale4").value(3.25))
                .andExpect(jsonPath("$.summary.sourceAccumulatedCredits").value(72))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("synthetic-program-a", "synthetic-program-b", "profileId", "sourceId",
                "sourceProgramId", "learnerId", "rsDiemTrungBinhChung");
        mvc.perform(get(PATH).with(user(new AccountPrincipal(b))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SOURCE_PROGRESS_UNAVAILABLE"));
        assertThat(jdbc.queryForObject("select curriculum_id from student_profile where user_id = ?", UUID.class, a.getId()))
                .isEqualTo(selectedA);
        for (int i = 0; i < unchanged.length; i++)
            assertThat(jdbc.queryForObject("select count(*) from " + unchanged[i], Long.class)).isEqualTo(before[i]);
        verify(http).fetchAcademicProgressSummary(any(), eq(programA));
        verify(http, never()).fetchAcademicProgressSummary(any(), eq(programB));
    }
}
