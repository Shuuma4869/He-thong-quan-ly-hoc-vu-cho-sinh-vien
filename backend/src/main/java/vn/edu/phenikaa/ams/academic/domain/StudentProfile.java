package vn.edu.phenikaa.ams.academic.domain;

import jakarta.persistence.*;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "student_profile")
public class StudentProfile {
    @Id
    private UUID id;
    @Column(nullable = false, unique = true, updatable = false)
    private UUID userId;
    @Column(length = 80)
    private String studentNumber;
    @Column(length = 200)
    private String institutionName;
    @Column(length = 200)
    private String programName;
    @Column(length = 40)
    private String cohort;
    private UUID curriculumId;
    private UUID gradingPolicyId;

    protected StudentProfile() {}
    public StudentProfile(UUID userId, String studentNumber, String institutionName, String programName, String cohort) {
        this.id = UUID.randomUUID();
        this.userId = Objects.requireNonNull(userId);
        this.studentNumber = AcademicValues.optionalText(studentNumber, 80);
        this.institutionName = AcademicValues.optionalText(institutionName, 200);
        this.programName = AcademicValues.optionalText(programName, 200);
        this.cohort = AcademicValues.optionalText(cohort, 40);
    }
    public void selectCurriculum(Curriculum curriculum) {
        AcademicValues.sameProfile(id, curriculum);
        curriculumId = curriculum.getId();
    }
    public void selectGradingPolicy(GradingPolicy policy) {
        AcademicValues.sameProfile(id, policy);
        gradingPolicyId = policy.getId();
    }
    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getStudentNumber() { return studentNumber; }
    public String getInstitutionName() { return institutionName; }
    public String getProgramName() { return programName; }
    public String getCohort() { return cohort; }
    public UUID getCurriculumId() { return curriculumId; }
    public UUID getGradingPolicyId() { return gradingPolicyId; }
}
