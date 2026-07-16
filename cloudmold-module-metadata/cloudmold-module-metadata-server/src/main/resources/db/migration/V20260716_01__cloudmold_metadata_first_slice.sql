-- CloudMold governed metadata control-plane first slice.
-- Definitions are immutable by version; observations/results are append-only.
-- Raw SQL, connection URLs, usernames and credentials are intentionally absent.

CREATE TABLE IF NOT EXISTS cloudmold_metadata_operation (
    operation_id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    command_type VARCHAR(64) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    attempt_token CHAR(36) NOT NULL,
    status TINYINT NOT NULL,
    aggregate_id VARCHAR(128) NULL,
    result_json JSON NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (operation_id),
    UNIQUE KEY uk_metadata_operation_tenant_id (tenant_id, operation_id),
    UNIQUE KEY uk_metadata_operation_tenant_idempotency (tenant_id, idempotency_key),
    CONSTRAINT ck_metadata_operation_status CHECK (status IN (0, 10))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_metadata_graph_guard (
    tenant_id BIGINT NOT NULL,
    graph_type VARCHAR(32) NOT NULL,
    revision BIGINT NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (tenant_id, graph_type),
    CONSTRAINT ck_metadata_graph_guard_type CHECK (graph_type IN ('TASK_DEPENDENCY','LINEAGE')),
    CONSTRAINT ck_metadata_graph_guard_revision CHECK (revision >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_metadata_definition (
    definition_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    definition_kind VARCHAR(32) NOT NULL,
    definition_code VARCHAR(128) NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    status VARCHAR(16) NOT NULL,
    current_version BIGINT NOT NULL,
    owner_principal_id VARCHAR(128) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (tenant_id, definition_id),
    UNIQUE KEY uk_metadata_definition_code (tenant_id, definition_kind, definition_code),
    CONSTRAINT ck_metadata_definition_kind CHECK (definition_kind IN
        ('DATA_SOURCE','DATASET','TASK','LINEAGE','DQC_RULE','METRIC')),
    CONSTRAINT ck_metadata_definition_status CHECK (status IN ('DRAFT','PUBLISHED')),
    CONSTRAINT ck_metadata_definition_version CHECK (current_version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_metadata_definition_version (
    tenant_id BIGINT NOT NULL,
    definition_id VARCHAR(128) NOT NULL,
    definition_version BIGINT NOT NULL,
    specification_sha256 CHAR(64) NOT NULL,
    artifact_ref VARCHAR(192) NOT NULL,
    published_by_principal_id VARCHAR(128) NOT NULL,
    published_at DATETIME(6) NOT NULL,
    PRIMARY KEY (tenant_id, definition_id, definition_version),
    CONSTRAINT fk_metadata_definition_version_definition FOREIGN KEY (tenant_id, definition_id)
        REFERENCES cloudmold_metadata_definition (tenant_id, definition_id),
    CONSTRAINT ck_metadata_definition_version_positive CHECK (definition_version > 0),
    CONSTRAINT ck_metadata_definition_artifact_ref CHECK
        (artifact_ref LIKE 'restricted:%' OR artifact_ref LIKE 'sha256:%')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_metadata_data_source_version (
    tenant_id BIGINT NOT NULL,
    definition_id VARCHAR(128) NOT NULL,
    definition_version BIGINT NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    environment VARCHAR(16) NOT NULL,
    endpoint_ref VARCHAR(192) NOT NULL,
    credential_ref VARCHAR(192) NOT NULL,
    namespace_ref VARCHAR(192) NOT NULL,
    PRIMARY KEY (tenant_id, definition_id, definition_version),
    CONSTRAINT fk_metadata_data_source_definition_version FOREIGN KEY
        (tenant_id, definition_id, definition_version)
        REFERENCES cloudmold_metadata_definition_version (tenant_id, definition_id, definition_version),
    CONSTRAINT ck_metadata_data_source_type CHECK
        (source_type IN ('MYSQL','STARROCKS','FLINK','OBJECT_STORAGE','API','OTHER')),
    CONSTRAINT ck_metadata_data_source_environment CHECK (environment IN ('DEV','TEST','STAGING','PROD')),
    CONSTRAINT ck_metadata_endpoint_opaque CHECK
        ((endpoint_ref LIKE 'restricted:%' OR endpoint_ref LIKE 'sha256:%') AND endpoint_ref NOT LIKE '%://%'),
    CONSTRAINT ck_metadata_credential_opaque CHECK
        (credential_ref LIKE 'restricted:%' OR credential_ref LIKE 'sha256:%'),
    CONSTRAINT ck_metadata_namespace_opaque CHECK
        (namespace_ref LIKE 'restricted:%' OR namespace_ref LIKE 'sha256:%')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_metadata_dataset_version (
    tenant_id BIGINT NOT NULL,
    definition_id VARCHAR(128) NOT NULL,
    definition_version BIGINT NOT NULL,
    data_source_id VARCHAR(128) NOT NULL,
    data_source_version BIGINT NOT NULL,
    dataset_type VARCHAR(24) NOT NULL,
    qualified_name VARCHAR(256) NOT NULL,
    layer_code VARCHAR(128) NOT NULL,
    grain_code VARCHAR(128) NOT NULL,
    schema_sha256 CHAR(64) NOT NULL,
    storage_location_ref VARCHAR(192) NOT NULL,
    retention_days INT NOT NULL,
    PRIMARY KEY (tenant_id, definition_id, definition_version),
    CONSTRAINT fk_metadata_dataset_definition_version FOREIGN KEY
        (tenant_id, definition_id, definition_version)
        REFERENCES cloudmold_metadata_definition_version (tenant_id, definition_id, definition_version),
    CONSTRAINT fk_metadata_dataset_data_source_version FOREIGN KEY
        (tenant_id, data_source_id, data_source_version)
        REFERENCES cloudmold_metadata_data_source_version (tenant_id, definition_id, definition_version),
    CONSTRAINT ck_metadata_dataset_type CHECK
        (dataset_type IN ('TABLE','VIEW','REPORT_DATASET','STREAM','FILESET')),
    CONSTRAINT ck_metadata_dataset_retention CHECK (retention_days BETWEEN 1 AND 36500),
    CONSTRAINT ck_metadata_dataset_location_opaque CHECK
        (storage_location_ref LIKE 'restricted:%' OR storage_location_ref LIKE 'sha256:%')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_metadata_field_version (
    tenant_id BIGINT NOT NULL,
    dataset_id VARCHAR(128) NOT NULL,
    dataset_version BIGINT NOT NULL,
    ordinal_position INT NOT NULL,
    field_code VARCHAR(128) NOT NULL,
    data_type VARCHAR(128) NOT NULL,
    nullable TINYINT(1) NOT NULL,
    primary_key_part TINYINT(1) NOT NULL,
    semantic_type VARCHAR(128) NOT NULL,
    classification VARCHAR(16) NOT NULL,
    PRIMARY KEY (tenant_id, dataset_id, dataset_version, ordinal_position),
    UNIQUE KEY uk_metadata_field_code (tenant_id, dataset_id, dataset_version, field_code),
    CONSTRAINT fk_metadata_field_dataset_version FOREIGN KEY (tenant_id, dataset_id, dataset_version)
        REFERENCES cloudmold_metadata_dataset_version (tenant_id, definition_id, definition_version),
    CONSTRAINT ck_metadata_field_ordinal CHECK (ordinal_position > 0),
    CONSTRAINT ck_metadata_field_nullable CHECK (nullable IN (0, 1)),
    CONSTRAINT ck_metadata_field_pk CHECK (primary_key_part IN (0, 1)),
    CONSTRAINT ck_metadata_field_classification CHECK
        (classification IN ('PUBLIC','INTERNAL','CONFIDENTIAL','RESTRICTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_metadata_task_version (
    tenant_id BIGINT NOT NULL,
    definition_id VARCHAR(128) NOT NULL,
    definition_version BIGINT NOT NULL,
    task_type VARCHAR(24) NOT NULL,
    executable_artifact_ref VARCHAR(192) NOT NULL,
    code_sha256 CHAR(64) NOT NULL,
    schedule_sha256 CHAR(64) NOT NULL,
    resource_group_ref VARCHAR(192) NOT NULL,
    PRIMARY KEY (tenant_id, definition_id, definition_version),
    CONSTRAINT fk_metadata_task_definition_version FOREIGN KEY
        (tenant_id, definition_id, definition_version)
        REFERENCES cloudmold_metadata_definition_version (tenant_id, definition_id, definition_version),
    CONSTRAINT ck_metadata_task_type CHECK
        (task_type IN ('BATCH_SQL','STREAMING','INGESTION','QUALITY','REPORT','OTHER')),
    CONSTRAINT ck_metadata_task_artifact_opaque CHECK
        (executable_artifact_ref LIKE 'restricted:%' OR executable_artifact_ref LIKE 'sha256:%'),
    CONSTRAINT ck_metadata_task_resource_opaque CHECK
        (resource_group_ref LIKE 'restricted:%' OR resource_group_ref LIKE 'sha256:%')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_metadata_task_dependency (
    tenant_id BIGINT NOT NULL,
    task_id VARCHAR(128) NOT NULL,
    task_version BIGINT NOT NULL,
    dependency_sequence INT NOT NULL,
    upstream_task_id VARCHAR(128) NOT NULL,
    upstream_task_version BIGINT NOT NULL,
    dependency_type VARCHAR(16) NOT NULL,
    required_dependency TINYINT(1) NOT NULL,
    PRIMARY KEY (tenant_id, task_id, task_version, dependency_sequence),
    UNIQUE KEY uk_metadata_task_dependency
        (tenant_id, task_id, task_version, upstream_task_id, dependency_type),
    CONSTRAINT fk_metadata_task_dependency_task FOREIGN KEY (tenant_id, task_id, task_version)
        REFERENCES cloudmold_metadata_task_version (tenant_id, definition_id, definition_version),
    CONSTRAINT fk_metadata_task_dependency_upstream FOREIGN KEY
        (tenant_id, upstream_task_id, upstream_task_version)
        REFERENCES cloudmold_metadata_task_version (tenant_id, definition_id, definition_version),
    CONSTRAINT ck_metadata_task_dependency_type CHECK (dependency_type IN ('DATA','CONTROL','SLA')),
    CONSTRAINT ck_metadata_task_dependency_required CHECK (required_dependency IN (0, 1)),
    CONSTRAINT ck_metadata_task_dependency_no_self CHECK (task_id <> upstream_task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_metadata_task_sla (
    tenant_id BIGINT NOT NULL,
    task_id VARCHAR(128) NOT NULL,
    task_version BIGINT NOT NULL,
    service_level_code VARCHAR(128) NOT NULL,
    deadline_minute_utc INT NOT NULL,
    maximum_duration_millis BIGINT NOT NULL,
    maximum_freshness_millis BIGINT NOT NULL,
    approved_by_principal_id VARCHAR(128) NOT NULL,
    PRIMARY KEY (tenant_id, task_id, task_version),
    CONSTRAINT fk_metadata_task_sla_task FOREIGN KEY (tenant_id, task_id, task_version)
        REFERENCES cloudmold_metadata_task_version (tenant_id, definition_id, definition_version),
    CONSTRAINT ck_metadata_task_sla_deadline CHECK (deadline_minute_utc BETWEEN 0 AND 1439),
    CONSTRAINT ck_metadata_task_sla_duration CHECK (maximum_duration_millis > 0),
    CONSTRAINT ck_metadata_task_sla_freshness CHECK (maximum_freshness_millis > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_metadata_task_run_observation (
    tenant_id BIGINT NOT NULL,
    run_id VARCHAR(128) NOT NULL,
    task_id VARCHAR(128) NOT NULL,
    task_version BIGINT NOT NULL,
    attempt INT NOT NULL,
    observation_sequence BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    scheduled_at DATETIME(6) NOT NULL,
    started_at DATETIME(6) NULL,
    finished_at DATETIME(6) NULL,
    duration_millis BIGINT NOT NULL,
    compute_cost_minor BIGINT NOT NULL,
    cost_currency CHAR(3) NOT NULL,
    resource_millis BIGINT NOT NULL,
    rows_read BIGINT NOT NULL,
    rows_written BIGINT NOT NULL,
    source_checkpoint_ref VARCHAR(192) NULL,
    output_snapshot_ref VARCHAR(192) NULL,
    error_ref VARCHAR(192) NULL,
    observed_at DATETIME(6) NOT NULL,
    operation_id BIGINT NOT NULL,
    PRIMARY KEY (tenant_id, run_id, observation_sequence),
    UNIQUE KEY uk_metadata_task_run_operation (tenant_id, operation_id),
    KEY idx_metadata_task_run_task (tenant_id, task_id, task_version, observed_at),
    CONSTRAINT fk_metadata_task_run_task FOREIGN KEY (tenant_id, task_id, task_version)
        REFERENCES cloudmold_metadata_task_version (tenant_id, definition_id, definition_version),
    CONSTRAINT fk_metadata_task_run_operation FOREIGN KEY (tenant_id, operation_id)
        REFERENCES cloudmold_metadata_operation (tenant_id, operation_id),
    CONSTRAINT ck_metadata_task_run_attempt CHECK (attempt > 0),
    CONSTRAINT ck_metadata_task_run_sequence CHECK (observation_sequence > 0),
    CONSTRAINT ck_metadata_task_run_status CHECK
        (status IN ('SCHEDULED','RUNNING','SUCCEEDED','FAILED','CANCELLED')),
    CONSTRAINT ck_metadata_task_run_measures CHECK
        (duration_millis >= 0 AND compute_cost_minor >= 0 AND resource_millis >= 0
         AND rows_read >= 0 AND rows_written >= 0),
    CONSTRAINT ck_metadata_task_run_currency CHECK (cost_currency REGEXP '^[A-Z]{3}$'),
    CONSTRAINT ck_metadata_task_run_time CHECK
        ((started_at IS NULL OR started_at >= scheduled_at)
         AND (finished_at IS NULL OR (started_at IS NOT NULL AND finished_at >= started_at))),
    CONSTRAINT ck_metadata_task_run_terminal CHECK
        (status NOT IN ('SUCCEEDED','FAILED','CANCELLED') OR finished_at IS NOT NULL),
    CONSTRAINT ck_metadata_task_run_success CHECK
        (status <> 'SUCCEEDED' OR (output_snapshot_ref IS NOT NULL AND error_ref IS NULL)),
    CONSTRAINT ck_metadata_task_run_failure CHECK (status <> 'FAILED' OR error_ref IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_metadata_lineage_version (
    tenant_id BIGINT NOT NULL,
    definition_id VARCHAR(128) NOT NULL,
    definition_version BIGINT NOT NULL,
    source_dataset_id VARCHAR(128) NOT NULL,
    source_dataset_version BIGINT NOT NULL,
    target_dataset_id VARCHAR(128) NOT NULL,
    target_dataset_version BIGINT NOT NULL,
    direction VARCHAR(24) NOT NULL,
    transform_sha256 CHAR(64) NOT NULL,
    transformation_ref VARCHAR(192) NOT NULL,
    PRIMARY KEY (tenant_id, definition_id, definition_version),
    CONSTRAINT fk_metadata_lineage_definition_version FOREIGN KEY
        (tenant_id, definition_id, definition_version)
        REFERENCES cloudmold_metadata_definition_version (tenant_id, definition_id, definition_version),
    CONSTRAINT fk_metadata_lineage_source_dataset FOREIGN KEY
        (tenant_id, source_dataset_id, source_dataset_version)
        REFERENCES cloudmold_metadata_dataset_version (tenant_id, definition_id, definition_version),
    CONSTRAINT fk_metadata_lineage_target_dataset FOREIGN KEY
        (tenant_id, target_dataset_id, target_dataset_version)
        REFERENCES cloudmold_metadata_dataset_version (tenant_id, definition_id, definition_version),
    CONSTRAINT ck_metadata_lineage_direction CHECK (direction = 'SOURCE_TO_TARGET'),
    CONSTRAINT ck_metadata_lineage_no_self CHECK (source_dataset_id <> target_dataset_id),
    CONSTRAINT ck_metadata_lineage_ref_opaque CHECK
        (transformation_ref LIKE 'restricted:%' OR transformation_ref LIKE 'sha256:%')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_metadata_dqc_rule_version (
    tenant_id BIGINT NOT NULL,
    definition_id VARCHAR(128) NOT NULL,
    definition_version BIGINT NOT NULL,
    dataset_id VARCHAR(128) NOT NULL,
    dataset_version BIGINT NOT NULL,
    dataset_field VARCHAR(128) NULL,
    rule_type VARCHAR(24) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    expression_sha256 CHAR(64) NOT NULL,
    threshold_value DECIMAL(38,9) NOT NULL,
    threshold_comparator VARCHAR(8) NOT NULL,
    PRIMARY KEY (tenant_id, definition_id, definition_version),
    UNIQUE KEY uk_metadata_dqc_rule_target
        (tenant_id, definition_id, definition_version, dataset_id, dataset_version),
    CONSTRAINT fk_metadata_dqc_rule_definition_version FOREIGN KEY
        (tenant_id, definition_id, definition_version)
        REFERENCES cloudmold_metadata_definition_version (tenant_id, definition_id, definition_version),
    CONSTRAINT fk_metadata_dqc_rule_dataset FOREIGN KEY (tenant_id, dataset_id, dataset_version)
        REFERENCES cloudmold_metadata_dataset_version (tenant_id, definition_id, definition_version),
    CONSTRAINT fk_metadata_dqc_rule_field FOREIGN KEY
        (tenant_id, dataset_id, dataset_version, dataset_field)
        REFERENCES cloudmold_metadata_field_version (tenant_id, dataset_id, dataset_version, field_code),
    CONSTRAINT ck_metadata_dqc_rule_type CHECK
        (rule_type IN ('NOT_NULL','UNIQUE','ROW_COUNT','RANGE','FRESHNESS','RECONCILIATION','CUSTOM_HASHED')),
    CONSTRAINT ck_metadata_dqc_severity CHECK (severity IN ('INFO','WARNING','ERROR','CRITICAL')),
    CONSTRAINT ck_metadata_dqc_comparator CHECK (threshold_comparator IN ('EQ','NE','GT','GTE','LT','LTE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_metadata_dqc_result (
    tenant_id BIGINT NOT NULL,
    result_id VARCHAR(128) NOT NULL,
    dqc_rule_id VARCHAR(128) NOT NULL,
    dqc_rule_version BIGINT NOT NULL,
    dataset_id VARCHAR(128) NOT NULL,
    dataset_version BIGINT NOT NULL,
    task_run_id VARCHAR(128) NULL,
    task_run_observation_sequence BIGINT NULL,
    status VARCHAR(8) NOT NULL,
    expected_value DECIMAL(38,9) NOT NULL,
    actual_value DECIMAL(38,9) NOT NULL,
    evaluated_rows BIGINT NOT NULL,
    violation_count BIGINT NOT NULL,
    evidence_ref VARCHAR(192) NOT NULL,
    observed_at DATETIME(6) NOT NULL,
    operation_id BIGINT NOT NULL,
    PRIMARY KEY (tenant_id, result_id),
    UNIQUE KEY uk_metadata_dqc_result_operation (tenant_id, operation_id),
    KEY idx_metadata_dqc_result_rule (tenant_id, dqc_rule_id, dqc_rule_version, observed_at),
    CONSTRAINT fk_metadata_dqc_result_rule_target FOREIGN KEY
        (tenant_id, dqc_rule_id, dqc_rule_version, dataset_id, dataset_version)
        REFERENCES cloudmold_metadata_dqc_rule_version
        (tenant_id, definition_id, definition_version, dataset_id, dataset_version),
    CONSTRAINT fk_metadata_dqc_result_dataset FOREIGN KEY (tenant_id, dataset_id, dataset_version)
        REFERENCES cloudmold_metadata_dataset_version (tenant_id, definition_id, definition_version),
    CONSTRAINT fk_metadata_dqc_result_task_run FOREIGN KEY
        (tenant_id, task_run_id, task_run_observation_sequence)
        REFERENCES cloudmold_metadata_task_run_observation (tenant_id, run_id, observation_sequence),
    CONSTRAINT fk_metadata_dqc_result_operation FOREIGN KEY (tenant_id, operation_id)
        REFERENCES cloudmold_metadata_operation (tenant_id, operation_id),
    CONSTRAINT ck_metadata_dqc_result_status CHECK (status IN ('PASS','WARN','FAIL','ERROR')),
    CONSTRAINT ck_metadata_dqc_result_counts CHECK
        (evaluated_rows >= 0 AND violation_count >= 0 AND violation_count <= evaluated_rows),
    CONSTRAINT ck_metadata_dqc_result_pass CHECK (status <> 'PASS' OR violation_count = 0),
    CONSTRAINT ck_metadata_dqc_result_violation CHECK
        (status NOT IN ('WARN','FAIL') OR violation_count > 0),
    CONSTRAINT ck_metadata_dqc_evidence_opaque CHECK
        (evidence_ref LIKE 'restricted:%' OR evidence_ref LIKE 'sha256:%')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_metadata_metric_version (
    tenant_id BIGINT NOT NULL,
    definition_id VARCHAR(128) NOT NULL,
    definition_version BIGINT NOT NULL,
    grain_code VARCHAR(128) NOT NULL,
    metric_unit VARCHAR(128) NOT NULL,
    aggregation_type VARCHAR(24) NOT NULL,
    expression_sha256 CHAR(64) NOT NULL,
    filter_sha256 CHAR(64) NOT NULL,
    dimensions_sha256 CHAR(64) NOT NULL,
    semantic_version VARCHAR(32) NOT NULL,
    PRIMARY KEY (tenant_id, definition_id, definition_version),
    UNIQUE KEY uk_metadata_metric_semver (tenant_id, definition_id, semantic_version),
    CONSTRAINT fk_metadata_metric_definition_version FOREIGN KEY
        (tenant_id, definition_id, definition_version)
        REFERENCES cloudmold_metadata_definition_version (tenant_id, definition_id, definition_version),
    CONSTRAINT ck_metadata_metric_aggregation CHECK
        (aggregation_type IN ('SUM','COUNT','COUNT_DISTINCT','MIN','MAX','AVG','RATIO','SNAPSHOT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_metadata_status_history (
    history_id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    aggregate_type VARCHAR(32) NOT NULL,
    aggregate_id VARCHAR(128) NOT NULL,
    aggregate_version BIGINT NOT NULL,
    previous_status VARCHAR(32) NULL,
    current_status VARCHAR(32) NOT NULL,
    operation_id BIGINT NOT NULL,
    reason_code VARCHAR(64) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (history_id),
    UNIQUE KEY uk_metadata_status_history_version
        (tenant_id, aggregate_type, aggregate_id, aggregate_version),
    CONSTRAINT fk_metadata_status_history_operation FOREIGN KEY (tenant_id, operation_id)
        REFERENCES cloudmold_metadata_operation (tenant_id, operation_id),
    CONSTRAINT ck_metadata_status_history_version CHECK (aggregate_version > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
