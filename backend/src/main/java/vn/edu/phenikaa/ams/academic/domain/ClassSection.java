package vn.edu.phenikaa.ams.academic.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "class_section")
public class ClassSection extends AcademicEntity {
    @Column(nullable = false, updatable = false)
    private UUID semesterId;
    @Column(nullable = false, updatable = false)
    private UUID courseId;
    @Column(nullable = false, updatable = false, length = 80)
    private String sectionCode;

    protected ClassSection() {}
    public ClassSection(Semester semester, Course course, String sectionCode) {
        super(semester.getProfileId());
        AcademicValues.sameProfile(getProfileId(), course);
        this.semesterId = semester.getId();
        this.courseId = course.getId();
        this.sectionCode = AcademicValues.text(sectionCode, 80);
    }
    public UUID getSemesterId() { return semesterId; }
    public UUID getCourseId() { return courseId; }
    public String getSectionCode() { return sectionCode; }
}
