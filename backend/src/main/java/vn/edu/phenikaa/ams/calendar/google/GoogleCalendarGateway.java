package vn.edu.phenikaa.ams.calendar.google;

interface GoogleCalendarGateway {
    String create(String accessToken);
    boolean exists(String accessToken, String calendarId);
}
