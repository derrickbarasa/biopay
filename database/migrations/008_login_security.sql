-- 008_login_security.sql
--
-- Backs three additions to the web dashboard's login flow:
--   1. A merged anchor/organisation sign-in (no more picking a portal) that
--      always requires a second-factor OTP step -- email by default, TOTP if
--      the user has enrolled it (see users.totp_secret / totp_enabled below).
--   2. Self-service anchor signup.
--   3. Emailed password-reset links (password_reset_tokens mirrors the
--      existing refresh_tokens table's shape/lifecycle).
--
-- Officers (supervisors) are unaffected -- they authenticate through the
-- Android app, not this dashboard.

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('users') AND name = 'totp_secret')
    -- AES-256-GCM encrypted at rest via com.biopay.utilities.Crypto, same as fingerprint templates.
    ALTER TABLE users ADD totp_secret VARCHAR(500) NULL;
GO
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('users') AND name = 'totp_enabled')
    ALTER TABLE users ADD totp_enabled BIT NOT NULL DEFAULT 0;
GO

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'password_reset_tokens')
BEGIN
    CREATE TABLE password_reset_tokens (
        id          INT IDENTITY(1,1) PRIMARY KEY,
        user_id     INT          NOT NULL,
        token_hash  VARCHAR(64)  NOT NULL,
        expires_at  DATETIME     NOT NULL,
        used        BIT          NOT NULL DEFAULT 0,
        created_at  DATETIME     NOT NULL DEFAULT GETDATE(),
        CONSTRAINT UQ_password_reset_tokens_hash UNIQUE (token_hash)
    );
    CREATE INDEX IX_password_reset_tokens_user_id ON password_reset_tokens(user_id);
END
GO
