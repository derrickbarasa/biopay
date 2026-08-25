-- 013_org_household_subscription_rbac.sql
--
-- Backs the 2026-08-19 dashboard/website request batch:
--   * Organisations gain a country + verification-method (biometric/facial) field,
--     and FOOD_DISTRIBUTION becomes a valid module code (app-level list only, no
--     schema change needed for that part -- see OrgModules.java).
--   * Households gain a review workflow (PENDING/CHECKED/APPROVED/REJECTED) with
--     a mandatory reason when rejected.
--   * One designated system-admin user (admin@biopay.com) can see across every
--     anchor/organisation, instead of being scoped to just their own anchor.
--   * Subscriptions gain an invoice/payment-history trail so the new Subscription
--     dashboard page has real data to show and a receipt to print.
--
-- Idempotent, no FK constraints, matching this database's convention.

-- OBJECT_ID('partners') IS NOT NULL: skipped once migration 029 has renamed
-- partners -> organizations (the columns survive the rename).
IF OBJECT_ID('partners') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('partners') AND name = 'country')
    ALTER TABLE partners ADD country VARCHAR(80) NULL;
GO
IF OBJECT_ID('partners') IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('partners') AND name = 'verification_method')
    ALTER TABLE partners ADD verification_method VARCHAR(20) NOT NULL DEFAULT 'BIOMETRIC';
GO

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('households') AND name = 'review_status')
    ALTER TABLE households ADD review_status VARCHAR(20) NOT NULL DEFAULT 'PENDING';
GO
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('households') AND name = 'rejection_reason')
    ALTER TABLE households ADD rejection_reason VARCHAR(500) NULL;
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_households_review_status')
    CREATE INDEX IX_households_review_status ON households(review_status);
GO

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('users') AND name = 'is_system_admin')
    ALTER TABLE users ADD is_system_admin BIT NOT NULL DEFAULT 0;
GO
-- The one seeded platform-operator account (see the 2026-08-18 login-rename session)
-- becomes the cross-anchor system admin. Safe to re-run; a no-op once already set.
UPDATE users SET is_system_admin = 1 WHERE email = 'admin@biopay.com';
GO

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'subscription_invoices')
BEGIN
    CREATE TABLE subscription_invoices (
        id             INT IDENTITY(1,1) PRIMARY KEY,
        anchor_id      INT           NOT NULL,
        invoice_number VARCHAR(40)   NOT NULL UNIQUE,
        plan_code      VARCHAR(30)   NULL,
        amount         DECIMAL(14,2) NULL,
        currency       VARCHAR(10)   NULL,
        period_start   DATE          NULL,
        period_end     DATE          NOT NULL,
        status         VARCHAR(20)   NOT NULL DEFAULT 'PAID',
        created_by     VARCHAR(100)  NULL,
        created_at     DATETIME      NOT NULL DEFAULT GETDATE()
    );
    CREATE INDEX IX_subscription_invoices_anchor_id ON subscription_invoices(anchor_id);
END
GO
