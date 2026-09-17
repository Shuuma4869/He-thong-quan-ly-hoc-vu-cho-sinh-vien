package vn.edu.phenikaa.ams.academic.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.*;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "grading_policy")
public class GradingPolicy extends AcademicEntity {
    public enum RepeatStrategy { LATEST, HIGHEST, ALL }

    @Column(nullable = false, length = 40)
    private String code;
    @Column(nullable = false, length = 40)
    private String revision;
    @Column(nullable = false, length = 200)
    private String name;
    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal maxNumericScore;
    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal maxGradePoints;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RepeatStrategy repeatStrategy;
    @ElementCollection
    @CollectionTable(name = "grading_classification", joinColumns = @JoinColumn(name = "policy_id"))
    @MapKeyColumn(name = "label", length = 80)
    @Column(name = "minimum_gpa", nullable = false, precision = 6, scale = 2)
    private Map<String, BigDecimal> classifications = new HashMap<>();

    protected GradingPolicy() {}
    public GradingPolicy(UUID profileId, String code, String revision, String name,
                         BigDecimal maxNumericScore, BigDecimal maxGradePoints, RepeatStrategy repeatStrategy,
                         Map<String, BigDecimal> classifications) {
        super(profileId);
        this.code = AcademicValues.text(code, 40);
        this.revision = AcademicValues.text(revision, 40);
        this.name = AcademicValues.text(name, 200);
        this.maxNumericScore = AcademicValues.decimal(maxNumericScore, 6);
        this.maxGradePoints = AcademicValues.decimal(maxGradePoints, 6);
        if (this.maxNumericScore.signum() == 0 || this.maxGradePoints.signum() == 0)
            throw new IllegalArgumentException("Grading scales must be positive");
        this.repeatStrategy = Objects.requireNonNull(repeatStrategy);
        classifications.forEach((label, minimum) -> {
            String normalizedLabel = AcademicValues.text(label, 80);
            BigDecimal threshold = AcademicValues.decimal(minimum, 6);
            if (threshold.compareTo(this.maxGradePoints) > 0 || this.classifications.containsValue(threshold)
                    || this.classifications.putIfAbsent(normalizedLabel, threshold) != null)
                throw new IllegalArgumentException("Invalid or duplicate classification threshold");
        });
    }
    public String getCode() { return code; }
    public String getRevision() { return revision; }
    public String getName() { return name; }
    public BigDecimal getMaxNumericScore() { return maxNumericScore; }
    public BigDecimal getMaxGradePoints() { return maxGradePoints; }
    public RepeatStrategy getRepeatStrategy() { return repeatStrategy; }
    public Map<String, BigDecimal> getClassifications() { return Map.copyOf(classifications); }
}
