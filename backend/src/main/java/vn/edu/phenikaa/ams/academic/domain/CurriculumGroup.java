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
    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal minimumCredits;

    protected CurriculumGroup() {}
    public CurriculumGroup(Curriculum curriculum, String code, String name, BigDecimal minimumCredits) {
        super(curriculum.getProfileId());
        this.curriculumId = curriculum.getId();
        this.code = AcademicValues.text(code, 40);
        this.name = AcademicValues.text(name, 200);
        this.minimumCredits = AcademicValues.decimal(minimumCredits, 6);
    }
    public UUID getCurriculumId() { return curriculumId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public BigDecimal getMinimumCredits() { return minimumCredits; }
}
