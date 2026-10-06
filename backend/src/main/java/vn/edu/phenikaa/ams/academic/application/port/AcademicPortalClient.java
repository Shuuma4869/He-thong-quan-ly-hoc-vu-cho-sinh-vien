package vn.edu.phenikaa.ams.academic.application.port;

import java.util.UUID;
import java.util.Objects;
import java.time.LocalDate;
import java.util.List;
import java.time.Instant;

public interface AcademicPortalClient {

    List<CurriculumOption> fetchCurricula(UUID currentUserId, StudentConnectionId connectionId);

    CurriculumObservation fetchCurriculum(UUID currentUserId, StudentConnectionId connectionId, CurriculumOption curriculum);

    CourseRelationObservation fetchCourseRelations(UUID currentUserId, StudentConnectionId connectionId,
                                                   CurriculumOption curriculum, String courseSourceId);

    StudentConnectionId currentConnection(UUID currentUserId);

    ConnectionInfo connectionInfo(UUID currentUserId);

    ProfileObservation fetchProfile(UUID currentUserId, StudentConnectionId connectionId);

    ScheduleObservation fetchSchedule(UUID currentUserId, StudentConnectionId connectionId, LocalDate from, LocalDate through);

    List<ExamPeriod> fetchExamPeriods(UUID currentUserId, StudentConnectionId connectionId);

    ExamObservation fetchExams(UUID currentUserId, StudentConnectionId connectionId, ExamPeriod period);

    List<AcademicProgram> fetchAcademicPrograms(UUID currentUserId, StudentConnectionId connectionId);

    List<AcademicPeriod> fetchAcademicPeriods(UUID currentUserId, StudentConnectionId connectionId);

    AcademicRecordObservation fetchAcademicRecords(UUID currentUserId, StudentConnectionId connectionId,
                                                    AcademicProgram program);

    AcademicProgressSummaryObservation fetchAcademicProgressSummary(UUID currentUserId, StudentConnectionId connectionId,
                                                                    AcademicProgram program);

    AcademicResultDetail fetchAcademicResultDetail(UUID currentUserId, StudentConnectionId connectionId,
                                                   AcademicProgram program, String sourceResultId);

    record StudentConnectionId(UUID value) {
        public StudentConnectionId { Objects.requireNonNull(value); }
    }

    record ConnectionInfo(State state, Instant lastSuccessfulAccessAt, Instant authenticatedAt) {
        public ConnectionInfo(State state, Instant lastSuccessfulAccessAt) {
            this(state, lastSuccessfulAccessAt, null);
        }
        public enum State { NOT_CONNECTED, CONNECTED, RECONNECTION_REQUIRED, DISCONNECTED }
    }
}
