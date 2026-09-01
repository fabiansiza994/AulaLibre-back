package com.alulalibre.app.aulalibre.roomrequest.application.dto.response;

/**
 * {@code GET /room-requests/my/counts} — BACKEND_API_CONTRACT.md §7.2. Field
 * names are the literal Spanish JSON keys the tabs read
 * ({@code todos/pendiente/aprobada/rechazada/cancelada}); introducing an
 * English-named DTO with a translation layer for five fixed keys would only
 * add indirection.
 */
public record RoomRequestCountsResponse(
        long todos,
        long pendiente,
        long aprobada,
        long rechazada,
        long cancelada) {
}
