-- ============================================================================
-- Atria Flyway Migration V2 (H2 Compatibility Mode)
-- Add nullable hire_date (DATE) to iam_user for seniority-based leave grants (ADR-0008 Decision 11)
-- ============================================================================

ALTER TABLE iam_user ADD COLUMN hire_date DATE;
