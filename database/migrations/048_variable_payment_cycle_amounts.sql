-- Payment-cycle amounts now belong to each beneficiary. The cycle header keeps
-- FCY/LCY totals; a single amount or rate is populated only when every row shares it.

IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('payment_cycles') AND name = 'amount_per_household' AND is_nullable = 0)
    ALTER TABLE payment_cycles ALTER COLUMN amount_per_household NUMERIC(18,2) NULL;
GO

IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('payment_cycles') AND name = 'exchange_rate' AND is_nullable = 0)
    ALTER TABLE payment_cycles ALTER COLUMN exchange_rate DECIMAL(18,6) NULL;
GO

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('payment_cycles') AND name = 'total_amount_lcy')
    ALTER TABLE payment_cycles ADD total_amount_lcy NUMERIC(18,2) NULL;
GO

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('payments') AND name = 'amount_lcy')
    ALTER TABLE payments ADD amount_lcy NUMERIC(18,2) NULL;
GO

UPDATE payment_cycles
SET total_amount_lcy = ROUND(total_amount * ISNULL(exchange_rate, 1), 2)
WHERE total_amount_lcy IS NULL;
GO

UPDATE payments
SET amount_lcy = ROUND(amount * ISNULL(exchange_rate, 1), 2)
WHERE amount_lcy IS NULL;
GO
