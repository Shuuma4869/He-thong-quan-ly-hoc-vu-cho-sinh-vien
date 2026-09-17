package vn.edu.phenikaa.ams.academic.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "semester")
public class Semester extends AcademicEntity {
    @Column(nullable = false, updatable = false)
    private int academicYearStart;
    @Column(nullable = false, updatable = false, length = 16)
    private String termCode;
    @Column(nullable = false, length = 160)
    private String name;
    private LocalDate startsOn;
    private LocalDate endsOn;

    protected Semester() {}
    public Semester(UUID profileId, Identifier identifier, String name, LocalDate startsOn, LocalDate endsOn) {
        super(profileId);
        if ((startsOn == null) != (endsOn == null) || (startsOn != null && endsOn.isBefore(startsOn)))
            throw new IllegalArgumentException("Invalid semester date range");
        this.academicYearStart = identifier.academicYearStart();
        this.termCode = identifier.termCode();
        this.name = AcademicValues.text(name, 160);
        this.startsOn = startsOn;
        this.endsOn = endsOn;
    }
    public Identifier getIdentifier() { return new Identifier(academicYearStart, termCode); }
    public String getName() { return name; }
    public LocalDate getStartsOn() { return startsOn; }
    public LocalDate getEndsOn() { return endsOn; }

    public record Identifier(int academicYearStart, String termCode) {
        public Identifier {
            if (academicYearStart < 1900 || academicYearStart > 9998 || termCode == null
                    || !termCode.matches("[A-Z0-9][A-Z0-9_-]{0,15}"))
                throw new IllegalArgumentException("Invalid academic year or term code");
        }
        public String canonicalCode() { return academicYearStart + "-" + (academicYearStart + 1) + ":" + termCode; }
    }
}
