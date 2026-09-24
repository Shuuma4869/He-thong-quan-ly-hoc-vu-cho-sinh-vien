package vn.edu.phenikaa.ams.academic.application.port;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

/** Personal exam observations; enrollment and occurrence identity remain unverified. */
public record ExamObservation(ExamPeriod requestedPeriod, ZoneId zone, List<Entry> entries) {
    public enum Completeness { UNKNOWN }
    public enum IdentityScope { UNVERIFIED }

    public ExamObservation {
        Objects.requireNonNull(requestedPeriod);
        Objects.requireNonNull(zone);
        entries = List.copyOf(entries);
    }
    public Completeness completeness() { return Completeness.UNKNOWN; }
    @Override public String toString() { return "ExamObservation[redacted]"; }

    public record CandidateIdentity(String sourceId) {
        public IdentityScope scope() { return IdentityScope.UNVERIFIED; }
        @Override public String toString() { return "ExamCandidateIdentity[redacted]"; }
    }

    public record Entry(CandidateIdentity identity, String courseCode, String courseName,
                        int examAttempt, String examSession, Instant startsAt, Instant endsAt, String room) {
        @Override public String toString() { return "ExamEntry[redacted]"; }
    }
}
