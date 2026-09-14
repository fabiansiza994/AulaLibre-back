-- Adds credential storage for real authentication (Security phase).
-- Nullable on purpose: any row already in app_users predates this column and
-- has no password yet. A null passwordHash simply can never match at login
-- (BCryptPasswordEncoder#matches never succeeds against null), so those rows
-- stay permanently unable to authenticate until an admin sets a password —
-- correct behavior, and it avoids failing this migration against an
-- already-seeded dev database that has no way to backfill a real password.
ALTER TABLE app_users ADD COLUMN password_hash VARCHAR(100);
