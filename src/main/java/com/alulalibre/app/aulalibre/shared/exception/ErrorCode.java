package com.alulalibre.app.aulalibre.shared.exception;

/**
 * Centralized catalog of business error codes returned to API consumers.
 * Not every value is used yet; the goal is to avoid magic strings as
 * modules mature.
 */
public enum ErrorCode {

    // Not found
    RESOURCE_NOT_FOUND,
    BLOCK_NOT_FOUND,
    ROOM_NOT_FOUND,
    SCHEDULE_NOT_FOUND,
    USER_NOT_FOUND,
    ROOM_REQUEST_NOT_FOUND,

    // Conflict
    DUPLICATE_BLOCK_CODE,
    DUPLICATE_ROOM,
    SCHEDULE_CONFLICT,
    ROOM_NOT_AVAILABLE,
    BLOCK_HAS_ROOMS,
    ROOM_HAS_REQUESTS,
    EMAIL_ALREADY_EXISTS,
    CANNOT_DISABLE_SELF,
    CANNOT_CHANGE_OWN_ROLE,

    // Validation / business rules
    INVALID_TIME_RANGE,
    INVALID_REQUEST_STATE,
    FORBIDDEN_OPERATION,
    INVALID_REQUEST,
    INVALID_USER_ROLE,
    INVALID_PASSWORD,

    // Authentication
    INVALID_CREDENTIALS
}
