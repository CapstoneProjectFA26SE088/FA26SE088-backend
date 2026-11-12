-- =====================================================================================
-- ACC-09: Thêm trạng thái INACTIVE cho chức năng vô hiệu hoá / xoá mềm tài khoản
-- =====================================================================================

ALTER TABLE user_accounts DROP CONSTRAINT IF EXISTS ck_user_accounts_status;
ALTER TABLE user_accounts ADD CONSTRAINT ck_user_accounts_status
    CHECK (status IN ('PENDING_VERIFICATION', 'ACTIVE', 'INACTIVE', 'LOCKED', 'DELETED'));

ALTER TABLE user_accounts DROP CONSTRAINT IF EXISTS ck_user_accounts_deleted;
ALTER TABLE user_accounts ADD CONSTRAINT ck_user_accounts_deleted
    CHECK ((status IN ('DELETED', 'INACTIVE')) = (deleted_at IS NOT NULL));

