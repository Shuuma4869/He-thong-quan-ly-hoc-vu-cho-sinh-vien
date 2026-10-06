package vn.edu.phenikaa.ams.academic.application.port;

import java.util.UUID;

public interface AcademicReadGate {
    enum Capability { PROGRAMS, RECORDS, DETAIL, SCHEDULE, EXAM_PERIODS, EXAMS }
    boolean tryAcquire(UUID userId, Capability capability);
}
