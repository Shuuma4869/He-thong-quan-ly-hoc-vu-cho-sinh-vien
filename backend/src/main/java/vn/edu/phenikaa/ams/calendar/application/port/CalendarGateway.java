package vn.edu.phenikaa.ams.calendar.application.port;

import java.time.Instant;
import java.util.Map;

public interface CalendarGateway {

    String upsertManagedEvent(ManagedCalendarEvent event);

    void removeManagedEvent(String externalEventId);

    record ManagedCalendarEvent(
            String externalEventId,
            String title,
            Instant startsAt,
            Instant endsAt,
            String location,
            Map<String, String> metadata) {}
}
