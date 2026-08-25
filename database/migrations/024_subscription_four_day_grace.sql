-- Anchor subscriptions renew in 30-day periods with a four-day grace window.
UPDATE subscriptions SET grace_days = 4 WHERE grace_days IS NULL OR grace_days <> 4;
GO

DECLARE @graceConstraint SYSNAME;
SELECT @graceConstraint = dc.name
FROM sys.default_constraints dc
JOIN sys.columns c ON c.default_object_id = dc.object_id
WHERE dc.parent_object_id = OBJECT_ID('subscriptions') AND c.name = 'grace_days';
IF @graceConstraint IS NOT NULL
    EXEC('ALTER TABLE subscriptions DROP CONSTRAINT [' + @graceConstraint + ']');
ALTER TABLE subscriptions ADD CONSTRAINT DF_subscriptions_grace_days DEFAULT 4 FOR grace_days;
GO
