-- 011_administration.sql -- scoped role/permission administration.

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('permissions') AND name = 'description')
    ALTER TABLE permissions ADD description VARCHAR(255) NULL;
GO
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('permissions') AND name = 'created_at')
    ALTER TABLE permissions ADD created_at DATETIME NOT NULL DEFAULT GETDATE();
GO

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('roles') AND name = 'description')
    ALTER TABLE roles ADD description VARCHAR(255) NULL;
GO
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('roles') AND name = 'anchor_id')
    ALTER TABLE roles ADD anchor_id INT NULL;
GO
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('roles') AND name IN ('partner_code', 'organization_code'))
    ALTER TABLE roles ADD partner_code VARCHAR(20) NULL;
GO
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('roles') AND name = 'role_scope')
    ALTER TABLE roles ADD role_scope VARCHAR(20) NOT NULL DEFAULT 'ORGANISATION';
GO
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('roles') AND name = 'status')
    ALTER TABLE roles ADD status INT NOT NULL DEFAULT 1;
GO
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('role_permissions') AND name = 'status')
    ALTER TABLE role_permissions ADD status BIT NOT NULL DEFAULT 1;
GO

IF NOT EXISTS (SELECT 1 FROM permissions WHERE permission_name='MANAGE_ORGANISATIONS')
    INSERT INTO permissions (permission_name, description) VALUES
      ('MANAGE_ORGANISATIONS', 'Create and update organisations'),
      ('MANAGE_USERS', 'Create, activate and deactivate dashboard users'),
      ('MANAGE_ROLES', 'Configure roles and permission assignments'),
      ('MANAGE_OFFICERS', 'Create and manage field officers'),
      ('MANAGE_HOUSEHOLDS', 'Create and maintain household records'),
      ('MANAGE_PAYMENTS', 'Generate, approve and manage transfers'),
      ('MANAGE_VOUCHERS', 'Issue, void and redeem vouchers'),
      ('VIEW_REPORTS', 'View operational dashboards and exports');
GO

IF NOT EXISTS (SELECT 1 FROM roles WHERE role_name='Anchor Administrator')
    INSERT INTO roles (role_name, description, role_scope, status)
    VALUES ('Anchor Administrator', 'Full administration within one anchor', 'ANCHOR', 1);
GO
IF NOT EXISTS (SELECT 1 FROM roles WHERE role_name='Organisation Administrator')
    INSERT INTO roles (role_name, description, role_scope, status)
    VALUES ('Organisation Administrator', 'Administration within one organisation', 'ORGANISATION', 1);
GO

DECLARE @anchorRole INT = (SELECT TOP 1 id FROM roles WHERE role_name='Anchor Administrator');
INSERT INTO role_permissions (role_id, permission_id, status)
SELECT @anchorRole, p.id, 1 FROM permissions p
WHERE NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id=@anchorRole AND rp.permission_id=p.id);
GO

DECLARE @orgRole INT = (SELECT TOP 1 id FROM roles WHERE role_name='Organisation Administrator');
INSERT INTO role_permissions (role_id, permission_id, status)
SELECT @orgRole, p.id, 1 FROM permissions p
WHERE p.permission_name IN ('MANAGE_USERS','MANAGE_OFFICERS','MANAGE_HOUSEHOLDS','MANAGE_PAYMENTS','MANAGE_VOUCHERS','VIEW_REPORTS')
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id=@orgRole AND rp.permission_id=p.id);
GO

UPDATE users SET role_id=(SELECT TOP 1 id FROM roles WHERE role_name='Anchor Administrator')
WHERE user_scope='ANCHOR' AND role_id IS NULL;
UPDATE users SET role_id=(SELECT TOP 1 id FROM roles WHERE role_name='Organisation Administrator')
WHERE user_scope='ORGANISATION' AND role_id IS NULL;
GO
