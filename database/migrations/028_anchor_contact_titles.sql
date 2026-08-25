-- Anchor rows describe anchor contacts; the platform System Owner exists only in users.
-- Moot once migration 030 has dropped anchors entirely (merged into users) --
-- guarded with dynamic SQL since a plain UPDATE against a dropped table
-- fails to parse even inside a false IF branch (see 001's comment).
IF OBJECT_ID('anchors') IS NOT NULL
    EXEC('UPDATE anchors
          SET authorised_name=''Anchor Administrator'', updated_at=GETDATE()
          WHERE LOWER(LTRIM(RTRIM(authorised_name)))=''system administrator''');
GO

-- BioPay currently has exactly one System Owner account: admin@biopay.com.
-- This filtered unique index prevents another users row from being marked as owner accidentally.
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('users') AND name='UX_users_single_system_owner')
    CREATE UNIQUE INDEX UX_users_single_system_owner ON users(is_system_admin) WHERE is_system_admin=1;
GO
