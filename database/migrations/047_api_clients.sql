-- 047_api_clients.sql
--
-- Lets an anchor/organisation issue a machine credential (an "API client") that is
-- distinct from any human's dashboard login, so programmatic access can be granted and
-- revoked independently of a person's account, and every login/audit record says which
-- channel (PORTAL vs API) actually performed it.
--
-- An API client is a row in `users` (account_type='API') rather than a separate table --
-- it already has everything one needs (role_id for permissions, anchor_id/organization_code
-- for tenant scope, status for revoke/restore) and this way every existing permission check
-- (EntryPoint#authorizePermissionsAndDispatch's role_permissions join keyed on users.id) works
-- for an API client with zero changes. `username` doubles as the client's public key id;
-- `password` stores the bcrypt hash of its secret, exactly like a human's password.
--
-- Idempotent: every ADD is guarded. Safe to re-run.

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('users') AND name = 'account_type')
    ALTER TABLE users ADD account_type VARCHAR(10) NOT NULL DEFAULT 'HUMAN';
GO
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('users') AND name = 'last_login_at')
    ALTER TABLE users ADD last_login_at DATETIME NULL;
GO

-- Which channel performed an audited action. Defaults every existing/legacy row (and every
-- writer that doesn't yet pass one, e.g. Biometric.java's field-capture audit trail, which is
-- always a portal/mobile-app login) to PORTAL; only the new API_TOKEN issuance writes API.
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('audit_logs') AND name = 'channel')
    ALTER TABLE audit_logs ADD channel VARCHAR(10) NOT NULL DEFAULT 'PORTAL';
GO
