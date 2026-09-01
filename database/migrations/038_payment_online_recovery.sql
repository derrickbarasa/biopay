-- 038_payment_online_recovery.sql
-- Lets a failed field payment (status=2) be recovered by paying it online from
-- the dashboard, gated by a new PAY_ONLINE permission. Unlike the other
-- dashboard-area permissions in 025_dashboard_access_permissions.sql, this one
-- is deliberately NOT auto-granted to Anchor Administrator/Organisation
-- Administrator -- only the System Owner (who bypasses permission checks
-- entirely, see TenantScope.isSystemOwner) sees "Pay Online" by default. Any
-- other role only gets it once the System Owner explicitly assigns it from
-- the Roles page.

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('payments') AND name = 'payment_channel')
    ALTER TABLE payments ADD payment_channel VARCHAR(20) NULL;
GO
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('payments') AND name = 'online_reference')
    ALTER TABLE payments ADD online_reference VARCHAR(100) NULL;
GO

-- Backfill so already-paid rows read as the cash channel they actually were.
UPDATE payments SET payment_channel = 'CASH' WHERE payment_channel IS NULL AND status = 1;
GO

IF NOT EXISTS (SELECT 1 FROM permissions WHERE permission_name = 'PAY_ONLINE')
    INSERT INTO permissions (permission_name, display_name, permission_group, description, system_defined, created_at)
    VALUES ('PAY_ONLINE', 'Pay online', 'HOUSEHOLDS_ALTERNATES',
        'Recover a failed field payment by paying it online. Only the System Owner has this by default -- grant it to a role from the Roles page to extend it.',
        1, GETDATE());
GO
