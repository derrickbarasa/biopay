-- 056_dedupe_geo_hierarchy_names.sql
--
-- Nothing ever stopped two active rows at the same geo level, same anchor and
-- same parent from sharing a name -- e.g. "Kenya" created twice under one
-- anchor, each with its own generated code (see UQ_geo_states_anchor_code:
-- unique on the *code*, not the name). Geography#create/processGeoUploadRow
-- now reject a duplicate name before generating a code; this migration merges
-- whatever duplicates already exist, one level at a time so a state merge's
-- effect on child scoping is picked up before counties are deduped, and so on
-- down to villages.
--
-- For each duplicate name group the lowest-id row is kept as canonical; every
-- reference to a duplicate's code (child geo rows, households, officer
-- assignments, household upload batch audit rows) is repointed to the
-- canonical code, then the duplicate row is soft-deleted (status=0), matching
-- DELETE_GEO_NODE's own soft-delete convention.

-- ---- STATES (scope: anchor_id) -------------------------------------------

IF OBJECT_ID('tempdb..#state_dupes') IS NOT NULL DROP TABLE #state_dupes;
GO
;WITH ranked AS (
    SELECT id, anchor_id, state_code,
           ROW_NUMBER() OVER (PARTITION BY anchor_id, LOWER(LTRIM(RTRIM(name))) ORDER BY id) AS rn,
           FIRST_VALUE(state_code) OVER (PARTITION BY anchor_id, LOWER(LTRIM(RTRIM(name))) ORDER BY id) AS canonical_code
    FROM geo_states
    WHERE status = 1
)
SELECT anchor_id, state_code AS old_code, canonical_code AS new_code
INTO #state_dupes
FROM ranked
WHERE rn > 1 AND state_code <> canonical_code;
GO

UPDATE c SET c.state_code = d.new_code, c.updated_at = GETDATE()
FROM geo_counties c JOIN #state_dupes d ON d.anchor_id = c.anchor_id AND d.old_code = c.state_code;
GO
UPDATE l SET l.state_code = d.new_code, l.updated_at = GETDATE()
FROM geo_locations l JOIN #state_dupes d ON d.anchor_id = l.anchor_id AND d.old_code = l.state_code;
GO
UPDATE v SET v.state_code = d.new_code, v.updated_at = GETDATE()
FROM geo_villages v JOIN #state_dupes d ON d.anchor_id = v.anchor_id AND d.old_code = v.state_code;
GO
UPDATE h SET h.state_code = d.new_code
FROM households h
JOIN organizations o ON o.organization_code = h.organization_code
JOIN #state_dupes d ON d.anchor_id = o.anchor_id AND d.old_code = h.state_code;
GO
UPDATE ol SET ol.state_code = d.new_code
FROM officer_locations ol JOIN #state_dupes d ON d.old_code = ol.state_code;
GO
UPDATE s SET s.status = 0, s.updated_at = GETDATE()
FROM geo_states s JOIN #state_dupes d ON d.anchor_id = s.anchor_id AND d.old_code = s.state_code;
GO
DROP TABLE #state_dupes;
GO

-- ---- COUNTIES (scope: anchor_id + state_code) ----------------------------

IF OBJECT_ID('tempdb..#county_dupes') IS NOT NULL DROP TABLE #county_dupes;
GO
;WITH ranked AS (
    SELECT id, anchor_id, county_code,
           ROW_NUMBER() OVER (PARTITION BY anchor_id, state_code, LOWER(LTRIM(RTRIM(name))) ORDER BY id) AS rn,
           FIRST_VALUE(county_code) OVER (PARTITION BY anchor_id, state_code, LOWER(LTRIM(RTRIM(name))) ORDER BY id) AS canonical_code
    FROM geo_counties
    WHERE status = 1
)
SELECT anchor_id, county_code AS old_code, canonical_code AS new_code
INTO #county_dupes
FROM ranked
WHERE rn > 1 AND county_code <> canonical_code;
GO

UPDATE l SET l.county_code = d.new_code, l.updated_at = GETDATE()
FROM geo_locations l JOIN #county_dupes d ON d.anchor_id = l.anchor_id AND d.old_code = l.county_code;
GO
UPDATE v SET v.county_code = d.new_code, v.updated_at = GETDATE()
FROM geo_villages v JOIN #county_dupes d ON d.anchor_id = v.anchor_id AND d.old_code = v.county_code;
GO
UPDATE h SET h.county_code = d.new_code
FROM households h
JOIN organizations o ON o.organization_code = h.organization_code
JOIN #county_dupes d ON d.anchor_id = o.anchor_id AND d.old_code = h.county_code;
GO
UPDATE ol SET ol.county_code = d.new_code
FROM officer_locations ol JOIN #county_dupes d ON d.old_code = ol.county_code;
GO
UPDATE c SET c.status = 0, c.updated_at = GETDATE()
FROM geo_counties c JOIN #county_dupes d ON d.anchor_id = c.anchor_id AND d.old_code = c.county_code;
GO
DROP TABLE #county_dupes;
GO

-- ---- LOCATIONS / payams (scope: anchor_id + county_code) -----------------

IF OBJECT_ID('tempdb..#location_dupes') IS NOT NULL DROP TABLE #location_dupes;
GO
;WITH ranked AS (
    SELECT id, anchor_id, location_code,
           ROW_NUMBER() OVER (PARTITION BY anchor_id, county_code, LOWER(LTRIM(RTRIM(name))) ORDER BY id) AS rn,
           FIRST_VALUE(location_code) OVER (PARTITION BY anchor_id, county_code, LOWER(LTRIM(RTRIM(name))) ORDER BY id) AS canonical_code
    FROM geo_locations
    WHERE status = 1
)
SELECT anchor_id, location_code AS old_code, canonical_code AS new_code
INTO #location_dupes
FROM ranked
WHERE rn > 1 AND location_code <> canonical_code;
GO

UPDATE v SET v.location_code = d.new_code, v.updated_at = GETDATE()
FROM geo_villages v JOIN #location_dupes d ON d.anchor_id = v.anchor_id AND d.old_code = v.location_code;
GO
UPDATE h SET h.location_code = d.new_code, h.payam_code = d.new_code
FROM households h
JOIN organizations o ON o.organization_code = h.organization_code
JOIN #location_dupes d ON d.anchor_id = o.anchor_id AND d.old_code = h.location_code;
GO
UPDATE ol SET ol.location_code = d.new_code, ol.payam_code = d.new_code
FROM officer_locations ol JOIN #location_dupes d ON d.old_code = ol.location_code;
GO
UPDATE b SET b.village_code = d.new_code
FROM household_upload_batches b JOIN #location_dupes d ON d.old_code = b.village_code;
GO
UPDATE l SET l.status = 0, l.updated_at = GETDATE()
FROM geo_locations l JOIN #location_dupes d ON d.anchor_id = l.anchor_id AND d.old_code = l.location_code;
GO
DROP TABLE #location_dupes;
GO

-- ---- VILLAGES / bomas (scope: anchor_id + location_code) -----------------

IF OBJECT_ID('tempdb..#village_dupes') IS NOT NULL DROP TABLE #village_dupes;
GO
;WITH ranked AS (
    SELECT id, anchor_id, village_code,
           ROW_NUMBER() OVER (PARTITION BY anchor_id, location_code, LOWER(LTRIM(RTRIM(name))) ORDER BY id) AS rn,
           FIRST_VALUE(village_code) OVER (PARTITION BY anchor_id, location_code, LOWER(LTRIM(RTRIM(name))) ORDER BY id) AS canonical_code
    FROM geo_villages
    WHERE status = 1
)
SELECT anchor_id, village_code AS old_code, canonical_code AS new_code
INTO #village_dupes
FROM ranked
WHERE rn > 1 AND village_code <> canonical_code;
GO

UPDATE h SET h.village_code = d.new_code, h.boma_code = d.new_code
FROM households h
JOIN organizations o ON o.organization_code = h.organization_code
JOIN #village_dupes d ON d.anchor_id = o.anchor_id AND d.old_code = h.village_code;
GO
UPDATE ol SET ol.village_code = d.new_code, ol.boma_code = d.new_code
FROM officer_locations ol JOIN #village_dupes d ON d.old_code = ol.village_code;
GO
UPDATE b SET b.village_code = d.new_code
FROM household_upload_batches b JOIN #village_dupes d ON d.old_code = b.village_code;
GO
UPDATE v SET v.status = 0, v.updated_at = GETDATE()
FROM geo_villages v JOIN #village_dupes d ON d.anchor_id = v.anchor_id AND d.old_code = v.village_code;
GO
DROP TABLE #village_dupes;
GO
