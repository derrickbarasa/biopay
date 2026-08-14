-- 006_geo_hierarchy.sql
--
-- Configurable geo master data (states -> counties -> locations -> villages)
-- backing the household bulk-upload-by-village feature and replacing free-typed
-- state_code/county_code/payam_code(location)/boma_code(village) values on
-- households and officer_locations with data drawn from a real hierarchy.
--
-- Scoping: every level is owned by anchor_id and shared across every
-- organisation under that anchor (an anchor sets up the hierarchy once;
-- organisations pick from it rather than each maintaining their own copy).
-- Both anchor admins and organisation admins may create/edit entries --
-- organisation admins carry anchor_id in their session too (see Auth#loginUser),
-- so no extra plumbing is needed to let them configure on the anchor's behalf.
--
-- No FOREIGN KEY constraints, matching this database's existing convention of
-- loosely-coupled *_code columns (see 001_anchors_and_scoping.sql). Safe to
-- re-run: every DDL change is guarded with an existence check.

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'geo_states')
BEGIN
    CREATE TABLE geo_states (
        id          INT IDENTITY(1,1) PRIMARY KEY,
        anchor_id   INT          NOT NULL,
        state_code  VARCHAR(20)  NOT NULL,
        name        VARCHAR(150) NOT NULL,
        status      INT          NOT NULL DEFAULT 1,
        created_by  VARCHAR(100) NULL,
        created_at  DATETIME     NOT NULL DEFAULT GETDATE(),
        updated_at  DATETIME     NULL,
        CONSTRAINT UQ_geo_states_anchor_code UNIQUE (anchor_id, state_code)
    );
    CREATE INDEX IX_geo_states_anchor_id ON geo_states(anchor_id);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'geo_counties')
BEGIN
    CREATE TABLE geo_counties (
        id          INT IDENTITY(1,1) PRIMARY KEY,
        anchor_id   INT          NOT NULL,
        state_code  VARCHAR(20)  NOT NULL,
        county_code VARCHAR(20)  NOT NULL,
        name        VARCHAR(150) NOT NULL,
        status      INT          NOT NULL DEFAULT 1,
        created_by  VARCHAR(100) NULL,
        created_at  DATETIME     NOT NULL DEFAULT GETDATE(),
        updated_at  DATETIME     NULL,
        CONSTRAINT UQ_geo_counties_anchor_code UNIQUE (anchor_id, county_code)
    );
    CREATE INDEX IX_geo_counties_anchor_id ON geo_counties(anchor_id);
    CREATE INDEX IX_geo_counties_state_code ON geo_counties(anchor_id, state_code);
END
GO

-- "location" = the existing payam terminology (see 005_locations_and_vouchers.sql).
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'geo_locations')
BEGIN
    CREATE TABLE geo_locations (
        id            INT IDENTITY(1,1) PRIMARY KEY,
        anchor_id     INT          NOT NULL,
        state_code    VARCHAR(20)  NOT NULL,
        county_code   VARCHAR(20)  NOT NULL,
        location_code VARCHAR(20)  NOT NULL,
        name          VARCHAR(150) NOT NULL,
        status        INT          NOT NULL DEFAULT 1,
        created_by    VARCHAR(100) NULL,
        created_at    DATETIME     NOT NULL DEFAULT GETDATE(),
        updated_at    DATETIME     NULL,
        CONSTRAINT UQ_geo_locations_anchor_code UNIQUE (anchor_id, location_code)
    );
    CREATE INDEX IX_geo_locations_anchor_id ON geo_locations(anchor_id);
    CREATE INDEX IX_geo_locations_county_code ON geo_locations(anchor_id, county_code);
END
GO

-- "village" = the existing boma terminology.
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'geo_villages')
BEGIN
    CREATE TABLE geo_villages (
        id            INT IDENTITY(1,1) PRIMARY KEY,
        anchor_id     INT          NOT NULL,
        state_code    VARCHAR(20)  NOT NULL,
        county_code   VARCHAR(20)  NOT NULL,
        location_code VARCHAR(20)  NOT NULL,
        village_code  VARCHAR(20)  NOT NULL,
        name          VARCHAR(150) NOT NULL,
        status        INT          NOT NULL DEFAULT 1,
        created_by    VARCHAR(100) NULL,
        created_at    DATETIME     NOT NULL DEFAULT GETDATE(),
        updated_at    DATETIME     NULL,
        CONSTRAINT UQ_geo_villages_anchor_code UNIQUE (anchor_id, village_code)
    );
    CREATE INDEX IX_geo_villages_anchor_id ON geo_villages(anchor_id);
    CREATE INDEX IX_geo_villages_location_code ON geo_villages(anchor_id, location_code);
END
GO
