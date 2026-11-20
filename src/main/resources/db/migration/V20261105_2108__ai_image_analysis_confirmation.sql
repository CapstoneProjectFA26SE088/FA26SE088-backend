-- Nguồn: docs/db/C5-community-moderation-ai-notification.sql (Cluster 5, Version v0.2 · 05/11/2026)
-- Bảng: image_analyses (is_auto_tagged -> is_confirmed, +3 cột) (+ cập nhật mô tả IMAGE_ANALYSIS_CONFIDENCE_THRESHOLD)

-- =====================================================================================
-- M-06 (Review 2, KG-09): the AI only suggests; the user confirms (UC40).
-- Nothing is tagged automatically any more. The corrected_* columns are kept: a correction
-- is still stored as a labelled pair for later tuning.
-- =====================================================================================

ALTER TABLE image_analyses DROP CONSTRAINT ck_image_analyses_auto_tag;
ALTER TABLE image_analyses RENAME COLUMN is_auto_tagged TO is_confirmed;

ALTER TABLE image_analyses ADD COLUMN confirmed_at timestamptz;
ALTER TABLE image_analyses ADD COLUMN confirmed_by_account_id bigint;
ALTER TABLE image_analyses ADD COLUMN confirmed_style_tags text[];

ALTER TABLE image_analyses ADD CONSTRAINT fk_image_analyses_confirmer
    FOREIGN KEY (confirmed_by_account_id) REFERENCES user_accounts (id);
ALTER TABLE image_analyses ADD CONSTRAINT ck_image_analyses_confirmation CHECK (
    is_confirmed = (confirmed_at IS NOT NULL)
    AND (confirmed_at IS NULL) = (confirmed_by_account_id IS NULL));

COMMENT ON COLUMN image_analyses.confirmed_style_tags IS 'Style tags kept by the user when confirming; style_tags stays the raw AI suggestion.';

UPDATE system_parameters
SET description = 'Minimum confidence for a film stock to be shown as a suggestion; the user always confirms (UC40)'
WHERE param_key = 'IMAGE_ANALYSIS_CONFIDENCE_THRESHOLD';
