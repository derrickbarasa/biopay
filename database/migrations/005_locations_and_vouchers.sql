-- Business terminology migration.  Legacy boma/payam columns remain during the
-- compatibility period; new code should use village/location columns.
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('households') AND name = 'village_code')
    ALTER TABLE households ADD village_code VARCHAR(20) NULL;
GO
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('households') AND name = 'location_code')
    ALTER TABLE households ADD location_code VARCHAR(20) NULL;
GO
UPDATE households SET village_code = boma_code WHERE village_code IS NULL;
UPDATE households SET location_code = payam_code WHERE location_code IS NULL;
GO

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('officer_locations') AND name = 'village_code')
    ALTER TABLE officer_locations ADD village_code VARCHAR(20) NULL;
GO
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('officer_locations') AND name = 'location_code')
    ALTER TABLE officer_locations ADD location_code VARCHAR(20) NULL;
GO
UPDATE officer_locations SET village_code = boma_code WHERE village_code IS NULL;
UPDATE officer_locations SET location_code = payam_code WHERE location_code IS NULL;
GO

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'vouchers')
BEGIN
    CREATE TABLE vouchers (
        id INT IDENTITY(1,1) PRIMARY KEY,
        voucher_code VARCHAR(40) NOT NULL UNIQUE,
        household_number VARCHAR(100) NOT NULL,
        partner_code VARCHAR(30) NOT NULL,
        anchor_id INT NULL,
        amount DECIMAL(18,2) NOT NULL,
        purpose VARCHAR(250) NULL,
        status VARCHAR(20) NOT NULL DEFAULT 'ISSUED', -- ISSUED / REDEEMED / VOID
        expires_at DATETIME NULL,
        redeemed_by_officer_id INT NULL,
        redeemed_at DATETIME NULL,
        matched_fp VARCHAR(100) NULL,
        latitude VARCHAR(50) NULL,
        longitude VARCHAR(50) NULL,
        created_by INT NULL,
        created_at DATETIME NOT NULL DEFAULT GETDATE()
    );
    CREATE INDEX IX_vouchers_household_status ON vouchers(household_number, status);
    CREATE INDEX IX_vouchers_partner_code ON vouchers(partner_code);
END
