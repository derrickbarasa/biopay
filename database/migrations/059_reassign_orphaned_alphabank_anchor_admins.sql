-- One-time data fix: two Anchor Administrator accounts under the alphabank-ss.com
-- domain had dangling anchor_id values left over from before hard-delete of anchors
-- was removed from the app (see Administration.java's deactivate-only comments) --
-- an anchor at their stored id was later deleted/recreated at a different id,
-- orphaning the user row's anchor_id:
--   - Denis Limo  (id 1072, denis.limo@alphabank-ss.com)  -- anchor_id 1072 matches
--     no anchor at all.
--   - Samson Tenoy (id 1071, samson.tenoy@alphabank-ss.com) -- anchor_id 1071 now
--     belongs to a completely different, unrelated anchor ("QA Test Anchor").
-- The real, currently-active anchor for this domain is "Alpha Commercial Bank"
-- (anchor id 1069), already correctly administered by Benard Mwangi
-- (benard.mwangi@alphabank-ss.com, anchor_id 1069). Per explicit user instruction,
-- reassign both stale accounts onto that same anchor, as additional Anchor
-- Administrator users under Benard rather than deactivating them outright.
--
-- Scoped tightly by both id AND email (not just id) so this never touches an
-- unrelated row if these specific ids are ever reused again by IDENTITY in the
-- future. Idempotent: re-running finds nothing left to update once applied.

UPDATE users
SET anchor_id = 1069,
    updated_at = GETDATE()
WHERE id = 1072
  AND email = 'denis.limo@alphabank-ss.com'
  AND anchor_id <> 1069;
GO

UPDATE users
SET anchor_id = 1069,
    updated_at = GETDATE()
WHERE id = 1071
  AND email = 'samson.tenoy@alphabank-ss.com'
  AND anchor_id <> 1069;
GO
