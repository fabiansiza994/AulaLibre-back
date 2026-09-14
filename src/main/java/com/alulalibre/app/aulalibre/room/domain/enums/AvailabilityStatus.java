package com.alulalibre.app.aulalibre.room.domain.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Computed (never persisted) availability of a room for a given date/time
 * range or instant. See RoomAvailabilityService. Output-only — the API never
 * accepts this as input, so no {@code @JsonCreator} is needed.
 */
public enum AvailabilityStatus {
    AVAILABLE("disponible"),
    OCCUPIED("ocupado");

    private final String label;

    AvailabilityStatus(String label) {
        this.label = label;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }
}
