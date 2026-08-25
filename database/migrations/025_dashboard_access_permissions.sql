-- 025_dashboard_access_permissions.sql
-- Dashboard-area permissions, grouped exactly as they appear in the role editor.

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('permissions') AND name = 'permission_group')
    ALTER TABLE permissions ADD permission_group VARCHAR(40) NULL;
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('permissions') AND name = 'display_name')
    ALTER TABLE permissions ADD display_name VARCHAR(100) NULL;
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('permissions') AND name = 'system_defined')
    ALTER TABLE permissions ADD system_defined BIT NOT NULL DEFAULT 0;
GO

DECLARE @permissions TABLE (
  permission_name VARCHAR(100), display_name VARCHAR(100), permission_group VARCHAR(40), description VARCHAR(255)
);
INSERT INTO @permissions VALUES
  ('VIEW_REPORTS', 'View reports', 'REPORTS', 'View the dashboard, charts and operational reports'),
  ('DOWNLOAD_REPORTS', 'Download reports', 'REPORTS', 'Download household, alternate, payment and attendance reports'),
  ('ACCESS_HOUSEHOLDS', 'Households', 'HOUSEHOLDS_ALTERNATES', 'View and manage household records'),
  ('ACCESS_ALTERNATES', 'Alternates', 'HOUSEHOLDS_ALTERNATES', 'View and manage household alternates'),
  ('ACCESS_PAYMENTS', 'Payments', 'HOUSEHOLDS_ALTERNATES', 'View and manage household payments'),
  ('ACCESS_PAYMENT_CYCLES', 'Payment cycles', 'HOUSEHOLDS_ALTERNATES', 'Generate, review, approve and disburse payment cycles'),
  ('ACCESS_VOUCHERS', 'Vouchers', 'HOUSEHOLDS_ALTERNATES', 'Issue, redeem, void and view vouchers'),
  ('ACCESS_ATTENDANCE', 'Attendance', 'HOUSEHOLDS_ALTERNATES', 'View field attendance'),
  ('ACCESS_USERS', 'Users', 'USER_MANAGEMENT', 'View and manage dashboard users'),
  ('ACCESS_ROLES', 'Roles', 'USER_MANAGEMENT', 'Create and manage roles'),
  ('ACCESS_PERMISSIONS', 'Permissions', 'USER_MANAGEMENT', 'View, create and assign permissions'),
  ('ACCESS_SUPERVISORS', 'Field officers', 'USER_MANAGEMENT', 'View and manage field officers'),
  ('ACCESS_ORGANISATIONS', 'Organisations', 'SYSTEM_SETUP', 'View and manage organisations'),
  ('ACCESS_LOCATIONS', 'Locations', 'SYSTEM_SETUP', 'View and manage states, counties, locations and villages'),
  ('ACCESS_SUBSCRIPTION', 'Subscription', 'SYSTEM_SETUP', 'View invoices and renew the anchor subscription');

INSERT INTO permissions (permission_name, display_name, permission_group, description, system_defined, created_at)
SELECT source.permission_name, source.display_name, source.permission_group, source.description, 1, GETDATE()
FROM @permissions source
WHERE NOT EXISTS (SELECT 1 FROM permissions currentPermission WHERE currentPermission.permission_name = source.permission_name);

UPDATE existing SET
  existing.display_name = source.display_name,
  existing.permission_group = source.permission_group,
  existing.description = source.description,
  existing.system_defined = 1
FROM permissions existing JOIN @permissions source ON source.permission_name = existing.permission_name;

-- Translate every permission name used by earlier BioPay versions into the new dashboard areas.
DECLARE @legacyMap TABLE (legacy_name VARCHAR(100), permission_name VARCHAR(100));
INSERT INTO @legacyMap VALUES
  ('Dashboard', 'VIEW_REPORTS'), ('Reports', 'VIEW_REPORTS'), ('VIEW_DASHBOARD', 'VIEW_REPORTS'),
  ('EXPORT_REPORTS', 'DOWNLOAD_REPORTS'), ('Download Households', 'DOWNLOAD_REPORTS'),
  ('EXPORT_HOUSEHOLDS', 'DOWNLOAD_REPORTS'), ('EXPORT_ALTERNATES', 'DOWNLOAD_REPORTS'),
  ('EXPORT_PAYMENTS', 'DOWNLOAD_REPORTS'), ('EXPORT_ATTENDANCE', 'DOWNLOAD_REPORTS'),
  ('Households', 'ACCESS_HOUSEHOLDS'), ('Households', 'ACCESS_ALTERNATES'),
  ('MANAGE_HOUSEHOLDS', 'ACCESS_HOUSEHOLDS'), ('MANAGE_HOUSEHOLDS', 'ACCESS_ALTERNATES'),
  ('VIEW_HOUSEHOLDS', 'ACCESS_HOUSEHOLDS'), ('CREATE_HOUSEHOLDS', 'ACCESS_HOUSEHOLDS'),
  ('EDIT_HOUSEHOLDS', 'ACCESS_HOUSEHOLDS'), ('REVIEW_HOUSEHOLDS', 'ACCESS_HOUSEHOLDS'),
  ('DELETE_HOUSEHOLDS', 'ACCESS_HOUSEHOLDS'), ('IMPORT_HOUSEHOLDS', 'ACCESS_HOUSEHOLDS'),
  ('VIEW_ALTERNATES', 'ACCESS_ALTERNATES'), ('CREATE_ALTERNATES', 'ACCESS_ALTERNATES'),
  ('EDIT_ALTERNATES', 'ACCESS_ALTERNATES'), ('DELETE_ALTERNATES', 'ACCESS_ALTERNATES'),
  ('MANAGE_PAYMENTS', 'ACCESS_PAYMENTS'), ('MANAGE_PAYMENTS', 'ACCESS_PAYMENT_CYCLES'),
  ('VIEW_PAYMENTS', 'ACCESS_PAYMENTS'), ('MARK_PAYMENTS_PAID', 'ACCESS_PAYMENTS'),
  ('DELETE_PAYMENTS', 'ACCESS_PAYMENTS'),
  ('VIEW_PAYMENT_CYCLES', 'ACCESS_PAYMENT_CYCLES'), ('CREATE_PAYMENT_CYCLES', 'ACCESS_PAYMENT_CYCLES'),
  ('APPROVE_PAYMENT_CYCLES', 'ACCESS_PAYMENT_CYCLES'), ('REJECT_PAYMENT_CYCLES', 'ACCESS_PAYMENT_CYCLES'),
  ('DISBURSE_PAYMENT_CYCLES', 'ACCESS_PAYMENT_CYCLES'), ('DELETE_PAYMENT_CYCLES', 'ACCESS_PAYMENT_CYCLES'),
  ('MANAGE_VOUCHERS', 'ACCESS_VOUCHERS'), ('VIEW_VOUCHERS', 'ACCESS_VOUCHERS'),
  ('ISSUE_VOUCHERS', 'ACCESS_VOUCHERS'), ('BULK_ISSUE_VOUCHERS', 'ACCESS_VOUCHERS'),
  ('REDEEM_VOUCHERS', 'ACCESS_VOUCHERS'), ('VOID_VOUCHERS', 'ACCESS_VOUCHERS'),
  ('VIEW_ATTENDANCE', 'ACCESS_ATTENDANCE'), ('RECORD_ATTENDANCE', 'ACCESS_ATTENDANCE'),
  ('User Management', 'ACCESS_USERS'), ('MANAGE_USERS', 'ACCESS_USERS'),
  ('VIEW_USERS', 'ACCESS_USERS'), ('CREATE_USERS', 'ACCESS_USERS'), ('EDIT_USERS', 'ACCESS_USERS'),
  ('MANAGE_USER_STATUS', 'ACCESS_USERS'),
  ('MANAGE_ROLES', 'ACCESS_ROLES'), ('MANAGE_ROLES', 'ACCESS_PERMISSIONS'),
  ('VIEW_ROLES', 'ACCESS_ROLES'), ('CREATE_ROLES', 'ACCESS_ROLES'), ('EDIT_ROLES', 'ACCESS_ROLES'),
  ('MANAGE_OFFICERS', 'ACCESS_SUPERVISORS'), ('VIEW_OFFICERS', 'ACCESS_SUPERVISORS'),
  ('CREATE_OFFICERS', 'ACCESS_SUPERVISORS'), ('EDIT_OFFICERS', 'ACCESS_SUPERVISORS'),
  ('DELETE_OFFICERS', 'ACCESS_SUPERVISORS'), ('MANAGE_OFFICER_STATUS', 'ACCESS_SUPERVISORS'),
  ('ASSIGN_OFFICER_LOCATIONS', 'ACCESS_SUPERVISORS'),
  ('MANAGE_ORGANISATIONS', 'ACCESS_ORGANISATIONS'), ('VIEW_ORGANISATIONS', 'ACCESS_ORGANISATIONS'),
  ('CREATE_ORGANISATIONS', 'ACCESS_ORGANISATIONS'), ('EDIT_ORGANISATIONS', 'ACCESS_ORGANISATIONS'),
  ('DELETE_ORGANISATIONS', 'ACCESS_ORGANISATIONS'), ('MANAGE_ORGANISATION_STATUS', 'ACCESS_ORGANISATIONS'),
  ('VIEW_LOCATIONS', 'ACCESS_LOCATIONS'), ('CREATE_LOCATIONS', 'ACCESS_LOCATIONS'),
  ('EDIT_LOCATIONS', 'ACCESS_LOCATIONS'), ('DELETE_LOCATIONS', 'ACCESS_LOCATIONS'), ('IMPORT_LOCATIONS', 'ACCESS_LOCATIONS'),
  ('VIEW_SUBSCRIPTION', 'ACCESS_SUBSCRIPTION'), ('MANAGE_SUBSCRIPTION', 'ACCESS_SUBSCRIPTION');

INSERT INTO role_permissions (role_id, permission_id, status, created_at)
SELECT DISTINCT legacyGrant.role_id, dashboardPermission.id, 1, GETDATE()
FROM role_permissions legacyGrant
JOIN permissions legacyPermission ON legacyPermission.id = legacyGrant.permission_id
JOIN @legacyMap mapping ON mapping.legacy_name = legacyPermission.permission_name
JOIN permissions dashboardPermission ON dashboardPermission.permission_name = mapping.permission_name
WHERE legacyGrant.status = 1
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions existingGrant
    WHERE existingGrant.role_id = legacyGrant.role_id AND existingGrant.permission_id = dashboardPermission.id
  );

-- Anchor Administrator receives the complete tenant dashboard permission set.
-- Its authority is still restricted to its own anchor; only System Owner is platform-unlimited.
INSERT INTO role_permissions (role_id, permission_id, status, created_at)
SELECT adminRole.id, permissionRow.id, 1, GETDATE()
FROM roles adminRole CROSS JOIN permissions permissionRow
WHERE adminRole.role_name = 'Anchor Administrator'
  AND permissionRow.permission_name IN (SELECT permission_name FROM @permissions)
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions existingGrant
    WHERE existingGrant.role_id = adminRole.id AND existingGrant.permission_id = permissionRow.id
  );

-- Organisation administrators receive every area usable inside their organisation.
INSERT INTO role_permissions (role_id, permission_id, status, created_at)
SELECT adminRole.id, permissionRow.id, 1, GETDATE()
FROM roles adminRole CROSS JOIN permissions permissionRow
WHERE adminRole.role_name = 'Organisation Administrator'
  AND permissionRow.permission_name IN (
    'VIEW_REPORTS', 'DOWNLOAD_REPORTS', 'ACCESS_HOUSEHOLDS', 'ACCESS_ALTERNATES', 'ACCESS_PAYMENTS',
    'ACCESS_PAYMENT_CYCLES', 'ACCESS_VOUCHERS', 'ACCESS_ATTENDANCE', 'ACCESS_USERS', 'ACCESS_SUPERVISORS',
    'ACCESS_LOCATIONS'
  )
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions existingGrant
    WHERE existingGrant.role_id = adminRole.id AND existingGrant.permission_id = permissionRow.id
  );
GO
