-- Normalize pre-worker exploration history after lease/retry columns have been introduced.

UPDATE cloudmold_dreamplant_exploration
SET started_at=COALESCE(started_at,created_at)
WHERE status<>'QUEUED';

UPDATE cloudmold_dreamplant_exploration
SET completed_at=COALESCE(completed_at,updated_at,created_at),
    next_retry_at=NULL,
    lease_owner=NULL,
    lease_until=NULL
WHERE status IN ('SUCCEEDED','FAILED','NEEDS_REVIEW','CANCELLED');
