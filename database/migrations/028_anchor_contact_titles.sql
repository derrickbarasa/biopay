-- Anchor rows describe anchor contacts; the platform System Owner exists only in users.
-- Moot once migration 030 has dropped the original anchors table (merged into users) --
-- guarded with dynamic SQL since a plain UPDATE against a dropped table fails to parse even
-- inside a false IF branch (see 001's comment). The guard checks for authorised_name
-- specifically, not just OBJECT_ID('anchors'): 053_dedicated_anchors_table.sql later
-- recreates a table with that same name but a different schema (no authorised_name column),
-- so re-running this full migration chain from scratch against a database that has already
-- reached 053 would otherwise hit this UPDATE against the new table and fail to parse
-- (Msg 207, Invalid column name 'authorised_name').
IF OBJECT_ID('anchors') IS NOT NULL
    AND EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('anchors') AND name = 'authorised_name')
    EXEC('UPDATE anchors
          SET authorised_name=''Anchor Administrator'', updated_at=GETDATE()
          WHERE LOWER(LTRIM(RTRIM(authorised_name)))=''system administrator''');
GO

-- BioPay originally allowed exactly one System Owner account. This filtered unique index
-- prevented another users row from being marked as owner accidentally -- migration 034
-- later drops it, since multiple Super Admins became a deliberate product decision. Guard
-- the creation itself on there still being at most one today, so re-running the full
-- migration chain from scratch stays safe once a database already has more than one
-- (034 removes this index again immediately after, so skipping it here changes nothing).
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('users') AND name='UX_users_single_system_owner')
    AND (SELECT COUNT(*) FROM users WHERE is_system_admin=1) <= 1
    CREATE UNIQUE INDEX UX_users_single_system_owner ON users(is_system_admin) WHERE is_system_admin=1;
GO
