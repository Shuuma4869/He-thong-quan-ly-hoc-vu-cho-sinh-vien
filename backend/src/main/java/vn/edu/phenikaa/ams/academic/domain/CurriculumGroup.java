package vn.edu.phenikaa.ams.academic.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "curriculum_group")
public class CurriculumGroup extends AcademicEntity {
    @Column(nullable = false, updatable = false)
    private UUID curriculumId;
    @Column(nullable = false, length = 40)
    private String code;
    @Column(nullable = false, length = 200)
    private String name;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CurriculumCourse.Requirement requirement;
    @Column(precision = 6, scale = 2)
    private BigDecimal minimumCredits;
    private Integer minimumCourseCount;

    protected CurriculumGroup() {}
    public CurriculumGroup(Curriculum curriculum, String code, String name, BigDecimal minimumCredits) {
        this(curriculum, code, name, CurriculumCourse.Requirement.ELECTIVE, minimumCredits, null);
    }
    public CurriculumGroup(Curriculum curriculum, String code, String name, CurriculumCourse.Requirement requirement,
                           BigDecimal minimumCredits, Integer minimumCourseCount) {
        super(curriculum.getProfileId());
        this.curriculumId = curriculum.getId();
        this.code = AcademicValues.text(code, 40);
        this.requirement = java.util.Objects.requireNonNull(requirement);
        updateRequirements(name, minimumCredits, minimumCourseCount);
    }
    public void updateRequirements(String name, BigDecimal minimumCredits, Integer minimumCourseCount) {
        this.name = AcademicValues.text(name, 200);
        if (requirement == CurriculumCourse.Requirement.ELECTIVE && minimumCredits == null)
            throw new IllegalArgumentException("Elective credit requirement must be known");
        if (minimumCourseCount != null && minimumCourseCount < 0) throw new IllegalArgumentException("Invalid course requirement");
        this.minimumCredits = minimumCredits == null ? null : AcademicValues.decimal(minimumCredits, 6);
        if (minimumCourseCount != null) this.minimumCourseCount = minimumCourseCount;
    }
    public UUID getCurriculumId() { return curriculumId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public BigDecimal getMinimumCredits() { return minimumCredits; }
    public CurriculumCourse.Requirement getRequirement() { return requirement; }
    public Integer getMinimumCourseCount() { return minimumCourseCount; }
}
