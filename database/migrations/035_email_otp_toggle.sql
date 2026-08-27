-- 035_email_otp_toggle.sql
--
-- Lets a user (anchor/organisation web login) turn EMAIL off as a login OTP
-- channel once they've enrolled an authenticator app -- mirrors totp_enabled
-- (008_login_security.sql) but defaults ON, since email is the fallback every
-- account starts with. Auth.startOtpChallenge (backend) still fails safe: if
-- both this and totp_enabled were ever 0 on the same row, EMAIL is offered
-- anyway rather than locking the account out of its own login.

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('users') AND name = 'email_otp_enabled')
    ALTER TABLE users ADD email_otp_enabled BIT NOT NULL DEFAULT 1;
GO
