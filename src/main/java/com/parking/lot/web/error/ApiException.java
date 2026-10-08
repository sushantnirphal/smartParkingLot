package com.parking.lot.web.error;

/**
 * Single exception type thrown by services and mapped to the uniform problem+json body
 * by {@link RestExceptionHandler} (contract rule C2).
 */
public class ApiException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErrorCode errorCode;

    public ApiException(ErrorCode errorCode) {
        super(errorCode.name());
        this.errorCode = errorCode;
    }

    public ApiException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ApiException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public static ApiException conflict(ErrorCode code) {
        return new ApiException(code);
    }
}
