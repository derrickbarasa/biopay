-- 041_promote_household_locations.sql
--
-- Older mobile registrations stored manually entered location labels directly on
-- households. Promote those values into the anchor geography catalogue so they
-- appear in Locations and in every hierarchy-backed location selector.
--
-- The geography schema treats codes as unique within an anchor. ROW_NUMBER picks
-- one deterministic parent path if legacy data contains the same child label
-- under more than one parent. Existing catalogue records are never overwritten.

;WITH candidates AS (
    SELECT o.anchor_id, LTRIM(RTRIM(h.state_code)) AS state_code,
           ROW_NUMBER() OVER (
               PARTITION BY o.anchor_id, LTRIM(RTRIM(h.state_code))
               ORDER BY h.id
           ) AS row_number
    FROM households h
    INNER JOIN organizations o ON o.organization_code = h.organization_code
    WHERE NULLIF(LTRIM(RTRIM(h.state_code)), '') IS NOT NULL
)
INSERT INTO geo_states (anchor_id, state_code, name, status, created_by, created_at)
SELECT c.anchor_id, c.state_code, c.state_code, 1, 'MOBILE_BACKFILL', GETDATE()
FROM candidates c
WHERE c.row_number = 1
  AND NOT EXISTS (
      SELECT 1 FROM geo_states existing
      WHERE existing.anchor_id = c.anchor_id AND existing.state_code = c.state_code
  );
GO

;WITH candidates AS (
    SELECT o.anchor_id,
           LTRIM(RTRIM(h.state_code)) AS state_code,
           LTRIM(RTRIM(h.county_code)) AS county_code,
           ROW_NUMBER() OVER (
               PARTITION BY o.anchor_id, LTRIM(RTRIM(h.county_code))
               ORDER BY h.id
           ) AS row_number
    FROM households h
    INNER JOIN organizations o ON o.organization_code = h.organization_code
    WHERE NULLIF(LTRIM(RTRIM(h.state_code)), '') IS NOT NULL
      AND NULLIF(LTRIM(RTRIM(h.county_code)), '') IS NOT NULL
)
INSERT INTO geo_counties (anchor_id, state_code, county_code, name, status, created_by, created_at)
SELECT c.anchor_id, c.state_code, c.county_code, c.county_code, 1, 'MOBILE_BACKFILL', GETDATE()
FROM candidates c
WHERE c.row_number = 1
  AND NOT EXISTS (
      SELECT 1 FROM geo_counties existing
      WHERE existing.anchor_id = c.anchor_id AND existing.county_code = c.county_code
  );
GO

;WITH candidates AS (
    SELECT o.anchor_id,
           LTRIM(RTRIM(h.state_code)) AS state_code,
           LTRIM(RTRIM(h.county_code)) AS county_code,
           LTRIM(RTRIM(h.payam_code)) AS location_code,
           ROW_NUMBER() OVER (
               PARTITION BY o.anchor_id, LTRIM(RTRIM(h.payam_code))
               ORDER BY h.id
           ) AS row_number
    FROM households h
    INNER JOIN organizations o ON o.organization_code = h.organization_code
    WHERE NULLIF(LTRIM(RTRIM(h.state_code)), '') IS NOT NULL
      AND NULLIF(LTRIM(RTRIM(h.county_code)), '') IS NOT NULL
      AND NULLIF(LTRIM(RTRIM(h.payam_code)), '') IS NOT NULL
)
INSERT INTO geo_locations (anchor_id, state_code, county_code, location_code, name, status, created_by, created_at)
SELECT c.anchor_id, c.state_code, c.county_code, c.location_code, c.location_code,
       1, 'MOBILE_BACKFILL', GETDATE()
FROM candidates c
WHERE c.row_number = 1
  AND NOT EXISTS (
      SELECT 1 FROM geo_locations existing
      WHERE existing.anchor_id = c.anchor_id AND existing.location_code = c.location_code
  );
GO

;WITH candidates AS (
    SELECT o.anchor_id,
           LTRIM(RTRIM(h.state_code)) AS state_code,
           LTRIM(RTRIM(h.county_code)) AS county_code,
           LTRIM(RTRIM(h.payam_code)) AS location_code,
           LTRIM(RTRIM(h.boma_code)) AS village_code,
           ROW_NUMBER() OVER (
               PARTITION BY o.anchor_id, LTRIM(RTRIM(h.boma_code))
               ORDER BY h.id
           ) AS row_number
    FROM households h
    INNER JOIN organizations o ON o.organization_code = h.organization_code
    WHERE NULLIF(LTRIM(RTRIM(h.state_code)), '') IS NOT NULL
      AND NULLIF(LTRIM(RTRIM(h.county_code)), '') IS NOT NULL
      AND NULLIF(LTRIM(RTRIM(h.payam_code)), '') IS NOT NULL
      AND NULLIF(LTRIM(RTRIM(h.boma_code)), '') IS NOT NULL
)
INSERT INTO geo_villages (
    anchor_id, state_code, county_code, location_code, village_code,
    name, status, created_by, created_at
)
SELECT c.anchor_id, c.state_code, c.county_code, c.location_code, c.village_code,
       c.village_code, 1, 'MOBILE_BACKFILL', GETDATE()
FROM candidates c
WHERE c.row_number = 1
  AND NOT EXISTS (
      SELECT 1 FROM geo_villages existing
      WHERE existing.anchor_id = c.anchor_id AND existing.village_code = c.village_code
  );
GO
