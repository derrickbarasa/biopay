-- Household review is a single decision: PENDING -> APPROVED or REJECTED.
-- Convert the retired CHECKED stage (and any unknown legacy value) back to
-- PENDING so every undecided household remains visible and actionable.

UPDATE households
SET review_status = 'PENDING'
WHERE review_status IS NULL
   OR review_status NOT IN ('PENDING', 'APPROVED', 'REJECTED');

IF NOT EXISTS (
    SELECT 1
    FROM sys.check_constraints
    WHERE parent_object_id = OBJECT_ID('households')
      AND name = 'CK_households_review_status'
)
    ALTER TABLE households
        ADD CONSTRAINT CK_households_review_status
        CHECK (review_status IN ('PENDING', 'APPROVED', 'REJECTED'));
