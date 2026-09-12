-- 045_users_surname_rename.sql
--
-- Renames users.other_names -> users.surname (display/API rename only, same VARCHAR(150)
-- NULL column, no data loss -- sp_rename preserves every existing row's value).
--
-- This also accompanies a fix (Auth.java, Administration.java, Organization.java) for the
-- actual data-entry bug behind messy existing rows: several account-creation paths (anchor
-- self-signup, an anchor created by the platform owner, and an organisation's admin login
-- created when the organisation itself is created) took a single "authorised contact name"
-- form field and dumped the whole string into first_name, either hardcoding this column to
-- '' or omitting it from the INSERT entirely (leaving it NULL). Those forms now collect a
-- first name and surname separately, like the Users and Field Officers pages already did.
--
-- Idempotent: guarded so a re-run after the first successful run is a no-op. Safe to re-run.

IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('users') AND name = 'other_names')
   AND NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('users') AND name = 'surname')
    EXEC sp_rename 'users.other_names', 'surname', 'COLUMN';
GO
