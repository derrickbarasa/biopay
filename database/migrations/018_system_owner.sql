-- 018_system_owner.sql
--
-- The 2026-08-19 migration (013_org_household_subscription_rbac.sql) added the
-- `users.is_system_admin` flag and every backend service already branches on it
-- (see the "designated cross-anchor operator (admin@biopay.com)" comments across
-- Dashboard/Household/Officer/Organization/Payroll/Payment/Voucher/Biometric),
-- but no such user was ever actually seeded -- the 2026-08-22 request flagged
-- "I don't see the system owner" and this closes that gap.
--
-- Historical bootstrap note: this migration predates the dedicated SYSTEM scope
-- and therefore creates the account through the then-current Anchor Administrator
-- compatibility path. Migration 026 removes the tenant links and migration 027
-- assigns the canonical, immutable System Owner role. On a fully migrated database
-- the owner is never an anchor member.
--
-- Idempotent, no FK constraints, matching this database's convention. Shares the
-- same seed bcrypt(12) hash used by every other seeded account in
-- database/seed/001_seed_data.sql for the password ChangeMe123! -- change it
-- after first login.

-- The historical bootstrap path (see file header) needs a real anchors row to
-- attach the account to, which only exists pre-029/030. Once anchors has been
-- merged into users, admin@biopay.com already exists from the original run
-- (migration 026 detaches it from any anchor entirely) -- this block is
-- naturally a no-op then, but the anchors reference below still needs
-- dynamic SQL so it parses at all with the table gone (see 001's comment).
IF OBJECT_ID('anchors') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM users WHERE email = 'admin@biopay.com')
BEGIN
    DECLARE @passwordHash VARCHAR(100) = '$2b$12$U1MOouhIlHsECTYuuqLBPOvc0q4uWY96qrW3ktoZ7.SX5Qbw4qsg.';
    DECLARE @anchorId INT;
    EXEC sp_executesql N'SELECT @id = (SELECT TOP 1 id FROM anchors ORDER BY id)', N'@id INT OUTPUT', @id = @anchorId OUTPUT;
    DECLARE @anchorAdminRole INT = (SELECT TOP 1 id FROM roles WHERE role_name = 'Anchor Administrator');

    IF @anchorId IS NOT NULL
        EXEC sp_executesql N'INSERT INTO users (partner_code, email, username, password, first_name, other_names, role_id, active, status, anchor_id, user_scope, is_system_admin, created_by, created_at, updated_at)
            VALUES (NULL, ''admin@biopay.com'', ''system.owner'', @pw, ''System'', ''Owner'', @role, 1, 1, @anchor, ''ANCHOR'', 1, NULL, GETDATE(), GETDATE())',
            N'@pw VARCHAR(100), @role INT, @anchor INT', @pw = @passwordHash, @role = @anchorAdminRole, @anchor = @anchorId;
END
GO

-- Backfill in case the account already existed under a different creation path
-- (e.g. created by hand before this migration existed) without the flag set.
UPDATE users SET is_system_admin = 1 WHERE email = 'admin@biopay.com';
GO
