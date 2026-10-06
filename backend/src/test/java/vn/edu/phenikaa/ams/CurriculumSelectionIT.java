package vn.edu.phenikaa.ams;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import vn.edu.phenikaa.ams.academic.application.port.AcademicPortalClient;
import vn.edu.phenikaa.ams.auth.application.AccountPrincipal;
import vn.edu.phenikaa.ams.user.domain.AppUser;
import vn.edu.phenikaa.ams.user.infrastructure.UserRepository;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {"spring.data.redis.password=", "ams.sync.worker-enabled=false", "ams.phenikaa.enabled=false"})
@AutoConfigureMockMvc
class CurriculumSelectionIT {
    private static final String PATH = "/api/me/academic/curriculum-selection";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @MockitoBean AcademicPortalClient portal;
    private final JsonMapper json = JsonMapper.builder().build();

    private AppUser account() { return users.save(new AppUser(UUID.randomUUID() + "@example.test", "!synthetic", null)); }
    private UUID profile(AppUser user) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into student_profile(id,user_id) values (?,?)", id, user.getId());
        return id;
    }
    private UUID curriculum(UUID profile, String code) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into curriculum(id,profile_id,code,name,minimum_credits) values (?,?,?,?,?)",
                id, profile, code, "Chương trình kiểm thử " + code, 132);
        return id;
    }
    private JsonNode readSelection(AppUser user) throws Exception {
        String body = mvc.perform(get(PATH).with(user(new AccountPrincipal(user))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("profileId", "source", "phenikaa");
        return json.readTree(body);
    }
    private JsonNode select(AppUser user, UUID id) throws Exception {
        String body = mvc.perform(put(PATH).with(user(new AccountPrincipal(user))).with(csrf())
                        .contentType("application/json").content("{\"curriculumId\":\"" + id + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    @Test void emptyProfileAndUnselectedProfileStayNullWithoutAutoSelection() throws Exception {
        AppUser user = account();
        assertThat(readSelection(user).path("selectionMode").asText()).isEqualTo("USER_SELECTED_AMS");
        assertThat(readSelection(user).path("curriculum").isNull()).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from student_profile where user_id = ?", Integer.class, user.getId())).isZero();
        UUID profile = profile(user);
        curriculum(profile, "CURR-A");
        assertThat(readSelection(user).path("curriculum").isNull()).isTrue();
        mvc.perform(put(PATH).with(user(new AccountPrincipal(account()))).with(csrf())
                .contentType("application/json").content("{\"curriculumId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CURRICULUM_NOT_FOUND"));
        verifyNoInteractions(portal);
    }

    @Test void selectsOwnCurriculumPersistsAndClearsWithoutChangingCatalog() throws Exception {
        AppUser user = account(); UUID profile = profile(user);
        UUID first = curriculum(profile, "CURR-A"); UUID second = curriculum(profile, "CURR-B");
        UUID course = UUID.randomUUID(), group = UUID.randomUUID();
        jdbc.update("insert into course(id,profile_id,code,name,credits) values (?,?,?,?,?)",
                course, profile, "TEST101", "Môn kiểm thử", 3);
        jdbc.update("insert into curriculum_group(id,profile_id,curriculum_id,code,name,requirement,minimum_credits) values (?,?,?,?,?,'REQUIRED',?)",
                group, profile, first, "R", "Nhóm kiểm thử", 3);
        jdbc.update("insert into curriculum_course(id,profile_id,curriculum_id,course_id,requirement,credits) values (?,?,?,?, 'REQUIRED', ?)",
                UUID.randomUUID(), profile, first, course, 3);
        assertThat(select(user, first).at("/curriculum/id").asText()).isEqualTo(first.toString());
        assertThat(select(user, first).at("/curriculum/code").asText()).isEqualTo("CURR-A");
        assertThat(select(user, second).at("/curriculum/minimumCredits").asInt()).isEqualTo(132);
        assertThat(readSelection(user).at("/curriculum/id").asText()).isEqualTo(second.toString());
        assertThat(jdbc.queryForObject("select curriculum_id from student_profile where id = ?", UUID.class, profile)).isEqualTo(second);
        for (int i = 0; i < 2; i++) {
            mvc.perform(delete(PATH).with(user(new AccountPrincipal(user))).with(csrf()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.curriculum").value(org.hamcrest.Matchers.nullValue()));
        }
        assertThat(readSelection(user).path("curriculum").isNull()).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from curriculum where profile_id = ?", Integer.class, profile)).isEqualTo(2);
        for (String table : new String[] {"course", "curriculum_group", "curriculum_course"})
            assertThat(jdbc.queryForObject("select count(*) from " + table + " where profile_id = ?", Integer.class, profile)).isEqualTo(1);
        verifyNoInteractions(portal);
    }

    @Test void selectedCurriculumIsReturnedOutsideFirstListPage() throws Exception {
        AppUser user = account(); UUID profile = profile(user);
        curriculum(profile, "CURR-A");
        UUID selected = curriculum(profile, "CURR-B");
        select(user, selected);
        String firstPage = mvc.perform(get("/api/me/academic/curricula").param("limit", "1")
                        .with(user(new AccountPrincipal(user))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(firstPage).at("/items/0/id").asText()).isNotEqualTo(selected.toString());
        assertThat(readSelection(user).at("/curriculum/id").asText()).isEqualTo(selected.toString());
        verifyNoInteractions(portal);
    }

    @Test void foreignAndUnknownIdsHaveSame404AndDatabaseEnforcesOwnership() throws Exception {
        AppUser firstUser = account(), secondUser = account();
        UUID firstProfile = profile(firstUser), secondProfile = profile(secondUser);
        UUID first = curriculum(firstProfile, "CURR-A"), second = curriculum(secondProfile, "CURR-B");
        for (var pair : new Object[][] {{firstUser, second}, {secondUser, first}, {firstUser, UUID.randomUUID()}}) {
            String body = mvc.perform(put(PATH).with(user(new AccountPrincipal((AppUser) pair[0]))).with(csrf())
                            .contentType("application/json").content("{\"curriculumId\":\"" + pair[1] + "\"}"))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CURRICULUM_NOT_FOUND"))
                    .andReturn().getResponse().getContentAsString();
            assertThat(body).doesNotContain("profileId", "source", "SQL", "Curriculum.class");
        }
        assertThat(readSelection(firstUser).path("curriculum").isNull()).isTrue();
        assertThat(readSelection(secondUser).path("curriculum").isNull()).isTrue();
        assertThatThrownBy(() -> jdbc.update("update student_profile set curriculum_id = ? where id = ?", second, firstProfile))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        verifyNoInteractions(portal);
    }

    @Test void authenticationCsrfValidationAndDisabledAccountAreEnforced() throws Exception {
        AppUser user = account(); UUID profile = profile(user), id = curriculum(profile, "CURR-A");
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        mvc.perform(put(PATH).contentType("application/json").content("{\"curriculumId\":\"" + id + "\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete(PATH).with(user(new AccountPrincipal(user)))).andExpect(status().isForbidden());
        mvc.perform(put(PATH).with(user(new AccountPrincipal(user))).with(csrf())
                .contentType("application/json").content("{\"curriculumId\":null}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put(PATH).with(user(new AccountPrincipal(user))).with(csrf())
                .contentType("application/json").content("{\"curriculumId\":\"bad\"}"))
                .andExpect(status().isBadRequest());
        jdbc.update("update app_user set account_status = 'DISABLED' where id = ?", user.getId());
        mvc.perform(get(PATH).with(user(new AccountPrincipal(user)))).andExpect(status().isForbidden());
        mvc.perform(put(PATH).with(user(new AccountPrincipal(user))).with(csrf())
                .contentType("application/json").content("{\"curriculumId\":\"" + id + "\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete(PATH).with(user(new AccountPrincipal(user))).with(csrf())).andExpect(status().isForbidden());
        verifyNoInteractions(portal);
    }
}
