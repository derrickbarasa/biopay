-- 019_geo_location_country_codes.sql
--
-- Location codes (geo_states.state_code / geo_counties.county_code /
-- geo_locations.location_code / geo_villages.village_code -- see
-- 006_geo_hierarchy.sql) were bare sequential numbers with no indication of
-- which country they belonged to. New codes are now generated server-side
-- (Geography.java) as <ISO2-country><sequential-number>, e.g. Kenya -> KE2000,
-- Uganda -> UG3000.
--
-- The geo hierarchy itself (state/county/location/village -- South-Sudan-style
-- admin terms) never tracked which country a state belongs to, so this adds a
-- nullable ISO-3166-1 alpha-2 `country` column to geo_states only (the top of
-- the hierarchy); counties/locations/villages resolve their country by looking
-- up their ancestor state's `country` via state_code at code-generation time,
-- rather than duplicating the column down every level. Nullable and optional --
-- a state created without a country keeps generating plain numeric codes
-- (the old behaviour) for itself and everything under it, so existing rows and
-- any admin who skips the new "Country" field are unaffected.
--
-- Idempotent, no FK constraints, matching this database's convention. Safe to re-run.

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('geo_states') AND name = 'country')
    ALTER TABLE geo_states ADD country VARCHAR(2) NULL;
GO
