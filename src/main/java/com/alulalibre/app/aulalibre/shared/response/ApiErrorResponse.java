package com.alulalibre.app.aulalibre.shared.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Uniform error payload returned by {@code GlobalExceptionHandler} for every
 * failed request. {@code fieldErrors} is only populated for validation
 * failures.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiErrorResponse {

    private final LocalDateTime timestamp;
    private final int status;
    private final String error;
    private final String code;
    private final String message;
    private final String path;
    private final List<FieldValidationError> fieldErrors;

    public ApiErrorResponse(LocalDateTime timestamp, int status, String error, String code,
            String message, String path, List<FieldValidationError> fieldErrors) {
        this.timestamp = timestamp;
        this.status = status;
        this.error = error;
        this.code = code;
        this.message = message;
        this.path = path;
        this.fieldErrors = fieldErrors;
    }

    public static ApiErrorResponse of(int status, String error, String code, String message, String path) {
        return new ApiErrorResponse(LocalDateTime.now(), status, error, code, message, path, null);
    }

    public static ApiErrorResponse ofValidation(int status, String error, String code, String message,
            String path, List<FieldValidationError> fieldErrors) {
        return new ApiErrorResponse(LocalDateTime.now(), status, error, code, message, path, fieldErrors);
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public int getStatus() {
        return status;
    }

    public String getError() {
        return error;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public String getPath() {
        return path;
    }

    public List<FieldValidationError> getFieldErrors() {
        return fieldErrors;
    }
}
