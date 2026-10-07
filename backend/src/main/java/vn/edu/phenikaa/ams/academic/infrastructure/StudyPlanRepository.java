package vn.edu.phenikaa.ams.academic.infrastructure;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;
import vn.edu.phenikaa.ams.academic.domain.CurriculumCourse.Requirement;
import vn.edu.phenikaa.ams.academic.domain.StudyPlanCourse;

@Repository
public class StudyPlanRepository {
    public record Selected(UUID profileId, UUID curriculumId, String code, String name) {}
    public record ProfileSelection(UUID profileId, UUID curriculumId) {}
    public record PlannedCourse(UUID courseId, String code, String name, BigDecimal credits,
                                Requirement requirement, String groupName, int plannedTerm) {}
    public record ScenarioTotals(int scenarioNo, long courseCount, long termCount, BigDecimal plannedCredits) {}
    public record Assignment(UUID courseId, int plannedTerm) {}

    private final NamedParameterJdbcTemplate jdbc;
    public StudyPlanRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<Selected> selected(UUID userId) {
        return jdbc.query("""
                select p.id profile_id, c.id curriculum_id, c.code, c.name
                from student_profile p
                join curriculum c on c.profile_id = p.id and c.id = p.curriculum_id
                where p.user_id = :userId
                """, new MapSqlParameterSource("userId", userId),
                (r, n) -> new Selected(r.getObject("profile_id", UUID.class),
                        r.getObject("curriculum_id", UUID.class), r.getString("code"), r.getString("name")))
                .stream().findFirst();
    }

    public List<PlannedCourse> courses(UUID userId, UUID curriculumId, int scenarioNo, int limit) {
        return jdbc.query("""
                select s.course_id, c.code, c.name, m.credits, m.requirement, g.name group_name, s.planned_term
                from student_profile p
                join study_plan_course s on s.profile_id = p.id and s.curriculum_id = p.curriculum_id
                    and s.scenario_no = :scenarioNo
                join curriculum_course m on m.profile_id = s.profile_id and m.curriculum_id = s.curriculum_id
                    and m.course_id = s.course_id
                join course c on c.profile_id = m.profile_id and c.id = m.course_id
                left join curriculum_group g on g.profile_id = m.profile_id and g.curriculum_id = m.curriculum_id
                    and g.id = m.group_id
                where p.user_id = :userId and p.curriculum_id = :curriculumId
                order by s.planned_term, c.code, c.id
                limit :limit
                """, new MapSqlParameterSource("userId", userId).addValue("curriculumId", curriculumId)
                        .addValue("scenarioNo", scenarioNo)
                        .addValue("limit", limit),
                (r, n) -> new PlannedCourse(r.getObject("course_id", UUID.class), r.getString("code"),
                        r.getString("name"), r.getBigDecimal("credits"),
                        Requirement.valueOf(r.getString("requirement")), r.getString("group_name"),
                        r.getInt("planned_term")));
    }

    public List<ScenarioTotals> totals(UUID userId, UUID curriculumId) {
        return jdbc.query("""
                select s.scenario_no, count(*) course_count, count(distinct s.planned_term) term_count,
                    coalesce(sum(m.credits), 0) planned_credits
                from student_profile p
                join study_plan_course s on s.profile_id = p.id and s.curriculum_id = p.curriculum_id
                join curriculum_course m on m.profile_id = s.profile_id and m.curriculum_id = s.curriculum_id
                    and m.course_id = s.course_id
                where p.user_id = :userId and p.curriculum_id = :curriculumId
                group by s.scenario_no order by s.scenario_no
                """, new MapSqlParameterSource("userId", userId).addValue("curriculumId", curriculumId),
                (r, n) -> new ScenarioTotals(r.getInt("scenario_no"), r.getLong("course_count"),
                        r.getLong("term_count"), r.getBigDecimal("planned_credits")));
    }

    public Optional<ProfileSelection> lockProfile(UUID userId) {
        return jdbc.query("""
                select id, curriculum_id from student_profile where user_id = :userId for update
                """, new MapSqlParameterSource("userId", userId),
                (r, n) -> new ProfileSelection(r.getObject("id", UUID.class),
                        r.getObject("curriculum_id", UUID.class))).stream().findFirst();
    }

    public boolean hasCourse(UUID profileId, UUID curriculumId, UUID courseId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists(select 1 from curriculum_course
                    where profile_id = :profileId and curriculum_id = :curriculumId and course_id = :courseId)
                """, new MapSqlParameterSource("profileId", profileId).addValue("curriculumId", curriculumId)
                        .addValue("courseId", courseId), Boolean.class));
    }

    public Optional<StudyPlanCourse> assignment(UUID profileId, UUID curriculumId, int scenarioNo, UUID courseId) {
        return jdbc.query("""
                select id, planned_term, created_at, updated_at from study_plan_course
                where profile_id = :profileId and curriculum_id = :curriculumId
                    and scenario_no = :scenarioNo and course_id = :courseId
                """, key(profileId, curriculumId, scenarioNo, courseId),
                (r, n) -> new StudyPlanCourse(r.getObject("id", UUID.class), profileId, curriculumId, scenarioNo, courseId,
                        r.getInt("planned_term"), r.getTimestamp("created_at").toInstant(),
                        r.getTimestamp("updated_at").toInstant())).stream().findFirst();
    }

    public long count(UUID profileId, UUID curriculumId, int scenarioNo) {
        return jdbc.queryForObject("""
                select count(*) from study_plan_course where profile_id = :profileId and curriculum_id = :curriculumId
                    and scenario_no = :scenarioNo
                """, scope(profileId, curriculumId, scenarioNo), Long.class);
    }

    public List<Assignment> assignments(UUID profileId, UUID curriculumId, int scenarioNo, int limit) {
        return jdbc.query("""
                select course_id, planned_term from study_plan_course
                where profile_id = :profileId and curriculum_id = :curriculumId and scenario_no = :scenarioNo
                order by course_id limit :limit
                """, scope(profileId, curriculumId, scenarioNo).addValue("limit", limit),
                (r, n) -> new Assignment(r.getObject("course_id", UUID.class), r.getInt("planned_term")));
    }

    public void upsert(StudyPlanCourse course) {
        jdbc.update("""
                insert into study_plan_course(id, profile_id, curriculum_id, scenario_no, course_id, planned_term, created_at, updated_at)
                values (:id, :profileId, :curriculumId, :scenarioNo, :courseId, :plannedTerm, :createdAt, :updatedAt)
                on conflict on constraint study_plan_course_unique do update
                set planned_term = excluded.planned_term, updated_at = excluded.updated_at
                where study_plan_course.planned_term <> excluded.planned_term
                """, key(course.profileId(), course.curriculumId(), course.scenarioNo(), course.courseId())
                        .addValue("id", course.id()).addValue("plannedTerm", course.plannedTerm())
                        .addValue("createdAt", Timestamp.from(course.createdAt()))
                        .addValue("updatedAt", Timestamp.from(course.updatedAt())));
    }

    public void insertCopies(List<StudyPlanCourse> copies) {
        if (copies.isEmpty()) return;
        SqlParameterSource[] rows = copies.stream().map(course -> key(course.profileId(), course.curriculumId(),
                course.scenarioNo(), course.courseId()).addValue("id", course.id())
                .addValue("plannedTerm", course.plannedTerm()).addValue("createdAt", Timestamp.from(course.createdAt()))
                .addValue("updatedAt", Timestamp.from(course.updatedAt()))).toArray(SqlParameterSource[]::new);
        jdbc.batchUpdate("""
                insert into study_plan_course(id, profile_id, curriculum_id, scenario_no, course_id, planned_term, created_at, updated_at)
                values (:id, :profileId, :curriculumId, :scenarioNo, :courseId, :plannedTerm, :createdAt, :updatedAt)
                """, rows);
    }

    public void remove(UUID profileId, UUID curriculumId, int scenarioNo, UUID courseId) {
        jdbc.update("""
                delete from study_plan_course
                where profile_id = :profileId and curriculum_id = :curriculumId
                    and scenario_no = :scenarioNo and course_id = :courseId
                """, key(profileId, curriculumId, scenarioNo, courseId));
    }

    public void clear(UUID profileId, UUID curriculumId, int scenarioNo) {
        jdbc.update("""
                delete from study_plan_course
                where profile_id = :profileId and curriculum_id = :curriculumId and scenario_no = :scenarioNo
                """, scope(profileId, curriculumId, scenarioNo));
    }

    private static MapSqlParameterSource scope(UUID profileId, UUID curriculumId, int scenarioNo) {
        return new MapSqlParameterSource("profileId", profileId).addValue("curriculumId", curriculumId)
                .addValue("scenarioNo", scenarioNo);
    }

    private static MapSqlParameterSource key(UUID profileId, UUID curriculumId, int scenarioNo, UUID courseId) {
        return scope(profileId, curriculumId, scenarioNo)
                .addValue("courseId", courseId);
    }
}
