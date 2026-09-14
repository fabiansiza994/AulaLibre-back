-- Demo rooms for local/dev use only. Depends on data-dev-blocks.sql having
-- already run (rooms.block_id is a NOT NULL FK into blocks) — see
-- application-dev.properties for the load order.
--
-- rooms has no unique constraint to key an ON CONFLICT off of, so each
-- INSERT ... SELECT is guarded with NOT EXISTS on (block_id, name) instead,
-- keeping this idempotent across restarts (spring.sql.init.mode=always runs
-- it every time).
--
-- date_trunc('second', now()) instead of plain now(): see data-dev-users.sql
-- for why.
--
-- Layout:
--   B1 piso 1: Salón 100-111 (Aula)
--   B1 piso 2: Salón 200-211 (Aula)
--   B2 piso 1: 2 laboratorios + 3 salas de reuniones
--   B2 piso 2: Salón 200-214 (Aula)

INSERT INTO rooms (name, block_id, floor, capacity, type, active, created_at, updated_at)
SELECT 'Salón ' || n::text, b.id, 1, 35, 'CLASSROOM', TRUE, date_trunc('second', now()), date_trunc('second', now())
FROM generate_series(100, 111) AS n
CROSS JOIN blocks b
WHERE b.code = 'B1'
  AND NOT EXISTS (
      SELECT 1 FROM rooms r WHERE r.block_id = b.id AND r.name = 'Salón ' || n::text
  );

INSERT INTO rooms (name, block_id, floor, capacity, type, active, created_at, updated_at)
SELECT 'Salón ' || n::text, b.id, 2, 35, 'CLASSROOM', TRUE, date_trunc('second', now()), date_trunc('second', now())
FROM generate_series(200, 211) AS n
CROSS JOIN blocks b
WHERE b.code = 'B1'
  AND NOT EXISTS (
      SELECT 1 FROM rooms r WHERE r.block_id = b.id AND r.name = 'Salón ' || n::text
  );

INSERT INTO rooms (name, block_id, floor, capacity, type, active, created_at, updated_at)
SELECT v.name, b.id, 1, v.capacity, v.type, TRUE, date_trunc('second', now()), date_trunc('second', now())
FROM blocks b
CROSS JOIN (VALUES
    ('Laboratorio 100', 25, 'LABORATORY'),
    ('Laboratorio 101', 25, 'LABORATORY'),
    ('Sala 102', 12, 'MEETING_ROOM'),
    ('Sala 103', 12, 'MEETING_ROOM'),
    ('Sala 104', 12, 'MEETING_ROOM')
) AS v(name, capacity, type)
WHERE b.code = 'B2'
  AND NOT EXISTS (
      SELECT 1 FROM rooms r WHERE r.block_id = b.id AND r.name = v.name
  );

INSERT INTO rooms (name, block_id, floor, capacity, type, active, created_at, updated_at)
SELECT 'Salón ' || n::text, b.id, 2, 35, 'CLASSROOM', TRUE, date_trunc('second', now()), date_trunc('second', now())
FROM generate_series(200, 214) AS n
CROSS JOIN blocks b
WHERE b.code = 'B2'
  AND NOT EXISTS (
      SELECT 1 FROM rooms r WHERE r.block_id = b.id AND r.name = 'Salón ' || n::text
  );
