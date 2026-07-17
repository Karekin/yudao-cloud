-- Canonical intelligence taxonomy and classified-observation OLTP invariants.
-- Every query must return zero. Legacy observation contract v1 remains explicit and immutable;
-- all newly accepted observation writes use contract/event schema v2.

SELECT 'intelligence_taxonomy_head_definition_mismatch' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_risk_intelligence_taxonomy taxonomy
LEFT JOIN (
    SELECT tenant_id,taxonomy_id,COUNT(*) AS definition_count,MAX(definition_version) AS max_definition_version
    FROM cloudmold_risk_intelligence_taxonomy_version
    GROUP BY tenant_id,taxonomy_id
) versions ON versions.tenant_id=taxonomy.tenant_id AND versions.taxonomy_id=taxonomy.taxonomy_id
WHERE COALESCE(versions.definition_count,0) <> taxonomy.current_definition_version
   OR COALESCE(versions.max_definition_version,0) <> taxonomy.current_definition_version;

SELECT 'intelligence_taxonomy_definition_version_gap' AS check_name, COUNT(*) AS violation_count
FROM (
    SELECT tenant_id,taxonomy_id,MIN(definition_version) AS min_version,
           MAX(definition_version) AS max_version,COUNT(*) AS version_count
    FROM cloudmold_risk_intelligence_taxonomy_version
    GROUP BY tenant_id,taxonomy_id
) versions
WHERE min_version <> 1 OR max_version <> version_count;

SELECT 'intelligence_taxonomy_level_count_mismatch' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_risk_intelligence_taxonomy_version version_state
LEFT JOIN (
    SELECT tenant_id,taxonomy_version_id,COUNT(*) AS level_count,
           MIN(level_sequence) AS min_sequence,MAX(level_sequence) AS max_sequence
    FROM cloudmold_risk_intelligence_taxonomy_level
    GROUP BY tenant_id,taxonomy_version_id
) levels ON levels.tenant_id=version_state.tenant_id
        AND levels.taxonomy_version_id=version_state.taxonomy_version_id
WHERE COALESCE(levels.level_count,0) <> version_state.level_count
   OR levels.min_sequence <> 1 OR levels.max_sequence <> version_state.level_count;

SELECT 'intelligence_taxonomy_effective_time_not_increasing' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_risk_intelligence_taxonomy_version current_version
JOIN cloudmold_risk_intelligence_taxonomy_version previous_version
  ON previous_version.tenant_id=current_version.tenant_id
 AND previous_version.taxonomy_id=current_version.taxonomy_id
 AND previous_version.definition_version=current_version.definition_version-1
WHERE current_version.effective_from <= previous_version.effective_from;

SELECT 'intelligence_taxonomy_retirement_head_mismatch' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_risk_intelligence_taxonomy taxonomy
LEFT JOIN cloudmold_risk_intelligence_taxonomy_retirement retirement
  ON retirement.tenant_id=taxonomy.tenant_id AND retirement.taxonomy_id=taxonomy.taxonomy_id
WHERE (taxonomy.status='RETIRED' AND (
          retirement.retirement_id IS NULL OR retirement.taxonomy_version<>taxonomy.version
          OR retirement.retired_at<>taxonomy.retired_at))
   OR (taxonomy.status<>'RETIRED' AND retirement.retirement_id IS NOT NULL);

SELECT 'intelligence_taxonomy_history_sequence_gap' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_risk_intelligence_taxonomy taxonomy
LEFT JOIN (
    SELECT tenant_id,aggregate_id,MIN(aggregate_version) AS min_version,
           MAX(aggregate_version) AS max_version,COUNT(*) AS history_count
    FROM cloudmold_risk_status_history
    WHERE aggregate_type='INTELLIGENCE_TAXONOMY'
    GROUP BY tenant_id,aggregate_id
) history ON history.tenant_id=taxonomy.tenant_id AND history.aggregate_id=taxonomy.taxonomy_id
WHERE history.min_version<>1 OR history.max_version<>taxonomy.version OR history.history_count<>taxonomy.version;

SELECT 'intelligence_taxonomy_current_history_mismatch' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_risk_intelligence_taxonomy taxonomy
LEFT JOIN cloudmold_risk_status_history history
  ON history.tenant_id=taxonomy.tenant_id
 AND history.aggregate_type='INTELLIGENCE_TAXONOMY'
 AND history.aggregate_id=taxonomy.taxonomy_id
 AND history.aggregate_version=taxonomy.version
WHERE history.history_id IS NULL OR history.current_status<>taxonomy.status;

SELECT 'intelligence_taxonomy_operation_history_missing' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_risk_operation operation_state
LEFT JOIN cloudmold_risk_status_history history
  ON history.tenant_id=operation_state.tenant_id AND history.operation_id=operation_state.operation_id
 AND history.aggregate_type='INTELLIGENCE_TAXONOMY'
WHERE operation_state.status=10
  AND operation_state.command_type IN (
      'CREATE_INTELLIGENCE_EVENT_TAXONOMY',
      'PUBLISH_INTELLIGENCE_EVENT_TAXONOMY_VERSION',
      'RETIRE_INTELLIGENCE_EVENT_TAXONOMY')
  AND history.history_id IS NULL;

SELECT 'intelligence_taxonomy_effect_event_missing' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_risk_operation operation_state
LEFT JOIN cloudmold_event_outbox event_state
  ON event_state.tenant_id=operation_state.tenant_id
 AND event_state.aggregate_type='risk_intelligence_event_taxonomy'
 AND event_state.aggregate_id=operation_state.aggregate_id
 AND event_state.destination='lakehouse'
 AND event_state.aggregate_version=(
     SELECT MAX(history.aggregate_version)
     FROM cloudmold_risk_status_history history
     WHERE history.tenant_id=operation_state.tenant_id
       AND history.operation_id=operation_state.operation_id
       AND history.aggregate_type='INTELLIGENCE_TAXONOMY')
WHERE operation_state.status=10
  AND operation_state.command_type IN (
      'PUBLISH_INTELLIGENCE_EVENT_TAXONOMY_VERSION',
      'RETIRE_INTELLIGENCE_EVENT_TAXONOMY')
  AND event_state.event_id IS NULL;

SELECT 'classified_observation_taxonomy_reference_missing' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_intelligence_observation observation
LEFT JOIN cloudmold_risk_intelligence_taxonomy taxonomy
  ON taxonomy.tenant_id=observation.tenant_id AND taxonomy.taxonomy_id=observation.taxonomy_id
LEFT JOIN cloudmold_risk_intelligence_taxonomy_version version_state
  ON version_state.tenant_id=observation.tenant_id
 AND version_state.taxonomy_version_id=observation.taxonomy_version_id
 AND version_state.taxonomy_id=observation.taxonomy_id
 AND version_state.definition_version=observation.taxonomy_definition_version
LEFT JOIN cloudmold_risk_intelligence_taxonomy_level level_state
  ON level_state.tenant_id=observation.tenant_id
 AND level_state.taxonomy_version_id=observation.taxonomy_version_id
 AND level_state.level_code=observation.intelligence_level_code
WHERE observation.classification_contract_version=2
  AND (taxonomy.taxonomy_id IS NULL OR version_state.taxonomy_version_id IS NULL
       OR level_state.level_definition_id IS NULL OR taxonomy.event_code<>observation.event_code);

SELECT 'classified_observation_not_effective_as_of_event' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_intelligence_observation observation
JOIN cloudmold_risk_intelligence_taxonomy taxonomy
  ON taxonomy.tenant_id=observation.tenant_id AND taxonomy.taxonomy_id=observation.taxonomy_id
JOIN cloudmold_risk_intelligence_taxonomy_version version_state
  ON version_state.tenant_id=observation.tenant_id
 AND version_state.taxonomy_version_id=observation.taxonomy_version_id
WHERE observation.classification_contract_version=2
  AND (version_state.effective_from>observation.observed_at
       OR (taxonomy.retired_at IS NOT NULL AND observation.observed_at>=taxonomy.retired_at)
       OR EXISTS (
           SELECT 1 FROM cloudmold_risk_intelligence_taxonomy_version newer
           WHERE newer.tenant_id=version_state.tenant_id
             AND newer.taxonomy_id=version_state.taxonomy_id
             AND newer.effective_from<=observation.observed_at
             AND (newer.effective_from>version_state.effective_from
                  OR (newer.effective_from=version_state.effective_from
                      AND newer.definition_version>version_state.definition_version))));

SELECT 'legacy_observation_classification_leak' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_intelligence_observation
WHERE classification_contract_version=1
  AND (taxonomy_id IS NOT NULL OR taxonomy_version_id IS NOT NULL
       OR taxonomy_definition_version IS NOT NULL OR event_code IS NOT NULL
       OR intelligence_level_code IS NOT NULL);

SELECT 'classified_observation_event_snapshot_mismatch' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_intelligence_observation observation
LEFT JOIN cloudmold_event_outbox event_state
  ON event_state.tenant_id=observation.tenant_id
 AND event_state.event_type='operations_intelligence.observation.recorded'
 AND event_state.schema_version=2
 AND event_state.aggregate_type='intelligence_observation'
 AND event_state.aggregate_id=observation.observation_id
WHERE observation.classification_contract_version=2
  AND (event_state.event_id IS NULL
       OR JSON_UNQUOTE(JSON_EXTRACT(event_state.payload,'$.taxonomy_id'))<>observation.taxonomy_id
       OR JSON_UNQUOTE(JSON_EXTRACT(event_state.payload,'$.taxonomy_version_id'))<>observation.taxonomy_version_id
       OR CAST(JSON_UNQUOTE(JSON_EXTRACT(event_state.payload,'$.taxonomy_definition_version')) AS UNSIGNED)
            <>observation.taxonomy_definition_version
       OR JSON_UNQUOTE(JSON_EXTRACT(event_state.payload,'$.event_code'))<>observation.event_code
       OR JSON_UNQUOTE(JSON_EXTRACT(event_state.payload,'$.intelligence_level_code'))
            <>observation.intelligence_level_code);

