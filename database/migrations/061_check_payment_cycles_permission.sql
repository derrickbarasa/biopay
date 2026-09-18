-- 061_check_payment_cycles_permission.sql
--
-- Approving/rejecting/disbursing a payment cycle, and setting an organisation's own
-- required-approvals policy, now require their own distinct permission
-- (CHECK_PAYMENT_CYCLES) rather than the general ACCESS_PAYMENT_CYCLES every payment-cycle
-- user already holds -- mirrors PAY_ONLINE's own precedent (its own permission, deliberately
-- not folded into ACCESS_PAYMENTS; see 038_payment_online_recovery.sql). This lets an
-- organisation give some users only the ability to generate/view cycles (the "maker" side)
-- while a separate, smaller set of users can approve/disburse them (the "checker" side) --
-- real separation of duties, not just the existing maker-cannot-approve-their-own-cycle
-- runtime check.
--
-- Backfilled onto every role that already holds ACCESS_PAYMENT_CYCLES so no existing role
-- silently loses the ability to approve/disburse the moment this ships; organisations can
-- then split the two permissions apart for new or edited roles going forward.

IF NOT EXISTS (SELECT 1 FROM permissions WHERE permission_name = 'CHECK_PAYMENT_CYCLES')
    INSERT INTO permissions (permission_name, display_name, permission_group, description, system_defined, created_at)
    VALUES ('CHECK_PAYMENT_CYCLES', 'Approve payment cycles', 'HOUSEHOLDS_ALTERNATES',
            'Approve, reject and disburse payment cycles, and set the organisation''s required-approvals policy', 1, GETDATE());
GO

INSERT INTO role_permissions (role_id, permission_id, status, created_at)
SELECT DISTINCT existingGrant.role_id, checkPermission.id, 1, GETDATE()
FROM role_permissions existingGrant
JOIN permissions accessPermission ON accessPermission.id = existingGrant.permission_id AND accessPermission.permission_name = 'ACCESS_PAYMENT_CYCLES'
JOIN permissions checkPermission ON checkPermission.permission_name = 'CHECK_PAYMENT_CYCLES'
WHERE existingGrant.status = 1
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions alreadyGranted
    WHERE alreadyGranted.role_id = existingGrant.role_id AND alreadyGranted.permission_id = checkPermission.id
  );
GO
