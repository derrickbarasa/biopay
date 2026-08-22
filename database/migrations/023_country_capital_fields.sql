-- 023_country_capital_fields.sql
--
-- 2026-08-22 request: organisation registration and the anchor settings page
-- should both offer a Country picker that auto-fills a Capital City field
-- (frontend/src/utils/countries.ts is the shared name->capital dataset), with
-- the anchor's existing free-text `address` field kept for the actual street
-- address (e.g. "Karen Road") rather than reused for the city.
--
-- partners.country already exists (013_org_household_subscription_rbac.sql);
-- this adds the matching capital_city column plus the same two fields on
-- anchors, which previously only had one combined `address` column.
--
-- Idempotent, no FK constraints, matching this database's convention.

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('partners') AND name = 'capital_city')
    ALTER TABLE partners ADD capital_city VARCHAR(100) NULL;
GO

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('anchors') AND name = 'country')
    ALTER TABLE anchors ADD country VARCHAR(80) NULL;
GO
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('anchors') AND name = 'city')
    ALTER TABLE anchors ADD city VARCHAR(100) NULL;
GO
