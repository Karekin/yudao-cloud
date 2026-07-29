DROP TABLE IF EXISTS cloudmold_ai_ops_automation_candidate;

ALTER TABLE cloudmold_ai_ops_temporal_schedule
    DROP COLUMN reconcile_error,
    DROP COLUMN last_reconciled_at,
    DROP COLUMN definition_closure_sha256,
    DROP COLUMN desired_policy_sha256,
    DROP COLUMN input_strategy,
    DROP COLUMN cron_expression;
