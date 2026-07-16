-- Canonical Metadata OLTP invariants. Empty results prove only structural safety,
-- never source coverage or runtime semantic completion.

SELECT 'metadata_operation_completion_shape' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_metadata_operation
WHERE (status = 10 AND (aggregate_id IS NULL OR result_json IS NULL))
   OR (status = 0 AND (aggregate_id IS NOT NULL OR result_json IS NOT NULL));

SELECT 'metadata_definition_current_version' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_metadata_definition definition_state
LEFT JOIN (
    SELECT tenant_id, definition_id, MAX(definition_version) AS maximum_version
    FROM cloudmold_metadata_definition_version
    GROUP BY tenant_id, definition_id
) versions ON versions.tenant_id = definition_state.tenant_id
          AND versions.definition_id = definition_state.definition_id
WHERE (definition_state.status = 'PUBLISHED'
       AND (definition_state.current_version = 0
            OR definition_state.current_version <> COALESCE(versions.maximum_version, 0)))
   OR (definition_state.status = 'DRAFT'
       AND definition_state.current_version <> COALESCE(versions.maximum_version, 0));

SELECT 'metadata_definition_subtype_cardinality' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_metadata_definition_version version_state
JOIN cloudmold_metadata_definition definition_state
  ON definition_state.tenant_id = version_state.tenant_id
 AND definition_state.definition_id = version_state.definition_id
WHERE CASE definition_state.definition_kind
    WHEN 'DATA_SOURCE' THEN (SELECT COUNT(*) FROM cloudmold_metadata_data_source_version child
        WHERE child.tenant_id = version_state.tenant_id
          AND child.definition_id = version_state.definition_id
          AND child.definition_version = version_state.definition_version)
    WHEN 'DATASET' THEN (SELECT COUNT(*) FROM cloudmold_metadata_dataset_version child
        WHERE child.tenant_id = version_state.tenant_id
          AND child.definition_id = version_state.definition_id
          AND child.definition_version = version_state.definition_version)
    WHEN 'TASK' THEN (SELECT COUNT(*) FROM cloudmold_metadata_task_version child
        WHERE child.tenant_id = version_state.tenant_id
          AND child.definition_id = version_state.definition_id
          AND child.definition_version = version_state.definition_version)
    WHEN 'LINEAGE' THEN (SELECT COUNT(*) FROM cloudmold_metadata_lineage_version child
        WHERE child.tenant_id = version_state.tenant_id
          AND child.definition_id = version_state.definition_id
          AND child.definition_version = version_state.definition_version)
    WHEN 'DQC_RULE' THEN (SELECT COUNT(*) FROM cloudmold_metadata_dqc_rule_version child
        WHERE child.tenant_id = version_state.tenant_id
          AND child.definition_id = version_state.definition_id
          AND child.definition_version = version_state.definition_version)
    WHEN 'METRIC' THEN (SELECT COUNT(*) FROM cloudmold_metadata_metric_version child
        WHERE child.tenant_id = version_state.tenant_id
          AND child.definition_id = version_state.definition_id
          AND child.definition_version = version_state.definition_version)
    ELSE 0 END <> 1;

SELECT 'metadata_dataset_field_inventory_gap' AS check_name, COUNT(*) AS violation_count
FROM (
    SELECT tenant_id, dataset_id, dataset_version,
           MIN(ordinal_position) AS minimum_ordinal,
           MAX(ordinal_position) AS maximum_ordinal,
           COUNT(*) AS field_count,
           COUNT(DISTINCT field_code) AS distinct_field_count
    FROM cloudmold_metadata_field_version
    GROUP BY tenant_id, dataset_id, dataset_version
) inventory
WHERE minimum_ordinal <> 1 OR maximum_ordinal <> field_count
   OR distinct_field_count <> field_count;

SELECT 'metadata_task_dependency_sequence_gap' AS check_name, COUNT(*) AS violation_count
FROM (
    SELECT tenant_id, task_id, task_version,
           MIN(dependency_sequence) AS minimum_sequence,
           MAX(dependency_sequence) AS maximum_sequence,
           COUNT(*) AS dependency_count
    FROM cloudmold_metadata_task_dependency
    GROUP BY tenant_id, task_id, task_version
) dependency_inventory
WHERE minimum_sequence <> 1 OR maximum_sequence <> dependency_count;

WITH RECURSIVE dependency_walk AS (
    SELECT tenant_id, task_id AS root_task_id, task_version AS root_task_version,
           upstream_task_id AS current_task_id, upstream_task_version AS current_task_version,
           CAST(CONCAT('|', task_id, '@', task_version, '|') AS CHAR(8192)) AS visited,
           1 AS depth,
           upstream_task_id = task_id AND upstream_task_version = task_version AS cycle_found
    FROM cloudmold_metadata_task_dependency
    UNION ALL
    SELECT walk.tenant_id, walk.root_task_id, walk.root_task_version,
           edge.upstream_task_id, edge.upstream_task_version,
           CONCAT(walk.visited, walk.current_task_id, '@', walk.current_task_version, '|'),
           walk.depth + 1,
           LOCATE(CONCAT('|', edge.upstream_task_id, '@', edge.upstream_task_version, '|'),
                  walk.visited) > 0
    FROM dependency_walk walk
    JOIN cloudmold_metadata_task_dependency edge
      ON edge.tenant_id = walk.tenant_id
     AND edge.task_id = walk.current_task_id
     AND edge.task_version = walk.current_task_version
    WHERE walk.depth < 512 AND walk.cycle_found = 0
)
SELECT 'metadata_task_dependency_cycle' AS check_name,
       COUNT(DISTINCT tenant_id, root_task_id, root_task_version) AS violation_count
FROM dependency_walk
WHERE cycle_found = 1;

SELECT 'metadata_task_run_observation_sequence_gap' AS check_name, COUNT(*) AS violation_count
FROM (
    SELECT tenant_id, run_id, MIN(observation_sequence) AS minimum_sequence,
           MAX(observation_sequence) AS maximum_sequence, COUNT(*) AS observation_count
    FROM cloudmold_metadata_task_run_observation
    GROUP BY tenant_id, run_id
) observation_inventory
WHERE minimum_sequence <> 1 OR maximum_sequence <> observation_count;

SELECT 'metadata_task_run_status_regression' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_metadata_task_run_observation earlier
JOIN cloudmold_metadata_task_run_observation later
  ON later.tenant_id = earlier.tenant_id AND later.run_id = earlier.run_id
 AND later.observation_sequence > earlier.observation_sequence
WHERE CASE later.status
        WHEN 'SCHEDULED' THEN 1 WHEN 'RUNNING' THEN 2 ELSE 3 END
    < CASE earlier.status
        WHEN 'SCHEDULED' THEN 1 WHEN 'RUNNING' THEN 2 ELSE 3 END
   OR (earlier.status IN ('SUCCEEDED','FAILED','CANCELLED')
       AND later.status <> earlier.status);

SELECT 'metadata_dqc_task_run_reference_half_null' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_metadata_dqc_result
WHERE (task_run_id IS NULL) <> (task_run_observation_sequence IS NULL);

SELECT 'metadata_status_history_sequence_gap' AS check_name, COUNT(*) AS violation_count
FROM (
    SELECT tenant_id, aggregate_type, aggregate_id,
           MIN(aggregate_version) AS minimum_version,
           MAX(aggregate_version) AS maximum_version,
           COUNT(*) AS history_count
    FROM cloudmold_metadata_status_history
    GROUP BY tenant_id, aggregate_type, aggregate_id
) history_inventory
WHERE minimum_version <> 1 OR maximum_version <> history_count;

SELECT 'metadata_definition_status_history_mismatch' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_metadata_definition definition_state
LEFT JOIN cloudmold_metadata_status_history history
  ON history.tenant_id = definition_state.tenant_id
 AND history.aggregate_type = definition_state.definition_kind
 AND history.aggregate_id = definition_state.definition_id
 AND history.aggregate_version = definition_state.current_version
WHERE definition_state.current_version > 0
  AND (history.history_id IS NULL OR history.current_status <> definition_state.status);

SELECT 'metadata_accepted_operation_missing_outbox' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_metadata_operation operation_state
LEFT JOIN cloudmold_event_outbox event_state
  ON event_state.tenant_id = operation_state.tenant_id
 AND event_state.aggregate_id COLLATE utf8mb4_unicode_ci = operation_state.aggregate_id
 AND event_state.destination = 'lakehouse'
 AND event_state.event_type LIKE 'metadata.%'
WHERE operation_state.status = 10 AND event_state.event_id IS NULL;
