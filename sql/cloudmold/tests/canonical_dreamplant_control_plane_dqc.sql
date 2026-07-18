-- DreamPlant control-plane invariants. Every query must return zero rows.

-- Published pointer must resolve to the exact immutable snapshot.
SELECT m.tenant_id, m.map_key, m.current_version
FROM cloudmold_dreamplant_world_map m
LEFT JOIN cloudmold_dreamplant_snapshot s
  ON s.tenant_id = m.tenant_id AND s.map_key = m.map_key AND s.version = m.current_version
WHERE m.current_version > 0 AND s.version IS NULL;

-- Current version must equal the greatest published immutable version.
SELECT m.tenant_id, m.map_key, m.current_version, MAX(s.version) AS maximum_version
FROM cloudmold_dreamplant_world_map m
JOIN cloudmold_dreamplant_snapshot s ON s.tenant_id = m.tenant_id AND s.map_key = m.map_key
GROUP BY m.tenant_id, m.map_key, m.current_version
HAVING m.current_version <> MAX(s.version);

-- Terminal AI conclusions must carry an opaque evidence reference.
SELECT tenant_id, exploration_run_id, status
FROM cloudmold_dreamplant_exploration
WHERE status IN ('SUCCEEDED', 'FAILED', 'NEEDS_REVIEW') AND evidence_ref IS NULL;

-- Every successful operation must retain its exact replay result.
SELECT tenant_id, operation_id, command_type
FROM cloudmold_dreamplant_operation
WHERE status = 10 AND (aggregate_id IS NULL OR result_json IS NULL);
