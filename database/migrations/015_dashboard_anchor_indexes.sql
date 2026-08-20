-- 015_dashboard_anchor_indexes.sql
--
-- Fixes a real performance bug found while investigating "the dashboard is
-- slow to load": Dashboard.java's DASHBOARD_METRICS/DASHBOARD_PAYMENTS_CHART
-- (and Payroll.java's GET_PAYROLLS, Biometric.java's GET_ATTENDANCE) filter
-- directly on payments.anchor_id / vouchers.anchor_id / payroll_cycles.anchor_id
-- / attendances.anchor_id for every anchor-role request -- but 001 and 002
-- and 005_locations_and_vouchers.sql and 005_attendance_scoping.sql only ever
-- added those columns, never an index on them (unlike partner_code, which was
-- indexed alongside anchor_id everywhere it was added). Every anchor dashboard
-- load and every payroll/attendance list therefore does a full table scan on
-- these tables against the remote MSSQL instance -- the slowness compounds
-- with table size and gets worse over time as more payments/vouchers accrue.
--
-- Idempotent, no FK constraints, matches this database's existing convention.

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_payments_anchor_id')
    CREATE INDEX IX_payments_anchor_id ON payments(anchor_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_vouchers_anchor_id')
    CREATE INDEX IX_vouchers_anchor_id ON vouchers(anchor_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_payroll_cycles_anchor_id')
    CREATE INDEX IX_payroll_cycles_anchor_id ON payroll_cycles(anchor_id);
GO
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_attendances_anchor_id')
    CREATE INDEX IX_attendances_anchor_id ON attendances(anchor_id);
GO

-- DASHBOARD_METRICS' recentTransactions query and DASHBOARD_PAYMENTS_CHART's
-- monthly GROUP BY both sort/bucket on payments.created_at with no index to
-- back it, on top of the missing anchor_id filter above.
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_payments_created_at')
    CREATE INDEX IX_payments_created_at ON payments(created_at);
GO
