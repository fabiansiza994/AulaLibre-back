-- Demo blocks for local/dev use only.
--
-- Loaded via spring.sql.init.data-locations, only when the "dev" profile is
-- active (see application-dev.properties). Must run BEFORE data-dev-rooms.sql
-- — rooms.block_id is a NOT NULL FK into this table. Runs on every startup;
-- ON CONFLICT DO NOTHING keeps it idempotent against uk_blocks_code.
--
-- date_trunc('second', now()) instead of plain now(): see data-dev-users.sql
-- for why (BaseEntity's @PrePersist truncates to whole seconds for rows
-- created through JPA; these bypass that hook).

INSERT INTO blocks (code, name, description, active, created_at, updated_at)
VALUES ('B1', 'Bloque 1', 'Edificio principal, pisos 1 y 2.', TRUE,
        date_trunc('second', now()), date_trunc('second', now()))
ON CONFLICT (code) DO NOTHING;

INSERT INTO blocks (code, name, description, active, created_at, updated_at)
VALUES ('B2', 'Bloque 2', 'Edificio de laboratorios y aulas, pisos 1 y 2.', TRUE,
        date_trunc('second', now()), date_trunc('second', now()))
ON CONFLICT (code) DO NOTHING;
