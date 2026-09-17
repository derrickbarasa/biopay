-- Canonicalize state display codes globally by country + normalized state name.
--
-- display_code is user-facing only. The internal state_code values are deliberately
-- not rewritten here because households, child geography, officer assignments, and
-- offline devices still reference them as stable relationship keys.

IF COL_LENGTH('geo_states', 'display_code') IS NOT NULL
BEGIN
    ;WITH Candidates AS (
        SELECT id,
               country,
               LOWER(LTRIM(RTRIM(name))) AS normalized_name,
               display_code,
               CASE
                   WHEN PATINDEX('%[0-9]%', display_code) > 0
                       THEN TRY_CAST(SUBSTRING(display_code, PATINDEX('%[0-9]%', display_code), LEN(display_code)) AS INT)
                   ELSE 2147483647
               END AS numeric_part
        FROM geo_states
        WHERE country IS NOT NULL
          AND NULLIF(LTRIM(RTRIM(name)), '') IS NOT NULL
          AND display_code IS NOT NULL
    ),
    Canonical AS (
        SELECT id,
               FIRST_VALUE(display_code) OVER (
                   PARTITION BY country, normalized_name
                   ORDER BY numeric_part, display_code, id
               ) AS canonical_display_code
        FROM Candidates
    )
    UPDATE s
    SET display_code = c.canonical_display_code,
        updated_at = GETDATE()
    FROM geo_states s
    JOIN Canonical c ON c.id = s.id
    WHERE s.display_code <> c.canonical_display_code;
END;
GO
