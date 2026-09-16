-- 053_dedicated_anchors_table.sql
--
-- Reintroduces a standalone `anchors` table. Migration 030 folded anchors into `users` ("an
-- anchor IS its Anchor Administrator's own row") to simplify anchor-scoped queries elsewhere,
-- but that made "is this row a genuine anchor" something every "list every anchor" query has to
-- independently re-derive from three conditions (self-referencing id=anchor_id, the canonical
-- Anchor Administrator role, a non-blank anchor_name) -- a check that's proven easy to miss: it
-- was missing or incomplete in Administration#getAnchors, Subscription#getAllSubscriptions, and
-- Subscription#getAllPrices, found and patched one at a time, each independently, before this
-- migration. A real table makes it structurally true instead of query-by-query inferred.
--
-- Existing anchor rows are copied out with IDENTITY_INSERT preserving their current users.id
-- value as the new anchors.id -- every other table's anchor_id column (payments, households,
-- organizations, roles, subscriptions, audit_logs, field officers, ...) keeps pointing at a
-- valid id with zero data changes anywhere else in the schema.
--
-- users.anchor_code/anchor_name/phone/address/country/city are left in place (not dropped --
-- this migration doesn't touch data outside the new table) but the application stops reading
-- and writing them for anchor identity as of this release; anchors.* is the source of truth
-- going forward. The Anchor Administrator's own `users` row is otherwise untouched: it keeps
-- its anchor_id pointing at this now-real anchors row, exactly like every other anchor-scoped
-- user already does -- it's just no longer required to self-reference.

IF OBJECT_ID('anchors') IS NULL
BEGIN
    CREATE TABLE anchors (
        id INT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        anchor_code VARCHAR(20) NOT NULL,
        anchor_name VARCHAR(150) NOT NULL,
        phone VARCHAR(50) NULL,
        address VARCHAR(255) NULL,
        country VARCHAR(80) NULL,
        city VARCHAR(100) NULL,
        status INT NOT NULL DEFAULT 1,
        created_at DATETIME NOT NULL DEFAULT GETDATE(),
        updated_at DATETIME NOT NULL DEFAULT GETDATE()
    );

    -- Only rows that pass the same three-part check every query above had to reconstruct by
    -- hand (see this file's header) become a real anchor. Anything that doesn't -- an
    -- anchor-wide staff user whose anchor_id ended up self-referential, a row with a blank
    -- name from before UPDATE_ANCHOR required one -- is left behind in `users` as what it
    -- always actually was: a user, not an anchor.
    SET IDENTITY_INSERT anchors ON;
    INSERT INTO anchors (id, anchor_code, anchor_name, phone, address, country, city, status, created_at, updated_at)
    SELECT u.id, u.anchor_code, u.anchor_name, u.phone, u.address, u.country, u.city, u.status, u.created_at, u.updated_at
    FROM users u
    JOIN roles r ON r.id = u.role_id AND r.role_name = 'Anchor Administrator' AND r.anchor_id IS NULL
    WHERE u.user_scope = 'ANCHOR' AND u.id = u.anchor_id
      AND LTRIM(RTRIM(ISNULL(u.anchor_name, ''))) <> '';
    SET IDENTITY_INSERT anchors OFF;

    -- Continue the identity sequence from the highest migrated id (0 if there were none), so
    -- the next CREATE_ANCHOR or self-service signup gets a fresh id instead of colliding with
    -- a migrated one.
    DECLARE @maxAnchorId INT = (SELECT ISNULL(MAX(id), 0) FROM anchors);
    DBCC CHECKIDENT ('anchors', RESEED, @maxAnchorId);
END
GO
