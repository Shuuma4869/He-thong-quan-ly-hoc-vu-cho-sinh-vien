package vn.edu.phenikaa.ams.academic.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class StudyPlanCourse {
    private final UUID id;
    private final UUID profileId;
    private final UUID curriculumId;
    private final int scenarioNo;
    private final UUID courseId;
    private final Instant createdAt;
    private int plannedTerm;
    private Instant updatedAt;

    public StudyPlanCourse(UUID profileId, UUID curriculumId, int scenarioNo, UUID courseId, int plannedTerm, Instant now) {
        this(UUID.randomUUID(), profileId, curriculumId, scenarioNo, courseId, plannedTerm, now, now);
    }

    public StudyPlanCourse(UUID id, UUID profileId, UUID curriculumId, int scenarioNo, UUID courseId,
                           int plannedTerm, Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id);
        this.profileId = Objects.requireNonNull(profileId);
        this.curriculumId = Objects.requireNonNull(curriculumId);
        this.scenarioNo = scenario(scenarioNo);
        this.courseId = Objects.requireNonNull(courseId);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
        if (updatedAt.isBefore(createdAt)) throw new IllegalArgumentException("Invalid plan timestamps");
        this.plannedTerm = term(plannedTerm);
    }

    public void moveToTerm(int term, Instant now) {
        int next = term(term);
        if (next == plannedTerm) return;
        if (Objects.requireNonNull(now).isBefore(createdAt)) throw new IllegalArgumentException("Invalid plan timestamp");
        plannedTerm = next;
        updatedAt = now;
    }

    public static int term(int value) {
        if (value < 1 || value > 99) throw new IllegalArgumentException("Invalid planned term");
        return value;
    }

    public static int scenario(int value) {
        if (value < 1 || value > 5) throw new IllegalArgumentException("Invalid study plan scenario");
        return value;
    }

    public UUID id() { return id; }
    public UUID profileId() { return profileId; }
    public UUID curriculumId() { return curriculumId; }
    public int scenarioNo() { return scenarioNo; }
    public UUID courseId() { return courseId; }
    public int plannedTerm() { return plannedTerm; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}
