package vn.edu.phenikaa.ams.academic.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "course")
public class Course extends AcademicEntity {
    @Column(nullable = false, updatable = false, length = 40)
    private String code;
    @Column(nullable = false, length = 240)
    private String name;
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal credits;

    protected Course() {}
    public Course(UUID profileId, String code, String name, BigDecimal credits) {
        super(profileId);
        this.code = AcademicValues.text(code.toUpperCase(Locale.ROOT), 40);
        this.name = AcademicValues.text(name, 240);
        this.credits = AcademicValues.decimal(credits, 5);
    }
    public String getCode() { return code; }
    public String getName() { return name; }
    public BigDecimal getCredits() { return credits; }
}
