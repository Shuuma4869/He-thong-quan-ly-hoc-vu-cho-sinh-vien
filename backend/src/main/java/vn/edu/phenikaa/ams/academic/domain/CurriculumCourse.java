package vn.edu.phenikaa.ams.academic.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "curriculum_course")
public class CurriculumCourse extends AcademicEntity {
    public enum Requirement { REQUIRED, ELECTIVE }

    @Column(nullable = false, updatable = false)
    private UUID curriculumId;
    @Column(nullable = false, updatable = false)
    private UUID courseId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Requirement requirement;
    private UUID groupId;
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal credits;
    private Integer recommendedTerm;

    protected CurriculumCourse() {}
    public CurriculumCourse(Curriculum curriculum, Course course, Requirement requirement, CurriculumGroup group,
                            BigDecimal credits, Integer recommendedTerm) {
        super(curriculum.getProfileId());
        AcademicValues.sameProfile(getProfileId(), course);
        this.requirement = Objects.requireNonNull(requirement);
        if ((requirement == Requirement.ELECTIVE) != (group != null))
            throw new IllegalArgumentException("Only elective courses require an elective group");
        if (group != null) {
            AcademicValues.sameProfile(getProfileId(), group);
            if (!group.getCurriculumId().equals(curriculum.getId()))
                throw new IllegalArgumentException("Elective group must belong to the curriculum");
        }
        if (recommendedTerm != null && recommendedTerm <= 0) throw new IllegalArgumentException("Invalid recommended term");
        this.curriculumId = curriculum.getId();
        this.courseId = course.getId();
        this.groupId = group == null ? null : group.getId();
        this.credits = AcademicValues.decimal(credits, 5);
        this.recommendedTerm = recommendedTerm;
    }
    public UUID getCurriculumId() { return curriculumId; }
    public UUID getCourseId() { return courseId; }
    public Requirement getRequirement() { return requirement; }
    public UUID getGroupId() { return groupId; }
    public BigDecimal getCredits() { return credits; }
    public Integer getRecommendedTerm() { return recommendedTerm; }
}
