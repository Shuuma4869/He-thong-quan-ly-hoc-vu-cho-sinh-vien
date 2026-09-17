package vn.edu.phenikaa.ams.academic.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "academic_result")
public class AcademicResult extends AcademicEntity {
    public enum Status { IN_PROGRESS, PASSED, FAILED, WITHDRAWN, EXEMPTED }

    @Column(nullable = false, updatable = false)
    private UUID studentCourseId;
    private UUID gradingPolicyId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;
    @Column(precision = 6, scale = 2)
    private BigDecimal numericScore;
    @Column(length = 16)
    private String letterGrade;
    @Column(precision = 6, scale = 2)
    private BigDecimal gradePoints;
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal creditsAttempted;
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal creditsEarned;
    @Column(nullable = false)
    private boolean includedInGpa;
    @Column(nullable = false)
    private Instant recordedAt;

    protected AcademicResult() {}
    public AcademicResult(StudentCourse attempt, GradingPolicy policy, Status status, BigDecimal numericScore,
                          String letterGrade, BigDecimal gradePoints, BigDecimal creditsEarned,
                          boolean includedInGpa, Instant recordedAt) {
        super(attempt.getProfileId());
        this.studentCourseId = attempt.getId();
        this.creditsAttempted = attempt.getCreditsAttempted();
        this.status = Objects.requireNonNull(status);
        this.numericScore = numericScore == null ? null : AcademicValues.decimal(numericScore, 6);
        this.letterGrade = AcademicValues.optionalText(letterGrade, 16);
        this.gradePoints = gradePoints == null ? null : AcademicValues.decimal(gradePoints, 6);
        this.creditsEarned = AcademicValues.decimal(creditsEarned, 5);
        this.includedInGpa = includedInGpa;
        this.recordedAt = Objects.requireNonNull(recordedAt);
        if (this.creditsEarned.compareTo(creditsAttempted) > 0
                || (status != Status.PASSED && status != Status.EXEMPTED && this.creditsEarned.signum() != 0))
            throw new IllegalArgumentException("Earned credits do not match the result");
        if (includedInGpa && (policy == null || gradePoints == null || (status != Status.PASSED && status != Status.FAILED)))
            throw new IllegalArgumentException("GPA requires a completed graded result and an explicit policy");
        if (policy != null) {
            AcademicValues.sameProfile(getProfileId(), policy);
            if ((numericScore != null && numericScore.compareTo(policy.getMaxNumericScore()) > 0)
                    || (gradePoints != null && gradePoints.compareTo(policy.getMaxGradePoints()) > 0))
                throw new IllegalArgumentException("Result exceeds its grading scale");
            this.gradingPolicyId = policy.getId();
        }
    }
    public UUID getStudentCourseId() { return studentCourseId; }
    public UUID getGradingPolicyId() { return gradingPolicyId; }
    public Status getStatus() { return status; }
    public BigDecimal getNumericScore() { return numericScore; }
    public String getLetterGrade() { return letterGrade; }
    public BigDecimal getGradePoints() { return gradePoints; }
    public BigDecimal getCreditsAttempted() { return creditsAttempted; }
    public BigDecimal getCreditsEarned() { return creditsEarned; }
    public boolean isIncludedInGpa() { return includedInGpa; }
    public Instant getRecordedAt() { return recordedAt; }
}
