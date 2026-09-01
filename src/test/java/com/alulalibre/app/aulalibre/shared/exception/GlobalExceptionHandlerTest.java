package com.alulalibre.app.aulalibre.shared.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.alulalibre.app.aulalibre.shared.response.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Mock
    private HttpServletRequest request;

    @Test
    void handleBusinessException_mapsCodeStatusAndPath() {
        when(request.getRequestURI()).thenReturn("/api/v1/rooms/123");
        ResourceNotFoundException ex = new ResourceNotFoundException(ErrorCode.ROOM_NOT_FOUND,
                "El salón solicitado no existe");

        ResponseEntity<ApiErrorResponse> response = handler.handleBusinessException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        ApiErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(404);
        assertThat(body.getError()).isEqualTo("NOT_FOUND");
        assertThat(body.getCode()).isEqualTo("ROOM_NOT_FOUND");
        assertThat(body.getMessage()).isEqualTo("El salón solicitado no existe");
        assertThat(body.getPath()).isEqualTo("/api/v1/rooms/123");
        assertThat(body.getFieldErrors()).isNull();
    }

    @Test
    void handleUnexpected_neverLeaksInternalDetails() {
        when(request.getRequestURI()).thenReturn("/api/v1/blocks");

        ResponseEntity<ApiErrorResponse> response = handler.handleUnexpected(
                new IllegalStateException("db connection leaked password=1234"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        ApiErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getMessage()).doesNotContain("password");
        assertThat(body.getCode()).isEqualTo("INTERNAL_ERROR");
    }
}
