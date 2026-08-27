-- 036_first_login_password_change.sql
--
-- Backs a real forced-password-change flow: any account whose password was
-- system-generated (anchor creation, organisation creation, dashboard user
-- creation, field-officer registration) is flagged must_change_password=1
-- until CHANGE_PASSWORD clears it. Self-service signup and a normal
-- self-chosen password never set it.
--
-- Also removes the unused `website` field from anchors (users) and
-- organizations -- never shown on either create/edit form, and not part of
-- any workflow; a plain dead column.

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('users') AND name = 'must_change_password')
    ALTER TABLE users ADD must_change_password BIT NOT NULL DEFAULT 0;
GO
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('field_officers') AND name = 'must_change_password')
    ALTER TABLE field_officers ADD must_change_password BIT NOT NULL DEFAULT 0;
GO

IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('users') AND name = 'website')
    ALTER TABLE users DROP COLUMN website;
GO
IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('organizations') AND name = 'website')
    ALTER TABLE organizations DROP COLUMN website;
GO
