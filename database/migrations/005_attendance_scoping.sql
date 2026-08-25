-- 005_attendance_scoping.sql
--
-- The pre-existing `attendances` table (biometric-verified daily clock-in
-- for cash/food-for-work programs -- payments.attendance + payments.wage
-- suggest attendance count * wage rate feeds payment calculation) predates
-- the anchor/organisation scoping introduced in 001. Bring it in line with
-- every other beneficiary-data table.

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('attendances') AND name IN ('partner_code', 'organization_code'))
    ALTER TABLE attendances ADD partner_code VARCHAR(20) NULL;
GO
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('attendances') AND name = 'anchor_id')
    ALTER TABLE attendances ADD anchor_id INT NULL;
GO
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('attendances') AND name = 'work_code')
    ALTER TABLE attendances ADD work_code VARCHAR(30) NULL;
GO
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('attendances') AND name = 'latitude')
    ALTER TABLE attendances ADD latitude VARCHAR(30) NULL;
GO
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('attendances') AND name = 'longitude')
    ALTER TABLE attendances ADD longitude VARCHAR(30) NULL;
GO
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('attendances') AND name = 'created_by')
    ALTER TABLE attendances ADD created_by VARCHAR(100) NULL;
GO

-- Best-effort backfill from households, same pattern as 001. Dynamic SQL so
-- this parses even once partner_code has been renamed away by migration 029
-- (see 001's comment for why a plain guarded UPDATE isn't enough).
IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('attendances') AND name = 'partner_code')
   AND EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('households') AND name = 'partner_code')
    EXEC('UPDATE a SET a.partner_code = h.partner_code
          FROM attendances a JOIN households h ON h.household_number = a.household_number
          WHERE a.partner_code IS NULL');
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_attendances_partner_code')
    CREATE INDEX IX_attendances_partner_code ON attendances(partner_code);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_attendances_household_number')
    CREATE INDEX IX_attendances_household_number ON attendances(household_number);
GO
