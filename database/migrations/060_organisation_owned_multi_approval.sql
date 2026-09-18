-- Payment cycles are now approved and disbursed by the organisation itself, not the anchor
-- (see Payroll.java). An organisation may also require more than one of its own approvers to
-- sign off: `organizations.required_approvals` is that organisation's own policy, set only by
-- its own administrator (Organization#setApprovalPolicy). It is snapshotted onto
-- `payment_cycles.required_approvals` at generation time so a later policy change never
-- retroactively changes how many approvals an already-pending cycle needs.

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('organizations') AND name = 'required_approvals')
    ALTER TABLE organizations ADD required_approvals INT NOT NULL DEFAULT 1;
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_organizations_required_approvals')
    ALTER TABLE organizations ADD CONSTRAINT CK_organizations_required_approvals CHECK (required_approvals BETWEEN 1 AND 5);
GO

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('payment_cycles') AND name = 'required_approvals')
    ALTER TABLE payment_cycles ADD required_approvals INT NOT NULL DEFAULT 1;
GO

-- One row per approver who has signed off on a cycle -- lets a cycle require several
-- distinct organisation approvers (maker-checker with N checkers) while keeping a full
-- audit trail of who approved and when. A single rejection still short-circuits the
-- whole cycle straight to REJECTED (see Payroll#reject), so there is no matching table
-- for rejections.
IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'payment_cycle_approvals')
BEGIN
    CREATE TABLE payment_cycle_approvals (
        id                INT IDENTITY(1,1) PRIMARY KEY,
        payment_cycle_id  INT           NOT NULL,
        approver_id       INT           NOT NULL,
        approved_at       DATETIME      NOT NULL DEFAULT GETDATE(),
        CONSTRAINT UQ_payment_cycle_approvals UNIQUE (payment_cycle_id, approver_id),
        CONSTRAINT FK_payment_cycle_approvals_cycle FOREIGN KEY (payment_cycle_id) REFERENCES payment_cycles(id)
    );

    CREATE INDEX IX_payment_cycle_approvals_cycle ON payment_cycle_approvals(payment_cycle_id);
END
GO
