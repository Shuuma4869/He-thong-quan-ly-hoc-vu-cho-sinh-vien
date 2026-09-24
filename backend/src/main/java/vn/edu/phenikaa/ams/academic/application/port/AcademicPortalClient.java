package vn.edu.phenikaa.ams.academic.application.port;

import java.util.UUID;
import java.util.Objects;
import java.time.LocalDate;
import java.util.List;

public interface AcademicPortalClient {

    StudentConnectionId currentConnection(UUID currentUserId);

    ProfileObservation fetchProfile(UUID currentUserId, StudentConnectionId connectionId);

    ScheduleObservation fetchSchedule(UUID currentUserId, StudentConnectionId connectionId, LocalDate from, LocalDate through);

    List<ExamPeriod> fetchExamPeriods(UUID currentUserId, StudentConnectionId connectionId);

    ExamObservation fetchExams(UUID currentUserId, StudentConnectionId connectionId, ExamPeriod period);

    record StudentConnectionId(UUID value) {
        public StudentConnectionId { Objects.requireNonNull(value); }
    }
}
