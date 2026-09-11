-- 044_platform_owner_rename.sql
-- Renames the platform-owner role "Super Admin" -> "Platform Owner" (display rename only --
-- authorization already keys off users.is_system_admin / the JWT systemAdmin claim, never off
-- role_name, exactly as 034_multiple_super_admins.sql already established when it did the
-- previous rename "System Owner" -> "Super Admin"; this migration just continues that lineage
-- one step further). The backend's "reserved/built-in role name" checks (Administration.java's
-- saveRole/deleteRole/getRoles) were updated in the same change to compare against the new name.

UPDATE roles SET role_name='Platform Owner', updated_at=GETDATE()
WHERE role_name='Super Admin' AND anchor_id IS NULL AND role_scope='SYSTEM';
GO
