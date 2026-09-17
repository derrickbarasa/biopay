-- One-time backfill: correct households.status for records created before the
-- household status fix (see Household.java#create / #setReviewStatus). New
-- households are now created status=0 (Inactive) and only flip to status=1
-- (Active) once review_status='APPROVED'; older rows created before that fix
-- still carry the previous hardcoded status=1, so a household that is still
-- Pending, or was Rejected, incorrectly shows "Active" in the dashboard.
--
-- Deliberately scoped to only that mismatch: households whose review outcome
-- is not APPROVED but whose status is still 1. Approved households are left
-- untouched even if an administrator later deactivated one for an unrelated
-- reason -- that is a real, independent administrative action this backfill
-- must never silently undo. Idempotent: re-running it once corrected finds
-- nothing left to update.

UPDATE households
SET status = 0,
    updated_at = GETDATE()
WHERE status = 1
  AND (review_status IS NULL OR review_status <> 'APPROVED');
GO
