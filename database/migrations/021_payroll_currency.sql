-- 021_payroll_currency.sql
--
-- Currency / exchange-rate for payroll cycles and their line items, plus
-- per-line-item rejection so a checker can knock out individual households
-- from a cycle instead of only rejecting the whole thing (REJECT_PAYROLL_ITEMS).
--
-- amount_in (what the household actually receives) is NOT stored -- it's
-- always amount * exchange_rate, computed in the JSON response (Payroll.java /
-- Payment.java), same idea as household/legal status: no redundant derived
-- column, one source of truth. `amount` on payments / `total_amount` on
-- payroll_cycles keeps meaning the source/"amount out" figure it already did.
--
-- No currency column exists on partners/anchors to inherit from, so both
-- tables default to 'USD' (this session's house-wide default) and a cycle's
-- currency/rate is instead set explicitly by the maker at generation time.
-- Nullable/defaulted so every pre-existing row stays valid. Safe to re-run.

-- OBJECT_ID('payroll_cycles') IS NOT NULL: skipped once migration 029 has
-- renamed payroll_cycles -> payment_cycles (the columns survive the rename).
IF OBJECT_ID('payroll_cycles') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('payroll_cycles') AND name = 'currency')
    ALTER TABLE payroll_cycles ADD currency VARCHAR(10) NOT NULL DEFAULT 'USD';
GO
IF OBJECT_ID('payroll_cycles') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('payroll_cycles') AND name = 'exchange_rate')
    ALTER TABLE payroll_cycles ADD exchange_rate DECIMAL(18,6) NOT NULL DEFAULT 1;
GO

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('payments') AND name = 'currency')
    ALTER TABLE payments ADD currency VARCHAR(10) NOT NULL DEFAULT 'USD';
GO
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('payments') AND name = 'exchange_rate')
    ALTER TABLE payments ADD exchange_rate DECIMAL(18,6) NOT NULL DEFAULT 1;
GO

-- Per-line-item reject (REJECT_PAYROLL_ITEMS), independent of the existing
-- whole-cycle REJECT_PAYROLL / payroll_cycles.rejection_reason. Mirrors the
-- existing approved/approved_by/approved_at columns rather than overloading
-- payments.status (0/1 there means pending/disbursed and DELETE_PAYMENT
-- already relies on that binary meaning).
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('payments') AND name = 'rejected')
    ALTER TABLE payments ADD rejected BIT NOT NULL DEFAULT 0;
GO
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('payments') AND name = 'rejected_by')
    ALTER TABLE payments ADD rejected_by INT NULL;
GO
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('payments') AND name = 'rejected_at')
    ALTER TABLE payments ADD rejected_at DATETIME NULL;
GO
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('payments') AND name = 'rejection_reason')
    ALTER TABLE payments ADD rejection_reason VARCHAR(255) NULL;
GO
