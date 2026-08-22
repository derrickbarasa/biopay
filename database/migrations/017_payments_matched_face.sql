-- 017_payments_matched_face.sql
--
-- payments.matched_fp records which enrolled fingerprint template a field payment was verified
-- against; this adds the face equivalent now that PaymentVerificationActivity (mobile) can also
-- verify via a live face capture against an enrolled embedding. Only one of matched_fp/
-- matched_face_uuid is ever populated per row -- whichever method the officer actually used.
--
-- Safe to re-run.

IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('payments') AND name = 'matched_face_uuid')
    ALTER TABLE payments ADD matched_face_uuid VARCHAR(100) NULL;
GO
