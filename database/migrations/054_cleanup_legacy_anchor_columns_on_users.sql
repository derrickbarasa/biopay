-- 054_cleanup_legacy_anchor_columns_on_users.sql
--
-- Follow-up to 053_dedicated_anchors_table.sql: now that an anchor's own identity lives on
-- `anchors`, the copies of that same data migration 030 had added directly to `users` (so an
-- anchor's own row could double as "the anchor") are dead weight -- nothing in the application
-- reads or writes users.anchor_code/anchor_name/phone/address/country/city/website anymore
-- (verified against every backend service and every frontend page that reads a user row; the
-- values were already copied into `anchors` by 053 before this drops them, so no data is lost).
-- Dropping them removes any chance of the two copies silently drifting apart, and removes the
-- possibility of a future query accidentally reading anchor identity back off a `users` row --
-- exactly the bug class 053's own header explains.
--
-- Also adds the uniqueness constraint on anchors.anchor_code that the original standalone
-- anchors table had (migration 001's UQ_anchors_anchor_code, dropped along with that table by
-- 030) but 053 didn't carry forward.
--
-- Idempotent (every step is existence-guarded) and safe to re-run.

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('anchors') AND name = 'UQ_anchors_anchor_code')
    ALTER TABLE anchors ADD CONSTRAINT UQ_anchors_anchor_code UNIQUE (anchor_code);
GO

IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('users') AND name = 'anchor_code')
    ALTER TABLE users DROP COLUMN anchor_code;
GO
IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('users') AND name = 'anchor_name')
    ALTER TABLE users DROP COLUMN anchor_name;
GO
IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('users') AND name = 'phone')
    ALTER TABLE users DROP COLUMN phone;
GO
IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('users') AND name = 'address')
    ALTER TABLE users DROP COLUMN address;
GO
IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('users') AND name = 'country')
    ALTER TABLE users DROP COLUMN country;
GO
IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('users') AND name = 'city')
    ALTER TABLE users DROP COLUMN city;
GO
IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('users') AND name = 'website')
    ALTER TABLE users DROP COLUMN website;
GO
