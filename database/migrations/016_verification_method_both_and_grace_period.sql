-- 016_verification_method_both_and_grace_period.sql
--
-- Two unrelated small changes bundled together (both are cheap, additive tweaks to columns
-- introduced in earlier migrations, not new tables):
--
-- 1. partners.verification_method (013_org_household_subscription_rbac.sql) gains a third
--    valid value, BOTH, alongside the existing BIOMETRIC/FACIAL -- the column itself is already
--    a plain VARCHAR(20) with no CHECK constraint, so no schema change is needed here; only the
--    application-level validation in Organization.java changes. Nothing to do in this file for
--    that half.
--
-- 2. subscriptions (010_subscriptions.sql) moves from a 30-day grace period to 7 days, and gains
--    a grace_notified_at column so the new renewal-reminder job (Subscription.sendGraceReminders)
--    emails an anchor once per lapse into GRACE rather than every time it runs.
--
-- Safe to re-run.

-- grace_notified_at: NULL means "not yet emailed for the current lapse"; RENEW_SUBSCRIPTION
-- clears it back to NULL so a later lapse gets a fresh reminder.
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('subscriptions') AND name = 'grace_notified_at')
    ALTER TABLE subscriptions ADD grace_notified_at DATETIME NULL;
GO

-- Re-point existing rows still on the old 30-day default to the new 7-day policy. Guarded to
-- exactly 30 so a subscription some future admin manually customized to a different grace_days
-- value isn't silently overwritten.
UPDATE subscriptions SET grace_days = 7 WHERE grace_days = 30;
GO

-- Column DEFAULT constraints get a system-generated name unless created with one, so the
-- original 010_subscriptions.sql default (30) has to be located dynamically before it can be
-- replaced -- there is no "ALTER COLUMN ... SET DEFAULT" shorthand in T-SQL.
DECLARE @constraintName NVARCHAR(200);
SELECT @constraintName = dc.name
FROM sys.default_constraints dc
JOIN sys.columns c ON c.object_id = dc.parent_object_id AND c.column_id = dc.parent_column_id
WHERE dc.parent_object_id = OBJECT_ID('subscriptions') AND c.name = 'grace_days';

IF @constraintName IS NOT NULL AND @constraintName <> 'DF_subscriptions_grace_days'
BEGIN
    EXEC('ALTER TABLE subscriptions DROP CONSTRAINT ' + @constraintName);
END

IF NOT EXISTS (SELECT 1 FROM sys.default_constraints WHERE name = 'DF_subscriptions_grace_days')
    ALTER TABLE subscriptions ADD CONSTRAINT DF_subscriptions_grace_days DEFAULT 7 FOR grace_days;
GO
