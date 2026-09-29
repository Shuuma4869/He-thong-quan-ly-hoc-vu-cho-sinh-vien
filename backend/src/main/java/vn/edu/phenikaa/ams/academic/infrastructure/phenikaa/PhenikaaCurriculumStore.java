package vn.edu.phenikaa.ams.academic.infrastructure.phenikaa;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.edu.phenikaa.ams.academic.application.CurriculumImportException;
import vn.edu.phenikaa.ams.academic.application.port.CurriculumObservation;
import vn.edu.phenikaa.ams.academic.domain.*;
import static vn.edu.phenikaa.ams.academic.application.CurriculumImportException.Code.*;

public class PhenikaaCurriculumStore {
    private final EntityManager entities;
    private final JdbcTemplate jdbc;
    public PhenikaaCurriculumStore(EntityManager entities, JdbcTemplate jdbc) { this.entities = entities; this.jdbc = jdbc; }

    public UUID upsert(UUID profileId, CurriculumObservation input) {
        validate(input);
        var option = input.curriculum();
        var existingId = mapped("phenikaa_curriculum_mapping", "source_curriculum_id", "curriculum_id", profileId, option.sourceId());
        Curriculum curriculum;
        if (existingId == null) {
            curriculum = new Curriculum(profileId, option.code(), null, option.name(), option.cohort(), option.requiredCredits());
            entities.persist(curriculum); entities.flush();
            insertMapping("phenikaa_curriculum_mapping", "source_curriculum_id", "curriculum_id", profileId, option.sourceId(), curriculum.getId());
        } else {
            curriculum = entities.find(Curriculum.class, existingId);
            if (!curriculum.getProfileId().equals(profileId) || !curriculum.getCode().equals(option.code())) throw failure(IDENTITY_CONFLICT);
            curriculum.updateMetadata(option.name(), option.cohort(), option.requiredCredits());
            touch("phenikaa_curriculum_mapping", profileId, existingId, "curriculum_id");
        }
        var courses = new HashMap<String, Course>();
        for (var entry : input.courses()) courses.put(entry.sourceId(), upsertCourse(profileId, entry));
        for (var groupInput : input.groups()) {
            var group = upsertGroup(curriculum, groupInput);
            for (String sourceId : groupInput.courseSourceIds()) {
                var course = courses.get(sourceId);
                var found = entities.createQuery("select c from CurriculumCourse c where c.profileId = :profile and c.curriculumId = :curriculum and c.courseId = :course", CurriculumCourse.class)
                        .setParameter("profile", profileId).setParameter("curriculum", curriculum.getId()).setParameter("course", course.getId()).getResultList();
                if (found.isEmpty()) {
                    entities.persist(new CurriculumCourse(curriculum, course, group.getRequirement(), group, course.getCredits(), null));
                } else {
                    var item = found.getFirst();
                    if (!Objects.equals(item.getGroupId(), group.getId()) || item.getRequirement() != group.getRequirement()) throw failure(GROUP_CONFLICT);
                    if (item.getCredits().compareTo(course.getCredits()) != 0) throw failure(CREDIT_CONFLICT);
                }
            }
        }
        entities.flush();
        return curriculum.getId();
    }

    private Course upsertCourse(UUID profile, CurriculumObservation.CourseEntry entry) {
        UUID id = mapped("phenikaa_course_mapping", "source_course_id", "course_id", profile, entry.sourceId());
        if (id == null) {
            if (!entities.createQuery("select c.id from Course c where c.profileId = :profile and c.code = :code", UUID.class)
                    .setParameter("profile", profile).setParameter("code", entry.code()).getResultList().isEmpty()) throw failure(IDENTITY_CONFLICT);
            var course = new Course(profile, entry.code(), entry.name(), entry.credits());
            entities.persist(course); entities.flush();
            insertMapping("phenikaa_course_mapping", "source_course_id", "course_id", profile, entry.sourceId(), course.getId());
            return course;
        }
        var course = entities.find(Course.class, id);
        if (!course.getProfileId().equals(profile) || !course.getCode().equals(entry.code())) throw failure(IDENTITY_CONFLICT);
        if (course.getCredits().compareTo(entry.credits()) != 0) throw failure(CREDIT_CONFLICT);
        course.updateName(entry.name());
        touch("phenikaa_course_mapping", profile, id, "course_id");
        return course;
    }

    private CurriculumGroup upsertGroup(Curriculum curriculum, CurriculumObservation.Group input) {
        var ids = jdbc.queryForList("select group_id from phenikaa_curriculum_group_mapping where profile_id = ? and curriculum_id = ? and source_group_id = ?",
                UUID.class, curriculum.getProfileId(), curriculum.getId(), input.sourceId());
        var requirement = CurriculumCourse.Requirement.valueOf(input.requirement().name());
        if (ids.isEmpty()) {
            if (!entities.createQuery("select g.id from CurriculumGroup g where g.profileId = :profile and g.curriculumId = :curriculum and g.code = :code", UUID.class)
                    .setParameter("profile", curriculum.getProfileId()).setParameter("curriculum", curriculum.getId()).setParameter("code", input.code()).getResultList().isEmpty()) throw failure(IDENTITY_CONFLICT);
            var group = new CurriculumGroup(curriculum, input.code(), input.name(), requirement, input.minimumCredits(), input.minimumCourseCount());
            entities.persist(group); entities.flush();
            Timestamp now = Timestamp.from(Instant.now());
            jdbc.update("insert into phenikaa_curriculum_group_mapping(id,profile_id,curriculum_id,source_group_id,group_id,first_seen_at,last_seen_at) values (?,?,?,?,?,?,?)",
                    UUID.randomUUID(), curriculum.getProfileId(), curriculum.getId(), input.sourceId(), group.getId(), now, now);
            return group;
        }
        var group = entities.find(CurriculumGroup.class, ids.getFirst());
        if (!group.getProfileId().equals(curriculum.getProfileId()) || !group.getCurriculumId().equals(curriculum.getId())
                || !group.getCode().equals(input.code()) || group.getRequirement() != requirement) throw failure(IDENTITY_CONFLICT);
        group.updateRequirements(input.name(), input.minimumCredits(), input.minimumCourseCount());
        touch("phenikaa_curriculum_group_mapping", curriculum.getProfileId(), group.getId(), "group_id");
        return group;
    }

    // Identifiers below are private call-site constants, never request parameters.
    private UUID mapped(String table, String sourceColumn, String entityColumn, UUID profile, String source) {
        var result = jdbc.queryForList("select " + entityColumn + " from " + table + " where profile_id = ? and " + sourceColumn + " = ?", UUID.class, profile, source);
        return result.isEmpty() ? null : result.getFirst();
    }
    private void insertMapping(String table, String sourceColumn, String entityColumn, UUID profile, String source, UUID entity) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("insert into " + table + " (id,profile_id," + sourceColumn + "," + entityColumn + ",first_seen_at,last_seen_at) values (?,?,?,?,?,?)",
                UUID.randomUUID(), profile, source, entity, now, now);
    }
    private void touch(String table, UUID profile, UUID entity, String entityColumn) {
        jdbc.update("update " + table + " set last_seen_at = ? where profile_id = ? and " + entityColumn + " = ?", Timestamp.from(Instant.now()), profile, entity);
    }

    private static void validate(CurriculumObservation input) {
        var courses = new HashMap<String, CurriculumObservation.CourseEntry>(); var codes = new HashSet<String>();
        for (var c : input.courses()) {
            if (c.sourceId() == null || c.sourceId().isBlank() || c.sourceId().length() > 128 || courses.putIfAbsent(c.sourceId(), c) != null
                    || c.code() == null || !codes.add(c.code()) || !c.code().equals(c.code().trim().toUpperCase(Locale.ROOT))) throw failure(INVALID_OBSERVATION);
            try { new Course(UUID.randomUUID(), c.code(), c.name(), c.credits()); }
            catch (IllegalArgumentException | NullPointerException | ArithmeticException ex) { throw failure(INVALID_OBSERVATION); }
        }
        var groups = new HashSet<String>(); var groupCodes = new HashSet<String>(); var assigned = new HashSet<String>();
        for (var g : input.groups()) {
            if (g.sourceId() == null || g.sourceId().isBlank() || g.sourceId().length() > 128 || !groups.add(g.sourceId())
                    || g.code() == null || g.code().isBlank() || g.code().length() > 40 || !groupCodes.add(g.code())
                    || g.name() == null || g.name().isBlank() || g.name().length() > 200 || g.requirement() == null) throw failure(INVALID_OBSERVATION);
            BigDecimal total = BigDecimal.ZERO;
            for (var id : g.courseSourceIds()) {
                if (!courses.containsKey(id) || !assigned.add(id)) throw failure(INVALID_OBSERVATION);
                total = total.add(courses.get(id).credits());
            }
            if ((g.requirement() == CurriculumObservation.Requirement.ELECTIVE && g.minimumCredits() == null)
                    || (g.minimumCredits() != null && (g.minimumCredits().signum() < 0 || g.minimumCredits().compareTo(total) > 0))
                    || (g.minimumCourseCount() != null && (g.minimumCourseCount() < 0 || g.minimumCourseCount() > g.courseSourceIds().size()))) throw failure(INVALID_OBSERVATION);
        }
    }
    private static CurriculumImportException failure(CurriculumImportException.Code code) { return new CurriculumImportException(code); }
}
