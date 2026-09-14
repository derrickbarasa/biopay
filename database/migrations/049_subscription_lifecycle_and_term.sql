-- Explicit subscription lifecycle controls and the configured renewal term.

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('subscriptions') AND name = 'lifecycle_status')
    ALTER TABLE subscriptions ADD lifecycle_status VARCHAR(20) NOT NULL CONSTRAINT DF_subscriptions_lifecycle_status DEFAULT 'ACTIVE';
GO

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('subscription_invoices') AND name = 'period_days')
    ALTER TABLE subscription_invoices ADD period_days INT NULL;
GO

UPDATE subscription_invoices
SET period_days = DATEDIFF(DAY, period_start, period_end)
WHERE period_days IS NULL AND period_start IS NOT NULL AND period_end IS NOT NULL;
GO
