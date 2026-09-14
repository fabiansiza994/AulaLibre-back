package com.alulalibre.app.aulalibre.roomrequest.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Persisted as STRING using the English constant name. The Spanish label is
 * the JSON wire representation required by BACKEND_API_CONTRACT.md.
 */
public enum RoomRequestStatus {
    PENDING("pendiente"),
    APPROVED("aprobada"),
    REJECTED("rechazada"),
    CANCELLED("cancelada");

    private final String label;

    RoomRequestStatus(String label) {
        this.label = label;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }

    @JsonCreator
    public static RoomRequestStatus fromLabel(String label) {
        for (RoomRequestStatus status : values()) {
            if (status.label.equalsIgnoreCase(label)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Estado de solicitud inválido: " + label);
    }
}
