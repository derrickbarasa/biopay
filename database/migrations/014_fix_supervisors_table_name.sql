-- 014_fix_supervisors_table_name.sql
--
-- Fixes a live-database drift discovered during 2026-08-19 end-to-end testing:
-- LOGIN_SUPERVISOR (mobile field-agent login) and every officer-management
-- processing code query `FROM supervisors` / `INSERT INTO supervisors` (see
-- Auth.java, Officer.java, Biometric.java) -- that is the name used
-- consistently across every migration in this repo (000_base_schema.sql
-- creates it, 001_anchors_and_scoping.sql alters it) and every Java query.
--
-- On at least one live database the table itself was renamed to `officers`
-- (its primary key is still literally named PK_supervisors and its unique
-- constraint UQ_supervisor_id, proving the rename happened after creation --
-- table renames don't touch constraint names) without updating the code that
-- reads it, which breaks mobile login outright ("Invalid object name
-- 'supervisors'"). Renaming it back keeps this database aligned with the
-- codebase's actual naming and preserves every existing row -- no data is
-- created, dropped, or altered, only the table's name.
--
-- Idempotent: only fires when `officers` exists and `supervisors` doesn't,
-- so a fresh install (which only ever creates `supervisors`) is a no-op here.

IF EXISTS (SELECT 1 FROM sys.tables WHERE name = 'officers')
   AND NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'supervisors')
BEGIN
    EXEC sp_rename 'officers', 'supervisors';
END
GO
