-- Demo users for local/dev use only.
--
-- Loaded via spring.sql.init.data-locations, which only runs (mode=always)
-- when the "dev" profile is active — see application-dev.properties. Runs on
-- every startup; ON CONFLICT DO NOTHING keeps it idempotent against
-- uk_app_users_email so re-running it never duplicates or errors.
--
-- Passwords below are pre-computed BCrypt hashes (same BCryptPasswordEncoder
-- AuthService uses) of the plaintext passwords documented in
-- SECURITY_IMPLEMENTATION_REPORT.md #8 — never stored or logged in plaintext.
--
-- date_trunc('second', now()) instead of plain now(): BaseEntity's
-- @PrePersist truncates createdAt/updatedAt to whole seconds for every row
-- created through JPA (API_ENDPOINTS.md's "sin fracción de segundo"
-- convention); rows inserted here bypass that hook, so they'd carry
-- microseconds unless truncated the same way explicitly.

INSERT INTO app_users (first_name, last_name, email, role, active, password_hash, created_at, updated_at)
VALUES ('Ana', 'Estudiante', 'student@aulalibre.edu', 'STUDENT', TRUE,
        '$2a$10$YDzlFoQySflFiowkeeiDjOnLZG7izBnquNvCt.OlTuEAKQg5DIJUa',
        date_trunc('second', now()), date_trunc('second', now()))
ON CONFLICT (email) DO NOTHING;

INSERT INTO app_users (first_name, last_name, email, role, active, password_hash, created_at, updated_at)
VALUES ('Juan Carlos', 'Pérez', 'professor@aulalibre.edu', 'PROFESSOR', TRUE,
        '$2a$10$Xv6ZRIbJz5sqQNY3W.GtvOD0TfMccFbUyZlTfrHQSeM9fgNP9IMvy',
        date_trunc('second', now()), date_trunc('second', now()))
ON CONFLICT (email) DO NOTHING;

INSERT INTO app_users (first_name, last_name, email, role, active, password_hash, created_at, updated_at)
VALUES ('Laura', 'Administradora', 'admin@aulalibre.edu', 'ADMIN', TRUE,
        '$2a$10$MqSKtHdfZbRXiXtOTCFjzOEJbeK/q3ROSNkYYXgE4dvQrTT8KGhgy',
        date_trunc('second', now()), date_trunc('second', now()))
ON CONFLICT (email) DO NOTHING;
