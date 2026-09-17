package vn.edu.phenikaa.ams.sync.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.edu.phenikaa.ams.academic.domain.*;

@Entity
@Immutable
@Table(name = "schedule_change")
public class ScheduleChange extends AcademicEntity {
    private UUID previousSnapshotId;
    @Column(nullable = false)
    private UUID currentSnapshotId;
    private UUID classSessionId;
    private UUID examId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AcademicChangeType changeType;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, String> beforeValues;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, String> afterValues;
    @Column(nullable = false)
    private Instant detectedAt;

    protected ScheduleChange() {}
    private ScheduleChange(AcademicEntity target, boolean exam, AcademicSnapshotMetadata previous,
                           AcademicSnapshotMetadata current, DetectedAcademicChange change) {
        super(target.getProfileId());
        if (!getProfileId().equals(current.getProfileId()) || !target.getId().equals(change.entityId())
                || current.getStatus() != AcademicSnapshotMetadata.Status.COMPLETE)
            throw new IllegalArgumentException("Change requires a matching entity and complete owned snapshot");
        if (previous != null && (!getProfileId().equals(previous.getProfileId())
                || previous.getId().equals(current.getId()) || !previous.getSourceSystem().equals(current.getSourceSystem())
                || previous.getStatus() != AcademicSnapshotMetadata.Status.COMPLETE
                || previous.getCapturedAt().isAfter(current.getCapturedAt())))
            throw new IllegalArgumentException("Invalid comparison snapshot");
        if (change.type() != AcademicChangeType.MULTIPLE_FIELDS_CHANGED && change.type().name().startsWith("EXAM_") != exam)
            throw new IllegalArgumentException("Change type does not match its schedule entity");
        this.previousSnapshotId = previous == null ? null : previous.getId();
        this.currentSnapshotId = current.getId();
        this.classSessionId = exam ? null : target.getId();
        this.examId = exam ? target.getId() : null;
        this.changeType = change.type();
        this.beforeValues = change.before();
        this.afterValues = change.after();
        this.detectedAt = change.detectedAt();
    }
    public static ScheduleChange forSession(ClassSession session, AcademicSnapshotMetadata previous,
                                            AcademicSnapshotMetadata current, DetectedAcademicChange change) {
        return new ScheduleChange(session, false, previous, current, change);
    }
    public static ScheduleChange forExam(Exam exam, AcademicSnapshotMetadata previous,
                                       AcademicSnapshotMetadata current, DetectedAcademicChange change) {
        return new ScheduleChange(exam, true, previous, current, change);
    }
    public UUID getPreviousSnapshotId() { return previousSnapshotId; }
    public UUID getCurrentSnapshotId() { return currentSnapshotId; }
    public UUID getClassSessionId() { return classSessionId; }
    public UUID getExamId() { return examId; }
    public AcademicChangeType getChangeType() { return changeType; }
    public Map<String, String> getBeforeValues() { return Map.copyOf(beforeValues); }
    public Map<String, String> getAfterValues() { return Map.copyOf(afterValues); }
    public Instant getDetectedAt() { return detectedAt; }
}
