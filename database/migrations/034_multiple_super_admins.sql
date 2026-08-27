-- 034_multiple_super_admins.sql
-- Renames the platform-owner role "System Owner" -> "Super Admin" (display rename only --
-- authorization already keys off users.is_system_admin / the JWT systemAdmin claim, never off
-- role_name, so this is safe) and removes the filtered unique index that capped the platform to
-- exactly one such account. That cap was a 028 safety guard against an *accidental* second
-- owner, not a deliberate one-owner business rule -- multiple Super Admins, each able to create
-- more, is now an explicit product decision.

UPDATE roles SET role_name='Super Admin', updated_at=GETDATE()
WHERE role_name='System Owner' AND anchor_id IS NULL AND role_scope='SYSTEM';
GO

IF EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('users') AND name='UX_users_single_system_owner')
    DROP INDEX UX_users_single_system_owner ON users;
GO
