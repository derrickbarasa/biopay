-- 010_subscriptions.sql
--
-- Per-anchor subscription with a manual-renewal lifecycle (no external billing
-- gateway wired yet -- renewal is an explicit admin action, see RENEW_SUBSCRIPTION).
--
-- Status is derived from expires_at + grace_days, never stored, so it can never
-- drift out of date:
--   ACTIVE   : today <= expires_at
--   GRACE    : expires_at < today <= expires_at + grace_days   (default 30-day grace)
--   ARCHIVED : today > expires_at + grace_days                 (access gated until renewed)
--
-- Subscription sits at the anchor tier (the paying party); organisation and
-- officer sessions inherit their anchor's status via the anchor_id on their JWT.
-- No FK constraints, matching this database's convention. Safe to re-run.

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'subscriptions')
BEGIN
    CREATE TABLE subscriptions (
        id            INT IDENTITY(1,1) PRIMARY KEY,
        anchor_id     INT          NOT NULL,
        plan_code     VARCHAR(30)  NULL,
        expires_at    DATE         NOT NULL,
        grace_days    INT          NOT NULL DEFAULT 30,
        renewed_by    VARCHAR(100) NULL,
        renewed_at    DATETIME     NULL,
        created_at    DATETIME     NOT NULL DEFAULT GETDATE(),
        updated_at    DATETIME     NULL,
        CONSTRAINT UQ_subscriptions_anchor UNIQUE (anchor_id)
    );
    CREATE INDEX IX_subscriptions_anchor_id ON subscriptions(anchor_id);
END
GO
