-- Canonical operations-intelligence OLTP invariants.
-- Every query must return zero. Empty results prove structural safety only;
-- they do not prove source coverage or runtime semantic completion.

SELECT 'operations_intelligence_operation_completion_shape' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_operations_intelligence_operation
WHERE (status = 10 AND (aggregate_type IS NULL OR aggregate_id IS NULL OR result_json IS NULL))
   OR (status = 0 AND (aggregate_type IS NOT NULL OR aggregate_id IS NOT NULL OR result_json IS NOT NULL));

SELECT 'operations_intelligence_clue_review_state_mismatch' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_intelligence_clue clue
LEFT JOIN cloudmold_intelligence_clue_review review
  ON review.tenant_id = clue.tenant_id AND review.clue_id = clue.clue_id
WHERE (clue.status = 'OBSERVED' AND review.review_id IS NOT NULL)
   OR (clue.status = 'ACCEPTED' AND (review.review_id IS NULL OR review.decision <> 'ACCEPT'))
   OR (clue.status = 'REJECTED' AND (review.review_id IS NULL OR review.decision <> 'REJECT'));

SELECT 'operations_intelligence_clue_model_observation_mismatch' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_intelligence_clue clue
JOIN cloudmold_intelligence_model_result model_result
  ON model_result.tenant_id = clue.tenant_id
 AND model_result.model_result_id = clue.model_result_id
WHERE clue.observation_id <> model_result.observation_id;

SELECT 'operations_intelligence_alert_source_missing' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_operations_alert alert_state
LEFT JOIN cloudmold_intelligence_observation observation
  ON alert_state.source_type = 'OBSERVATION'
 AND observation.tenant_id = alert_state.tenant_id
 AND observation.observation_id = alert_state.source_ref
LEFT JOIN cloudmold_intelligence_clue clue
  ON alert_state.source_type = 'CLUE'
 AND clue.tenant_id = alert_state.tenant_id
 AND clue.clue_id = alert_state.source_ref
WHERE (alert_state.source_type = 'OBSERVATION' AND observation.observation_id IS NULL)
   OR (alert_state.source_type = 'CLUE' AND (clue.clue_id IS NULL OR clue.status <> 'ACCEPTED'));

SELECT 'operations_intelligence_alert_history_sequence_gap' AS check_name, COUNT(*) AS violation_count
FROM (
    SELECT tenant_id, alert_id,
           MIN(alert_version) AS minimum_version,
           MAX(alert_version) AS maximum_version,
           COUNT(*) AS history_count
    FROM cloudmold_operations_alert_history
    GROUP BY tenant_id, alert_id
) history_inventory
WHERE minimum_version <> 1 OR maximum_version <> history_count;

SELECT 'operations_intelligence_alert_current_history_mismatch' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_operations_alert alert_state
LEFT JOIN cloudmold_operations_alert_history history
  ON history.tenant_id = alert_state.tenant_id
 AND history.alert_id = alert_state.alert_id
 AND history.alert_version = alert_state.version
WHERE history.history_id IS NULL
   OR history.current_status <> alert_state.status
   OR history.actor_principal_id <> alert_state.current_actor_principal_id;

SELECT 'operations_intelligence_alert_history_transition_invalid' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_operations_alert_history history
WHERE (history.alert_version = 1 AND (history.previous_status IS NOT NULL OR history.current_status <> 'OPEN'))
   OR (history.alert_version > 1 AND (
          history.previous_status IS NULL
       OR NOT ((history.previous_status = 'OPEN' AND history.current_status IN ('NOTIFIED','CLAIMED','INVALID','CLOSED_NO_ACTION'))
            OR (history.previous_status = 'NOTIFIED' AND history.current_status IN ('CLAIMED','INVALID','CLOSED_NO_ACTION'))
            OR (history.previous_status = 'CLAIMED' AND history.current_status IN ('RESOLVED','INVALID')))));

SELECT 'operations_intelligence_alert_history_chain_broken' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_operations_alert_history current_history
LEFT JOIN cloudmold_operations_alert_history previous_history
  ON previous_history.tenant_id = current_history.tenant_id
 AND previous_history.alert_id = current_history.alert_id
 AND previous_history.alert_version = current_history.alert_version - 1
WHERE current_history.alert_version > 1
  AND (previous_history.history_id IS NULL
       OR current_history.previous_status <> previous_history.current_status);

SELECT 'operations_intelligence_alert_terminal_time_mismatch' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_operations_alert
WHERE (status IN ('RESOLVED','INVALID','CLOSED_NO_ACTION') AND terminal_at IS NULL)
   OR (status IN ('OPEN','NOTIFIED','CLAIMED') AND terminal_at IS NOT NULL);

SELECT 'operations_intelligence_accepted_operation_missing_outbox' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_operations_intelligence_operation operation_state
LEFT JOIN cloudmold_event_outbox event_state
  ON event_state.tenant_id = operation_state.tenant_id
 AND event_state.aggregate_type COLLATE utf8mb4_unicode_ci = operation_state.aggregate_type
 AND event_state.aggregate_id COLLATE utf8mb4_unicode_ci = operation_state.aggregate_id
 AND event_state.destination = 'lakehouse'
 AND event_state.event_type LIKE 'operations_intelligence.%'
WHERE operation_state.status = 10 AND event_state.event_id IS NULL;
