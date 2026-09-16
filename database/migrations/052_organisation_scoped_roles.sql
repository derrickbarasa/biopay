-- 052_organisation_scoped_roles.sql
--
-- Extends role forking one tier below anchors: an Organisation Administrator may now customize
-- their own organisation's copy of a role -- most usefully "Organisation Administrator" itself --
-- the same way an Anchor Administrator already forks the shared template into an anchor-owned
-- row (see Administration#saveRole). roles.organization_code has existed since migration 011
-- (renamed from partner_code in 029) but was never used until now.

-- The (anchor_id, role_name) unique index from migration 026 doesn't leave room for a second,
-- organisation-owned row with the same anchor_id and role_name once an organisation forks its
-- own copy alongside its anchor's fork -- both would collide on (anchor_id, role_name) even
-- though organization_code differs. Replace it with a 3-column index so uniqueness holds per
-- (anchor, organisation, role name) instead: still a single shared template row
-- (NULL, NULL, name), still at most one fork per anchor (anchor_id, NULL, name), now also at
-- most one fork per organisation (anchor_id, organization_code, name).
IF EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('roles') AND name = 'UX_roles_anchor_name')
    DROP INDEX UX_roles_anchor_name ON roles;
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('roles') AND name = 'UX_roles_anchor_org_name')
    CREATE UNIQUE INDEX UX_roles_anchor_org_name ON roles(anchor_id, organization_code, role_name);
GO

-- Migration 025 granted "Organisation Administrator" every area usable inside an organisation,
-- but never ACCESS_ROLES itself -- so before this release an org admin couldn't have reached
-- Roles & Permissions even once the page stopped being Anchor-only. Grant it to every existing
-- "Organisation Administrator" row (the shared template and any anchor's already-forked copy),
-- not just the template, so no anchor's customization is left behind.
INSERT INTO role_permissions (role_id, permission_id, status, created_at)
SELECT r.id, perm.id, 1, GETDATE()
FROM roles r CROSS JOIN permissions perm
WHERE r.role_name = 'Organisation Administrator' AND perm.permission_name = 'ACCESS_ROLES'
  AND NOT EXISTS (
    SELECT 1 FROM role_permissions existing
    WHERE existing.role_id = r.id AND existing.permission_id = perm.id
  );
GO
