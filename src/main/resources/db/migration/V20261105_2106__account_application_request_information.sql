-- Nguồn: docs/db/C1-account-masterdata-lab.sql (Cluster 1, Version v0.4 · 05/11/2026)
-- Bảng: lab_registration_applications (trạng thái AWAITING_INFORMATION, +2 cột)

-- =====================================================================================
-- M-03 (Review 2, KG-05): the Admin can ask the applicant for more information (UC08)
-- instead of only approving or rejecting. expert_upgrade_applications is not changed.
-- =====================================================================================

ALTER TABLE lab_registration_applications DROP CONSTRAINT ck_lab_registration_applications_status;
ALTER TABLE lab_registration_applications ADD CONSTRAINT ck_lab_registration_applications_status
    CHECK (status IN ('PENDING_REVIEW', 'AWAITING_INFORMATION', 'APPROVED', 'REJECTED'));

-- What the Admin asks for, and when. Kept after the applicant answers (last request).
ALTER TABLE lab_registration_applications ADD COLUMN info_request_note varchar(500);
ALTER TABLE lab_registration_applications ADD COLUMN info_requested_at timestamptz;

ALTER TABLE lab_registration_applications ADD CONSTRAINT ck_lab_registration_applications_info_note
    CHECK (info_request_note IS NULL OR char_length(info_request_note) BETWEEN 10 AND 500);
ALTER TABLE lab_registration_applications ADD CONSTRAINT ck_lab_registration_applications_info_request
    CHECK (status <> 'AWAITING_INFORMATION' OR (info_request_note IS NOT NULL AND info_requested_at IS NOT NULL));
