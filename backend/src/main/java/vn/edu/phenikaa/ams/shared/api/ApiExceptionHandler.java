package vn.edu.phenikaa.ams.shared.api;

import java.net.URI;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import vn.edu.phenikaa.ams.notification.application.NotificationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.ResponseEntity;
import vn.edu.phenikaa.ams.academic.application.AcademicSourceQueryException;
import vn.edu.phenikaa.ams.sync.application.SyncCommandException;
import vn.edu.phenikaa.ams.calendar.google.GoogleCalendarException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(NotificationException.class)
    ProblemDetail handleNotification(NotificationException exception) {
        var problem = ProblemDetail.forStatusAndDetail(exception.status(), exception.getMessage());
        problem.setProperty("code", exception.code());
        return problem;
    }

    @ExceptionHandler(GoogleCalendarException.class)
    ResponseEntity<ProblemDetail> handleGoogleCalendar(GoogleCalendarException exception) {
        var status = switch (exception.code()) {
            case GOOGLE_NOT_CONFIGURED, GOOGLE_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case GOOGLE_STATE_INVALID -> HttpStatus.BAD_REQUEST;
            case GOOGLE_AUTH_DENIED -> HttpStatus.FORBIDDEN;
            case GOOGLE_RECONNECTION_REQUIRED, GOOGLE_CALENDAR_SETUP_FAILED -> HttpStatus.CONFLICT;
            case GOOGLE_TOKEN_EXCHANGE_FAILED -> HttpStatus.BAD_GATEWAY;
        };
        var detail = switch (exception.code()) {
            case GOOGLE_NOT_CONFIGURED -> "Kết nối Google Calendar chưa được cấu hình.";
            case GOOGLE_STATE_INVALID -> "Yêu cầu kết nối đã hết hạn hoặc không hợp lệ.";
            case GOOGLE_AUTH_DENIED -> "Quyền truy cập Google Calendar chưa được cấp.";
            case GOOGLE_RECONNECTION_REQUIRED -> "Cần kết nối lại Google Calendar.";
            case GOOGLE_CALENDAR_SETUP_FAILED -> "Chưa thể hoàn tất lịch riêng của AMS.";
            case GOOGLE_TOKEN_EXCHANGE_FAILED, GOOGLE_UNAVAILABLE -> "Google Calendar tạm thời không sẵn sàng.";
        };
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", exception.code().name());
        return ResponseEntity.status(status).body(problem);
    }

    @ExceptionHandler(SyncCommandException.class)
    ResponseEntity<ProblemDetail> handleSync(SyncCommandException exception) {
        var status = switch (exception.code()) {
            case CONNECTION_NOT_FOUND, RECONNECTION_REQUIRED -> HttpStatus.CONFLICT;
            case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
            case RUN_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case ACCOUNT_UNAVAILABLE -> HttpStatus.FORBIDDEN;
            case QUEUE_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case INVALID_HISTORY_LIMIT, INVALID_HISTORY_CURSOR -> HttpStatus.BAD_REQUEST;
        };
        var problem = ProblemDetail.forStatusAndDetail(status, switch (exception.code()) {
            case CONNECTION_NOT_FOUND -> "Chưa có kết nối học vụ có thể sử dụng.";
            case RECONNECTION_REQUIRED -> "Phiên học vụ đã hết hạn; cần kết nối lại.";
            case RATE_LIMITED -> "Vui lòng chờ trước khi yêu cầu đồng bộ tiếp.";
            case RUN_NOT_FOUND -> "Không tìm thấy lượt đồng bộ của tài khoản này.";
            case ACCOUNT_UNAVAILABLE -> "Tài khoản hiện không thể đồng bộ.";
            case QUEUE_UNAVAILABLE -> "Hàng đợi đồng bộ tạm thời không sẵn sàng.";
            case INVALID_HISTORY_LIMIT -> "Giới hạn trang lịch sử không hợp lệ.";
            case INVALID_HISTORY_CURSOR -> "Con trỏ lịch sử không hợp lệ hoặc đã hết hạn.";
        });
        problem.setProperty("code", exception.code().name());
        return ResponseEntity.status(status).body(problem);
    }

    @ExceptionHandler(AcademicSourceQueryException.class)
    ResponseEntity<ProblemDetail> handleAcademicSource(AcademicSourceQueryException exception) {
        var status = switch (exception.code()) {
            case CONNECTION_NOT_FOUND, RECONNECTION_REQUIRED -> HttpStatus.CONFLICT;
            case INVALID_SOURCE_REFERENCE -> HttpStatus.NOT_FOUND;
            case SOURCE_TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
            case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
            default -> HttpStatus.BAD_GATEWAY;
        };
        var detail = switch (exception.code()) {
            case CONNECTION_NOT_FOUND -> "Chưa có kết nối học vụ có thể sử dụng.";
            case RECONNECTION_REQUIRED -> "Phiên học vụ đã hết hạn; cần kết nối lại.";
            case INVALID_SOURCE_REFERENCE -> "Không tìm thấy dữ liệu học vụ được yêu cầu.";
            case SOURCE_TIMEOUT -> "Cổng học vụ phản hồi quá chậm.";
            case RATE_LIMITED -> "Vui lòng chờ trước khi đọc lại dữ liệu học vụ.";
            default -> "Chưa thể đọc dữ liệu học vụ từ nguồn.";
        };
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", exception.code().name());
        return ResponseEntity.status(status).body(problem);
    }

    @ExceptionHandler(ResponseStatusException.class)
    ProblemDetail handleStatus(ResponseStatusException exception) {
        return ProblemDetail.forStatusAndDetail(exception.getStatusCode(), exception.getReason());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail handleUnreadableBody() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Nội dung yêu cầu không hợp lệ.");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException exception) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Dữ liệu gửi lên không hợp lệ");
        problem.setType(URI.create("https://ams.local/problems/validation"));
        problem.setTitle("Validation failed");
        List<String> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();
        problem.setProperty("errors", errors);
        return problem;
    }
}
