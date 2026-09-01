package com.alulalibre.app.aulalibre.roomrequest.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Persisted as STRING using the English constant name. The Spanish label is
 * the JSON wire representation required by BACKEND_API_CONTRACT.md
 * (REQUEST_REASONS).
 */
public enum RoomRequestReason {
    TUTORING("Tutoría"),
    ACADEMIC_ADVISORY("Asesoría"),
    ACADEMIC_MEETING("Reunión académica"),
    EXTRACURRICULAR_ACTIVITY("Actividad extracurricular"),
    OTHER("Otro");

    private final String label;

    RoomRequestReason(String label) {
        this.label = label;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }

    @JsonCreator
    public static RoomRequestReason fromLabel(String label) {
        for (RoomRequestReason reason : values()) {
            if (reason.label.equalsIgnoreCase(label)) {
                return reason;
            }
        }
        throw new IllegalArgumentException("Motivo de solicitud inválido: " + label);
    }
}
