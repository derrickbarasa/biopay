-- 001_seed_data.sql
--
-- Demo data for a freshly-migrated, empty biopay database (every table
-- wiped except the system-owner user). Matches the current schema: an
-- anchor is its own row in `users` (user_scope='ANCHOR', carrying
-- anchor_code/anchor_name/phone/address/...), organisations live in
-- `organizations` (organization_code), field officers in `field_officers`
-- (officer_code).
--
-- Creates:
--   - 1 anchor: Frontier Trust Bank (ANC001)
--   - 2 organisations under it: Alpha Bank Programme (1001), Bright Future NGO (1002)
--   - 1 organisation-admin user per organisation
--   - 2 field officers (one per organisation)
--   - 2 sample households
--
-- All seeded accounts share one bcrypt(12) password hash for
--   ChangeMe123!
-- Change these passwords after first login.
--
-- Idempotent (IF NOT EXISTS guards throughout). Safe to re-run.

DECLARE @passwordHash VARCHAR(100) = '$2b$12$U1MOouhIlHsECTYuuqLBPOvc0q4uWY96qrW3ktoZ7.SX5Qbw4qsg.';

-- 1. Anchor -- its own row in `users`, not a separate table ----------------
IF NOT EXISTS (SELECT 1 FROM users WHERE email = 'anchor.admin@frontiertrust.bank')
BEGIN
    INSERT INTO users (email, username, password, first_name, other_names, role_id, active, status,
                        user_scope, anchor_code, anchor_name, phone, address, country, city, created_at, updated_at)
    VALUES ('anchor.admin@frontiertrust.bank', 'anchor.admin', @passwordHash, 'Anchor', 'Administrator',
            (SELECT TOP 1 id FROM roles WHERE role_name='Anchor Administrator' AND anchor_id IS NULL AND status=1),
            1, 1, 'ANCHOR', 'ANC001', 'Frontier Trust Bank', '+211900000000', 'Juba, South Sudan', 'South Sudan', 'Juba',
            GETDATE(), GETDATE());
    UPDATE users SET anchor_id = id WHERE email = 'anchor.admin@frontiertrust.bank';
END
GO

-- 2. Two organisations under the anchor -------------------------------------
DECLARE @anchorId INT = (SELECT id FROM users WHERE email = 'anchor.admin@frontiertrust.bank' AND user_scope = 'ANCHOR');

IF NOT EXISTS (SELECT 1 FROM organizations WHERE organization_code = '1001')
BEGIN
    INSERT INTO organizations (organization_code, name, types, authorised_name, authorised_email, authorised_contact, address, anchor_id, status, created_by, created_at, updated_at)
    VALUES ('1001', 'Alpha Bank Programme', '1', 'Programme Administrator', 'admin@alphabank.example', '+211900000001', 'Juba, South Sudan', @anchorId, 1, 'SEED', GETDATE(), GETDATE());
END
GO

DECLARE @anchorId2 INT = (SELECT id FROM users WHERE email = 'anchor.admin@frontiertrust.bank' AND user_scope = 'ANCHOR');
IF NOT EXISTS (SELECT 1 FROM organizations WHERE organization_code = '1002')
BEGIN
    INSERT INTO organizations (organization_code, name, types, authorised_name, authorised_email, authorised_contact, address, anchor_id, status, created_by, created_at, updated_at)
    VALUES ('1002', 'Bright Future NGO', '1', 'Grace Ochan', 'admin@brightfuture.org', '+211911111111', 'Bentiu, South Sudan', @anchorId2, 1, 'SEED', GETDATE(), GETDATE());
END
GO

-- Every seeded organisation starts with the complete module catalogue.
DECLARE @seedModules TABLE (module_code VARCHAR(40));
INSERT INTO @seedModules VALUES ('HOUSEHOLDS'),('ALTERNATES'),('CASH_TRANSFERS'),('VOUCHERS'),('FOOD_DISTRIBUTION');
INSERT INTO organisation_modules (organization_code,module_code,enabled,created_at)
SELECT o.organization_code,m.module_code,1,GETDATE()
FROM organizations o CROSS JOIN @seedModules m
WHERE o.organization_code IN ('1001','1002')
  AND NOT EXISTS (SELECT 1 FROM organisation_modules om WHERE om.organization_code=o.organization_code AND om.module_code=m.module_code);
GO

-- 3. Organisation admin users -------------------------------------------------
DECLARE @anchorId3 INT = (SELECT id FROM users WHERE email = 'anchor.admin@frontiertrust.bank' AND user_scope = 'ANCHOR');
DECLARE @passwordHash VARCHAR(100) = '$2b$12$U1MOouhIlHsECTYuuqLBPOvc0q4uWY96qrW3ktoZ7.SX5Qbw4qsg.';
IF NOT EXISTS (SELECT 1 FROM users WHERE email = 'admin@alphabank.example')
BEGIN
    INSERT INTO users (organization_code, email, username, password, first_name, other_names, role_id, active, status, anchor_id, user_scope, created_at, updated_at)
    VALUES ('1001', 'admin@alphabank.example', 'alphabank.admin', @passwordHash, 'Programme', 'Administrator',
            (SELECT TOP 1 id FROM roles WHERE role_name='Organisation Administrator' AND anchor_id IS NULL AND status=1),
            1, 1, @anchorId3, 'ORGANISATION', GETDATE(), GETDATE());
END
GO

DECLARE @anchorId4 INT = (SELECT id FROM users WHERE email = 'anchor.admin@frontiertrust.bank' AND user_scope = 'ANCHOR');
DECLARE @passwordHash VARCHAR(100) = '$2b$12$U1MOouhIlHsECTYuuqLBPOvc0q4uWY96qrW3ktoZ7.SX5Qbw4qsg.';
IF NOT EXISTS (SELECT 1 FROM users WHERE email = 'admin@brightfuture.org')
BEGIN
    INSERT INTO users (organization_code, email, username, password, first_name, other_names, role_id, active, status, anchor_id, user_scope, created_at, updated_at)
    VALUES ('1002', 'admin@brightfuture.org', 'brightfuture.admin', @passwordHash, 'Grace', 'Ochan',
            (SELECT TOP 1 id FROM roles WHERE role_name='Organisation Administrator' AND anchor_id IS NULL AND status=1),
            1, 1, @anchorId4, 'ORGANISATION', GETDATE(), GETDATE());
END
GO

-- 4. One field officer per organisation --------------------------------------
DECLARE @anchorId5 INT = (SELECT id FROM users WHERE email = 'anchor.admin@frontiertrust.bank' AND user_scope = 'ANCHOR');
DECLARE @passwordHash VARCHAR(100) = '$2b$12$U1MOouhIlHsECTYuuqLBPOvc0q4uWY96qrW3ktoZ7.SX5Qbw4qsg.';
IF NOT EXISTS (SELECT 1 FROM field_officers WHERE email = 'agent@alphabank.example')
BEGIN
    INSERT INTO field_officers (officer_code, username, email, password, firstname, lastname, organization_code, role, active, anchor_id, created_at)
    VALUES (9500, 'ab.agent1', 'agent@alphabank.example', @passwordHash, 'Mary', 'Achieng', '1001', 2, '1', @anchorId5, GETDATE());
END
GO

DECLARE @anchorId6 INT = (SELECT id FROM users WHERE email = 'anchor.admin@frontiertrust.bank' AND user_scope = 'ANCHOR');
DECLARE @passwordHash VARCHAR(100) = '$2b$12$U1MOouhIlHsECTYuuqLBPOvc0q4uWY96qrW3ktoZ7.SX5Qbw4qsg.';
IF NOT EXISTS (SELECT 1 FROM field_officers WHERE email = 'agent@brightfuture.org')
BEGIN
    INSERT INTO field_officers (officer_code, username, email, password, firstname, lastname, organization_code, role, active, anchor_id, created_at)
    VALUES (9600, 'bf.agent1', 'agent@brightfuture.org', @passwordHash, 'Peter', 'Deng', '1002', 2, '1', @anchorId6, GETDATE());
END
GO

-- 5. Sample households --------------------------------------------------------
IF NOT EXISTS (SELECT 1 FROM households WHERE household_number = 'HH-SEED-0001')
BEGIN
    INSERT INTO households (
        officer_code, organization_code, household_number, beneficiary_type, household_name, age,
        gender, phone_number, household_size, state_code, county_code, payam_code, boma_code,
        status, created_by, created_at, updated_at
    ) VALUES (
        '9500', '1001', 'HH-SEED-0001', '1', 'Nyandeng Malual', 42,
        'F', '+211920000001', 5, '5000', '5100', '5100', '5100',
        1, 'SEED', GETDATE(), GETDATE()
    );
END
GO

IF NOT EXISTS (SELECT 1 FROM households WHERE household_number = 'HH-SEED-0002')
BEGIN
    INSERT INTO households (
        officer_code, organization_code, household_number, beneficiary_type, household_name, age,
        gender, phone_number, household_size, state_code, county_code, payam_code, boma_code,
        status, created_by, created_at, updated_at
    ) VALUES (
        '9600', '1002', 'HH-SEED-0002', '1', 'Peter Gatkuoth', 55,
        'M', '+211920000002', 3, '6000', '6100', '6100', '6100',
        1, 'SEED', GETDATE(), GETDATE()
    );
END
GO
