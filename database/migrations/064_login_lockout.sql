-- 064_login_lockout.sql
--
-- Backs an account-lockout flow for the users table (dashboard/anchor/
-- organisation/system accounts): too many consecutive failed password
-- attempts locks the account (locked_at set) instead of allowing unlimited
-- guessing. A locked account can't sign in even with the right password
-- until a platform owner clears it via UNBLOCK_USER (see Administration#
-- unblockUser). failed_login_attempts resets to 0 on a successful password
-- check, so it only ever counts a *consecutive* run of failures.

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('users') AND name = 'failed_login_attempts')
    ALTER TABLE users ADD failed_login_attempts INT NOT NULL DEFAULT 0;
GO
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('users') AND name = 'locked_at')
    ALTER TABLE users ADD locked_at DATETIME2 NULL;
GO
