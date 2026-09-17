package vn.edu.phenikaa.ams.academic.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "class_session")
public class ClassSession extends AcademicEntity {
    public enum Status { SCHEDULED, CANCELLED }

    @Column(nullable = false, updatable = false)
    private UUID sectionId;
    @Column(nullable = false, updatable = false, length = 80)
    private String occurrenceKey;
    @Column(nullable = false)
    private Instant startsAt;
    @Column(nullable = false)
    private Instant endsAt;
    @Column(length = 160)
    private String room;
    @Column(length = 200)
    private String lecturer;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;
    @Version
    private long version;

    protected ClassSession() {}
    public ClassSession(ClassSection section, String occurrenceKey, Instant startsAt, Instant endsAt, String room, String lecturer) {
        super(section.getProfileId());
        this.sectionId = section.getId();
        this.occurrenceKey = AcademicValues.text(occurrenceKey, 80);
        this.status = Status.SCHEDULED;
        reschedule(startsAt, endsAt, room, lecturer);
    }
    public void reschedule(Instant startsAt, Instant endsAt, String room, String lecturer) {
        Objects.requireNonNull(startsAt);
        Objects.requireNonNull(endsAt);
        if (!endsAt.isAfter(startsAt)) throw new IllegalArgumentException("Session must end after it starts");
        String newRoom = AcademicValues.optionalText(room, 160);
        String newLecturer = AcademicValues.optionalText(lecturer, 200);
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.room = newRoom;
        this.lecturer = newLecturer;
    }
    public void cancel() { status = Status.CANCELLED; }
    public UUID getSectionId() { return sectionId; }
    public String getOccurrenceKey() { return occurrenceKey; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public String getRoom() { return room; }
    public String getLecturer() { return lecturer; }
    public Status getStatus() { return status; }
    public long getVersion() { return version; }
}
