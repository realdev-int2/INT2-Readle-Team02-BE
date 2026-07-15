-- V4__add_grading_status.sql
-- Drop the existing constraint
ALTER TABLE quiz_attempt DROP CONSTRAINT chk_attempt_status;

-- Add the new constraint with GRADING status included
ALTER TABLE quiz_attempt ADD CONSTRAINT chk_attempt_status CHECK (status IN ('IN_PROGRESS', 'GRADING', 'SUBMITTED'));
