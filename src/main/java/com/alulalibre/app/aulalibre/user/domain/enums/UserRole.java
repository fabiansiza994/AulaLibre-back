package com.alulalibre.app.aulalibre.user.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Persisted as STRING using the English constant name (see RoomRequest/User
 * entities). The Spanish label is only the JSON wire representation required
 * by BACKEND_API_CONTRACT.md — the domain itself stays language-neutral.
 */
public enum UserRole {
    STUDENT("estudiante"),
    PROFESSOR("profesor"),
    ADMIN("administrador");

    private final String label;

    UserRole(String label) {
        this.label = label;
    }

    @JsonValue
    public String getLabel() {
        return label;
    }

    @JsonCreator
    public static UserRole fromLabel(String label) {
        for (UserRole role : values()) {
            if (role.label.equalsIgnoreCase(label)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Rol de usuario inválido: " + label);
    }
}
