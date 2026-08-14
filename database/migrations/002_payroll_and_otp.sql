-- 002_payroll_and_otp.sql
--
-- Payroll cycle records (the "payroll" concept referenced across the spec)
-- and the maker-checker OTP/TOTP verifications required to generate and
-- approve them. payments.payroll_cycle_id (added in 001) links individual
-- payment line items back to the cycle that produced them.

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'payroll_cycles')
BEGIN
    CREATE TABLE payroll_cycles (
        id                    INT IDENTITY(1,1) PRIMARY KEY,
        cycle_code            VARCHAR(30)   NOT NULL,
        partner_code          VARCHAR(20)   NOT NULL,
        anchor_id             INT           NULL,
        period_start          DATE          NOT NULL,
        period_end            DATE          NOT NULL,
        amount_per_household  NUMERIC(18,2) NOT NULL,
        household_count       INT           NOT NULL DEFAULT 0,
        total_amount          NUMERIC(18,2) NOT NULL DEFAULT 0,
        -- DRAFT -> PENDING_APPROVAL -> APPROVED -> DISBURSED, or REJECTED
        status                VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
        maker_id              INT           NULL,
        maker_at              DATETIME      NULL,
        checker_id            INT           NULL,
        checker_at            DATETIME      NULL,
        rejection_reason      VARCHAR(255)  NULL,
        otp_verified          BIT           NOT NULL DEFAULT 0,
        disbursed_at          DATETIME      NULL,
        created_by            VARCHAR(100)  NULL,
        created_at            DATETIME      NOT NULL DEFAULT GETDATE(),
        updated_at            DATETIME      NULL,
        CONSTRAINT UQ_payroll_cycles_cycle_code UNIQUE (cycle_code)
    );

    CREATE INDEX IX_payroll_cycles_partner_code ON payroll_cycles(partner_code);
    CREATE INDEX IX_payroll_cycles_status ON payroll_cycles(status);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'otp_verifications')
BEGIN
    CREATE TABLE otp_verifications (
        id               INT IDENTITY(1,1) PRIMARY KEY,
        -- e.g. PAYROLL_GENERATE, PAYROLL_APPROVE
        reference_type   VARCHAR(40)   NOT NULL,
        -- payroll cycle_code once known; null while generating a brand-new cycle
        reference_code   VARCHAR(30)   NULL,
        user_id          INT           NOT NULL,
        user_scope       VARCHAR(20)   NOT NULL,   -- ANCHOR / ORGANISATION
        channel          VARCHAR(10)   NOT NULL,   -- EMAIL / TOTP
        code_hash        VARCHAR(255)  NOT NULL,
        expires_at       DATETIME      NOT NULL,
        verified         BIT           NOT NULL DEFAULT 0,
        verified_at      DATETIME      NULL,
        attempts         INT           NOT NULL DEFAULT 0,
        created_at       DATETIME      NOT NULL DEFAULT GETDATE()
    );

    CREATE INDEX IX_otp_verifications_lookup ON otp_verifications(user_id, reference_type, verified);
END
GO
