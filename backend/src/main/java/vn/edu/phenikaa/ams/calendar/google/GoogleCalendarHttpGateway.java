package vn.edu.phenikaa.ams.calendar.google;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

final class GoogleCalendarHttpGateway implements GoogleCalendarGateway {
    private static final URI API = URI.create("https://www.googleapis.com/calendar/v3");
    private final RestClient http;
    private final URI api;

    GoogleCalendarHttpGateway() { this(API); }

    GoogleCalendarHttpGateway(URI api) {
        this(api, Duration.ofSeconds(5), Duration.ofSeconds(8));
    }

    GoogleCalendarHttpGateway(URI api, Duration connectTimeout, Duration readTimeout) {
        this.api = api;
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(connectTimeout).followRedirects(HttpClient.Redirect.NEVER).build());
        factory.setReadTimeout(readTimeout);
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    @Override public String create(String accessToken) {
        try {
            var uri = UriComponentsBuilder.fromUri(api).pathSegment("calendars").build().toUri();
            var response = http.post().uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("summary", "AMS - Lịch học vụ", "description", "Lịch riêng do AMS quản lý.",
                            "timeZone", "Asia/Ho_Chi_Minh"))
                    .retrieve().body(CalendarResponse.class);
            if (response == null || response.id() == null || response.id().isBlank())
                throw new GoogleCalendarException(GoogleCalendarException.Code.GOOGLE_CALENDAR_SETUP_FAILED);
            return response.id();
        } catch (HttpStatusCodeException ex) { throw mapped(ex); }
        catch (RestClientException ex) {
            throw new GoogleCalendarException(GoogleCalendarException.Code.GOOGLE_CALENDAR_SETUP_FAILED);
        }
    }

    @Override public boolean exists(String accessToken, String calendarId) {
        if (calendarId == null || calendarId.isBlank() || calendarId.length() > 1024)
            throw new GoogleCalendarException(GoogleCalendarException.Code.GOOGLE_CALENDAR_SETUP_FAILED);
        var uri = UriComponentsBuilder.fromUri(api).pathSegment("calendars", calendarId).build().encode().toUri();
        try {
            var response = http.get().uri(uri).header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve().body(CalendarResponse.class);
            if (response == null || !calendarId.equals(response.id()))
                throw new GoogleCalendarException(GoogleCalendarException.Code.GOOGLE_CALENDAR_SETUP_FAILED);
            return true;
        } catch (HttpStatusCodeException ex) {
            if (ex.getStatusCode().value() == 404) return false;
            throw mapped(ex);
        } catch (RestClientException ex) {
            throw new GoogleCalendarException(GoogleCalendarException.Code.GOOGLE_UNAVAILABLE);
        }
    }

    private static GoogleCalendarException mapped(HttpStatusCodeException ex) {
        var code = ex.getStatusCode().value() == 401
                ? GoogleCalendarException.Code.GOOGLE_RECONNECTION_REQUIRED
                : ex.getStatusCode().value() == 403
                ? GoogleCalendarException.Code.GOOGLE_CALENDAR_SETUP_FAILED
                : GoogleCalendarException.Code.GOOGLE_UNAVAILABLE;
        return new GoogleCalendarException(code);
    }

    private record CalendarResponse(String id) {}
}
