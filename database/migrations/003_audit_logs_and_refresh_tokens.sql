-- 003_audit_logs_and_refresh_tokens.sql
--
-- audit_logs: compliance trail for payment creation, payroll approval and
-- biometric verification events (spec's "Security & Compliance" section).
-- Distinct from the pre-existing `audits` table, which already tracks
-- generic web-portal user activity (user_id, activity, ip_address) and is
-- left untouched here.
--
-- refresh_tokens: backs POST /api/auth/refresh. Tokens are rotated on every
-- use (replaced_by_id chains old -> new) so a stolen/replayed refresh token
-- can be detected and the chain revoked.

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'audit_logs')
BEGIN
    CREATE TABLE audit_logs (
        id            INT IDENTITY(1,1) PRIMARY KEY,
        actor_type    VARCHAR(20)   NOT NULL,  -- ANCHOR_USER / ORGANISATION_USER / SUPERVISOR / SYSTEM
        actor_id      INT           NULL,
        anchor_id     INT           NULL,
        partner_code  VARCHAR(20)   NULL,
        action        VARCHAR(60)   NOT NULL,  -- PAYMENT_CREATED, PAYROLL_APPROVED, BIOMETRIC_VERIFIED, ...
        entity_type   VARCHAR(40)   NULL,
        entity_id     VARCHAR(60)   NULL,
        details       NVARCHAR(MAX) NULL,
        ip_address    VARCHAR(50)   NULL,
        created_at    DATETIME      NOT NULL DEFAULT GETDATE()
    );

    CREATE INDEX IX_audit_logs_partner_code ON audit_logs(partner_code);
    CREATE INDEX IX_audit_logs_action ON audit_logs(action);
    CREATE INDEX IX_audit_logs_created_at ON audit_logs(created_at);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'refresh_tokens')
BEGIN
    CREATE TABLE refresh_tokens (
        id              INT IDENTITY(1,1) PRIMARY KEY,
        subject_type    VARCHAR(20)   NOT NULL, -- ANCHOR_USER / ORGANISATION_USER / SUPERVISOR
        subject_id      INT           NOT NULL,
        token_hash      VARCHAR(255)  NOT NULL,
        expires_at      DATETIME      NOT NULL,
        revoked         BIT           NOT NULL DEFAULT 0,
        replaced_by_id  INT           NULL,
        created_at      DATETIME      NOT NULL DEFAULT GETDATE()
    );

    CREATE INDEX IX_refresh_tokens_subject ON refresh_tokens(subject_type, subject_id);
    CREATE INDEX IX_refresh_tokens_token_hash ON refresh_tokens(token_hash);
END
GO
