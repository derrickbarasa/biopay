-- Supports hierarchy-scoped audit browsing and per-person activity history.
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='IX_audit_logs_anchor_created' AND object_id=OBJECT_ID('audit_logs'))
    CREATE INDEX IX_audit_logs_anchor_created ON audit_logs(anchor_id, created_at DESC);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='IX_audit_logs_actor_created' AND object_id=OBJECT_ID('audit_logs'))
    CREATE INDEX IX_audit_logs_actor_created ON audit_logs(actor_type, actor_id, created_at DESC);
GO
