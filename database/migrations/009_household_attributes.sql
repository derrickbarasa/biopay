-- 009_household_attributes.sql
--
-- Adds the two beneficiary-classification attributes the web dashboard filters
-- and breaks down households by: vulnerability status (e.g. ELDERLY, DISABLED,
-- CHILD_HEADED, CHRONICALLY_ILL, ...) and legal status (e.g. CITIZEN, REFUGEE,
-- IDP, STATELESS, ...). The mobile app already captures a vulnerability status
-- during registration; this brings the web `households` schema in line so it
-- can be stored, filtered and graphed.
--
-- Free-text VARCHAR rather than a lookup table, matching this database's
-- existing loosely-coupled *_code / *_status convention (no FK constraints).
-- Nullable so every pre-existing household row stays valid. Safe to re-run.

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('households') AND name = 'vulnerability_status')
    ALTER TABLE households ADD vulnerability_status VARCHAR(50) NULL;
GO
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('households') AND name = 'legal_status')
    ALTER TABLE households ADD legal_status VARCHAR(50) NULL;
GO

-- Filtered-listing helpers (both columns are optional query filters in GET_HOUSEHOLDS).
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_households_vulnerability_status' AND object_id = OBJECT_ID('households'))
    CREATE INDEX IX_households_vulnerability_status ON households(vulnerability_status);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_households_legal_status' AND object_id = OBJECT_ID('households'))
    CREATE INDEX IX_households_legal_status ON households(legal_status);
GO
