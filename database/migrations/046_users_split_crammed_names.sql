-- 046_users_split_crammed_names.sql
--
-- One-off data cleanup for rows created by the bugs fixed alongside 045_users_surname_rename.sql:
-- anchor self-signup, an anchor created by the platform owner, and an organisation's admin login
-- (created when the organisation itself is created) each took a single "authorised contact name"
-- form field and dumped the whole string into first_name, leaving surname blank/NULL.
--
-- This splits every such row on its first space: the part before the space becomes first_name,
-- the rest becomes surname. It only touches rows where surname is currently blank/NULL AND
-- first_name contains a space -- a row that already has a real surname, or whose first_name is
-- genuinely a single word, is left untouched.
--
-- Idempotent: after the first run, every affected row has surname populated and first_name
-- no longer contains a space, so the WHERE clause matches nothing on a re-run. Safe to re-run.

UPDATE users
SET surname = LTRIM(SUBSTRING(first_name, CHARINDEX(' ', first_name) + 1, LEN(first_name))),
    first_name = LEFT(first_name, CHARINDEX(' ', first_name) - 1),
    updated_at = GETDATE()
WHERE (surname IS NULL OR LTRIM(RTRIM(surname)) = '')
  AND first_name IS NOT NULL
  AND CHARINDEX(' ', LTRIM(RTRIM(first_name))) > 0
  AND LTRIM(RTRIM(first_name)) = first_name;
GO
