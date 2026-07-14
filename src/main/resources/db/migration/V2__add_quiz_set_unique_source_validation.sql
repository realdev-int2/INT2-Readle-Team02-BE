-- =========================================================
-- Add UNIQUE constraint on quiz_set.source_validation_id
-- =========================================================

ALTER TABLE quiz_set ADD CONSTRAINT uq_quiz_set_source_validation UNIQUE (source_validation_id);
