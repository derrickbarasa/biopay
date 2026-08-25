-- 001_anchors_and_scoping.sql
--
-- Introduces the anchor (bank / payment-agent) tier that sits above the
-- existing organisation records, and threads anchor_id / organisation
-- scoping through the tables that need it.
--
-- Naming: `partners` is the organisations (NGO) table and `supervisors` is
-- the officers (field agent) table in this schema -- both predate this
-- migration and are kept as-is rather than renamed, to avoid breaking any
-- other system still pointed at these table names.
--
-- Migrations 029-031 (added later) rename `partners`->`organizations`,
-- `supervisors`->`field_officers`, drop `anchors` entirely, and rename every
-- table's `partner_code` reference column to `organization_code`. Every
-- statement below that touches one of those renamed names is guarded to
-- also check whether the rename has already happened, so this migration
-- stays replayable against a database that has already been through it --
-- not just against a brand new one. Without those guards, SQL Server
-- resolves table/column names at parse time regardless of runtime branching,
-- so a plain `IF NOT EXISTS (...)` around a CREATE/ALTER is not enough once
-- the referenced object has actually been renamed away.
--
-- No FOREIGN KEY constraints are added, matching this database's existing
-- convention of loosely-coupled *_code / *_id columns with no declared FKs.
-- Safe to re-run: every DDL change is guarded with an existence check.

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'anchors')
   AND OBJECT_ID('organizations') IS NULL
BEGIN
    CREATE TABLE anchors (
        id                  INT IDENTITY(1,1) PRIMARY KEY,
        anchor_code         VARCHAR(20)  NOT NULL,
        name                VARCHAR(150) NOT NULL,
        authorised_name     VARCHAR(150) NULL,
        authorised_email    VARCHAR(150) NULL,
        authorised_contact  VARCHAR(50)  NULL,
        address             VARCHAR(255) NULL,
        website             VARCHAR(150) NULL,
        status              INT          NOT NULL DEFAULT 1,
        created_by          VARCHAR(100) NULL,
        created_at          DATETIME     NOT NULL DEFAULT GETDATE(),
        updated_at          DATETIME     NULL,
        CONSTRAINT UQ_anchors_anchor_code UNIQUE (anchor_code)
    );
END
GO

-- partners (organisations): owning anchor + activate/deactivate + audit trail.
-- (skipped once migration 029 has renamed partners -> organizations)
IF OBJECT_ID('partners') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('partners') AND name = 'anchor_id')
    ALTER TABLE partners ADD anchor_id INT NULL;
GO
IF OBJECT_ID('partners') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('partners') AND name = 'status')
    ALTER TABLE partners ADD status INT NOT NULL DEFAULT 1;
GO
IF OBJECT_ID('partners') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('partners') AND name = 'created_by')
    ALTER TABLE partners ADD created_by VARCHAR(100) NULL;
GO

-- supervisors (officers): owning anchor alongside the existing partner_code
-- (organisation) scope, plus the updated_at/updated_by columns users has.
-- (skipped once migration 029 has renamed supervisors -> field_officers)
IF OBJECT_ID('supervisors') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('supervisors') AND name = 'anchor_id')
    ALTER TABLE supervisors ADD anchor_id INT NULL;
GO
IF OBJECT_ID('supervisors') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('supervisors') AND name = 'updated_at')
    ALTER TABLE supervisors ADD updated_at DATETIME NULL;
GO
IF OBJECT_ID('supervisors') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('supervisors') AND name = 'updated_by')
    ALTER TABLE supervisors ADD updated_by VARCHAR(100) NULL;
GO

-- users (web dashboard): user_scope distinguishes an Anchor Admin
-- (cross-organisation, anchor_id set, partner_code null) from an
-- Organisation Admin (partner_code set, scoped to that organisation).
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('users') AND name = 'anchor_id')
    ALTER TABLE users ADD anchor_id INT NULL;
GO
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('users') AND name = 'user_scope')
    ALTER TABLE users ADD user_scope VARCHAR(20) NOT NULL DEFAULT 'ORGANISATION';
GO

-- Direct organisation scoping on the biometric tables, previously only
-- reachable via a join through households.household_number. Each guard also
-- checks the renamed column (organization_code) is absent, so a replay after
-- migration 029 doesn't resurrect a stray partner_code column.
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('alternates') AND name IN ('partner_code', 'organization_code'))
    ALTER TABLE alternates ADD partner_code VARCHAR(20) NULL;
GO
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('alternates') AND name = 'created_by')
    ALTER TABLE alternates ADD created_by VARCHAR(100) NULL;
GO
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('alternates') AND name = 'updated_by')
    ALTER TABLE alternates ADD updated_by VARCHAR(100) NULL;
GO

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('fingerprints') AND name IN ('partner_code', 'organization_code'))
    ALTER TABLE fingerprints ADD partner_code VARCHAR(20) NULL;
GO
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('fingerprints') AND name = 'created_by')
    ALTER TABLE fingerprints ADD created_by VARCHAR(100) NULL;
GO

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('images') AND name IN ('partner_code', 'organization_code'))
    ALTER TABLE images ADD partner_code VARCHAR(20) NULL;
GO
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('images') AND name = 'created_by')
    ALTER TABLE images ADD created_by VARCHAR(100) NULL;
GO

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('households') AND name = 'created_by')
    ALTER TABLE households ADD created_by VARCHAR(100) NULL;
GO
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('households') AND name = 'updated_by')
    ALTER TABLE households ADD updated_by VARCHAR(100) NULL;
GO

-- payments: direct organisation/anchor scoping + link to the new
-- payroll_cycles table (see 002), replacing the loose cycle/batch_no text
-- matching used until now. Nullable so historical rows are untouched.
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('payments') AND name IN ('partner_code', 'organization_code'))
    ALTER TABLE payments ADD partner_code VARCHAR(20) NULL;
GO
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('payments') AND name = 'anchor_id')
    ALTER TABLE payments ADD anchor_id INT NULL;
GO
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('payments') AND name IN ('payroll_cycle_id', 'payment_cycle_id'))
    ALTER TABLE payments ADD payroll_cycle_id INT NULL;
GO

-- Best-effort backfill of partner_code from households, for rows captured
-- before these columns existed. Only runs while the old column name is
-- still present on both sides -- once migration 029 has renamed it, this
-- backfill has either already happened or is no longer meaningful.
-- Dynamic SQL, deliberately: SQL Server resolves column names in an ad-hoc
-- batch at parse time for the WHOLE batch, even inside an IF branch that
-- never executes -- a plain `IF EXISTS(...) BEGIN UPDATE ... partner_code
-- ... END` still fails to PARSE once partner_code has been renamed away,
-- regardless of whether the IF would have been true. Wrapping the statement
-- text in EXEC() defers parsing until the IF has already decided whether to
-- run it at all.
IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('alternates') AND name = 'partner_code')
   AND EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('households') AND name = 'partner_code')
    EXEC('UPDATE a SET a.partner_code = h.partner_code
          FROM alternates a JOIN households h ON h.household_number = a.household_number
          WHERE a.partner_code IS NULL');
GO

IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('fingerprints') AND name = 'partner_code')
   AND EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('households') AND name = 'partner_code')
BEGIN
    EXEC('UPDATE f SET f.partner_code = h.partner_code
          FROM fingerprints f JOIN households h ON h.household_number = f.beneficiary_id
          WHERE f.partner_code IS NULL AND f.beneficiary_type = 1');

    EXEC('UPDATE f SET f.partner_code = h.partner_code
          FROM fingerprints f
          JOIN alternates alt ON alt.alternate_number = f.beneficiary_id
          JOIN households h ON h.household_number = alt.household_number
          WHERE f.partner_code IS NULL AND f.beneficiary_type = 2');
END
GO

IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('images') AND name = 'partner_code')
   AND EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('households') AND name = 'partner_code')
BEGIN
    EXEC('UPDATE i SET i.partner_code = h.partner_code
          FROM images i JOIN households h ON h.household_number = i.beneficiary_id
          WHERE i.partner_code IS NULL AND i.beneficiary_type = 1');

    EXEC('UPDATE i SET i.partner_code = h.partner_code
          FROM images i
          JOIN alternates alt ON alt.alternate_number = i.beneficiary_id
          JOIN households h ON h.household_number = alt.household_number
          WHERE i.partner_code IS NULL AND i.beneficiary_type = 2');
END
GO

IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('payments') AND name = 'partner_code')
   AND EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('households') AND name = 'partner_code')
    EXEC('UPDATE p SET p.partner_code = h.partner_code
          FROM payments p JOIN households h ON h.household_number = p.household_number
          WHERE p.partner_code IS NULL');
GO

-- Indexes for the new scoping columns. Skipped once the underlying table has
-- been renamed away (029) -- the index itself survives a column/table rename
-- automatically, so there is nothing left to create.
IF OBJECT_ID('partners') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_partners_anchor_id')
    CREATE INDEX IX_partners_anchor_id ON partners(anchor_id);
GO
IF OBJECT_ID('supervisors') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_supervisors_partner_code')
    CREATE INDEX IX_supervisors_partner_code ON supervisors(partner_code);
GO
IF OBJECT_ID('supervisors') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_supervisors_anchor_id')
    CREATE INDEX IX_supervisors_anchor_id ON supervisors(anchor_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_households_partner_code')
    CREATE INDEX IX_households_partner_code ON households(partner_code);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_alternates_partner_code')
    CREATE INDEX IX_alternates_partner_code ON alternates(partner_code);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_fingerprints_partner_code')
    CREATE INDEX IX_fingerprints_partner_code ON fingerprints(partner_code);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_images_partner_code')
    CREATE INDEX IX_images_partner_code ON images(partner_code);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_payments_partner_code')
    CREATE INDEX IX_payments_partner_code ON payments(partner_code);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_payments_payroll_cycle_id')
    CREATE INDEX IX_payments_payroll_cycle_id ON payments(payroll_cycle_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_users_partner_code')
    CREATE INDEX IX_users_partner_code ON users(partner_code);
GO
