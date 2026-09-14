package com.alulalibre.app.aulalibre.roomrequest.application.dto.request;

import jakarta.validation.constraints.Size;

public record RejectRoomRequestRequest(

        @Size(max = 500, message = "El comentario no puede superar los 500 caracteres")
        String reviewNote) {
}
