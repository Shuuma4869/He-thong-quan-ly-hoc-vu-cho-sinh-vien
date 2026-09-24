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

    List<AcademicProgram> fetchAcademicPrograms(UUID currentUserId, StudentConnectionId connectionId);

    List<AcademicPeriod> fetchAcademicPeriods(UUID currentUserId, StudentConnectionId connectionId);

    AcademicRecordObservation fetchAcademicRecords(UUID currentUserId, StudentConnectionId connectionId,
                                                    AcademicProgram program);

    record StudentConnectionId(UUID value) {
        public StudentConnectionId { Objects.requireNonNull(value); }
    }
}
