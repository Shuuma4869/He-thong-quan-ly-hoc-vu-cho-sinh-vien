package vn.edu.phenikaa.ams.academic.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "curriculum")
public class Curriculum extends AcademicEntity {
    @Column(nullable = false, length = 40)
    private String code;
    @Column(nullable = false, length = 40)
    private String revision;
    @Column(nullable = false, length = 240)
    private String name;
    @Column(length = 40)
    private String cohort;
    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal minimumCredits;

    protected Curriculum() {}
    public Curriculum(UUID profileId, String code, String revision, String name, String cohort, BigDecimal minimumCredits) {
        super(profileId);
        this.code = AcademicValues.text(code, 40);
        this.revision = AcademicValues.text(revision, 40);
        this.name = AcademicValues.text(name, 240);
        this.cohort = AcademicValues.optionalText(cohort, 40);
        this.minimumCredits = AcademicValues.decimal(minimumCredits, 6);
    }
    public String getCode() { return code; }
    public String getRevision() { return revision; }
    public String getName() { return name; }
    public String getCohort() { return cohort; }
    public BigDecimal getMinimumCredits() { return minimumCredits; }
}
