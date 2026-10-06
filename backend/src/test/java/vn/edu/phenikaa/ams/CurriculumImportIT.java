package vn.edu.phenikaa.ams;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import vn.edu.phenikaa.ams.academic.application.*;
import vn.edu.phenikaa.ams.academic.application.port.*;
import vn.edu.phenikaa.ams.academic.infrastructure.phenikaa.*;
import vn.edu.phenikaa.ams.user.domain.AppUser;
import vn.edu.phenikaa.ams.user.infrastructure.UserRepository;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {"spring.data.redis.password=", "ams.phenikaa.enabled=true"})
class CurriculumImportIT {
    @Autowired UserRepository users;
    @Autowired PhenikaaAcademicPortalClient portal;
    @Autowired ProfileImportService profiles;
    @Autowired CurriculumImportService imports;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean PhenikaaHttpClient http;
    private UUID user, profile;
    private AcademicPortalClient.StudentConnectionId connection;
    private final CurriculumOption option = new CurriculumOption("synthetic-curriculum", "TEST-CT", "Chương trình giả định", "TEST", new BigDecimal("6"));

    @DynamicPropertySource static void key(DynamicPropertyRegistry properties) {
        byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
        String key = Base64.getEncoder().encodeToString(bytes); Arrays.fill(bytes, (byte) 0);
        properties.add("ams.phenikaa.session-key", () -> key);
    }
    @BeforeEach void setup() {
        user = users.save(new AppUser(UUID.randomUUID() + "@example.test", "!synthetic-unusable", null)).getId();
        when(http.fetchProfile(any())).thenReturn(new ProfileObservation("SYNTHETIC", "Ngành giả định"));
        connection = connect(user, true);
        profile = profiles.importCurrentProfile(user);
        when(http.fetchCurricula(any())).thenReturn(List.of(option));
        when(http.fetchCurriculum(any(), any())).thenReturn(observation(option, "Môn giả định", new BigDecimal("3")));
    }
    private AcademicPortalClient.StudentConnectionId connect(UUID owner, boolean curriculum) {
        try (var material = new PhenikaaSessionMaterial("Bearer synthetic-secret", "", "synthetic-key", "synthetic-learner",
                "profile", "schedule", null, "academic", curriculum ? "curriculum" : null)) { return portal.connect(owner, material); }
    }
    private CurriculumObservation observation(CurriculumOption selected, String name, BigDecimal credits) {
        return new CurriculumObservation(selected, List.of(
                new CurriculumObservation.CourseEntry("synthetic-course-1", "TEST1", name, credits, false),
                new CurriculumObservation.CourseEntry("synthetic-course-2", "TEST2", name, credits, true),
                new CurriculumObservation.CourseEntry("synthetic-course-3", "TEST3", name, credits, false)), List.of(
                new CurriculumObservation.Group("synthetic-group-R", "R", "Nhóm bắt buộc giả định", CurriculumObservation.Requirement.REQUIRED, null, null, List.of("synthetic-course-1")),
                new CurriculumObservation.Group("synthetic-group-E", "E", "Nhóm tự chọn giả định", CurriculumObservation.Requirement.ELECTIVE, new BigDecimal("3"), 1, List.of("synthetic-course-2"))));
    }
    private int count(String table) { return jdbc.queryForObject("select count(*) from " + table + " where profile_id = ?", Integer.class, profile); }
    private List<UUID> ids(String table) { return jdbc.queryForList("select id from " + table + " where profile_id = ? order by id", UUID.class, profile); }

    @Test void firstAndRepeatedImportRetainIdsAndDoNotSelectUnverifiedCurriculumOrImportPrerequisites() {
        UUID first = imports.importCurriculum(user, option);
        var courses = ids("course"); var groups = ids("curriculum_group"); var members = ids("curriculum_course");
        assertThat(imports.importCurriculum(user, option)).isEqualTo(first);
        assertThat(ids("course")).isEqualTo(courses).hasSize(3);
        assertThat(ids("curriculum_group")).isEqualTo(groups).hasSize(2);
        assertThat(ids("curriculum_course")).isEqualTo(members).hasSize(2);
        assertThat(count("course_prerequisite")).isZero();
        assertThat(count("phenikaa_course_mapping")).isEqualTo(3);
        assertThat(jdbc.queryForObject("select curriculum_id from student_profile where id = ?", UUID.class, profile)).isNull();
        assertThat(jdbc.queryForObject("select revision from curriculum where id = ?", String.class, first)).isNull();
    }
    @Test void updatesConfirmedNamesAndGroupRequirementsWithoutChangingUuidsOrClearingOptionalMetadata() {
        UUID id = imports.importCurriculum(user, option);
        var changed = new CurriculumOption(option.sourceId(), option.code(), "Tên chương trình cập nhật", null, option.requiredCredits());
        var input = observation(changed, "Tên môn cập nhật", new BigDecimal("3"));
        var elective = input.groups().getLast();
        input = new CurriculumObservation(changed, input.courses(), List.of(input.groups().getFirst(),
                new CurriculumObservation.Group(elective.sourceId(), elective.code(), "Nhóm cập nhật", elective.requirement(), new BigDecimal("2"), null, elective.courseSourceIds())));
        when(http.fetchCurriculum(any(), any())).thenReturn(input);
        assertThat(imports.importCurriculum(user, option)).isEqualTo(id);
        assertThat(jdbc.queryForList("select name from course where profile_id = ?", String.class, profile)).containsOnly("Tên môn cập nhật");
        assertThat(jdbc.queryForObject("select cohort from curriculum where id = ?", String.class, id)).isEqualTo("TEST");
        assertThat(jdbc.queryForObject("select minimum_course_count from curriculum_group where profile_id = ? and code = 'E'", Integer.class, profile)).isEqualTo(1);
    }
    @Test void separateSourceCurriculaReuseSameCourseCatalog() {
        var first = imports.importCurriculum(user, option);
        var secondOption = new CurriculumOption("synthetic-curriculum-version-2", option.code(), option.name(), "TEST2", option.requiredCredits());
        when(http.fetchCurriculum(any(), any())).thenReturn(observation(secondOption, "Môn giả định", new BigDecimal("3")));
        assertThat(imports.importCurriculum(user, secondOption)).isNotEqualTo(first);
        assertThat(count("curriculum")).isEqualTo(2); assertThat(count("course")).isEqualTo(3);
        assertThat(count("curriculum_group")).isEqualTo(4); assertThat(count("curriculum_course")).isEqualTo(4);
    }
    @Test void concurrentFirstImportsDoNotDuplicateAnything() throws Exception {
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var gate = new CountDownLatch(1);
            Callable<UUID> task = () -> { gate.await(); return imports.importCurriculum(user, option); };
            var a = pool.submit(task); var b = pool.submit(task); gate.countDown();
            assertThat(a.get(15, TimeUnit.SECONDS)).isEqualTo(b.get(15, TimeUnit.SECONDS));
        }
        assertThat(count("curriculum")).isEqualTo(1); assertThat(count("course")).isEqualTo(3);
        assertThat(count("curriculum_course")).isEqualTo(2); assertThat(count("curriculum_group")).isEqualTo(2);
    }
    @Test void unknownEmptyResponsePreservesRowsAndExistingProfileSelection() {
        UUID id = imports.importCurriculum(user, option);
        jdbc.update("update student_profile set curriculum_id = ? where id = ?", id, profile);
        when(http.fetchCurriculum(any(), any())).thenReturn(new CurriculumObservation(option, List.of(), List.of()));
        imports.importCurriculum(user, option);
        assertThat(count("course")).isEqualTo(3); assertThat(count("curriculum_course")).isEqualTo(2);
        assertThat(jdbc.queryForObject("select curriculum_id from student_profile where id = ?", UUID.class, profile)).isEqualTo(id);
    }
    @Test void refreshingSelectedCurriculumKeepsItsUuidAndImportingAnotherDoesNotSelectIt() {
        UUID selected = imports.importCurriculum(user, option);
        jdbc.update("update student_profile set curriculum_id = ? where id = ?", selected, profile);
        var updated = new CurriculumOption(option.sourceId(), option.code(), "Tên chương trình cập nhật", "TEST2", new BigDecimal("7"));
        when(http.fetchCurriculum(any(), any())).thenReturn(observation(updated, "Môn giả định", new BigDecimal("3")));
        assertThat(imports.importCurriculum(user, updated)).isEqualTo(selected);
        assertThat(jdbc.queryForObject("select name from curriculum where id = ?", String.class, selected)).isEqualTo("Tên chương trình cập nhật");
        assertThat(jdbc.queryForObject("select curriculum_id from student_profile where id = ?", UUID.class, profile)).isEqualTo(selected);
        var another = new CurriculumOption("synthetic-other-curriculum", "CURR-B", "Chương trình kiểm thử B", null, new BigDecimal("7"));
        when(http.fetchCurriculum(any(), any())).thenReturn(observation(another, "Môn giả định", new BigDecimal("3")));
        imports.importCurriculum(user, another);
        assertThat(jdbc.queryForObject("select curriculum_id from student_profile where id = ?", UUID.class, profile)).isEqualTo(selected);
    }
    @Test void partialUnknownResponseDoesNotRemoveAbsentMemberships() {
        imports.importCurriculum(user, option);
        var full = observation(option, "Môn giả định", new BigDecimal("3"));
        when(http.fetchCurriculum(any(), any())).thenReturn(new CurriculumObservation(option, full.courses().subList(0, 1), full.groups().subList(0, 1)));
        imports.importCurriculum(user, option); assertThat(count("curriculum_course")).isEqualTo(2); assertThat(count("course")).isEqualTo(3);
    }
    @Test void crossUserAccessAndDisabledAccountFailBeforeHttp() {
        UUID other = users.save(new AppUser(UUID.randomUUID() + "@example.test", "!synthetic", null)).getId();
        clearInvocations(http);
        assertThatThrownBy(() -> portal.fetchCurricula(other, connection)).hasMessage("CONNECTION_UNAVAILABLE");
        assertThatThrownBy(() -> portal.fetchCurriculum(other, connection, option)).hasMessage("CONNECTION_UNAVAILABLE");
        assertThatThrownBy(() -> portal.fetchCourseRelations(other, connection, option, "synthetic-course-1")).hasMessage("CONNECTION_UNAVAILABLE");
        assertThatThrownBy(() -> imports.importCurriculum(other, option)).hasMessage("CONNECTION_UNAVAILABLE"); verifyNoInteractions(http);
        jdbc.update("update app_user set account_status = 'DISABLED' where id = ?", user);
        assertThatThrownBy(() -> imports.importCurriculum(user, option)).hasMessage("CONNECTION_UNAVAILABLE"); verifyNoInteractions(http);
    }
    @Test void importsOfDifferentUsersCannotModifyEachOthersCatalogAndMappingsEnforceOwnership() {
        UUID firstCurriculum = imports.importCurriculum(user, option); var oldCourses = ids("course");
        UUID other = users.save(new AppUser(UUID.randomUUID() + "@example.test", "!synthetic", null)).getId();
        connect(other, true); UUID otherProfile = profiles.importCurrentProfile(other);
        when(http.fetchCurriculum(any(), any())).thenReturn(observation(option, "Môn của hồ sơ khác", new BigDecimal("3")));
        imports.importCurriculum(other, option);
        assertThat(ids("course")).isEqualTo(oldCourses);
        assertThat(jdbc.queryForList("select name from course where profile_id = ?", String.class, profile)).containsOnly("Môn giả định");
        assertThatThrownBy(() -> jdbc.update("update phenikaa_course_mapping set profile_id = ? where profile_id = ?", otherProfile, profile)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update phenikaa_curriculum_mapping set profile_id = ? where curriculum_id = ?", otherProfile, firstCurriculum)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update phenikaa_curriculum_group_mapping set profile_id = ? where profile_id = ?", otherProfile, profile)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update curriculum_course set requirement = 'ELECTIVE' where profile_id = ? and requirement = 'REQUIRED'", profile)).isInstanceOf(DataIntegrityViolationException.class);
    }
    @Test void missingCurriculumCapabilityDoesNotInvalidateExistingConnection() {
        connect(user, false); clearInvocations(http);
        assertThatThrownBy(() -> imports.importCurriculum(user, option)).hasMessage("CONNECTION_UNAVAILABLE");
        verifyNoInteractions(http); assertThat(portal.status(user).reconnectionRequired()).isFalse();
    }
    @ParameterizedTest @EnumSource(PhenikaaClientException.Code.class)
    void sourceFailurePreservesExistingCurriculumAndOnlyExpiryRequiresReconnect(PhenikaaClientException.Code code) {
        var id = imports.importCurriculum(user, option);
        when(http.fetchCurriculum(any(), any())).thenThrow(new PhenikaaClientException(code));
        assertThatThrownBy(() -> imports.importCurriculum(user, option)).hasMessage(code.name()).hasNoCause();
        assertThat(ids("curriculum")).containsExactly(id); assertThat(count("course")).isEqualTo(3);
        assertThat(portal.status(user).reconnectionRequired()).isEqualTo(code == PhenikaaClientException.Code.SESSION_EXPIRED);
    }
    @Test void creditConflictRollsBackCatalogButKeepsSuccessfulSourceReadMetadata() {
        imports.importCurriculum(user, option); var success = portal.status(user).lastSuccessfulAccessAt();
        when(http.fetchCurriculum(any(), any())).thenReturn(observation(option, "Tên không được lưu", new BigDecimal("4")));
        assertThatThrownBy(() -> imports.importCurriculum(user, option)).hasMessage("CREDIT_CONFLICT");
        assertThat(jdbc.queryForList("select credits from course where profile_id = ?", BigDecimal.class, profile)).allMatch(c -> c.compareTo(new BigDecimal("3")) == 0);
        assertThat(portal.status(user).lastSuccessfulAccessAt()).isAfter(success);
    }
    @Test void sameCodeDifferentSourceIdDoesNotMergeAndRollsBackEarlierUpdates() {
        imports.importCurriculum(user, option);
        var input = observation(option, "Tên không được lưu", new BigDecimal("3")); var list = new ArrayList<>(input.courses());
        list.set(2, new CurriculumObservation.CourseEntry("other-source", "TEST3", "Không được lưu", new BigDecimal("3"), false));
        when(http.fetchCurriculum(any(), any())).thenReturn(new CurriculumObservation(option, list, input.groups()));
        assertThatThrownBy(() -> imports.importCurriculum(user, option)).hasMessage("IDENTITY_CONFLICT");
        assertThat(jdbc.queryForList("select name from course where profile_id = ?", String.class, profile)).containsOnly("Môn giả định");
    }
    @Test void rejectsMalformedObservationBeforeCreatingCurriculum() {
        var input = observation(option, "Môn giả định", new BigDecimal("3"));
        when(http.fetchCurriculum(any(), any())).thenReturn(new CurriculumObservation(option, List.of(input.courses().getFirst(), input.courses().getFirst()), input.groups()));
        assertThatThrownBy(() -> imports.importCurriculum(user, option)).hasMessage("INVALID_OBSERVATION");
        assertThat(count("curriculum")).isZero(); assertThat(count("course")).isZero();
    }

    @Test void connectedUserNeedsOwnProfileBeforeCurriculumImport() {
        UUID other = users.save(new AppUser(UUID.randomUUID() + "@example.test", "!synthetic", null)).getId();
        connect(other, true); clearInvocations(http);
        assertThatThrownBy(() -> imports.importCurriculum(other, option)).hasMessage("PROFILE_REQUIRED");
        verifyNoInteractions(http);
    }

    @Test void academicSourceCourseIdentityResolvesToExistingCatalogWithoutCreatingStudentAttempts() {
        imports.importCurriculum(user, option);
        var entry = new AcademicRecordObservation.Entry("synthetic-enrollment", "synthetic-section", "synthetic-course-1",
                "TEST1", "Môn giả định", new BigDecimal("3"), "synthetic-period", 2026, 1, 1, List.of(), null);
        UUID catalogId = jdbc.queryForObject("select course_id from phenikaa_course_mapping where profile_id = ? and source_course_id = ?",
                UUID.class, profile, entry.sourceCourseId());
        assertThat(jdbc.queryForObject("select code from course where id = ? and profile_id = ?", String.class, catalogId, profile)).isEqualTo(entry.courseCode());
        assertThat(jdbc.queryForObject("select credits from course where id = ? and profile_id = ?", BigDecimal.class, catalogId, profile)).isEqualByComparingTo(entry.courseCredits());
        assertThat(count("student_course")).isZero(); assertThat(count("academic_result")).isZero();
        assertThat(count("course")).isEqualTo(3);
    }

    @Test void changingKnownMembershipRollsBackRatherThanMovingOrDuplicatingCourse() {
        imports.importCurriculum(user, option);
        var input = observation(option, "Tên không được lưu", new BigDecimal("3"));
        var moved = new CurriculumObservation.Group("synthetic-group-new", "NEW", "Nhóm mới giả định",
                CurriculumObservation.Requirement.REQUIRED, null, null, List.of("synthetic-course-1"));
        when(http.fetchCurriculum(any(), any())).thenReturn(new CurriculumObservation(option, input.courses(), List.of(moved, input.groups().getLast())));
        assertThatThrownBy(() -> imports.importCurriculum(user, option)).hasMessage("GROUP_CONFLICT");
        assertThat(count("curriculum_group")).isEqualTo(2);
        assertThat(jdbc.queryForList("select name from course where profile_id = ?", String.class, profile)).containsOnly("Môn giả định");
    }
}
