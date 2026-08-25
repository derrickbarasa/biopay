-- The system owner is a platform identity, not a member of any anchor.
-- Anchor and organisation users retain the anchor_id that drives subscription and data scoping.
-- Dynamic SQL against whichever name the reference column currently has
-- (partner_code pre-029, organization_code after) -- a plain UPDATE naming
-- one or the other fails to parse once migration 029 has renamed it (see
-- 001's comment on why guarding alone isn't enough for column references).
IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('users') AND name = 'organization_code')
    EXEC('UPDATE users SET user_scope = ''SYSTEM'', anchor_id = NULL, organization_code = NULL, updated_at = GETDATE() WHERE is_system_admin = 1');
ELSE
    EXEC('UPDATE users SET user_scope = ''SYSTEM'', anchor_id = NULL, partner_code = NULL, updated_at = GETDATE() WHERE is_system_admin = 1');

-- Role names are tenant-owned. The original foundation made role_name globally
-- unique, which prevents two anchors from independently using a natural name such
-- as "Finance Officer". Replace that legacy constraint with anchor/name uniqueness.
DECLARE @legacyRoleConstraint SYSNAME;
SELECT TOP 1 @legacyRoleConstraint = kc.name
FROM sys.key_constraints kc
JOIN sys.index_columns ic ON ic.object_id = kc.parent_object_id
    AND ic.index_id = kc.unique_index_id
JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
WHERE kc.parent_object_id = OBJECT_ID('roles')
  AND kc.[type] = 'UQ'
GROUP BY kc.name
HAVING COUNT(*) = 1 AND MAX(c.name) = 'role_name';

IF @legacyRoleConstraint IS NOT NULL
    EXEC('ALTER TABLE roles DROP CONSTRAINT [' + @legacyRoleConstraint + ']');

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('roles') AND name = 'UX_roles_anchor_name')
    CREATE UNIQUE INDEX UX_roles_anchor_name ON roles(anchor_id, role_name);
