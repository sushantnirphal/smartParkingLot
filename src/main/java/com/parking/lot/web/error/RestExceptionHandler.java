package com.parking.lot.web.error;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Maps every failure to the uniform envelope of architecture §8 (contract rules C1/C2).
 */
@RestControllerAdvice
public class RestExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(RestExceptionHandler.class);

    private final Clock clock;

    public RestExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException ex, HttpServletRequest request) {
        ErrorCode code = ex.getErrorCode();
        log.info("code={} path={} message={}", code.getCode(), request.getRequestURI(), ex.getMessage());
        return build(code.getStatus(), code.getCode(), ex.getMessage(), request, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                          HttpServletRequest request) {
        Map<String, Object> details = new LinkedHashMap<String, Object>();
        List<FieldError> errors = ex.getBindingResult().getFieldErrors();
        for (FieldError fe : errors) {
            details.put(fe.getField(), fe.getDefaultMessage());
        }
        return build(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR.getCode(),
                "Request failed validation", request, details);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            IllegalArgumentException.class})
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR.getCode(),
                ex.getMessage() == null ? "Malformed request" : ex.getMessage(), request, null);
    }

    /**
     * Cross-floor backstop for the unique index on {@code vehicle.plate_no}: when two gates on
     * different floors register the same plate simultaneously, the loser rolls back and we answer
     * with the same code the in-lock duplicate check would have produced (threat R3).
     */
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleIntegrity(org.springframework.dao.DataIntegrityViolationException ex,
                                                         HttpServletRequest request) {
        log.info("integrity violation on {} {}", request.getMethod(), request.getRequestURI());
        return build(HttpStatus.CONFLICT, ErrorCode.ALREADY_PARKED.getCode(),
                "Concurrent registration of the same vehicle or ticket.", request, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR.getCode(),
                "Unexpected server error", request, null);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String code, String message,
                                                HttpServletRequest request, Map<String, Object> details) {
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        ErrorResponse body = new ErrorResponse(
                code,
                message,
                Instant.now(clock),
                traceId,
                status.value(),
                details == null ? ErrorResponse.noDetails() : details);
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .header("X-Trace-Id", traceId)
                .body(body);
    }
}
