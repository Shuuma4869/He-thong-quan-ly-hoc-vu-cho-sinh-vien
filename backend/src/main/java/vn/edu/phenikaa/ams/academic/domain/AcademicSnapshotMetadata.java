package vn.edu.phenikaa.ams.academic.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "academic_snapshot")
public class AcademicSnapshotMetadata extends AcademicEntity {
    public enum Status { COMPLETE, PARTIAL, FAILED }

    @Column(nullable = false, length = 80)
    private String sourceSystem;
    @Column(nullable = false)
    private Instant capturedAt;
    @Column(nullable = false)
    private int schemaVersion;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;
    @Column(length = 64)
    private String contentHash;

    protected AcademicSnapshotMetadata() {}
    public AcademicSnapshotMetadata(UUID profileId, String sourceSystem, Instant capturedAt, int schemaVersion, Status status, String contentHash) {
        super(profileId);
        this.sourceSystem = AcademicValues.text(sourceSystem, 80);
        this.capturedAt = Objects.requireNonNull(capturedAt);
        this.status = Objects.requireNonNull(status);
        if (schemaVersion <= 0 || (status != Status.FAILED && contentHash == null)
                || (contentHash != null && !contentHash.matches("[0-9a-f]{64}")))
            throw new IllegalArgumentException("Invalid snapshot metadata");
        this.schemaVersion = schemaVersion;
        this.contentHash = contentHash;
    }
    public String getSourceSystem() { return sourceSystem; }
    public Instant getCapturedAt() { return capturedAt; }
    public int getSchemaVersion() { return schemaVersion; }
    public Status getStatus() { return status; }
    public String getContentHash() { return contentHash; }
}
