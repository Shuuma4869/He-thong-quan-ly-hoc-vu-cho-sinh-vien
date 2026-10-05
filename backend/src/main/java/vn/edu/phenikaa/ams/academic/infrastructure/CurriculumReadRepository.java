package vn.edu.phenikaa.ams.academic.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.edu.phenikaa.ams.academic.application.CatalogPageRequest;
import vn.edu.phenikaa.ams.academic.application.CurriculumQueryService.*;
import vn.edu.phenikaa.ams.academic.domain.CurriculumCourse.Requirement;

@Repository
public class CurriculumReadRepository {
    private static final String CURRICULA = """
            select c.id, c.code, c.name, c.cohort, c.revision, c.minimum_credits,
              (select count(*) from curriculum_course m where m.profile_id = c.profile_id and m.curriculum_id = c.id) course_count,
              (select count(*) from curriculum_group g where g.profile_id = c.profile_id and g.curriculum_id = c.id) group_count
            from curriculum c where c.profile_id = :profile
            """;
    private final NamedParameterJdbcTemplate jdbc;
    public CurriculumReadRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<CurriculumView> curricula(UUID profile, CatalogPageRequest page) {
        return jdbc.query(CURRICULA + window(page), params(profile, page), (r, n) -> curriculum(r));
    }

    public Optional<CurriculumView> curriculum(UUID profile, UUID id) {
        return jdbc.query(CURRICULA + " and c.id = :id", new MapSqlParameterSource("profile", profile).addValue("id", id),
                (r, n) -> curriculum(r)).stream().findFirst();
    }

    public List<GroupView> groups(UUID profile, UUID curriculum, CatalogPageRequest page) {
        return jdbc.query("""
                select c.id, c.code, c.name, c.requirement, c.minimum_credits, c.minimum_course_count
                from curriculum_group c where c.profile_id = :profile and c.curriculum_id = :curriculum
                """ + window(page), params(profile, page).addValue("curriculum", curriculum),
                (r, n) -> new GroupView(uuid(r, "id"), r.getString("code"), r.getString("name"),
                        Requirement.valueOf(r.getString("requirement")), r.getBigDecimal("minimum_credits"),
                        r.getObject("minimum_course_count", Integer.class)));
    }

    public List<CurriculumCourseView> courses(UUID profile, UUID curriculum, CatalogPageRequest page) {
        return jdbc.query("""
                select m.id, c.id course_id, c.code, c.name, m.credits, m.requirement, m.group_id, g.name group_name, m.recommended_term
                from course c join curriculum_course m on m.profile_id = c.profile_id and m.course_id = c.id
                left join curriculum_group g on g.profile_id = m.profile_id and g.curriculum_id = m.curriculum_id and g.id = m.group_id
                where c.profile_id = :profile and m.curriculum_id = :curriculum
                """ + search(page) + window(page), params(profile, page).addValue("curriculum", curriculum),
                (r, n) -> new CurriculumCourseView(uuid(r, "id"), uuid(r, "course_id"), r.getString("code"), r.getString("name"),
                        r.getBigDecimal("credits"), Requirement.valueOf(r.getString("requirement")),
                        uuid(r, "group_id"), r.getString("group_name"), r.getObject("recommended_term", Integer.class)));
    }

    public List<CatalogCourseView> catalog(UUID profile, CatalogPageRequest page) {
        return jdbc.query("""
                select c.id, c.code, c.name, c.credits,
                  exists(select 1 from curriculum_course m where m.profile_id = c.profile_id and m.course_id = c.id) curriculum_linked
                from course c where c.profile_id = :profile
                """ + search(page) + window(page), params(profile, page),
                (r, n) -> new CatalogCourseView(uuid(r, "id"), r.getString("code"), r.getString("name"),
                        r.getBigDecimal("credits"), r.getBoolean("curriculum_linked")));
    }

    private static String search(CatalogPageRequest page) {
        return page.search().isEmpty() ? "" : " and (c.code ilike :search escape '!' or c.name ilike :search escape '!')";
    }
    private static String window(CatalogPageRequest page) {
        return (page.afterId() == null ? "" : " and (c.code, c.id) > (:afterCode, :afterId)")
                + " order by c.code asc, c.id asc limit :size";
    }
    private static MapSqlParameterSource params(UUID profile, CatalogPageRequest page) {
        return new MapSqlParameterSource("profile", profile).addValue("size", page.limit() + 1)
                .addValue("search", page.searchPattern()).addValue("afterCode", page.afterCode()).addValue("afterId", page.afterId());
    }
    private static UUID uuid(ResultSet r, String name) throws SQLException { return r.getObject(name, UUID.class); }
    private static CurriculumView curriculum(ResultSet r) throws SQLException {
        return new CurriculumView(uuid(r, "id"), r.getString("code"), r.getString("name"), r.getString("cohort"),
                r.getString("revision"), r.getBigDecimal("minimum_credits"), r.getLong("course_count"), r.getLong("group_count"));
    }
}
