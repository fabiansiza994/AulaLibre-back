package com.alulalibre.app.aulalibre.room.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Persisted as STRING using the English constant name. The Spanish label is
 * the JSON wire representation required by BACKEND_API_CONTRACT.md
 * (ROOM_TYPES). Limited to the 3 values the frontend can actually create or
 * filter by — no speculative types the UI has no way to render.
 */
public enum RoomType {
    CLASSROOM("Aula"),
    LABORATORY("Laboratorio"),
    MEETING_ROOM("Sala de reuniones");

    private final String label;

    RoomType(String label) {
        this.label = label;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }

    @JsonCreator
    public static RoomType fromLabel(String label) {
        for (RoomType type : values()) {
            if (type.label.equalsIgnoreCase(label)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Tipo de salón inválido: " + label);
    }
}
