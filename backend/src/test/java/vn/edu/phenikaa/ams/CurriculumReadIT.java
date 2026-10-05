package vn.edu.phenikaa.ams;

import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import vn.edu.phenikaa.ams.academic.application.port.AcademicPortalClient;
import vn.edu.phenikaa.ams.auth.application.AccountPrincipal;
import vn.edu.phenikaa.ams.user.domain.AppUser;
import vn.edu.phenikaa.ams.user.infrastructure.UserRepository;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {"spring.data.redis.password=", "ams.sync.worker-enabled=false"})
@AutoConfigureMockMvc
class CurriculumReadIT {
    private static final String BASE = "/api/me/academic";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @MockitoBean AcademicPortalClient portal;
    private final JsonMapper json = JsonMapper.builder().build();
    private AppUser owner, other;
    private UUID profile, otherProfile, curriculum, otherCurriculum, required, elective, unlinked;

    @BeforeEach void setup() {
        owner = account(); other = account();
        profile = profile(owner); otherProfile = profile(other);
        curriculum = curriculum(profile, "TEST", "Chương trình tổng hợp");
        otherCurriculum = curriculum(otherProfile, "PRIVATE", "Tên không được lộ");
        required = group("R", "Nhóm bắt buộc tổng hợp", "REQUIRED", null, null);
        elective = group("E", "Nhóm tự chọn tổng hợp", "ELECTIVE", 3, 1);
        UUID first = course(profile, "TEST101", "Môn Alpha tổng hợp", 1.5);
        UUID second = course(profile, "TEST102", "Môn Beta tổng hợp", 3);
        unlinked = course(profile, "TEST103", "Môn chưa phân nhóm tổng hợp", 2);
        link(first, required, "REQUIRED", 1.5, null); link(second, elective, "ELECTIVE", 3, 2);
        course(otherProfile, "PRIVATE101", "Môn của tài khoản khác", 3);
        jdbc.update("insert into phenikaa_curriculum_mapping values (?,?,?, ?,now(),now())", UUID.randomUUID(), profile, "synthetic-private-curriculum", curriculum);
        jdbc.update("insert into phenikaa_course_mapping values (?,?,?, ?,now(),now())", UUID.randomUUID(), profile, "synthetic-private-course", first);
        jdbc.update("insert into phenikaa_curriculum_group_mapping values (?,?,?,?,?,now(),now())", UUID.randomUUID(), profile, curriculum, "synthetic-private-group", required);
    }
    @AfterEach void noExternalRequests() { verifyNoInteractions(portal); }

    private AppUser account() { return users.save(new AppUser(UUID.randomUUID() + "@example.test", "!synthetic-unusable", null)); }
    private UUID profile(AppUser account) {
        UUID id = UUID.randomUUID(); jdbc.update("insert into student_profile(id,user_id) values (?,?)", id, account.getId()); return id;
    }
    private UUID curriculum(UUID p, String code, String name) {
        UUID id = UUID.randomUUID(); jdbc.update("insert into curriculum(id,profile_id,code,name,minimum_credits) values (?,?,?,?,?)", id, p, code, name, 120); return id;
    }
    private UUID group(String code, String name, String requirement, Integer credits, Integer count) {
        UUID id = UUID.randomUUID(); jdbc.update("insert into curriculum_group(id,profile_id,curriculum_id,code,name,requirement,minimum_credits,minimum_course_count) values (?,?,?,?,?,?,?,?)",
                id, profile, curriculum, code, name, requirement, credits, count); return id;
    }
    private UUID course(UUID p, String code, String name, double credits) {
        UUID id = UUID.randomUUID(); jdbc.update("insert into course(id,profile_id,code,name,credits) values (?,?,?,?,?)", id, p, code, name, credits); return id;
    }
    private void link(UUID course, UUID group, String requirement, double credits, Integer term) {
        jdbc.update("insert into curriculum_course(id,profile_id,curriculum_id,course_id,group_id,requirement,credits,recommended_term) values (?,?,?,?,?,?,?,?)",
                UUID.randomUUID(), profile, curriculum, course, group, requirement, credits, term);
    }
    private JsonNode read(MockHttpServletRequestBuilder request) throws Exception {
        var response = mvc.perform(request.with(user(new AccountPrincipal(owner)))).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("sourceId", "sourceCurriculumId", "sourceCourseId", "sourceGroupId", "profileId", "version\"",
                "synthetic-private", "PRIVATE101", "Tên không được lộ", "phenikaa", "selector", "DAOTAO", "currentCurriculum");
        return json.readTree(response);
    }

    @Test void ownedListsGroupsAndCoursesPreserveUnknownValues() throws Exception {
        var list = read(get(BASE + "/curricula"));
        assertThat(list.path("items").size()).isEqualTo(1);
        assertThat(list.at("/items/0/courseCount").asInt()).isEqualTo(2);
        assertThat(list.at("/items/0/groupCount").asInt()).isEqualTo(2);
        assertThat(list.at("/items/0/revision").isNull()).isTrue();
        assertThat(list.at("/items/0/cohort").isNull()).isTrue();
        var detail = read(get(BASE + "/curricula/" + curriculum));
        assertThat(detail.at("/groups/items/0/requirement").asText()).isEqualTo("ELECTIVE");
        assertThat(detail.at("/groups/items/1/minimumCredits").isNull()).isTrue();
        assertThat(detail.at("/groups/items/1/minimumCourseCount").isNull()).isTrue();
        var members = read(get(BASE + "/curricula/" + curriculum + "/courses"));
        assertThat(members.path("items").size()).isEqualTo(2);
        assertThat(members.at("/items/0/credits").asDouble()).isEqualTo(1.5);
        assertThat(members.at("/items/0/recommendedTerm").isNull()).isTrue();
        assertThat(members.at("/items/1/recommendedTerm").asInt()).isEqualTo(2);
        var catalog = read(get(BASE + "/catalog/courses"));
        assertThat(catalog.path("items").size()).isEqualTo(3);
        assertThat(catalog.at("/items/2/id").asText()).isEqualTo(unlinked.toString());
        assertThat(catalog.at("/items/2/curriculumLinked").asBoolean()).isFalse();
        assertThat(catalog.at("/items/0/curriculumLinked").asBoolean()).isTrue();
    }

    @Test void foreignAndUnknownCurriculumHaveSame404AndDoNotLeakExistence() throws Exception {
        for (String suffix : List.of("", "/courses")) {
            String foreign = mvc.perform(get(BASE + "/curricula/" + otherCurriculum + suffix).with(user(new AccountPrincipal(owner))))
                    .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
            String missing = mvc.perform(get(BASE + "/curricula/" + UUID.randomUUID() + suffix).with(user(new AccountPrincipal(owner))))
                    .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
            assertThat(json.readTree(foreign).path("detail")).isEqualTo(json.readTree(missing).path("detail"));
        }
        mvc.perform(get(BASE + "/curricula/" + curriculum).with(user(new AccountPrincipal(other)))).andExpect(status().isNotFound());
    }

    @Test void unauthenticatedRequestsRejectedAndReadsNeedNoCsrf() throws Exception {
        for (String path : List.of("/curricula", "/catalog/courses", "/curricula/" + curriculum, "/curricula/" + curriculum + "/courses"))
            mvc.perform(get(BASE + path)).andExpect(status().isUnauthorized());
        read(get(BASE + "/curricula"));
        mvc.perform(post(BASE + "/curricula").with(user(new AccountPrincipal(owner)))).andExpect(status().isForbidden());
    }

    @Test void missingProfileIsEmptyAndNotCreated() throws Exception {
        owner = account();
        assertThat(read(get(BASE + "/curricula")).path("items").isEmpty()).isTrue();
        assertThat(read(get(BASE + "/catalog/courses")).path("items").isEmpty()).isTrue();
        mvc.perform(get(BASE + "/curricula/" + curriculum).with(user(new AccountPrincipal(owner)))).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select count(*) from student_profile where user_id = ?", Integer.class, owner.getId())).isZero();
    }

    @Test void disabledAccountCannotUseAnOldAuthenticatedSession() throws Exception {
        var principal = new AccountPrincipal(owner);
        jdbc.update("update app_user set account_status = 'DISABLED' where id = ?", owner.getId());
        mvc.perform(get(BASE + "/curricula").with(user(principal))).andExpect(status().isForbidden());
        mvc.perform(get(BASE + "/catalog/courses").with(user(principal))).andExpect(status().isForbidden());
    }

    @Test void sameCodeCurriculaUseUuidTieBreakerWithoutSelectingCurrent() throws Exception {
        curriculum(profile, "TEST", "Chương trình tổng hợp thứ hai");
        var expected = jdbc.queryForList("select id from curriculum where profile_id = ? order by code,id", UUID.class, profile);
        var page1 = read(get(BASE + "/curricula").param("limit", "1"));
        var page2 = read(get(BASE + "/curricula").param("limit", "1").param("cursor", page1.path("nextCursor").asText()));
        assertThat(page1.at("/items/0/id").asText()).isEqualTo(expected.getFirst().toString());
        assertThat(page2.at("/items/0/id").asText()).isEqualTo(expected.getLast().toString());
        assertThat(page2.path("nextCursor").isNull()).isTrue();
        assertThat(jdbc.queryForObject("select curriculum_id from student_profile where id = ?", UUID.class, profile)).isNull();
    }

    @Test void groupsAndCoursesPageStablyAndNeverRepeatRows() throws Exception {
        for (String path : List.of("/catalog/courses", "/curricula/" + curriculum + "/courses", "/curricula/" + curriculum)) {
            var seen = new HashSet<String>(); String cursor = null;
            do {
                var request = get(BASE + path).param("limit", "1"); if (cursor != null) request.param("cursor", cursor);
                var result = read(request); var page = result.has("groups") ? result.path("groups") : result;
                for (var item : page.path("items")) assertThat(seen.add(item.path("id").asText())).isTrue();
                cursor = page.path("nextCursor").isNull() ? null : page.path("nextCursor").asText();
            } while (cursor != null);
            assertThat(seen).hasSize(path.equals("/catalog/courses") ? 3 : 2);
        }
    }

    @Test void defaultAndMaximumPagesAreBounded() throws Exception {
        for (int i = 0; i < 103; i++) course(profile, "BULK" + String.format("%03d", i), "Môn tổng hợp phân trang", 3);
        assertThat(read(get(BASE + "/catalog/courses")).path("items").size()).isEqualTo(50);
        var first = read(get(BASE + "/catalog/courses").param("limit", "100"));
        assertThat(first.path("items").size()).isEqualTo(100);
        var last = read(get(BASE + "/catalog/courses").param("limit", "100").param("cursor", first.path("nextCursor").asText()));
        assertThat(last.path("items").size()).isEqualTo(6);
        assertThat(last.path("nextCursor").isNull()).isTrue();
    }

    @ParameterizedTest @ValueSource(strings = {"test101", "  TeSt101  ", "alpha", "ALPHA"})
    void searchesCodeAndNameCaseInsensitively(String term) throws Exception {
        for (String path : List.of("/catalog/courses", "/curricula/" + curriculum + "/courses")) {
            var result = read(get(BASE + path).param("search", term));
            assertThat(result.path("items").size()).isEqualTo(1);
            assertThat(result.at("/items/0/code").asText()).isEqualTo("TEST101");
        }
    }

    @Test void wildcardCharactersAreLiteralAndInjectionIsOnlySearchText() throws Exception {
        course(profile, "LITERAL", "Môn 50%_! tổng hợp", 0);
        for (String term : List.of("%", "_", "!", "50%_!"))
            assertThat(read(get(BASE + "/catalog/courses").param("search", term)).path("items").size()).isEqualTo(1);
        assertThat(read(get(BASE + "/catalog/courses").param("search", "' OR 1=1 --")).path("items").isEmpty()).isTrue();
        assertThat(read(get(BASE + "/catalog/courses").param("search", "not present")).path("nextCursor").isNull()).isTrue();
    }

    @Test void requiredCourseCanHaveNoGroupAndKnownZeroIsNotNull() throws Exception {
        UUID standalone = course(profile, "TEST104", "Môn bắt buộc tổng hợp chưa có nhóm", 0);
        link(standalone, null, "REQUIRED", 0, null);
        group("ZERO", "Nhóm yêu cầu bằng không tổng hợp", "REQUIRED", 0, 0);
        var members = read(get(BASE + "/curricula/" + curriculum + "/courses").param("search", "104"));
        assertThat(members.path("items").size()).isEqualTo(1);
        assertThat(members.at("/items/0/groupId").isNull()).isTrue();
        assertThat(members.at("/items/0/groupName").isNull()).isTrue();
        assertThat(members.at("/items/0/credits").asDouble()).isZero();
        var groups = read(get(BASE + "/curricula/" + curriculum)).at("/groups/items");
        assertThat(groups.get(2).path("minimumCredits").isNumber()).isTrue();
        assertThat(groups.get(2).path("minimumCredits").asDouble()).isZero();
        assertThat(groups.get(2).path("minimumCourseCount").asInt()).isZero();
    }

    @Test void changingSearchStartsASeparateBoundedQueryAndForeignCursorCannotGrantAccess() throws Exception {
        var partial = read(get(BASE + "/catalog/courses").param("search", "test10").param("limit", "2"));
        assertThat(partial.path("items").size()).isEqualTo(2);
        var next = read(get(BASE + "/catalog/courses").param("search", "test10").param("limit", "2")
                .param("cursor", partial.path("nextCursor").asText()));
        assertThat(next.at("/items/0/id").asText()).isEqualTo(unlinked.toString());
        var cursor = vn.edu.phenikaa.ams.academic.application.CatalogPageRequest.encode("A", otherCurriculum);
        assertThat(read(get(BASE + "/catalog/courses").param("cursor", cursor)).path("items").size()).isEqualTo(3);
    }

    @Test void invalidInputsReturnSafe400() throws Exception {
        for (String value : List.of("0", "-1", "101", "abc", "999999999999"))
            mvc.perform(get(BASE + "/catalog/courses").param("limit", value).with(user(new AccountPrincipal(owner)))).andExpect(status().isBadRequest());
        for (String cursor : List.of("", "bad", "a".repeat(281)))
            mvc.perform(get(BASE + "/catalog/courses").param("cursor", cursor).with(user(new AccountPrincipal(owner)))).andExpect(status().isBadRequest());
        mvc.perform(get(BASE + "/catalog/courses").param("search", "a".repeat(101)).with(user(new AccountPrincipal(owner)))).andExpect(status().isBadRequest());
        String body = mvc.perform(get(BASE + "/curricula/not-a-uuid").with(user(new AccountPrincipal(owner))))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("java.", "stack", "SQL", "UUID.fromString");
    }

    @Test void readsDoNotMutateAcademicMappingsConnectionsOrSyncRuns() throws Exception {
        jdbc.update("insert into phenikaa_connection(id,user_id,status,encrypted_subject,encryption_key_version,last_authenticated_at,created_at,updated_at) values (?,?,'DISCONNECTED',?,1,now(),now(),now())",
                UUID.randomUUID(), owner.getId(), new byte[29]);
        jdbc.update("insert into sync_run(id,user_id,trigger_type,status,requested_at,finished_at,next_attempt_at,updated_at) values (?,?,'MANUAL','FAILED',now(),now(),now(),now())",
                UUID.randomUUID(), owner.getId());
        var tables = List.of("student_profile", "curriculum", "course", "curriculum_course", "curriculum_group", "phenikaa_curriculum_mapping", "phenikaa_course_mapping", "phenikaa_curriculum_group_mapping", "phenikaa_connection", "sync_run");
        var before = new HashMap<String, List<String>>();
        for (String table : tables) before.put(table, snapshot(table));
        read(get(BASE + "/curricula")); read(get(BASE + "/curricula/" + curriculum));
        read(get(BASE + "/curricula/" + curriculum + "/courses")); read(get(BASE + "/catalog/courses"));
        for (String table : tables) assertThat(snapshot(table)).as(table).isEqualTo(before.get(table));
    }
    private List<String> snapshot(String table) {
        return jdbc.queryForList("select row_to_json(t)::text from " + table + " t order by id", String.class);
    }
}
