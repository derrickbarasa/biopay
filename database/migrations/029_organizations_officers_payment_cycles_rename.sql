-- 029_organizations_officers_payment_cycles_rename.sql
--
-- The dashboard and product copy have said "Organizations", "Field Officers"
-- and "Payment Cycles" for a long time, but the underlying tables and their
-- reference columns were still named after the old internal terms (partners,
-- supervisors, payroll_cycles). This renames the physical schema to match,
-- using sp_rename so existing rows and data are preserved untouched -- this
-- never drops or recreates a table, only renames tables/columns in place.
--
-- Idempotent: every rename is guarded so a re-run after the first successful
-- run is a no-op. Safe to re-run.

-- ---- Tables ---------------------------------------------------------------

IF EXISTS (SELECT 1 FROM sys.tables WHERE name = 'partners')
   AND NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'organizations')
    EXEC sp_rename 'partners', 'organizations';
GO

IF EXISTS (SELECT 1 FROM sys.tables WHERE name = 'supervisors')
   AND NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'field_officers')
    EXEC sp_rename 'supervisors', 'field_officers';
GO

IF EXISTS (SELECT 1 FROM sys.tables WHERE name = 'payroll_cycles')
   AND NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'payment_cycles')
    EXEC sp_rename 'payroll_cycles', 'payment_cycles';
GO

-- ---- organizations' own code column (was partners.partner_id) -------------
-- This is the organization's own natural business code (e.g. "1001"), distinct
-- from the partner_code/organization_code reference columns below.

IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('organizations') AND name = 'partner_id')
   AND NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('organizations') AND name = 'organization_code')
    EXEC sp_rename 'organizations.partner_id', 'organization_code', 'COLUMN';
GO

-- ---- field_officers' own code column (was supervisors.supervisor_id) ------

IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('field_officers') AND name = 'supervisor_id')
   AND NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('field_officers') AND name = 'officer_code')
    EXEC sp_rename 'field_officers.supervisor_id', 'officer_code', 'COLUMN';
GO

-- ---- Every other table's organization reference column (was partner_code) -

IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('alternates') AND name = 'partner_code')
   AND NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('alternates') AND name = 'organization_code')
    EXEC sp_rename 'alternates.partner_code', 'organization_code', 'COLUMN';
GO
IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('attendances') AND name = 'partner_code')
   AND NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('attendances') AND name = 'organization_code')
    EXEC sp_rename 'attendances.partner_code', 'organization_code', 'COLUMN';
GO
IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('audit_logs') AND name = 'partner_code')
   AND NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('audit_logs') AND name = 'organization_code')
    EXEC sp_rename 'audit_logs.partner_code', 'organization_code', 'COLUMN';
GO
IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('faces') AND name = 'partner_code')
   AND NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('faces') AND name = 'organization_code')
    EXEC sp_rename 'faces.partner_code', 'organization_code', 'COLUMN';
GO
IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('fingerprints') AND name = 'partner_code')
   AND NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('fingerprints') AND name = 'organization_code')
    EXEC sp_rename 'fingerprints.partner_code', 'organization_code', 'COLUMN';
GO
IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('household_upload_batches') AND name = 'partner_code')
   AND NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('household_upload_batches') AND name = 'organization_code')
    EXEC sp_rename 'household_upload_batches.partner_code', 'organization_code', 'COLUMN';
GO
IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('households') AND name = 'partner_code')
   AND NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('households') AND name = 'organization_code')
    EXEC sp_rename 'households.partner_code', 'organization_code', 'COLUMN';
GO
IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('images') AND name = 'partner_code')
   AND NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('images') AND name = 'organization_code')
    EXEC sp_rename 'images.partner_code', 'organization_code', 'COLUMN';
GO
IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('organisation_modules') AND name = 'partner_code')
   AND NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('organisation_modules') AND name = 'organization_code')
    EXEC sp_rename 'organisation_modules.partner_code', 'organization_code', 'COLUMN';
GO
IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('payments') AND name = 'partner_code')
   AND NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('payments') AND name = 'organization_code')
    EXEC sp_rename 'payments.partner_code', 'organization_code', 'COLUMN';
GO
IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('payment_cycles') AND name = 'partner_code')
   AND NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('payment_cycles') AND name = 'organization_code')
    EXEC sp_rename 'payment_cycles.partner_code', 'organization_code', 'COLUMN';
GO
IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('roles') AND name = 'partner_code')
   AND NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('roles') AND name = 'organization_code')
    EXEC sp_rename 'roles.partner_code', 'organization_code', 'COLUMN';
GO
IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('users') AND name = 'partner_code')
   AND NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('users') AND name = 'organization_code')
    EXEC sp_rename 'users.partner_code', 'organization_code', 'COLUMN';
GO
IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('vouchers') AND name = 'partner_code')
   AND NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('vouchers') AND name = 'organization_code')
    EXEC sp_rename 'vouchers.partner_code', 'organization_code', 'COLUMN';
GO
IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('field_officers') AND name = 'partner_code')
   AND NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('field_officers') AND name = 'organization_code')
    EXEC sp_rename 'field_officers.partner_code', 'organization_code', 'COLUMN';
GO

-- ---- Every table's field-officer reference column (was supervisor_id) -----

IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('alternates') AND name = 'supervisor_id')
   AND NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('alternates') AND name = 'officer_code')
    EXEC sp_rename 'alternates.supervisor_id', 'officer_code', 'COLUMN';
GO
IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('attendances') AND name = 'supervisor_id')
   AND NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('attendances') AND name = 'officer_code')
    EXEC sp_rename 'attendances.supervisor_id', 'officer_code', 'COLUMN';
GO
IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('faces') AND name = 'supervisor_id')
   AND NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('faces') AND name = 'officer_code')
    EXEC sp_rename 'faces.supervisor_id', 'officer_code', 'COLUMN';
GO
IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('fingerprints') AND name = 'supervisor_id')
   AND NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('fingerprints') AND name = 'officer_code')
    EXEC sp_rename 'fingerprints.supervisor_id', 'officer_code', 'COLUMN';
GO
IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('households') AND name = 'supervisor_id')
   AND NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('households') AND name = 'officer_code')
    EXEC sp_rename 'households.supervisor_id', 'officer_code', 'COLUMN';
GO
IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('images') AND name = 'supervisor_id')
   AND NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('images') AND name = 'officer_code')
    EXEC sp_rename 'images.supervisor_id', 'officer_code', 'COLUMN';
GO
IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('officer_locations') AND name = 'supervisor_id')
   AND NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('officer_locations') AND name = 'officer_code')
    EXEC sp_rename 'officer_locations.supervisor_id', 'officer_code', 'COLUMN';
GO
IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('payments') AND name = 'supervisor_id')
   AND NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('payments') AND name = 'officer_code')
    EXEC sp_rename 'payments.supervisor_id', 'officer_code', 'COLUMN';
GO

-- ---- payments' payment-cycle reference column (was payroll_cycle_id) ------

IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('payments') AND name = 'payroll_cycle_id')
   AND NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('payments') AND name = 'payment_cycle_id')
    EXEC sp_rename 'payments.payroll_cycle_id', 'payment_cycle_id', 'COLUMN';
GO
