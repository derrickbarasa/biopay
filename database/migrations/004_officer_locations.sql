-- 004_officer_locations.sql
--
-- Many-to-many assignment of officers (supervisors) to the existing geo
-- hierarchy (states/counties/payams/bomas), backing the officer management
-- spec's "assign locations" action. A boma-level assignment implies the
-- county/state/payam it belongs to; the wider codes are kept alongside it
-- purely so location-scoped queries don't need a join back into the geo
-- tables just to filter by state or county.

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'officer_locations')
BEGIN
    CREATE TABLE officer_locations (
        id            INT IDENTITY(1,1) PRIMARY KEY,
        supervisor_id INT           NOT NULL,
        state_code    VARCHAR(20)   NULL,
        county_code   VARCHAR(20)   NULL,
        payam_code    VARCHAR(20)   NULL,
        boma_code     VARCHAR(20)   NULL,
        assigned_by   VARCHAR(100)  NULL,
        created_at    DATETIME      NOT NULL DEFAULT GETDATE()
    );

    CREATE INDEX IX_officer_locations_supervisor_id ON officer_locations(supervisor_id);
END
GO
