package vn.edu.phenikaa.ams.academic.application.port;

import java.time.*;
import java.util.List;

public record ScheduleObservation(LocalDate from, LocalDate through, ZoneId zone, List<Entry> entries) {
    public enum Completeness { UNKNOWN }
    public enum Kind { CLASS, EXAM, UNKNOWN }
    public enum IdentityScope { UNVERIFIED }

    public ScheduleObservation { entries = List.copyOf(entries); }
    public Completeness completeness() { return Completeness.UNKNOWN; }
    @Override public String toString() { return "ScheduleObservation[redacted]"; }

    public record CandidateIdentity(String scheduleId, String sectionId, String enrollmentSectionId) {
        public IdentityScope scope() { return IdentityScope.UNVERIFIED; }
        @Override public String toString() { return "CandidateIdentity[redacted]"; }
    }

    public record Entry(CandidateIdentity identity, String courseName, LocalDate date,
                        LocalTime startsAt, LocalTime endsAt, String room, String lecturer, Kind kind) {
        @Override public String toString() { return "ScheduleEntry[redacted]"; }
    }
}
