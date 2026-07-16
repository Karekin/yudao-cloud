# CloudMold Metadata Control Plane

This red-zone module is the governed metadata authority used to correct and supersede the
Y-Shopping prototype's snapshot-shaped metadata tables. It does not copy vendor scheduler or
information-schema rows into an OLTP write model.

## Authority boundary

- `Definition` owns tenant-scoped logical identity, kind, code, owner and current version.
- Every DataSource, Dataset/Table, Task, Lineage, DQC Rule and Metric publication creates a new
  immutable `DefinitionVersion` plus one typed version. Older versions are never overwritten.
- Dataset field rows are immutable children of an exact Dataset version.
- Task dependency edges are direct, directional and bind both tasks to exact versions. Publication
  serializes each tenant graph with an optimistic revision, rejects self-dependency and uses a
  recursive, version-bound path check to reject indirect cycles without a concurrent publish race.
- SLA is an approved, immutable child of a Task version. A raw baseline label is not an SLA.
- Task run observations and DQC results are append-only evidence. Task status advances with an
  optimistic observation sequence; retries advance `attempt` after a terminal observation.
- Lineage always points `source Dataset version -> target Dataset version`; a recursive path check
  plus the tenant graph revision keeps the graph acyclic under concurrent publications, while
  transformation code is represented by a SHA-256 digest and an opaque artifact reference.
- Metric definitions preserve grain, unit, aggregation, expression/filter/dimension digests and
  semantic version. They are definitions, not computed business facts.

The module never stores connection URLs, usernames, credentials, raw SQL or raw error messages.
Sensitive locations and artifacts must be `restricted:` opaque references or `sha256:` digests.
Runtime cost is an exact minor-unit amount with a currency code; DQC expected/actual values use
`DECIMAL(38,9)` and are never floating-point.

## Y-Shopping source disposition

| Prototype surface | Canonical disposition |
|---|---|
| task node / Flink task snapshots | versioned Task + append-only TaskRunObservation |
| parent/child node lists | direct TaskDependency edges; transitive closure is derived |
| baseline / SLA snapshots | approved TaskSla on an exact Task version |
| datasource snapshot | versioned DataSource with opaque endpoint/credential/namespace refs |
| table / report dataset / Doris table snapshots | versioned Dataset; physical observations stay in the lakehouse |
| column snapshot | immutable FieldVersion rows under an exact Dataset version |
| lineage snapshot | directional, version-bound Lineage definition |
| embedded DQC text | hashed, versioned DqcRule; exact append-only DqcResult |
| report/derived indicators | versioned governed Metric definition |

This is a canonical control-plane first slice. It does not claim ingestion of the prototype's
historical rows, scheduler execution, source discovery, physical
statistics history, non-empty reconciliation or production cutover.

## Admin API

- `POST /cloudmold/metadata/command`
- `GET /cloudmold/metadata/definition/get?definitionId=...`
- `GET /cloudmold/metadata/task-run/get?runId=...`
- `GET /cloudmold/metadata/dqc-result/get?resultId=...`

Permissions are `cloudmold:metadata:command` and `cloudmold:metadata:query`.

## Outbox contracts (schema v1)

Every event also carries the common CloudMold envelope. Payload fields are exact:

- `metadata.datasource.version_published`: `definition_id`, `definition_code`,
  `definition_version`, `display_name`, `owner_principal_id`, `specification_sha256`,
  `artifact_ref`, `previous_status`, `current_status`, `source_type`, `environment`,
  `endpoint_ref`, `credential_ref`, `namespace_ref`.
- `metadata.dataset.version_published`: common definition fields plus `data_source_id`,
  `data_source_version`, `dataset_type`, `qualified_name`, `layer_code`, `grain_code`,
  `schema_sha256`, `storage_location_ref`, `retention_days`, `field_count`.
- `metadata.task.version_published`: common definition fields plus `task_type`,
  `executable_artifact_ref`, `code_sha256`, `schedule_sha256`, `resource_group_ref`,
  `dependency_count`, `service_level_code`, `deadline_minute_utc`,
  `maximum_duration_millis`, `maximum_freshness_millis`,
  `sla_approved_by_principal_id`.
- `metadata.task_run.observed`: `run_id`, `task_id`, `task_version`, `attempt`,
  `observation_sequence`, `previous_status`, `current_status`, `scheduled_at`, `started_at`,
  `finished_at`, `duration_millis`, `compute_cost_minor`, `cost_currency`, `resource_millis`,
  `rows_read`, `rows_written`, `source_checkpoint_ref`, `output_snapshot_ref`, `error_ref`.
- `metadata.lineage.version_published`: common definition fields plus `source_dataset_id`,
  `source_dataset_version`, `target_dataset_id`, `target_dataset_version`, `direction`,
  `transform_sha256`, `transformation_ref`.
- `metadata.dqc_rule.version_published`: common definition fields plus `dataset_id`,
  `dataset_version`, `dataset_field`, `rule_type`, `severity`, `expression_sha256`,
  `threshold_value`, `threshold_comparator`.
- `metadata.dqc_result.recorded`: `result_id`, `dqc_rule_id`, `dqc_rule_version`, `dataset_id`,
  `dataset_version`, `task_run_id`, `task_run_observation_sequence`, `result_status`, `expected_value`, `actual_value`,
  `evaluated_rows`, `violation_count`, `evidence_ref`, `observed_at`.
- `metadata.metric.version_published`: common definition fields plus `grain_code`, `metric_unit`,
  `aggregation_type`, `expression_sha256`, `filter_sha256`, `dimensions_sha256`,
  `semantic_version`.

Headers explicitly state `pii_safe=true`, `raw_sql_stored=false` and
`raw_connection_stored=false`.
