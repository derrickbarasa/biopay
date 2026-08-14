-- 007_org_modules_and_uploads.sql
--
-- Per-organisation module toggles (households / alternates / cash transfers /
-- voucher redemption -- chosen at registration, editable later by the anchor)
-- and a household bulk-upload audit trail (one row per template upload,
-- village-scoped, with per-row error detail for the uploader to review).

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'organisation_modules')
BEGIN
    CREATE TABLE organisation_modules (
        id           INT IDENTITY(1,1) PRIMARY KEY,
        partner_code VARCHAR(20) NOT NULL,
        -- HOUSEHOLDS / ALTERNATES / CASH_TRANSFERS / VOUCHERS
        module_code  VARCHAR(30) NOT NULL,
        enabled      BIT         NOT NULL DEFAULT 1,
        created_at   DATETIME    NOT NULL DEFAULT GETDATE(),
        updated_at   DATETIME    NULL,
        CONSTRAINT UQ_organisation_modules UNIQUE (partner_code, module_code)
    );
    CREATE INDEX IX_organisation_modules_partner_code ON organisation_modules(partner_code);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'household_upload_batches')
BEGIN
    CREATE TABLE household_upload_batches (
        id             INT IDENTITY(1,1) PRIMARY KEY,
        partner_code   VARCHAR(20)   NOT NULL,
        village_code   VARCHAR(20)   NOT NULL,
        file_name      VARCHAR(255)  NULL,
        total_rows     INT           NOT NULL DEFAULT 0,
        success_count  INT           NOT NULL DEFAULT 0,
        failure_count  INT           NOT NULL DEFAULT 0,
        -- JSON array of {row, message}, kept for the uploader to review after the fact.
        errors         NVARCHAR(MAX) NULL,
        created_by     VARCHAR(100)  NULL,
        created_at     DATETIME      NOT NULL DEFAULT GETDATE()
    );
    CREATE INDEX IX_household_upload_batches_partner_code ON household_upload_batches(partner_code);
END
GO
