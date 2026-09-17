package vn.edu.phenikaa.ams.academic.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "exam")
public class Exam extends AcademicEntity {
    public enum Status { SCHEDULED, CANCELLED }

    @Column(nullable = false, updatable = false)
    private UUID studentCourseId;
    @Column(nullable = false, updatable = false, length = 80)
    private String occurrenceKey;
    @Column(nullable = false)
    private Instant startsAt;
    private Instant endsAt;
    @Column(length = 160)
    private String room;
    @Column(length = 80)
    private String format;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;
    @Version
    private long version;

    protected Exam() {}
    public Exam(StudentCourse attempt, String occurrenceKey, Instant startsAt, Instant endsAt, String room, String format) {
        super(attempt.getProfileId());
        this.studentCourseId = attempt.getId();
        this.occurrenceKey = AcademicValues.text(occurrenceKey, 80);
        this.format = AcademicValues.optionalText(format, 80);
        this.status = Status.SCHEDULED;
        reschedule(startsAt, endsAt, room);
    }
    public void reschedule(Instant startsAt, Instant endsAt, String room) {
        Objects.requireNonNull(startsAt);
        if (endsAt != null && !endsAt.isAfter(startsAt)) throw new IllegalArgumentException("Exam must end after it starts");
        String newRoom = AcademicValues.optionalText(room, 160);
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.room = newRoom;
    }
    public void cancel() { status = Status.CANCELLED; }
    public UUID getStudentCourseId() { return studentCourseId; }
    public String getOccurrenceKey() { return occurrenceKey; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public String getRoom() { return room; }
    public String getFormat() { return format; }
    public Status getStatus() { return status; }
    public long getVersion() { return version; }
}
