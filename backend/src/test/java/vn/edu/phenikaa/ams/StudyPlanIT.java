package vn.edu.phenikaa.ams;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
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
@SpringBootTest(properties = {"spring.data.redis.password=", "ams.phenikaa.enabled=false"})
@AutoConfigureMockMvc
class StudyPlanIT {
    private static final String PATH = "/api/me/academic/study-plan";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired PlatformTransactionManager transactions;
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
                id, profile, code, "Chương trình kiểm thử " + code, 120);
        return id;
    }
    private UUID course(UUID profile, UUID curriculum, String code, String name, String credits) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into course(id,profile_id,code,name,credits) values (?,?,?,?,?::numeric)",
                id, profile, code, name, credits);
        jdbc.update("""
                insert into curriculum_course(id,profile_id,curriculum_id,course_id,requirement,credits)
                values (?,?,?,?,'REQUIRED',?::numeric)
                """, UUID.randomUUID(), profile, curriculum, id, credits);
        return id;
    }
    private String path(UUID curriculum, UUID course) { return PATH + "/curricula/" + curriculum + "/courses/" + course; }
    private JsonNode read(AppUser account) throws Exception {
        var body = mvc.perform(get(PATH).with(user(new AccountPrincipal(account))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("profileId", "sourceId", "sourceCurriculumId", "phenikaa");
        return json.readTree(body);
    }
    private JsonNode read(AppUser account, int scenario) throws Exception {
        var body = mvc.perform(get(PATH + "?scenario=" + scenario).with(user(new AccountPrincipal(account))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("profileId", "sourceId", "sourceCurriculumId", "phenikaa");
        return json.readTree(body);
    }
    private JsonNode summaries(AppUser account) throws Exception {
        var body = mvc.perform(get(PATH + "/scenarios").with(user(new AccountPrincipal(account))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }
    private JsonNode compare(AppUser account, int left, int right) throws Exception {
        var body = mvc.perform(get(PATH + "/compare?left=" + left + "&right=" + right)
                        .with(user(new AccountPrincipal(account))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("profileId", "userId", "sourceId", "sourceCourseId",
                "sourceCurriculumId", "phenikaa", "assignmentId");
        return json.readTree(body);
    }
    private void select(UUID profile, UUID curriculum) {
        jdbc.update("update student_profile set curriculum_id = ? where id = ?", curriculum, profile);
    }
    private void assign(AppUser account, UUID curriculum, UUID course, String term) throws Exception {
        mvc.perform(put(path(curriculum, course)).with(user(new AccountPrincipal(account))).with(csrf())
                .contentType("application/json").content("{\"plannedTerm\":" + term + "}"))
                .andExpect(status().isNoContent());
    }
    private void assign(AppUser account, UUID curriculum, UUID course, String term, int scenario) throws Exception {
        mvc.perform(put(path(curriculum, course) + "?scenario=" + scenario)
                .with(user(new AccountPrincipal(account))).with(csrf())
                .contentType("application/json").content("{\"plannedTerm\":" + term + "}"))
                .andExpect(status().isNoContent());
    }
    private void copy(AppUser account, int source, int target) throws Exception {
        mvc.perform(post(PATH + "/scenarios/" + target + "/copy").with(user(new AccountPrincipal(account)))
                .with(csrf()).contentType("application/json").content("{\"sourceScenario\":" + source + "}"))
                .andExpect(status().isNoContent());
    }
    private void clear(AppUser account, int scenario) throws Exception {
        mvc.perform(delete(PATH + "/scenarios/" + scenario).with(user(new AccountPrincipal(account)))
                .with(csrf())).andExpect(status().isNoContent());
    }

    @Test void emptyAndSelectedPlansDoNotAutoPopulate() throws Exception {
        var owner = account();
        assertThat(read(owner).at("/curriculum").isNull()).isTrue();
        assertThat(read(owner).at("/terms").isEmpty()).isTrue();
        mvc.perform(put(path(UUID.randomUUID(), UUID.randomUUID())).with(user(new AccountPrincipal(owner))).with(csrf())
                .contentType("application/json").content("{\"plannedTerm\":1}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CURRICULUM_SELECTION_REQUIRED"));
        UUID profile = profile(owner), curriculum = curriculum(profile, "CURR-A");
        assertThat(read(owner).at("/curriculum").isNull()).isTrue();
        select(profile, curriculum);
        assertThat(read(owner).at("/mode").asText()).isEqualTo("USER_PLANNED_AMS");
        assertThat(read(owner).at("/curriculum/id").asText()).isEqualTo(curriculum.toString());
        assertThat(read(owner).at("/terms").isEmpty()).isTrue();
        verifyNoInteractions(portal);
    }

    @Test void addMoveRemoveAreIdempotentAndCreditsFollowCurrentMembership() throws Exception {
        var owner = account(); UUID profile = profile(owner), curriculum = curriculum(profile, "CURR-A");
        UUID b = course(profile, curriculum, "TEST102", "Môn kiểm thử B", "4.00");
        UUID a = course(profile, curriculum, "TEST101", "Môn kiểm thử A", "3.25");
        UUID c = course(profile, curriculum, "TEST103", "Môn kiểm thử C", "2.00");
        select(profile, curriculum);
        assign(owner, curriculum, b, "1"); assign(owner, curriculum, a, "1"); assign(owner, curriculum, c, "2");
        var first = read(owner);
        assertThat(first.at("/terms/0/plannedTerm").asInt()).isEqualTo(1);
        assertThat(first.at("/terms/0/courseCount").asInt()).isEqualTo(2);
        assertThat(first.at("/terms/0/plannedCredits").decimalValue()).isEqualByComparingTo("7.25");
        assertThat(first.at("/terms/0/courses/0/code").asText()).isEqualTo("TEST101");
        assertThat(first.at("/terms/0/courses/1/code").asText()).isEqualTo("TEST102");
        assertThat(first.at("/terms/1/plannedCredits").decimalValue()).isEqualByComparingTo("2.00");
        UUID assignment = jdbc.queryForObject("select id from study_plan_course where course_id = ?", UUID.class, b);
        assign(owner, curriculum, b, "1"); assign(owner, curriculum, b, "2");
        assertThat(jdbc.queryForObject("select id from study_plan_course where course_id = ?", UUID.class, b)).isEqualTo(assignment);
        assertThat(read(owner).at("/terms/1/courseCount").asInt()).isEqualTo(2);
        jdbc.update("update curriculum_course set credits = 4.50 where course_id = ?", b);
        jdbc.update("update course set name = 'Tên cập nhật' where id = ?", b);
        var refreshed = read(owner);
        assertThat(refreshed.at("/terms/1/plannedCredits").decimalValue()).isEqualByComparingTo("6.50");
        assertThat(refreshed.at("/terms/1/courses/0/name").asText()).isEqualTo("Tên cập nhật");
        mvc.perform(delete(path(curriculum, a)).with(user(new AccountPrincipal(owner))).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(delete(path(curriculum, a)).with(user(new AccountPrincipal(owner))).with(csrf())).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select count(*) from study_plan_course where profile_id = ?", Integer.class, profile)).isEqualTo(2);
        verifyNoInteractions(portal);
    }

    @Test void switchingAndClearingSelectionPreservesSeparatePlans() throws Exception {
        var owner = account(); UUID profile = profile(owner);
        UUID a = curriculum(profile, "CURR-A"), b = curriculum(profile, "CURR-B");
        UUID aCourse = course(profile, a, "TEST101", "Môn A", "3");
        UUID bCourse = course(profile, b, "TEST201", "Môn B", "4");
        select(profile, a); assign(owner, a, aCourse, "1");
        select(profile, b);
        assertThat(read(owner).at("/terms").isEmpty()).isTrue();
        assign(owner, b, bCourse, "2");
        assertThat(read(owner).at("/terms/0/courses/0/courseId").asText()).isEqualTo(bCourse.toString());
        select(profile, a);
        assertThat(read(owner).at("/terms/0/courses/0/courseId").asText()).isEqualTo(aCourse.toString());
        select(profile, null);
        assertThat(read(owner).at("/curriculum").isNull()).isTrue();
        assertThat(read(owner).at("/terms").isEmpty()).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from study_plan_course where profile_id = ?", Integer.class, profile)).isEqualTo(2);
        select(profile, a);
        assertThat(read(owner).at("/terms/0/courses/0/courseId").asText()).isEqualTo(aCourse.toString());
        verifyNoInteractions(portal);
    }

    @Test void ownershipValidationAndDatabaseForeignKeyRejectCrossProfileCourse() throws Exception {
        var first = account(); var second = account();
        UUID firstProfile = profile(first), secondProfile = profile(second);
        UUID firstCurriculum = curriculum(firstProfile, "CURR-A"), secondCurriculum = curriculum(secondProfile, "CURR-B");
        UUID firstCourse = course(firstProfile, firstCurriculum, "TEST101", "Môn A", "3");
        UUID secondCourse = course(secondProfile, secondCurriculum, "TEST201", "Môn B", "4");
        select(firstProfile, firstCurriculum);
        mvc.perform(put(path(secondCurriculum, secondCourse)).with(user(new AccountPrincipal(first))).with(csrf())
                .contentType("application/json").content("{\"plannedTerm\":1}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STUDY_PLAN_SELECTION_CHANGED"));
        mvc.perform(put(path(firstCurriculum, secondCourse)).with(user(new AccountPrincipal(first))).with(csrf())
                .contentType("application/json").content("{\"plannedTerm\":1}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("STUDY_PLAN_COURSE_NOT_FOUND"));
        mvc.perform(delete(path(firstCurriculum, secondCourse)).with(user(new AccountPrincipal(first))).with(csrf()))
                .andExpect(status().isNotFound());
        assertThatThrownBy(() -> jdbc.update("""
                insert into study_plan_course(id,profile_id,curriculum_id,scenario_no,course_id,planned_term,created_at,updated_at)
                values (?,?,?,1,?,1,now(),now())
                """, UUID.randomUUID(), firstProfile, firstCurriculum, secondCourse))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(read(second).at("/terms").isEmpty()).isTrue();
        assign(first, firstCurriculum, firstCourse, "1");
        verifyNoInteractions(portal);
    }

    @Test void validationAuthenticationCsrfAndNoOutcomeMutation() throws Exception {
        var owner = account(); UUID profile = profile(owner), curriculum = curriculum(profile, "CURR-A");
        UUID course = course(profile, curriculum, "TEST101", "Môn A", "3");
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        mvc.perform(put(path(curriculum, course)).with(user(new AccountPrincipal(owner)))
                .contentType("application/json").content("{\"plannedTerm\":1}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete(path(curriculum, course)).with(user(new AccountPrincipal(owner))))
                .andExpect(status().isForbidden());
        mvc.perform(put(path(curriculum, course)).with(user(new AccountPrincipal(owner))).with(csrf())
                .contentType("application/json").content("{\"plannedTerm\":1}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CURRICULUM_SELECTION_REQUIRED"));
        select(profile, curriculum);
        for (String term : new String[] {"0", "100", "2.5", "null"})
            mvc.perform(put(path(curriculum, course)).with(user(new AccountPrincipal(owner))).with(csrf())
                    .contentType("application/json").content("{\"plannedTerm\":" + term + "}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PLANNED_TERM"));
        String[] unchanged = {"student_course", "academic_result", "semester", "class_section", "class_session",
                "exam", "academic_snapshot", "schedule_change", "notification_outbox"};
        long[] before = new long[unchanged.length];
        for (int i = 0; i < unchanged.length; i++)
            before[i] = jdbc.queryForObject("select count(*) from " + unchanged[i], Long.class);
        assign(owner, curriculum, course, "99");
        for (int i = 0; i < unchanged.length; i++)
            assertThat(jdbc.queryForObject("select count(*) from " + unchanged[i], Long.class)).isEqualTo(before[i]);
        verifyNoInteractions(portal);
    }

    @Test void concurrentPutsKeepOneAssignment() throws Exception {
        var owner = account(); UUID profile = profile(owner), curriculum = curriculum(profile, "CURR-A");
        UUID course = course(profile, curriculum, "TEST101", "Môn A", "3");
        select(profile, curriculum);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var ready = new CountDownLatch(2);
            var start = new CountDownLatch(1);
            var tasks = java.util.stream.IntStream.range(0, 2).mapToObj(i -> workers.submit(() -> {
                ready.countDown();
                start.await();
                assign(owner, curriculum, course, i == 0 ? "1" : "2");
                return null;
            })).toList();
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (var task : tasks) task.get(10, TimeUnit.SECONDS);
        }
        assertThat(jdbc.queryForObject("select count(*) from study_plan_course where profile_id = ?", Integer.class, profile)).isEqualTo(1);
        assertThat(read(owner).at("/terms/0/courseCount").asInt()).isEqualTo(1);
        verifyNoInteractions(portal);
    }

    @Test void mutationWaitsForSelectionChangeAndRejectsOldCurriculum() throws Exception {
        var owner = account(); UUID profile = profile(owner);
        UUID first = curriculum(profile, "CURR-A"), second = curriculum(profile, "CURR-B");
        UUID course = course(profile, first, "TEST101", "Môn A", "3");
        select(profile, first);
        var held = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var switcher = workers.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                jdbc.queryForObject("select id from student_profile where id = ? for update", UUID.class, profile);
                jdbc.update("update student_profile set curriculum_id = ? where id = ?", second, profile);
                held.countDown();
                try { if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Selection lock timeout"); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
                return null;
            }));
            assertThat(held.await(5, TimeUnit.SECONDS)).isTrue();
            var mutation = workers.submit(() -> mvc.perform(put(path(first, course))
                    .with(user(new AccountPrincipal(owner))).with(csrf())
                    .contentType("application/json").content("{\"plannedTerm\":1}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("STUDY_PLAN_SELECTION_CHANGED")));
            try { assertThatThrownBy(() -> mutation.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class); }
            finally { release.countDown(); }
            switcher.get(10, TimeUnit.SECONDS);
            mutation.get(10, TimeUnit.SECONDS);
        }
        assertThat(jdbc.queryForObject("select count(*) from study_plan_course where profile_id = ?", Integer.class, profile)).isZero();
        verifyNoInteractions(portal);
    }

    @Test void curriculumRefreshStyleUpdatesKeepPlanWithoutAutoAddingCourses() throws Exception {
        var owner = account(); UUID profile = profile(owner), curriculum = curriculum(profile, "CURR-A");
        UUID course = course(profile, curriculum, "TEST101", "Tên ban đầu", "3");
        select(profile, curriculum);
        assign(owner, curriculum, course, "3");
        UUID assignment = jdbc.queryForObject("select id from study_plan_course where course_id = ?", UUID.class, course);
        jdbc.update("update curriculum set name = 'Tên chương trình cập nhật' where id = ?", curriculum);
        jdbc.update("update course set name = 'Tên môn cập nhật' where id = ?", course);
        jdbc.update("update curriculum_course set credits = 3.50 where curriculum_id = ? and course_id = ?", curriculum, course);
        course(profile, curriculum, "TEST102", "Môn mới", "4");
        curriculum(profile, "CURR-B");
        var plan = read(owner);
        assertThat(plan.at("/curriculum/name").asText()).isEqualTo("Tên chương trình cập nhật");
        assertThat(plan.at("/terms/0/courses/0/name").asText()).isEqualTo("Tên môn cập nhật");
        assertThat(plan.at("/terms/0/plannedCredits").decimalValue()).isEqualByComparingTo("3.50");
        assertThat(plan.at("/terms/0/courseCount").asInt()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select id from study_plan_course where course_id = ?", UUID.class, course)).isEqualTo(assignment);
        verifyNoInteractions(portal);
    }

    @Test void databaseRejectsInvalidTermTimestampAndDuplicateAssignment() throws Exception {
        var owner = account(); UUID profile = profile(owner), curriculum = curriculum(profile, "CURR-A");
        UUID course = course(profile, curriculum, "TEST101", "Môn A", "3");
        select(profile, curriculum);
        for (int term : new int[] {0, 100})
            assertThatThrownBy(() -> jdbc.update("""
                    insert into study_plan_course(id,profile_id,curriculum_id,scenario_no,course_id,planned_term,created_at,updated_at)
                    values (?,?,?,1,?,?,now(),now())
                    """, UUID.randomUUID(), profile, curriculum, course, term))
                    .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                insert into study_plan_course(id,profile_id,curriculum_id,scenario_no,course_id,planned_term,created_at,updated_at)
                values (?,?,?,1,?,1,now(),now() - interval '1 day')
                """, UUID.randomUUID(), profile, curriculum, course))
                .isInstanceOf(DataIntegrityViolationException.class);
        assign(owner, curriculum, course, "1");
        assertThatThrownBy(() -> jdbc.update("""
                insert into study_plan_course(id,profile_id,curriculum_id,scenario_no,course_id,planned_term,created_at,updated_at)
                values (?,?,?,1,?,2,now(),now())
                """, UUID.randomUUID(), profile, curriculum, course))
                .isInstanceOf(DataIntegrityViolationException.class);
        verifyNoInteractions(portal);
    }

    @Test void defaultsToScenarioOneAndSummarizesFiveIndependentScenarios() throws Exception {
        var owner = account();
        assertThat(read(owner).at("/scenarioNo").asInt()).isEqualTo(1);
        assertThat(read(owner, 5).at("/scenarioNo").asInt()).isEqualTo(5);
        assertThat(summaries(owner).at("/curriculum").isNull()).isTrue();
        assertThat(summaries(owner).at("/scenarios").isEmpty()).isTrue();
        UUID profile = profile(owner), curriculum = curriculum(profile, "CURR-A");
        UUID first = course(profile, curriculum, "TEST101", "Môn A", "3.25");
        UUID second = course(profile, curriculum, "TEST102", "Môn B", "4.00");
        select(profile, curriculum);
        assign(owner, curriculum, first, "1");
        assign(owner, curriculum, second, "2");
        assign(owner, curriculum, first, "3", 2);
        assertThat(read(owner).at("/terms/0/courses/0/courseId").asText()).isEqualTo(first.toString());
        assertThat(read(owner, 2).at("/terms/0/plannedTerm").asInt()).isEqualTo(3);
        assertThat(read(owner, 5).at("/terms").isEmpty()).isTrue();
        var list = summaries(owner).at("/scenarios");
        assertThat(list.size()).isEqualTo(5);
        assertThat(list.get(0).get("courseCount").asInt()).isEqualTo(2);
        assertThat(list.get(0).get("termCount").asInt()).isEqualTo(2);
        assertThat(list.get(0).get("plannedCredits").decimalValue()).isEqualByComparingTo("7.25");
        assertThat(list.get(1).get("courseCount").asInt()).isEqualTo(1);
        assertThat(list.get(1).get("plannedCredits").decimalValue()).isEqualByComparingTo("3.25");
        assertThat(list.get(4).get("courseCount").asInt()).isZero();
        assertThat(list.get(4).get("termCount").asInt()).isZero();
        assertThat(list.get(4).get("plannedCredits").decimalValue()).isEqualByComparingTo("0");
        mvc.perform(delete(path(curriculum, first) + "?scenario=2").with(user(new AccountPrincipal(owner)))
                .with(csrf())).andExpect(status().isNoContent());
        assertThat(read(owner, 2).at("/terms").isEmpty()).isTrue();
        assertThat(read(owner).at("/terms/0/courses/0/courseId").asText()).isEqualTo(first.toString());
        verifyNoInteractions(portal);
    }

    @Test void copyMakesIndependentAssignmentsAndClearOnlyAffectsTarget() throws Exception {
        var owner = account(); UUID profile = profile(owner), curriculum = curriculum(profile, "CURR-A");
        UUID first = course(profile, curriculum, "TEST101", "Môn A", "3");
        UUID second = course(profile, curriculum, "TEST102", "Môn B", "4");
        select(profile, curriculum);
        assign(owner, curriculum, first, "1");
        assign(owner, curriculum, second, "2");
        UUID sourceId = jdbc.queryForObject("select id from study_plan_course where course_id = ? and scenario_no = 1", UUID.class, second);
        java.time.Instant sourceCreated = jdbc.queryForObject("select created_at from study_plan_course where id = ?",
                java.sql.Timestamp.class, sourceId).toInstant();
        String[] unchanged = {"student_course", "academic_result", "semester", "class_section", "class_session",
                "exam", "academic_snapshot", "schedule_change", "notification_outbox"};
        long[] before = new long[unchanged.length];
        for (int i = 0; i < unchanged.length; i++)
            before[i] = jdbc.queryForObject("select count(*) from " + unchanged[i], Long.class);
        copy(owner, 1, 2);
        assertThat(read(owner, 2).at("/terms/1/courses/0/courseId").asText()).isEqualTo(second.toString());
        UUID copiedId = jdbc.queryForObject("select id from study_plan_course where course_id = ? and scenario_no = 2", UUID.class, second);
        assertThat(copiedId).isNotEqualTo(sourceId);
        assertThat(jdbc.queryForObject("select created_at from study_plan_course where id = ?",
                java.sql.Timestamp.class, copiedId).toInstant()).isAfter(sourceCreated);
        mvc.perform(post(PATH + "/scenarios/2/copy").with(user(new AccountPrincipal(owner))).with(csrf())
                .contentType("application/json").content("{\"sourceScenario\":1}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STUDY_PLAN_SCENARIO_NOT_EMPTY"));
        assign(owner, curriculum, second, "3", 2);
        assertThat(read(owner, 2).at("/terms/1/plannedTerm").asInt()).isEqualTo(3);
        assertThat(read(owner).at("/terms/1/plannedTerm").asInt()).isEqualTo(2);
        clear(owner, 2); clear(owner, 2);
        assertThat(read(owner, 2).at("/terms").isEmpty()).isTrue();
        assertThat(read(owner).at("/terms/1/courses/0/courseId").asText()).isEqualTo(second.toString());
        copy(owner, 5, 2);
        assertThat(read(owner, 2).at("/terms").isEmpty()).isTrue();
        copy(owner, 1, 5);
        assertThat(read(owner, 5).at("/terms/1/courses/0/courseId").asText()).isEqualTo(second.toString());
        clear(owner, 5);
        for (int i = 0; i < unchanged.length; i++)
            assertThat(jdbc.queryForObject("select count(*) from " + unchanged[i], Long.class)).isEqualTo(before[i]);
        verifyNoInteractions(portal);
    }

    @Test void scenarioValidationAuthenticationAndOwnershipAreEnforced() throws Exception {
        var owner = account(); var other = account();
        UUID profile = profile(owner), foreignProfile = profile(other);
        UUID curriculum = curriculum(profile, "CURR-A"), foreign = curriculum(foreignProfile, "CURR-B");
        UUID course = course(profile, curriculum, "TEST101", "Môn A", "3");
        UUID foreignCourse = course(foreignProfile, foreign, "TEST201", "Môn B", "4");
        select(profile, curriculum); select(foreignProfile, foreign);
        for (String invalid : new String[] {"0", "6", "2.5", "abc"}) {
            mvc.perform(get(PATH + "?scenario=" + invalid).with(user(new AccountPrincipal(owner))))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_STUDY_PLAN_SCENARIO"));
            mvc.perform(put(path(curriculum, course) + "?scenario=" + invalid).with(user(new AccountPrincipal(owner)))
                    .with(csrf()).contentType("application/json").content("{\"plannedTerm\":1}"))
                    .andExpect(status().isBadRequest());
            mvc.perform(delete(PATH + "/scenarios/" + invalid).with(user(new AccountPrincipal(owner)))
                    .with(csrf())).andExpect(status().isBadRequest());
        }
        mvc.perform(post(PATH + "/scenarios/1/copy").with(user(new AccountPrincipal(owner))).with(csrf())
                .contentType("application/json").content("{\"sourceScenario\":1}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_STUDY_PLAN_SCENARIO"));
        mvc.perform(post(PATH + "/scenarios/2/copy").with(user(new AccountPrincipal(owner))).with(csrf())
                .contentType("application/json").content("{\"sourceScenario\":2.5}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(PATH + "/scenarios")).andExpect(status().isUnauthorized());
        mvc.perform(post(PATH + "/scenarios/2/copy").with(user(new AccountPrincipal(owner)))
                .contentType("application/json").content("{\"sourceScenario\":1}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete(PATH + "/scenarios/2").with(user(new AccountPrincipal(owner))))
                .andExpect(status().isForbidden());
        mvc.perform(put(path(foreign, foreignCourse) + "?scenario=2").with(user(new AccountPrincipal(owner)))
                .with(csrf()).contentType("application/json").content("{\"plannedTerm\":1}"))
                .andExpect(status().isConflict());
        mvc.perform(put(path(curriculum, foreignCourse) + "?scenario=2").with(user(new AccountPrincipal(owner)))
                .with(csrf()).contentType("application/json").content("{\"plannedTerm\":1}"))
                .andExpect(status().isNotFound());
        clear(owner, 2);
        assertThat(read(other, 2).at("/terms").isEmpty()).isTrue();
        assertThat(summaries(owner).at("/curriculum/id").asText()).isEqualTo(curriculum.toString());
        verifyNoInteractions(portal);
    }

    @Test void scenariosSurviveCurriculumAndSelectionChanges() throws Exception {
        var owner = account(); UUID profile = profile(owner);
        UUID first = curriculum(profile, "CURR-A"), second = curriculum(profile, "CURR-B");
        UUID firstCourse = course(profile, first, "TEST101", "Môn A", "3");
        UUID secondCourse = course(profile, second, "TEST201", "Môn B", "4");
        select(profile, first); assign(owner, first, firstCourse, "2", 2);
        select(profile, second); assign(owner, second, secondCourse, "3", 2);
        assertThat(read(owner, 2).at("/terms/0/courses/0/courseId").asText()).isEqualTo(secondCourse.toString());
        select(profile, null);
        assertThat(read(owner, 2).at("/curriculum").isNull()).isTrue();
        assertThat(read(owner, 2).at("/terms").isEmpty()).isTrue();
        assertThat(summaries(owner).at("/scenarios").isEmpty()).isTrue();
        mvc.perform(delete(PATH + "/scenarios/2").with(user(new AccountPrincipal(owner))).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CURRICULUM_SELECTION_REQUIRED"));
        select(profile, first);
        assertThat(read(owner, 2).at("/terms/0/courses/0/courseId").asText()).isEqualTo(firstCourse.toString());
        assertThat(jdbc.queryForObject("select count(*) from study_plan_course where profile_id = ?", Integer.class, profile)).isEqualTo(2);
        verifyNoInteractions(portal);
    }

    @Test void databaseEnforcesScenarioRangeUniquePerScenarioAndProfileMembership() throws Exception {
        var owner = account(); var other = account();
        UUID profile = profile(owner), foreignProfile = profile(other);
        UUID curriculum = curriculum(profile, "CURR-A"), foreign = curriculum(foreignProfile, "CURR-B");
        UUID course = course(profile, curriculum, "TEST101", "Môn A", "3");
        UUID foreignCourse = course(foreignProfile, foreign, "TEST201", "Môn B", "4");
        select(profile, curriculum);
        for (int scenario : new int[] {0, 6}) {
            assertThatThrownBy(() -> jdbc.update("""
                    insert into study_plan_course(id,profile_id,curriculum_id,scenario_no,course_id,planned_term,created_at,updated_at)
                    values (?,?,?,?,?,1,now(),now())
                    """, UUID.randomUUID(), profile, curriculum, scenario, course))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
        assign(owner, curriculum, course, "1");
        assign(owner, curriculum, course, "2", 2);
        assertThat(jdbc.queryForObject("select count(*) from study_plan_course where profile_id = ? and course_id = ?",
                Integer.class, profile, course)).isEqualTo(2);
        assertThatThrownBy(() -> jdbc.update("""
                insert into study_plan_course(id,profile_id,curriculum_id,scenario_no,course_id,planned_term,created_at,updated_at)
                values (?,?,?,1,?,3,now(),now())
                """, UUID.randomUUID(), profile, curriculum, course)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                insert into study_plan_course(id,profile_id,curriculum_id,scenario_no,course_id,planned_term,created_at,updated_at)
                values (?,?,?,3,?,3,now(),now())
                """, UUID.randomUUID(), profile, curriculum, foreignCourse)).isInstanceOf(DataIntegrityViolationException.class);
        verifyNoInteractions(portal);
    }

    @Test void copyWaitsForSelectionChangeAndCannotWriteOldCurriculum() throws Exception {
        var owner = account(); UUID profile = profile(owner);
        UUID first = curriculum(profile, "CURR-A"), second = curriculum(profile, "CURR-B");
        UUID course = course(profile, first, "TEST101", "Môn A", "3");
        select(profile, first); assign(owner, first, course, "1");
        var held = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var switcher = workers.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                jdbc.queryForObject("select id from student_profile where id = ? for update", UUID.class, profile);
                jdbc.update("update student_profile set curriculum_id = ? where id = ?", second, profile);
                held.countDown();
                try { if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Selection lock timeout"); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
                return null;
            }));
            assertThat(held.await(5, TimeUnit.SECONDS)).isTrue();
            var copying = workers.submit(() -> mvc.perform(post(PATH + "/scenarios/2/copy")
                    .with(user(new AccountPrincipal(owner))).with(csrf())
                    .contentType("application/json").content("{\"sourceScenario\":1}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("STUDY_PLAN_SELECTION_CHANGED")));
            try { assertThatThrownBy(() -> copying.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class); }
            finally { release.countDown(); }
            switcher.get(10, TimeUnit.SECONDS);
            copying.get(10, TimeUnit.SECONDS);
        }
        assertThat(read(owner, 2).at("/terms").isEmpty()).isTrue();
        select(profile, first);
        assertThat(read(owner, 2).at("/terms").isEmpty()).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from study_plan_course where profile_id = ?", Integer.class, profile)).isEqualTo(1);
        verifyNoInteractions(portal);
    }

    @Test void copyAndCourseMutationSerializeOnTheProfile() throws Exception {
        var owner = account(); UUID profile = profile(owner), curriculum = curriculum(profile, "CURR-A");
        UUID sourceCourse = course(profile, curriculum, "TEST101", "Môn A", "3");
        UUID addedCourse = course(profile, curriculum, "TEST102", "Môn B", "4");
        select(profile, curriculum); assign(owner, curriculum, sourceCourse, "1");
        var start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var copying = workers.submit(() -> {
                start.await();
                return mvc.perform(post(PATH + "/scenarios/2/copy")
                        .with(user(new AccountPrincipal(owner))).with(csrf())
                        .contentType("application/json").content("{\"sourceScenario\":1}"))
                        .andReturn().getResponse();
            });
            var adding = workers.submit(() -> {
                start.await();
                assign(owner, curriculum, addedCourse, "2", 2);
                return null;
            });
            start.countDown();
            var copyResponse = copying.get(10, TimeUnit.SECONDS);
            adding.get(10, TimeUnit.SECONDS);
            assertThat(copyResponse.getStatus()).isIn(204, 409);
            if (copyResponse.getStatus() == 409)
                assertThat(json.readTree(copyResponse.getContentAsString()).get("code").asText())
                        .isEqualTo("STUDY_PLAN_SCENARIO_NOT_EMPTY");
            var target = read(owner, 2);
            assertThat(target.toString()).contains(addedCourse.toString());
            assertThat(target.toString().contains(sourceCourse.toString())).isEqualTo(copyResponse.getStatus() == 204);
            assertThat(read(owner).toString()).contains(sourceCourse.toString()).doesNotContain(addedCourse.toString());
        }
        verifyNoInteractions(portal);
    }

    @Test void comparisonWithoutSelectionAndWithEmptyScenariosIsSafe() throws Exception {
        var owner = account();
        var withoutProfile = compare(owner, 1, 2);
        assertThat(withoutProfile.at("/mode").asText()).isEqualTo("USER_PLANNED_AMS");
        assertThat(withoutProfile.at("/curriculum").isNull()).isTrue();
        assertThat(withoutProfile.at("/leftScenario").asInt()).isEqualTo(1);
        assertThat(withoutProfile.at("/rightScenario").asInt()).isEqualTo(2);
        assertThat(withoutProfile.at("/left/courseCount").asInt()).isZero();
        assertThat(withoutProfile.at("/right/plannedCredits").decimalValue()).isEqualByComparingTo("0");
        assertThat(withoutProfile.at("/terms").isEmpty()).isTrue();
        assertThat(withoutProfile.at("/courses").isEmpty()).isTrue();
        UUID profile = profile(owner), curriculum = curriculum(profile, "CURR-A");
        assertThat(compare(owner, 1, 2).at("/curriculum").isNull()).isTrue();
        select(profile, curriculum);
        var selected = compare(owner, 1, 2);
        assertThat(selected.at("/curriculum/id").asText()).isEqualTo(curriculum.toString());
        assertThat(selected.at("/terms").isEmpty()).isTrue();
        assertThat(selected.at("/courses").isEmpty()).isTrue();
        verifyNoInteractions(portal);
    }

    @Test void comparisonClassifiesAssignmentsAndDoesNotMutateAnyAcademicTables() throws Exception {
        var owner = account(); UUID profile = profile(owner), curriculum = curriculum(profile, "CURR-A");
        UUID unchanged = course(profile, curriculum, "TEST101", "Môn A", "3.25");
        UUID moved = course(profile, curriculum, "TEST102", "Môn B", "4.00");
        UUID onlyLeft = course(profile, curriculum, "TEST103", "Môn C", "2.00");
        UUID onlyRight = course(profile, curriculum, "TEST104", "Môn D", "1.50");
        UUID group = UUID.randomUUID();
        jdbc.update("insert into curriculum_group(id,profile_id,curriculum_id,code,name,minimum_credits,requirement) values (?,?,?,'E','Nhóm kiểm thử',0,'ELECTIVE')",
                group, profile, curriculum);
        jdbc.update("update curriculum_course set requirement = 'ELECTIVE', group_id = ? where course_id = ?",
                group, onlyRight);
        select(profile, curriculum);
        assign(owner, curriculum, unchanged, "1", 1);
        assign(owner, curriculum, moved, "2", 1);
        assign(owner, curriculum, onlyLeft, "2", 1);
        assign(owner, curriculum, unchanged, "1", 2);
        assign(owner, curriculum, moved, "3", 2);
        assign(owner, curriculum, onlyRight, "3", 2);
        String[] tables = {"study_plan_course", "student_course", "academic_result", "semester",
                "class_section", "class_session", "exam", "academic_snapshot", "schedule_change",
                "notification_outbox"};
        long[] before = new long[tables.length];
        for (int i = 0; i < tables.length; i++)
            before[i] = jdbc.queryForObject("select count(*) from " + tables[i], Long.class);
        var timestamps = jdbc.queryForList("select id, created_at, updated_at from study_plan_course where profile_id = ? order by id", profile);
        var result = compare(owner, 1, 2);
        assertThat(result.at("/left/plannedCredits").decimalValue()).isEqualByComparingTo("9.25");
        assertThat(result.at("/right/plannedCredits").decimalValue()).isEqualByComparingTo("8.75");
        assertThat(result.at("/left/courseCount").asInt()).isEqualTo(3);
        assertThat(result.at("/right/courseCount").asInt()).isEqualTo(3);
        assertThat(result.at("/left/termCount").asInt()).isEqualTo(2);
        assertThat(result.at("/right/termCount").asInt()).isEqualTo(2);
        assertThat(result.at("/terms/0/plannedTerm").asInt()).isEqualTo(1);
        assertThat(result.at("/terms/1/plannedTerm").asInt()).isEqualTo(2);
        assertThat(result.at("/terms/1/leftCourseCount").asInt()).isEqualTo(2);
        assertThat(result.at("/terms/1/rightCourseCount").asInt()).isZero();
        assertThat(result.at("/terms/1/rightPlannedCredits").decimalValue()).isEqualByComparingTo("0");
        assertThat(result.at("/terms/2/plannedTerm").asInt()).isEqualTo(3);
        assertThat(result.at("/courses/0/change").asText()).isEqualTo("UNCHANGED");
        assertThat(result.at("/courses/1/change").asText()).isEqualTo("MOVED");
        assertThat(result.at("/courses/1/leftPlannedTerm").asInt()).isEqualTo(2);
        assertThat(result.at("/courses/1/rightPlannedTerm").asInt()).isEqualTo(3);
        assertThat(result.at("/courses/2/change").asText()).isEqualTo("ONLY_LEFT");
        assertThat(result.at("/courses/2/rightPlannedTerm").isNull()).isTrue();
        assertThat(result.at("/courses/3/change").asText()).isEqualTo("ONLY_RIGHT");
        assertThat(result.at("/courses/3/requirement").asText()).isEqualTo("ELECTIVE");
        assertThat(result.at("/courses/3/groupName").asText()).isEqualTo("Nhóm kiểm thử");
        assertThat(result.at("/courses/3/credits").decimalValue()).isEqualByComparingTo("1.50");
        assertThat(jdbc.queryForList("select id, created_at, updated_at from study_plan_course where profile_id = ? order by id", profile))
                .isEqualTo(timestamps);
        for (int i = 0; i < tables.length; i++)
            assertThat(jdbc.queryForObject("select count(*) from " + tables[i], Long.class)).isEqualTo(before[i]);
        verifyNoInteractions(portal);
    }

    @Test void comparisonHandlesOneEmptySideIdenticalPlansAndSeparateCurricula() throws Exception {
        var owner = account(); UUID profile = profile(owner);
        UUID first = curriculum(profile, "CURR-A"), second = curriculum(profile, "CURR-B");
        UUID firstCourse = course(profile, first, "TEST101", "Môn A", "3");
        UUID secondCourse = course(profile, second, "TEST201", "Môn B", "4");
        select(profile, first); assign(owner, first, firstCourse, "2", 2);
        var oneSide = compare(owner, 1, 2);
        assertThat(oneSide.at("/courses/0/change").asText()).isEqualTo("ONLY_RIGHT");
        assertThat(oneSide.at("/terms/0/leftCourseCount").asInt()).isZero();
        assign(owner, first, firstCourse, "2", 1);
        assertThat(compare(owner, 1, 2).at("/courses/0/change").asText()).isEqualTo("UNCHANGED");
        select(profile, second); assign(owner, second, secondCourse, "3", 1);
        var switched = compare(owner, 1, 2);
        assertThat(switched.at("/curriculum/id").asText()).isEqualTo(second.toString());
        assertThat(switched.at("/courses/0/courseId").asText()).isEqualTo(secondCourse.toString());
        assertThat(switched.toString()).doesNotContain(firstCourse.toString());
        verifyNoInteractions(portal);
    }

    @Test void comparisonRejectsInvalidPairsAndNeverReadsAnotherUsersPlan() throws Exception {
        var owner = account(); var other = account();
        UUID profile = profile(owner), foreignProfile = profile(other);
        UUID curriculum = curriculum(profile, "CURR-A"), foreign = curriculum(foreignProfile, "CURR-B");
        UUID course = course(profile, curriculum, "TEST101", "Môn A", "3");
        UUID foreignCourse = course(foreignProfile, foreign, "TEST201", "Môn B", "4");
        select(profile, curriculum); select(foreignProfile, foreign);
        assign(owner, curriculum, course, "1", 1);
        assign(other, foreign, foreignCourse, "2", 1);
        for (String query : new String[] {"left=0&right=2", "left=6&right=2", "left=1&right=0",
                "left=1&right=6", "left=1.5&right=2", "left=1&right=abc",
                "left=&right=2", "left=1&right=", "left=2&right=2", "right=2", "left=1"}) {
            mvc.perform(get(PATH + "/compare?" + query).with(user(new AccountPrincipal(owner))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_STUDY_PLAN_COMPARISON"));
        }
        mvc.perform(get(PATH + "/compare?left=1&right=2")).andExpect(status().isUnauthorized());
        var result = compare(owner, 1, 2);
        assertThat(result.at("/curriculum/id").asText()).isEqualTo(curriculum.toString());
        assertThat(result.toString()).contains(course.toString())
                .doesNotContain(foreign.toString(), foreignCourse.toString(), foreignProfile.toString());
        verifyNoInteractions(portal);
    }
}
