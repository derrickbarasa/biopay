-- 030_anchors_into_users.sql
--
-- Removes the standalone `anchors` table. An anchor is no longer its own row
-- in its own table -- it IS its Anchor Administrator's row in `users`, carrying
-- the anchor's own identity fields directly. Every other table's anchor_id
-- column is unchanged in name or type: it now points at users.id of an
-- ANCHOR-scope row instead of the old anchors.id. There is no FK constraint
-- either way (matches this database's convention), so no other table's schema
-- needs to change for this.
--
-- Idempotent: column adds are guarded, the anchors->users data copy only
-- touches rows it can match, and the DROP TABLE only runs once. Safe to re-run.

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('users') AND name = 'anchor_code')
    ALTER TABLE users ADD anchor_code VARCHAR(20) NULL;
GO
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('users') AND name = 'anchor_name')
    ALTER TABLE users ADD anchor_name VARCHAR(150) NULL;
GO
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('users') AND name = 'phone')
    ALTER TABLE users ADD phone VARCHAR(50) NULL;
GO
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('users') AND name = 'address')
    ALTER TABLE users ADD address VARCHAR(255) NULL;
GO
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('users') AND name = 'website')
    ALTER TABLE users ADD website VARCHAR(150) NULL;
GO
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('users') AND name = 'country')
    ALTER TABLE users ADD country VARCHAR(80) NULL;
GO
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('users') AND name = 'city')
    ALTER TABLE users ADD city VARCHAR(100) NULL;
GO

-- Carry over any existing anchor's data onto its Anchor Administrator's user
-- row before the anchors table is dropped. Only fills columns that are still
-- NULL, so a second run (or a run after the app has already started writing
-- these fields) never clobbers newer data.
IF EXISTS (SELECT 1 FROM sys.tables WHERE name = 'anchors')
BEGIN
    UPDATE u
    SET u.anchor_code = a.anchor_code,
        u.anchor_name = a.name,
        u.phone = COALESCE(u.phone, a.authorised_contact),
        u.address = COALESCE(u.address, a.address),
        u.website = COALESCE(u.website, a.website),
        u.country = COALESCE(u.country, a.country),
        u.city = COALESCE(u.city, a.city)
    FROM users u
    JOIN anchors a ON a.id = u.anchor_id
    WHERE u.user_scope = 'ANCHOR' AND u.anchor_code IS NULL;
END
GO

IF EXISTS (SELECT 1 FROM sys.tables WHERE name = 'anchors')
    DROP TABLE anchors;
GO

-- An anchor's own row must self-reference (anchor_id = its own id) so every
-- "WHERE anchor_id=@theAnchorsOwnId" query elsewhere in the app also matches
-- the anchor administrator's own row, the same way a freshly-created anchor
-- already does. Rows migrated above from the old anchors table still carry
-- the old anchors.id value here; this corrects them.
UPDATE users SET anchor_id = id WHERE user_scope = 'ANCHOR' AND anchor_id <> id;
GO
