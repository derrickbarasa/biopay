-- 043_subscription_billing.sql
-- Backing tables for the subscription/billing overhaul:
--  - billing_settings: one-row platform-wide default subscription price, set by the platform
--    (system) owner from the new Billing page. Falls back to this when an anchor has no
--    override in subscription_prices.
--  - subscription_prices: per-anchor subscription price override, also set from Billing.
--  - subscription_payment_requests: every payment attempt/intent against a subscription --
--    an anchor's own Card/Mobile Money submission (no gateway is wired up yet -- see
--    Subscription.java's class doc -- so these start PENDING and a platform owner manually
--    confirms them once the money is actually seen, e.g. on a bank/M-Pesa statement), or a
--    platform-owner-initiated Push Payment Link sent to the anchor by email. Deliberately
--    does NOT store card numbers/CVV anywhere -- only a payment method, and for mobile money
--    a phone number, both non-sensitive.

IF OBJECT_ID('billing_settings', 'U') IS NULL
BEGIN
    CREATE TABLE billing_settings (
        id               INT           NOT NULL PRIMARY KEY CHECK (id = 1),
        default_amount   DECIMAL(14,2) NOT NULL DEFAULT 0,
        default_currency VARCHAR(10)   NOT NULL DEFAULT 'USD',
        updated_by       VARCHAR(100)  NULL,
        updated_at       DATETIME2     NULL
    );
    INSERT INTO billing_settings (id, default_amount, default_currency) VALUES (1, 0, 'USD');
END
GO

IF OBJECT_ID('subscription_prices', 'U') IS NULL
BEGIN
    CREATE TABLE subscription_prices (
        anchor_id  INT           NOT NULL PRIMARY KEY,
        amount     DECIMAL(14,2) NOT NULL,
        currency   VARCHAR(10)   NOT NULL DEFAULT 'USD',
        updated_by VARCHAR(100)  NULL,
        updated_at DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME()
    );
END
GO

IF OBJECT_ID('subscription_payment_requests', 'U') IS NULL
BEGIN
    CREATE TABLE subscription_payment_requests (
        id               BIGINT IDENTITY(1,1) PRIMARY KEY,
        anchor_id        INT           NOT NULL,
        reference        VARCHAR(60)   NOT NULL,
        method           VARCHAR(20)   NOT NULL, -- CARD | MOBILE_MONEY | CASH | PUSH_LINK
        mobile_provider  VARCHAR(20)   NULL,     -- MPESA | AIRTEL | MTN
        phone_number     VARCHAR(30)   NULL,
        amount           DECIMAL(14,2) NOT NULL,
        currency         VARCHAR(10)   NOT NULL,
        status           VARCHAR(20)   NOT NULL DEFAULT 'PENDING', -- PENDING | CONFIRMED | CANCELLED
        comment          VARCHAR(500)  NULL,
        initiated_by     VARCHAR(100)  NULL,
        initiated_role   VARCHAR(20)   NULL,     -- ANCHOR | SYSTEM
        confirmed_by     VARCHAR(100)  NULL,
        confirmed_at     DATETIME2     NULL,
        created_at       DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at       DATETIME2     NULL,
        CONSTRAINT UQ_subscription_payment_requests_reference UNIQUE (reference)
    );
    CREATE INDEX IX_subscription_payment_requests_anchor ON subscription_payment_requests(anchor_id, status);
END
GO
