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
-- SUPERSEDED by 053_dedicated_anchors_table.sql, which reintroduces a table
-- named `anchors` with a *different* schema (no authorised_contact column --
-- see that migration's header for why). Every step below is guarded on
-- anchors.authorised_contact specifically, not just the table existing or
-- users.anchor_code being NULL, so replaying this file against a database
-- that has already moved past it -- where a same-named `anchors` table exists
-- again with 053's schema -- is a safe no-op instead of either erroring on a
-- column that table doesn't have, or (far worse) the last statement below
-- forcing anchor_id back to a self-referencing id for every anchor-scoped
-- user created under the new architecture, or the DROP TABLE step deleting
-- 053's real data.
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
-- these fields) never clobbers newer data. authorised_contact only ever
-- existed on the original (pre-030) anchors table -- see this file's header.
IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('anchors') AND name = 'authorised_contact')
   AND EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('users') AND name = 'anchor_code')
BEGIN
    EXEC('UPDATE u
    SET u.anchor_code = a.anchor_code,
        u.anchor_name = a.name,
        u.phone = COALESCE(u.phone, a.authorised_contact),
        u.address = COALESCE(u.address, a.address),
        u.website = COALESCE(u.website, a.website),
        u.country = COALESCE(u.country, a.country),
        u.city = COALESCE(u.city, a.city)
    FROM users u
    JOIN anchors a ON a.id = u.anchor_id
    WHERE u.user_scope = ''ANCHOR'' AND u.anchor_code IS NULL');
END
GO

-- Only ever drops the original (pre-030) anchors table -- see this file's header for why the
-- guard checks authorised_contact specifically rather than just table existence.
IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('anchors') AND name = 'authorised_contact')
    DROP TABLE anchors;
GO

-- An anchor's own row must self-reference (anchor_id = its own id) so every
-- "WHERE anchor_id=@theAnchorsOwnId" query elsewhere in the app also matches
-- the anchor administrator's own row, the same way a freshly-created anchor
-- already does. Rows migrated above from the old anchors table still carry
-- the old anchors.id value here; this corrects them.
--
-- CRITICAL guard, same signal as the two steps above (not just "does a table
-- named anchors exist" or "does users.anchor_code still exist" -- both stay
-- true through the whole window between 053 running and 054 running): under
-- the new architecture an anchor-wide *staff* user legitimately has
-- anchor_id pointing at their real anchor while their own id is a different
-- row entirely (see 053's header) -- exactly what this statement would
-- overwrite with their own id, silently detaching every such user from
-- their anchor, if it ever ran again post-053.
IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('anchors') AND name = 'authorised_contact')
    UPDATE users SET anchor_id = id WHERE user_scope = 'ANCHOR' AND anchor_id <> id;
GO
