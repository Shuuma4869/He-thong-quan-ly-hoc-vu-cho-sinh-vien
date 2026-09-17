package vn.edu.phenikaa.ams.academic.domain;

import jakarta.persistence.*;
import java.util.*;

@Entity
@Table(name = "course_prerequisite")
public class CoursePrerequisite extends AcademicEntity {
    public enum Kind { PREREQUISITE, COREQUISITE }

    @Column(nullable = false, updatable = false)
    private UUID curriculumId;
    @Column(nullable = false, updatable = false)
    private UUID courseId;
    @Column(nullable = false, updatable = false)
    private UUID prerequisiteCourseId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Kind kind;

    protected CoursePrerequisite() {}
    public CoursePrerequisite(CurriculumCourse course, CurriculumCourse prerequisite, Kind kind) {
        super(course.getProfileId());
        AcademicValues.sameProfile(getProfileId(), prerequisite);
        if (!course.getCurriculumId().equals(prerequisite.getCurriculumId()) || course.getCourseId().equals(prerequisite.getCourseId()))
            throw new IllegalArgumentException("Prerequisites must be distinct courses in the same curriculum");
        this.curriculumId = course.getCurriculumId();
        this.courseId = course.getCourseId();
        this.prerequisiteCourseId = prerequisite.getCourseId();
        this.kind = Objects.requireNonNull(kind);
    }
    public UUID getCurriculumId() { return curriculumId; }
    public UUID getCourseId() { return courseId; }
    public UUID getPrerequisiteCourseId() { return prerequisiteCourseId; }
    public Kind getKind() { return kind; }

    public static void validateAcyclic(Collection<CoursePrerequisite> prerequisites) {
        Map<UUID, Set<UUID>> remaining = new HashMap<>();
        UUID curriculum = null;
        for (var relation : prerequisites) {
            if (curriculum != null && !curriculum.equals(relation.curriculumId))
                throw new IllegalArgumentException("Validate one curriculum at a time");
            curriculum = relation.curriculumId;
            if (relation.kind != Kind.PREREQUISITE) continue;
            remaining.computeIfAbsent(relation.courseId, key -> new HashSet<>()).add(relation.prerequisiteCourseId);
            remaining.computeIfAbsent(relation.prerequisiteCourseId, key -> new HashSet<>());
        }
        while (!remaining.isEmpty()) {
            Set<UUID> roots = new HashSet<>();
            remaining.forEach((course, dependencies) -> { if (dependencies.isEmpty()) roots.add(course); });
            if (roots.isEmpty()) throw new IllegalArgumentException("Prerequisite cycle");
            roots.forEach(remaining::remove);
            remaining.values().forEach(dependencies -> dependencies.removeAll(roots));
        }
    }
}
