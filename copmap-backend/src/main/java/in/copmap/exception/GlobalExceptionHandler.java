package in.copmap.exception;

import in.copmap.dto.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(CopMapException.class)
    public ResponseEntity<ApiResponse<Void>> handleCopMapException(CopMapException ex) {
        log.warn("Application exception [{}]: {}", ex.getCode(), ex.getMessage());
        HttpStatus status = resolveStatus(ex.getCode());
        return ResponseEntity.status(status).body(
                ApiResponse.fail(ex.getMessage(),
                        ApiResponse.ErrorDetails.builder().code(ex.getCode()).build()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            errors.put(fe.getField(), fe.getDefaultMessage());
        }
        return ResponseEntity.badRequest().body(
                ApiResponse.fail("Validation failed",
                        ApiResponse.ErrorDetails.builder().code("VALIDATION_ERROR").details(errors).build()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                ApiResponse.fail("Access denied: " + ex.getMessage(),
                        ApiResponse.ErrorDetails.builder().code("ACCESS_DENIED").build()));
    }

    @ExceptionHandler({BadCredentialsException.class, UsernameNotFoundException.class})
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(Exception ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                ApiResponse.fail("Invalid badge number or password",
                        ApiResponse.ErrorDetails.builder().code("INVALID_CREDENTIALS").build()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        log.error("Unexpected error: ", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ApiResponse.fail("An unexpected error occurred",
                        ApiResponse.ErrorDetails.builder().code("INTERNAL_ERROR").build()));
    }

    private HttpStatus resolveStatus(String code) {
        return switch (code) {
            case "OPERATION_NOT_FOUND", "USER_NOT_FOUND", "ALERT_NOT_FOUND",
                    "CHECKPOINT_NOT_FOUND", "ASSIGNMENT_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "INVALID_CREDENTIALS", "INVALID_REFRESH_TOKEN",
                    "EXPIRED_REFRESH_TOKEN" -> HttpStatus.UNAUTHORIZED;
            case "BADGE_EXISTS", "EMAIL_EXISTS", "PHONE_EXISTS",
                    "ALREADY_ACKNOWLEDGED", "ALREADY_CHECKED_IN" -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_REQUEST;
        };
    }
}
