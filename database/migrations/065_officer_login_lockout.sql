-- 065_officer_login_lockout.sql
--
-- Extends the same failed-login lockout (see 064_login_lockout.sql) to the
-- field_officers table -- the Android field-app account, backing Auth#
-- loginSupervisor. Too many consecutive failed password attempts locks the
-- account (locked_at set) until a platform owner clears it via
-- UNBLOCK_OFFICER (see Officer#unblock). failed_login_attempts resets to 0
-- on a successful password check, so it only ever counts a *consecutive*
-- run of failures.

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('field_officers') AND name = 'failed_login_attempts')
    ALTER TABLE field_officers ADD failed_login_attempts INT NOT NULL DEFAULT 0;
GO
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('field_officers') AND name = 'locked_at')
    ALTER TABLE field_officers ADD locked_at DATETIME2 NULL;
GO
