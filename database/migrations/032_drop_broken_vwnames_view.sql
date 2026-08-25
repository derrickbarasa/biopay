-- 032_drop_broken_vwnames_view.sql
--
-- Drops `vwnames`, a leftover view from before migration 031. It unions
-- `households` with `vwalternates`, but 031 dropped `vwalternates` as part
-- of the legacy view cleanup without also dropping this dependent view --
-- SQL Server doesn't track view-to-view dependencies at DROP time, so it
-- didn't error, it just left `vwnames` permanently broken ("Invalid object
-- name 'vwalternates'" on any SELECT). Unreferenced anywhere in backend/src
-- or the migration history.
--
-- Idempotent: guarded, safe to re-run.

IF OBJECT_ID('vwnames', 'V') IS NOT NULL DROP VIEW vwnames;
GO
