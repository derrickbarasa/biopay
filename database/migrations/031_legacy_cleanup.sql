-- 031_legacy_cleanup.sql
--
-- Removes leftover objects from an earlier schema iteration that the app
-- stopped using but the database never dropped: the `audits` table
-- (superseded by `audit_logs`; confirmed empty and unreferenced anywhere in
-- backend/src or the migration history), the vwalternates/vwhouseholds/
-- vwpayments/vwfingerprints views (unreferenced anywhere in backend/src --
-- also would have broken silently after migration 029's column renames,
-- since SQL Server does not update a view's stored SELECT text when the
-- underlying columns it names are renamed), and payments.payment_cycle (a
-- write-only column: populated on insert, never read back by that name
-- anywhere in the codebase -- payments.payroll_cycle_id, since migration 029
-- renamed to payment_cycle_id, is the real foreign reference, and
-- payments.cycle, read in several places, is the display label).
--
-- Idempotent: every drop is guarded. Safe to re-run.

IF OBJECT_ID('vwalternates', 'V') IS NOT NULL DROP VIEW vwalternates;
GO
IF OBJECT_ID('vwhouseholds', 'V') IS NOT NULL DROP VIEW vwhouseholds;
GO
IF OBJECT_ID('vwpayments', 'V') IS NOT NULL DROP VIEW vwpayments;
GO
IF OBJECT_ID('vwfingerprints', 'V') IS NOT NULL DROP VIEW vwfingerprints;
GO

IF OBJECT_ID('audits', 'U') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM audits)
    DROP TABLE audits;
GO

IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('payments') AND name = 'payment_cycle')
    ALTER TABLE payments DROP COLUMN payment_cycle;
GO
