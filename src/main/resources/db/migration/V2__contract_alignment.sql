-- Aligns column names with BACKEND_API_CONTRACT.md (field names the
-- frontend actually uses): schedules.subject_or_activity -> subject,
-- room_requests.observation -> note, room_requests.rejection_reason ->
-- review_note (now a general reviewer note, set on reject).

ALTER TABLE schedules RENAME COLUMN subject_or_activity TO subject;

ALTER TABLE room_requests RENAME COLUMN observation TO note;
ALTER TABLE room_requests RENAME COLUMN rejection_reason TO review_note;
