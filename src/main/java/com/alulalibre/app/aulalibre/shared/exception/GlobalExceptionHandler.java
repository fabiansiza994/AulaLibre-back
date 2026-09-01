package com.alulalibre.app.aulalibre.shared.exception;

import com.alulalibre.app.aulalibre.shared.response.ApiErrorResponse;
import com.alulalibre.app.aulalibre.shared.response.FieldValidationError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Translates every exception thrown by controllers/services into a single,
 * predictable {@link ApiErrorResponse} shape. No stacktraces are ever
 * returned to the client — but {@link #handleUnexpected} does log them
 * server-side, since a silently swallowed 500 is undiagnosable otherwise.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiErrorResponse> handleBusinessException(BusinessException ex, HttpServletRequest request) {
        HttpStatus status = ex.getStatus();
        ApiErrorResponse body = ApiErrorResponse.of(
                status.value(),
                status.name(),
                ex.getErrorCode().name(),
                ex.getMessage(),
                request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpServletRequest request) {
        List<FieldValidationError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toFieldValidationError)
                .toList();
        ApiErrorResponse body = ApiErrorResponse.ofValidation(
                HttpStatus.BAD_REQUEST.value(),
                "VALIDATION_ERROR",
                ErrorCode.INVALID_REQUEST.name(),
                "La solicitud contiene datos inválidos",
                request.getRequestURI(),
                fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException ex,
            HttpServletRequest request) {
        List<FieldValidationError> fieldErrors = ex.getConstraintViolations().stream()
                .map(violation -> new FieldValidationError(violation.getPropertyPath().toString(), violation.getMessage()))
                .toList();
        ApiErrorResponse body = ApiErrorResponse.ofValidation(
                HttpStatus.BAD_REQUEST.value(),
                "VALIDATION_ERROR",
                ErrorCode.INVALID_REQUEST.name(),
                "La solicitud contiene datos inválidos",
                request.getRequestURI(),
                fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleNotReadable(HttpMessageNotReadableException ex,
            HttpServletRequest request) {
        ApiErrorResponse body = ApiErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.name(),
                ErrorCode.INVALID_REQUEST.name(),
                "El cuerpo de la solicitud no es válido o está mal formado",
                request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * A path under {@code /api/v1/**} that doesn't match any {@code @RequestMapping}
     * falls through to Spring's default static-resource handler, which
     * throws this instead of leaving a 404 in place — without this handler
     * it fell through to {@link #handleUnexpected} and reported a scary
     * "INTERNAL_ERROR" 500 for what's really just a bad/typo'd URL or a
     * client hitting a route that only exists on a controller the running
     * process hasn't loaded (e.g. a stale JVM after adding a new endpoint).
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoResourceFound(NoResourceFoundException ex,
            HttpServletRequest request) {
        ApiErrorResponse body = ApiErrorResponse.of(
                HttpStatus.NOT_FOUND.value(),
                HttpStatus.NOT_FOUND.name(),
                ErrorCode.RESOURCE_NOT_FOUND.name(),
                "El recurso solicitado no existe",
                request.getRequestURI());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    /**
     * A query param bound to an enum via {@code EnumConverterConfig} (e.g.
     * {@code ?role=foo}, {@code ?roomType=foo}, {@code ?status=foo}) throws
     * this — not {@link HttpMessageNotReadableException}, which only covers
     * the request *body* — when the converter's {@code fromLabel(...)}
     * rejects the value. Without this handler it fell through to
     * {@link #handleUnexpected} as a 500 for a plain bad query param.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
            HttpServletRequest request) {
        ApiErrorResponse body = ApiErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                "VALIDATION_ERROR",
                ErrorCode.INVALID_REQUEST.name(),
                "El parámetro '%s' tiene un valor inválido".formatted(ex.getName()),
                request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * {@code @PreAuthorize} denials throw this from inside the controller
     * method invocation (a JDK/CGLIB proxy around the handler bean), which
     * Spring MVC's own exception resolution sees *before* the exception can
     * propagate back out to Spring Security's {@code ExceptionTranslationFilter}
     * — so without this handler it would fall through to {@link #handleUnexpected}
     * as a 500. {@code JwtAccessDeniedHandler} (shared/security) still covers
     * the other case: a denial expressed as a request-matcher rule in the
     * filter chain itself, which never reaches the DispatcherServlet at all.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        ApiErrorResponse body = ApiErrorResponse.of(
                HttpStatus.FORBIDDEN.value(),
                "FORBIDDEN",
                "FORBIDDEN_OPERATION",
                "No tienes permisos para realizar esta acción",
                request.getRequestURI());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        ApiErrorResponse body = ApiErrorResponse.of(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.name(),
                "INTERNAL_ERROR",
                "Ocurrió un error inesperado. Intenta nuevamente más tarde.",
                request.getRequestURI());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    private FieldValidationError toFieldValidationError(FieldError fieldError) {
        return new FieldValidationError(fieldError.getField(), fieldError.getDefaultMessage());
    }
}
