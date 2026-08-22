-- 018_system_owner.sql
--
-- The 2026-08-19 migration (013_org_household_subscription_rbac.sql) added the
-- `users.is_system_admin` flag and every backend service already branches on it
-- (see the "designated cross-anchor operator (admin@biopay.com)" comments across
-- Dashboard/Household/Officer/Organization/Payroll/Payment/Voucher/Biometric),
-- but no such user was ever actually seeded -- the 2026-08-22 request flagged
-- "I don't see the system owner" and this closes that gap.
--
-- The System Owner is a normal 'Anchor Administrator' role user (so it gets the
-- full anchor permission set and the frontend's isAnchor-gated nav) plus
-- is_system_admin=1 (so every systemAdmin(payload)/isSystemAdmin(payload) branch
-- in the backend, and useAuthStore's isSystemAdmin computed on the frontend,
-- unlock cross-anchor/cross-organisation visibility). It is attached to the
-- first seeded anchor (ANCH001) only because users.anchor_id still has to be a
-- real value for the handful of handlers that read anchorId directly even for a
-- system admin (e.g. Administration.saveRole); every read path that matters
-- (getAnchors/getUsers/Dashboard/Household/Officer/Organization/Payroll/Payment/
-- Voucher) branches on is_system_admin first and ignores that anchor_id.
--
-- Idempotent, no FK constraints, matching this database's convention. Shares the
-- same seed bcrypt(12) hash used by every other seeded account in
-- database/seed/001_seed_data.sql for the password ChangeMe123! -- change it
-- after first login.

DECLARE @passwordHash VARCHAR(100) = '$2b$12$U1MOouhIlHsECTYuuqLBPOvc0q4uWY96qrW3ktoZ7.SX5Qbw4qsg.';
DECLARE @anchorId INT = (SELECT TOP 1 id FROM anchors ORDER BY id);
DECLARE @anchorAdminRole INT = (SELECT TOP 1 id FROM roles WHERE role_name = 'Anchor Administrator');

IF @anchorId IS NOT NULL AND NOT EXISTS (SELECT 1 FROM users WHERE email = 'admin@biopay.com')
BEGIN
    INSERT INTO users (partner_code, email, username, password, first_name, other_names, role_id, active, status, anchor_id, user_scope, is_system_admin, created_by, created_at, updated_at)
    VALUES (NULL, 'admin@biopay.com', 'system.owner', @passwordHash, 'System', 'Owner', @anchorAdminRole, 1, 1, @anchorId, 'ANCHOR', 1, NULL, GETDATE(), GETDATE());
END
GO

-- Backfill in case the account already existed under a different creation path
-- (e.g. created by hand before this migration existed) without the flag set.
UPDATE users SET is_system_admin = 1 WHERE email = 'admin@biopay.com';
GO
