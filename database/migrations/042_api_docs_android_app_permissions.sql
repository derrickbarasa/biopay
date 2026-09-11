-- 042_api_docs_android_app_permissions.sql
-- The API Documentation and Android App dashboard pages were previously visible to
-- any signed-in user with no permission check at all. Give the platform owner a way
-- to allocate who can see them: two new assignable permissions, following the same
-- pattern as 025_dashboard_access_permissions.sql. Deliberately NOT auto-granted to
-- any existing role here -- every non-system-owner role starts without access, and
-- the platform owner assigns it from Roles & Permissions to whoever should have it.

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
  ('ACCESS_API_DOCS', 'API Documentation', 'SYSTEM_SETUP', 'View the API documentation page'),
  ('ACCESS_ANDROID_APP', 'Android App', 'SYSTEM_SETUP', 'View and download the Android field-agent app');

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
GO
