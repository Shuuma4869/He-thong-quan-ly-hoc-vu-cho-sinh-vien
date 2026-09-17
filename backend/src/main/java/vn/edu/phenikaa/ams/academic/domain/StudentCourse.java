package vn.edu.phenikaa.ams.academic.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "student_course")
public class StudentCourse extends AcademicEntity {
    @Column(nullable = false, updatable = false)
    private UUID courseId;
    @Column(nullable = false, updatable = false)
    private UUID semesterId;
    private UUID sectionId;
    @Column(nullable = false, updatable = false)
    private int attemptNumber;
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal creditsAttempted;

    protected StudentCourse() {}
    public StudentCourse(Course course, Semester semester, ClassSection section, int attemptNumber, BigDecimal creditsAttempted) {
        super(course.getProfileId());
        AcademicValues.sameProfile(getProfileId(), semester);
        if (attemptNumber <= 0) throw new IllegalArgumentException("Attempt number must be positive");
        if (section != null) {
            AcademicValues.sameProfile(getProfileId(), section);
            if (!section.getCourseId().equals(course.getId()) || !section.getSemesterId().equals(semester.getId()))
                throw new IllegalArgumentException("Section must match the course and semester");
        }
        this.courseId = course.getId();
        this.semesterId = semester.getId();
        this.sectionId = section == null ? null : section.getId();
        this.attemptNumber = attemptNumber;
        this.creditsAttempted = AcademicValues.decimal(creditsAttempted, 5);
    }
    public UUID getCourseId() { return courseId; }
    public UUID getSemesterId() { return semesterId; }
    public UUID getSectionId() { return sectionId; }
    public int getAttemptNumber() { return attemptNumber; }
    public BigDecimal getCreditsAttempted() { return creditsAttempted; }
}
