-- The additive attestation evidence repair is intentionally non-reversible.
-- Dropping frozen approver/task/operator evidence would make already-observed
-- BPM decisions impossible to attest safely. Roll forward with another
-- additive migration instead of removing these columns or constraints.
SELECT 'agent approval workflow attestation evidence is non-reversible by design' AS rollback_note;
