package com.alulalibre.app.aulalibre.block.application.dto.response;

public record BlockResponse(
        Long id,
        String code,
        String name,
        String description,
        boolean active) {
}
