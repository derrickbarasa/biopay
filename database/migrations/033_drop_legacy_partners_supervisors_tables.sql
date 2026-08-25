-- 033_drop_legacy_partners_supervisors_tables.sql
--
-- Drops two more leftover pre-rename tables, same shape of bug as 032's
-- vwnames: migration 029 renamed `partners`->`organizations` and
-- `supervisors`->`field_officers` via sp_rename (preserving the real data),
-- but 000_base_schema.sql's "create if missing" guards for those two tables
-- only checked whether a table literally named `partners`/`supervisors`
-- existed -- once the rename made that name disappear, a later idempotent
-- re-run of 000 against an already-migrated database silently recreated
-- empty, pre-rename-shaped tables under the old names again. Confirmed both
-- are empty and unreferenced anywhere in backend/src before dropping.
--
-- Idempotent, and genuinely replay-safe (unlike a naive first attempt at this
-- file): everything that names `partners`/`supervisors` directly runs as
-- dynamic SQL inside an OBJECT_ID guard. A plain
-- `IF OBJECT_ID(...) IS NOT NULL AND NOT EXISTS (SELECT 1 FROM partners) ...`
-- looks safe but isn't -- SQL Server resolves every table name in a batch at
-- parse time, even one behind a false IF branch, so the very next replay
-- (after the table's already gone) fails with "Invalid object name
-- 'partners'" -- the exact class of bug 001/002/005/011/013/018/021/023/026/
-- 027/028 were fixed for previously in this same migration set.

IF OBJECT_ID('partners', 'U') IS NOT NULL
BEGIN
    DECLARE @partnerRows INT;
    EXEC sp_executesql N'SELECT @c = COUNT(*) FROM partners', N'@c INT OUTPUT', @c = @partnerRows OUTPUT;
    IF @partnerRows = 0
        EXEC('DROP TABLE partners');
END
GO

IF OBJECT_ID('supervisors', 'U') IS NOT NULL
BEGIN
    DECLARE @supervisorRows INT;
    EXEC sp_executesql N'SELECT @c = COUNT(*) FROM supervisors', N'@c INT OUTPUT', @c = @supervisorRows OUTPUT;
    IF @supervisorRows = 0
        EXEC('DROP TABLE supervisors');
END
GO
