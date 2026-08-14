-- 001_seed_data.sql
--
-- Minimal demo data on top of the existing live biopay database:
--   - 1 anchor (links to the pre-existing "Alpha bank" partner)
--   - 1 additional organisation (partner) so there are two to browse
--   - 1 anchor-admin user, 1 organisation-admin user for the new org
--   - the 3 pre-existing supervisors backfilled with partner_code/anchor_id
--   - 1 new supervisor under the new organisation
--   - 2 sample households (the households table was empty)
--
-- All seeded accounts share one bcrypt(12) password hash for
--   ChangeMe123!
-- generated with at.favre.lib:bcrypt-compatible output ($2b$). Change these
-- passwords after first login. Existing rows (partners id=1, supervisors
-- 83-85, users 1/2/1024/1029/1030/1035/1036) are only backfilled with new
-- columns -- none of their existing data is overwritten.

DECLARE @passwordHash VARCHAR(100) = '$2b$12$U1MOouhIlHsECTYuuqLBPOvc0q4uWY96qrW3ktoZ7.SX5Qbw4qsg.';

-- 1. Anchor -------------------------------------------------------------
IF NOT EXISTS (SELECT 1 FROM anchors WHERE anchor_code = 'ANCH001')
BEGIN
    INSERT INTO anchors (anchor_code, name, authorised_name, authorised_email, authorised_contact, address, status, created_by)
    VALUES ('ANCH001', 'Frontier Trust Bank', 'System Administrator', 'admin@frontiertrust.bank', '+211900000000', 'Juba, South Sudan', 1, 'SEED');
END
GO

-- 2. Link the existing partner (organisation) to the anchor -------------
DECLARE @anchorId INT = (SELECT id FROM anchors WHERE anchor_code = 'ANCH001');
UPDATE partners SET anchor_id = @anchorId, status = 1 WHERE partner_id = '1001' AND anchor_id IS NULL;
GO

-- 3. A second organisation ----------------------------------------------
DECLARE @anchorId2 INT = (SELECT id FROM anchors WHERE anchor_code = 'ANCH001');
IF NOT EXISTS (SELECT 1 FROM partners WHERE partner_id = '1002')
BEGIN
    INSERT INTO partners (partner_id, name, types, authorised_name, authorised_email, authorised_contact, address, anchor_id, status, created_by, created_at, updated_at)
    VALUES ('1002', 'Bright Future NGO', '1', 'Grace Ochan', 'admin@brightfuture.org', '+211911111111', 'Bentiu, South Sudan', @anchorId2, 1, 'SEED', GETDATE(), GETDATE());
END
GO

-- 4. Anchor admin user (full cross-organisation access) -----------------
DECLARE @anchorId3 INT = (SELECT id FROM anchors WHERE anchor_code = 'ANCH001');
DECLARE @pw VARCHAR(100) = '$2b$12$U1MOouhIlHsECTYuuqLBPOvc0q4uWY96qrW3ktoZ7.SX5Qbw4qsg.';
IF NOT EXISTS (SELECT 1 FROM users WHERE email = 'anchor.admin@frontiertrust.bank')
BEGIN
    -- users.created_by is a legacy INT column (creating user's id), not text -- NULL for seed rows.
    INSERT INTO users (partner_code, email, username, password, first_name, other_names, role_id, active, status, anchor_id, user_scope, created_by, created_at, updated_at)
    VALUES (NULL, 'anchor.admin@frontiertrust.bank', 'anchor.admin', @pw, 'Anchor', 'Administrator', 1, 1, 1, @anchorId3, 'ANCHOR', NULL, GETDATE(), GETDATE());
END
GO

-- 5. Organisation admin user for the new org (Bright Future NGO) --------
DECLARE @anchorId4 INT = (SELECT id FROM anchors WHERE anchor_code = 'ANCH001');
DECLARE @pw2 VARCHAR(100) = '$2b$12$U1MOouhIlHsECTYuuqLBPOvc0q4uWY96qrW3ktoZ7.SX5Qbw4qsg.';
IF NOT EXISTS (SELECT 1 FROM users WHERE email = 'admin@brightfuture.org')
BEGIN
    INSERT INTO users (partner_code, email, username, password, first_name, other_names, role_id, active, status, anchor_id, user_scope, created_by, created_at, updated_at)
    VALUES ('1002', 'admin@brightfuture.org', 'brightfuture.admin', @pw2, 'Grace', 'Ochan', 1, 1, 1, @anchorId4, 'ORGANISATION', NULL, GETDATE(), GETDATE());
END
GO

-- 6. Backfill the pre-existing supervisors (officers) under partner 1001 --
DECLARE @anchorId5 INT = (SELECT id FROM anchors WHERE anchor_code = 'ANCH001');
UPDATE supervisors SET partner_code = '1001', anchor_id = @anchorId5
WHERE id IN (83, 84, 85) AND partner_code IS NULL;
GO

-- 7. A new supervisor (officer) under the new organisation --------------
DECLARE @anchorId6 INT = (SELECT id FROM anchors WHERE anchor_code = 'ANCH001');
DECLARE @pw3 VARCHAR(100) = '$2b$12$U1MOouhIlHsECTYuuqLBPOvc0q4uWY96qrW3ktoZ7.SX5Qbw4qsg.';
IF NOT EXISTS (SELECT 1 FROM supervisors WHERE email = 'agent@brightfuture.org')
BEGIN
    -- supervisors.created_by is a legacy INT column (creating user's id), not text -- NULL for seed rows.
    INSERT INTO supervisors (supervisor_id, username, email, password, firstname, lastname, partner_code, role, active, anchor_id, created_by, created_at)
    VALUES (9600, 'bf.agent1', 'agent@brightfuture.org', @pw3, 'Peter', 'Deng', '1002', 2, '1', @anchorId6, NULL, GETDATE());
END
GO

-- 8. Sample households (households table was empty) ---------------------
IF NOT EXISTS (SELECT 1 FROM households WHERE household_number = 'HH-SEED-0001')
BEGIN
    INSERT INTO households (
        supervisor_id, partner_code, household_number, beneficiary_type, household_name, age,
        gender, phone_number, household_size, state_code, county_code, payam_code, boma_code,
        status, created_by, created_at, updated_at
    ) VALUES (
        '83', '1001', 'HH-SEED-0001', '1', 'Nyandeng Malual', 42,
        'F', '+211920000001', 5, '5000', '5100', '5100', '5100',
        1, 'SEED', GETDATE(), GETDATE()
    );
END
GO

IF NOT EXISTS (SELECT 1 FROM households WHERE household_number = 'HH-SEED-0002')
BEGIN
    INSERT INTO households (
        supervisor_id, partner_code, household_number, beneficiary_type, household_name, age,
        gender, phone_number, household_size, state_code, county_code, payam_code, boma_code,
        status, created_by, created_at, updated_at
    ) VALUES (
        '9600', '1002', 'HH-SEED-0002', '1', 'Peter Gatkuoth', 55,
        'M', '+211920000002', 3, '6000', '6100', '6100', '6100',
        1, 'SEED', GETDATE(), GETDATE()
    );
END
GO
