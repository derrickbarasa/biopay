-- 062_four_eyes_toggle.sql
--
-- The maker-checker "four eyes" rule -- a payment cycle's own creator can never also approve
-- it -- becomes a per-organisation toggle instead of being unconditional. Default 1 (TRUE)
-- preserves today's behavior for every existing organisation. Snapshotted onto payment_cycles
-- at generation time for the same reason required_approvals is (see 060): a later policy
-- change must never retroactively change the rule for a cycle already sitting
-- PENDING_APPROVAL.

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('organizations') AND name = 'four_eyes_required')
    ALTER TABLE organizations ADD four_eyes_required BIT NOT NULL DEFAULT 1;
GO

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('payment_cycles') AND name = 'four_eyes_required')
    ALTER TABLE payment_cycles ADD four_eyes_required BIT NOT NULL DEFAULT 1;
GO
