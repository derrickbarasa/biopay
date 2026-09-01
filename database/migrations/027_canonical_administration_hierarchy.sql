-- 027_canonical_administration_hierarchy.sql
-- Canonical identity and access hierarchy:
--   System Owner -> platform-wide, tenantless, immutable, permission bypass
--   Anchor Administrator -> one anchor and every organisation beneath it
--   Organisation Administrator -> one organisation only

-- partner_code/organization_code is left out of the column list entirely --
-- it's always NULL here regardless, and the column is nullable, so omitting
-- it sidesteps needing to know which name it currently has.
--
-- Keyed on role_scope='SYSTEM' + anchor_id IS NULL rather than role_name: migration 034
-- later renames this role 'System Owner' -> 'Super Admin', and looking it up by the old
-- name here would no longer find it on a database where 034 already ran, inserting a
-- second, duplicate system role and repointing every system-admin user onto it.
IF NOT EXISTS (SELECT 1 FROM roles WHERE role_scope='SYSTEM' AND anchor_id IS NULL)
    INSERT INTO roles (role_name, description, anchor_id, role_scope, status, created_at)
    VALUES ('System Owner', 'Platform owner with permanent access to every BioPay feature and tenant', NULL, 'SYSTEM', 1, GETDATE());
GO

UPDATE roles
SET description='Platform owner with permanent access to every BioPay feature and tenant',
    status=1, updated_at=GETDATE()
WHERE role_scope='SYSTEM' AND anchor_id IS NULL;

UPDATE roles
SET description='Full operational and administrative access within one anchor and its organisations',
    role_scope='ANCHOR', status=1, updated_at=GETDATE()
WHERE role_name='Anchor Administrator' AND anchor_id IS NULL;

UPDATE roles
SET description='Full operational and administrative access within one organisation',
    role_scope='ORGANISATION', status=1, updated_at=GETDATE()
WHERE role_name='Organisation Administrator' AND anchor_id IS NULL;
GO

DECLARE @systemOwnerRole INT = (SELECT TOP 1 id FROM roles WHERE role_scope='SYSTEM' AND anchor_id IS NULL AND status=1);
DECLARE @anchorAdminRole INT = (SELECT TOP 1 id FROM roles WHERE role_name='Anchor Administrator' AND anchor_id IS NULL AND status=1);
DECLARE @organisationAdminRole INT = (SELECT TOP 1 id FROM roles WHERE role_name='Organisation Administrator' AND anchor_id IS NULL AND status=1);

-- A system owner is a users-table identity only. It is never an anchor or organisation member.
-- Dynamic SQL against whichever name the reference column currently has (see
-- 026's identical guard for why a plain UPDATE isn't enough post-rename).
IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('users') AND name = 'organization_code')
    EXEC sp_executesql N'UPDATE users SET role_id=@role, user_scope=''SYSTEM'', anchor_id=NULL, organization_code=NULL,
        is_system_admin=1, active=1, status=1, updated_at=GETDATE()
        WHERE is_system_admin=1 OR LOWER(email)=''admin@biopay.com''', N'@role INT', @role = @systemOwnerRole;
ELSE
    EXEC sp_executesql N'UPDATE users SET role_id=@role, user_scope=''SYSTEM'', anchor_id=NULL, partner_code=NULL,
        is_system_admin=1, active=1, status=1, updated_at=GETDATE()
        WHERE is_system_admin=1 OR LOWER(email)=''admin@biopay.com''', N'@role INT', @role = @systemOwnerRole;

-- Repair legacy seed/admin assignments without replacing intentional tenant-owned custom roles.
UPDATE u
SET role_id=@anchorAdminRole, updated_at=GETDATE()
FROM users u LEFT JOIN roles r ON r.id=u.role_id
WHERE u.is_system_admin=0 AND u.user_scope='ANCHOR'
  AND (u.role_id IS NULL OR r.role_scope<>'ANCHOR' OR LOWER(r.role_name)='administrator');

UPDATE u
SET role_id=@organisationAdminRole, updated_at=GETDATE()
FROM users u LEFT JOIN roles r ON r.id=u.role_id
WHERE u.is_system_admin=0 AND u.user_scope='ORGANISATION'
  AND (u.role_id IS NULL OR r.role_scope<>'ORGANISATION' OR LOWER(r.role_name)='administrator');
GO

-- The system role records the complete policy even though the application also enforces
-- an immutable system-owner bypass at its authorization boundary.
DECLARE @systemOwnerRole2 INT = (SELECT TOP 1 id FROM roles WHERE role_scope='SYSTEM' AND anchor_id IS NULL AND status=1);
INSERT INTO role_permissions (role_id, permission_id, status, created_at)
SELECT @systemOwnerRole2, p.id, 1, GETDATE()
FROM permissions p
WHERE NOT EXISTS (
    SELECT 1 FROM role_permissions rp WHERE rp.role_id=@systemOwnerRole2 AND rp.permission_id=p.id
);
UPDATE role_permissions SET status=1 WHERE role_id=@systemOwnerRole2;
GO

-- Older demo organisations pre-date module selection. Only backfill organisations that have
-- no enabled module at all; deliberately configured organisations remain unchanged.
-- Dynamic SQL, naming whichever table/column the rename has (or hasn't) put
-- in place yet -- see 001's comment for why a plain guarded version isn't
-- enough once migration 029 has actually renamed these.
DECLARE @orgTable SYSNAME = CASE WHEN OBJECT_ID('organizations') IS NOT NULL THEN 'organizations' ELSE 'partners' END;
DECLARE @orgOwnCodeCol SYSNAME = CASE WHEN OBJECT_ID('organizations') IS NOT NULL THEN 'organization_code' ELSE 'partner_id' END;
DECLARE @orgRefCodeCol SYSNAME = CASE WHEN EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('organisation_modules') AND name = 'organization_code') THEN 'organization_code' ELSE 'partner_code' END;
EXEC('
DECLARE @moduleCodes TABLE (module_code VARCHAR(40));
INSERT INTO @moduleCodes VALUES
  (''HOUSEHOLDS''), (''ALTERNATES''), (''CASH_TRANSFERS''), (''VOUCHERS''), (''FOOD_DISTRIBUTION'');
DECLARE @unprovisionedOrganisations TABLE (org_code VARCHAR(20));
INSERT INTO @unprovisionedOrganisations
SELECT p.' + @orgOwnCodeCol + ' FROM ' + @orgTable + ' p
WHERE NOT EXISTS (
    SELECT 1 FROM organisation_modules existing
    WHERE existing.' + @orgRefCodeCol + '=p.' + @orgOwnCodeCol + ' AND existing.enabled=1
);

UPDATE existing SET enabled=1
FROM organisation_modules existing
JOIN @unprovisionedOrganisations target ON target.org_code=existing.' + @orgRefCodeCol + ';

INSERT INTO organisation_modules (' + @orgRefCodeCol + ', module_code, enabled, created_at)
SELECT target.org_code, m.module_code, 1, GETDATE()
FROM @unprovisionedOrganisations target CROSS JOIN @moduleCodes m
WHERE NOT EXISTS (
    SELECT 1 FROM organisation_modules duplicate
    WHERE duplicate.' + @orgRefCodeCol + '=target.org_code AND duplicate.module_code=m.module_code
);
');
GO
