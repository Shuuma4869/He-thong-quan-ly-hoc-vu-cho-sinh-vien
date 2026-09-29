package vn.edu.phenikaa.ams.shared.api;

import java.net.URI;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.ResponseEntity;
import vn.edu.phenikaa.ams.academic.application.AcademicSourceQueryException;

@RestControllerAdvice
public class ApiExceptionHandler {

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
