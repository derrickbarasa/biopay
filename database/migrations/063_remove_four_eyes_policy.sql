-- The four-eyes toggle was withdrawn. Payment-cycle creators are eligible to approve
-- their own cycles when they hold CHECK_PAYMENT_CYCLES, just like every other approver.

DECLARE @constraintName sysname;
DECLARE @sql nvarchar(max);

SELECT @constraintName = dc.name
FROM sys.default_constraints dc
JOIN sys.columns c ON c.default_object_id = dc.object_id
WHERE dc.parent_object_id = OBJECT_ID('organizations') AND c.name = 'four_eyes_required';

IF @constraintName IS NOT NULL
BEGIN
    SET @sql = N'ALTER TABLE organizations DROP CONSTRAINT ' + QUOTENAME(@constraintName);
    EXEC(@sql);
END

IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('organizations') AND name = 'four_eyes_required')
    ALTER TABLE organizations DROP COLUMN four_eyes_required;
GO

DECLARE @constraintName sysname;
DECLARE @sql nvarchar(max);

SELECT @constraintName = dc.name
FROM sys.default_constraints dc
JOIN sys.columns c ON c.default_object_id = dc.object_id
WHERE dc.parent_object_id = OBJECT_ID('payment_cycles') AND c.name = 'four_eyes_required';

IF @constraintName IS NOT NULL
BEGIN
    SET @sql = N'ALTER TABLE payment_cycles DROP CONSTRAINT ' + QUOTENAME(@constraintName);
    EXEC(@sql);
END

IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('payment_cycles') AND name = 'four_eyes_required')
    ALTER TABLE payment_cycles DROP COLUMN four_eyes_required;
GO
