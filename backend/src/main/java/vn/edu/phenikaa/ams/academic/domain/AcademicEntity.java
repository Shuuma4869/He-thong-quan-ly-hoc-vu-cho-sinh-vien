package vn.edu.phenikaa.ams.academic.domain;

import jakarta.persistence.*;
import java.util.Objects;
import java.util.UUID;

@MappedSuperclass
public abstract class AcademicEntity {
    @Id
    private UUID id;
    @Column(nullable = false, updatable = false)
    private UUID profileId;

    protected AcademicEntity() {}
    protected AcademicEntity(UUID profileId) {
        this.id = UUID.randomUUID();
        this.profileId = Objects.requireNonNull(profileId);
    }
    public UUID getId() { return id; }
    public UUID getProfileId() { return profileId; }
}
