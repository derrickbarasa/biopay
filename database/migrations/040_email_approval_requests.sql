-- Time-limited, single-use email actions for approval workflows.
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'email_approval_requests')
BEGIN
    CREATE TABLE email_approval_requests (
        id BIGINT IDENTITY(1,1) PRIMARY KEY,
        request_type VARCHAR(30) NOT NULL,
        reference_code VARCHAR(100) NOT NULL,
        approver_id INT NOT NULL,
        anchor_id INT NULL,
        organization_code VARCHAR(20) NULL,
        token_hash CHAR(64) NOT NULL,
        request_data NVARCHAR(MAX) NULL,
        expires_at DATETIME NOT NULL,
        used_at DATETIME NULL,
        decision VARCHAR(20) NULL,
        created_at DATETIME NOT NULL DEFAULT GETDATE(),
        CONSTRAINT UQ_email_approval_token_hash UNIQUE (token_hash),
        CONSTRAINT CK_email_approval_request_type CHECK (request_type IN ('PAYROLL', 'HOUSEHOLD')),
        CONSTRAINT CK_email_approval_decision CHECK (decision IS NULL OR decision IN ('APPROVED', 'SUPERSEDED', 'UNAVAILABLE'))
    );

    CREATE INDEX IX_email_approval_reference
        ON email_approval_requests(request_type, reference_code, approver_id, used_at, expires_at);
END
GO
