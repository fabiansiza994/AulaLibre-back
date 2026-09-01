package com.alulalibre.app.aulalibre.shared.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bound from {@code aulalibre.jwt.*} (see application.properties), backed by
 * {@code JWT_SECRET} / {@code JWT_ACCESS_TOKEN_EXPIRATION_MINUTES} env vars.
 * Centralized here instead of scattered {@code System.getenv()} calls.
 */
@ConfigurationProperties(prefix = "aulalibre.jwt")
public record JwtProperties(String secret, long expirationMinutes) {
}
