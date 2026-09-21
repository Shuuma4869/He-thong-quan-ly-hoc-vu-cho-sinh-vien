package vn.edu.phenikaa.ams.academic.application.port;

import java.util.UUID;
import java.util.Objects;
import java.time.LocalDate;

public interface AcademicPortalClient {

    StudentConnectionId currentConnection(UUID currentUserId);

    ProfileObservation fetchProfile(UUID currentUserId, StudentConnectionId connectionId);

    ScheduleObservation fetchSchedule(UUID currentUserId, StudentConnectionId connectionId, LocalDate from, LocalDate through);

    record StudentConnectionId(UUID value) {
        public StudentConnectionId { Objects.requireNonNull(value); }
    }
}
