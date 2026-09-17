-- Stable public state codes without rewriting legacy relationship keys.
--
-- Older mobile/manual geography stored the typed state name directly in
-- geo_states.state_code. That value is referenced by households, child geography,
-- officer assignments, and offline devices, so changing it in place would orphan
-- data. display_code is the user-facing identifier; state_code remains the stable
-- internal relationship key. New portal-created states use the same generated value
-- for both columns, while legacy rows receive a safe public code here.

IF NOT EXISTS (
    SELECT 1 FROM sys.columns
    WHERE object_id = OBJECT_ID('geo_states') AND name = 'display_code'
)
    ALTER TABLE geo_states ADD display_code VARCHAR(20) NULL;
GO

-- Populate ISO country metadata for the country-name legacy rows reported in the
-- existing catalogue. Other legacy rows retain their current nullable country and
-- can be assigned explicitly from Edit State without changing state_code.
UPDATE geo_states
SET country = CASE LOWER(LTRIM(RTRIM(name)))
    WHEN 'equatorial guinea' THEN 'GQ'
    WHEN 'kenya' THEN 'KE'
    WHEN 'somalia' THEN 'SO'
    ELSE country
END
WHERE country IS NULL
  AND LOWER(LTRIM(RTRIM(name))) IN ('equatorial guinea', 'kenya', 'somalia');
GO

-- Keep already-valid generated codes unchanged. Rank only legacy rows so every
-- anchor receives a deterministic, non-destructive display sequence in 1000 steps.
UPDATE geo_states
SET display_code = UPPER(state_code)
WHERE display_code IS NULL
  AND state_code LIKE '[A-Za-z][A-Za-z][0-9]%'
  AND TRY_CAST(SUBSTRING(state_code, 3, LEN(state_code)) AS INT) IS NOT NULL;
GO

;WITH ExistingMaximum AS (
    SELECT anchor_id,
           COALESCE(MAX(TRY_CAST(SUBSTRING(display_code, 3, LEN(display_code)) AS INT)), 999) AS max_number
    FROM geo_states
    GROUP BY anchor_id
), LegacyRows AS (
    SELECT s.id, s.anchor_id, m.max_number,
           ROW_NUMBER() OVER (PARTITION BY s.anchor_id ORDER BY s.id) AS row_number,
           CASE
               WHEN s.country LIKE '[A-Za-z][A-Za-z]' THEN UPPER(s.country)
               WHEN LOWER(LTRIM(RTRIM(s.name))) = 'equatorial guinea' THEN 'GQ'
               WHEN LOWER(LTRIM(RTRIM(s.name))) = 'kenya' THEN 'KE'
               WHEN LOWER(LTRIM(RTRIM(s.name))) = 'somalia' THEN 'SO'
               WHEN LEN(REPLACE(LTRIM(RTRIM(s.name)), ' ', '')) >= 2
                   THEN UPPER(LEFT(REPLACE(LTRIM(RTRIM(s.name)), ' ', ''), 2))
               ELSE 'ST'
           END AS prefix
    FROM geo_states s
    JOIN ExistingMaximum m ON m.anchor_id = s.anchor_id
    WHERE s.display_code IS NULL
)
UPDATE s
SET display_code = l.prefix + CAST(((l.max_number / 1000) + l.row_number) * 1000 AS VARCHAR(12))
FROM geo_states s
JOIN LegacyRows l ON l.id = s.id;
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID('geo_states') AND name = 'UX_geo_states_anchor_display_code'
)
    CREATE UNIQUE INDEX UX_geo_states_anchor_display_code
        ON geo_states(anchor_id, display_code)
        WHERE display_code IS NOT NULL;
GO
