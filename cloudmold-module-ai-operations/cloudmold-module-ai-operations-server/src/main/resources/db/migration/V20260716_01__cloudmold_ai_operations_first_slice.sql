CREATE TABLE cloudmold_ai_ops_operation (
    operation_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    operation_type VARCHAR(64) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    attempt_token CHAR(36) NOT NULL,
    status SMALLINT NOT NULL,
    aggregate_type VARCHAR(64) NULL,
    aggregate_id VARCHAR(64) NULL,
    result_json JSON NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (operation_id),
    UNIQUE KEY uk_ai_ops_operation_tenant_key (tenant_id, idempotency_key),
    UNIQUE KEY uk_ai_ops_operation_tenant_id (tenant_id, operation_id),
    CONSTRAINT ck_ai_ops_operation_status CHECK (status IN (0, 10))
) ENGINE=InnoDB;

CREATE TABLE cloudmold_ai_ops_application (
    application_id VARCHAR(64) NOT NULL,
    tenant_id BIGINT NOT NULL,
    application_code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    status VARCHAR(24) NOT NULL,
    version BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (application_id),
    UNIQUE KEY uk_ai_ops_application_tenant_id (tenant_id, application_id),
    UNIQUE KEY uk_ai_ops_application_tenant_code (tenant_id, application_code),
    KEY idx_ai_ops_application_status (tenant_id, status),
    CONSTRAINT ck_ai_ops_application_status CHECK (status IN ('DRAFT', 'ACTIVE', 'SUSPENDED', 'RETIRED')),
    CONSTRAINT ck_ai_ops_application_version CHECK (version > 0)
) ENGINE=InnoDB;

CREATE TABLE cloudmold_ai_ops_workflow_definition (
    workflow_id VARCHAR(64) NOT NULL,
    tenant_id BIGINT NOT NULL,
    application_id VARCHAR(64) NOT NULL,
    workflow_code VARCHAR(64) NOT NULL,
    current_version BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (workflow_id),
    UNIQUE KEY uk_ai_ops_workflow_tenant_id (tenant_id, workflow_id),
    UNIQUE KEY uk_ai_ops_workflow_tenant_code (tenant_id, application_id, workflow_code),
    CONSTRAINT fk_ai_ops_workflow_application FOREIGN KEY (tenant_id, application_id)
        REFERENCES cloudmold_ai_ops_application (tenant_id, application_id),
    CONSTRAINT ck_ai_ops_workflow_version CHECK (current_version > 0)
) ENGINE=InnoDB;

CREATE TABLE cloudmold_ai_ops_workflow_version (
    workflow_version_id VARCHAR(64) NOT NULL,
    tenant_id BIGINT NOT NULL,
    workflow_id VARCHAR(64) NOT NULL,
    application_id VARCHAR(64) NOT NULL,
    workflow_version BIGINT NOT NULL,
    definition_ref VARCHAR(256) NOT NULL,
    definition_sha256 CHAR(64) NOT NULL,
    published_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (workflow_version_id),
    UNIQUE KEY uk_ai_ops_wfver_tenant_id (tenant_id, workflow_version_id),
    UNIQUE KEY uk_ai_ops_wfver_workflow_version (tenant_id, workflow_id, workflow_version),
    UNIQUE KEY uk_ai_ops_wfver_workflow_id (tenant_id, workflow_id, workflow_version_id),
    CONSTRAINT fk_ai_ops_wfver_workflow FOREIGN KEY (tenant_id, workflow_id)
        REFERENCES cloudmold_ai_ops_workflow_definition (tenant_id, workflow_id),
    CONSTRAINT fk_ai_ops_wfver_application FOREIGN KEY (tenant_id, application_id)
        REFERENCES cloudmold_ai_ops_application (tenant_id, application_id),
    CONSTRAINT ck_ai_ops_wfver_version CHECK (workflow_version > 0),
    CONSTRAINT ck_ai_ops_wfver_sha CHECK (definition_sha256 REGEXP '^[0-9a-fA-F]{64}$')
) ENGINE=InnoDB;

CREATE TABLE cloudmold_ai_ops_workflow_run (
    run_id VARCHAR(64) NOT NULL,
    tenant_id BIGINT NOT NULL,
    run_key VARCHAR(128) NOT NULL,
    application_id VARCHAR(64) NOT NULL,
    workflow_id VARCHAR(64) NOT NULL,
    workflow_version_id VARCHAR(64) NOT NULL,
    workflow_version BIGINT NOT NULL,
    trigger_type VARCHAR(32) NOT NULL,
    business_ref VARCHAR(256) NULL,
    status VARCHAR(24) NOT NULL,
    expected_invocation_count INT NOT NULL,
    started_at DATETIME(6) NOT NULL,
    finished_at DATETIME(6) NULL,
    error_code VARCHAR(64) NULL,
    version BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (run_id),
    UNIQUE KEY uk_ai_ops_run_tenant_id (tenant_id, run_id),
    UNIQUE KEY uk_ai_ops_run_tenant_key (tenant_id, run_key),
    KEY idx_ai_ops_run_application_time (tenant_id, application_id, started_at),
    KEY idx_ai_ops_run_workflow_time (tenant_id, workflow_id, started_at),
    CONSTRAINT fk_ai_ops_run_application FOREIGN KEY (tenant_id, application_id)
        REFERENCES cloudmold_ai_ops_application (tenant_id, application_id),
    CONSTRAINT fk_ai_ops_run_workflow FOREIGN KEY (tenant_id, workflow_id)
        REFERENCES cloudmold_ai_ops_workflow_definition (tenant_id, workflow_id),
    CONSTRAINT fk_ai_ops_run_wfver FOREIGN KEY (tenant_id, workflow_id, workflow_version_id)
        REFERENCES cloudmold_ai_ops_workflow_version (tenant_id, workflow_id, workflow_version_id),
    CONSTRAINT ck_ai_ops_run_status CHECK (status IN ('RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED')),
    CONSTRAINT ck_ai_ops_run_counts CHECK (expected_invocation_count >= 0 AND version > 0),
    CONSTRAINT ck_ai_ops_run_terminal CHECK (
        (status = 'RUNNING' AND finished_at IS NULL AND error_code IS NULL)
        OR (status = 'SUCCEEDED' AND finished_at IS NOT NULL AND error_code IS NULL)
        OR (status IN ('FAILED', 'CANCELLED') AND finished_at IS NOT NULL AND error_code IS NOT NULL)
    ),
    CONSTRAINT ck_ai_ops_run_time CHECK (finished_at IS NULL OR finished_at >= started_at)
) ENGINE=InnoDB;

CREATE TABLE cloudmold_ai_ops_invocation_attempt (
    attempt_id VARCHAR(64) NOT NULL,
    tenant_id BIGINT NOT NULL,
    attempt_key VARCHAR(128) NOT NULL,
    run_id VARCHAR(64) NOT NULL,
    application_id VARCHAR(64) NOT NULL,
    workflow_id VARCHAR(64) NOT NULL,
    step_ref VARCHAR(128) NOT NULL,
    attempt_no INT NOT NULL,
    provider_code VARCHAR(64) NOT NULL,
    model_code VARCHAR(128) NOT NULL,
    provider_request_ref VARCHAR(256) NULL,
    outcome VARCHAR(24) NOT NULL,
    input_tokens BIGINT NOT NULL,
    cached_input_tokens BIGINT NOT NULL,
    output_tokens BIGINT NOT NULL,
    total_tokens BIGINT NOT NULL,
    latency_millis BIGINT NOT NULL,
    cost_amount_minor BIGINT NULL,
    currency_code CHAR(3) NULL,
    pricing_version_ref VARCHAR(128) NULL,
    error_code VARCHAR(64) NULL,
    occurred_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (attempt_id),
    UNIQUE KEY uk_ai_ops_attempt_tenant_id (tenant_id, attempt_id),
    UNIQUE KEY uk_ai_ops_attempt_tenant_key (tenant_id, attempt_key),
    UNIQUE KEY uk_ai_ops_attempt_run_step_no (tenant_id, run_id, step_ref, attempt_no),
    KEY idx_ai_ops_attempt_application_time (tenant_id, application_id, occurred_at),
    KEY idx_ai_ops_attempt_workflow_time (tenant_id, workflow_id, occurred_at),
    CONSTRAINT fk_ai_ops_attempt_run FOREIGN KEY (tenant_id, run_id)
        REFERENCES cloudmold_ai_ops_workflow_run (tenant_id, run_id),
    CONSTRAINT fk_ai_ops_attempt_application FOREIGN KEY (tenant_id, application_id)
        REFERENCES cloudmold_ai_ops_application (tenant_id, application_id),
    CONSTRAINT fk_ai_ops_attempt_workflow FOREIGN KEY (tenant_id, workflow_id)
        REFERENCES cloudmold_ai_ops_workflow_definition (tenant_id, workflow_id),
    CONSTRAINT ck_ai_ops_attempt_outcome CHECK (outcome IN ('SUCCEEDED', 'FAILED', 'CANCELLED')),
    CONSTRAINT ck_ai_ops_attempt_tokens CHECK (
        input_tokens >= 0 AND cached_input_tokens >= 0 AND output_tokens >= 0 AND total_tokens >= 0
        AND cached_input_tokens <= input_tokens AND total_tokens = input_tokens + output_tokens
    ),
    CONSTRAINT ck_ai_ops_attempt_latency CHECK (attempt_no > 0 AND latency_millis >= 0),
    CONSTRAINT ck_ai_ops_attempt_cost CHECK (
        (cost_amount_minor IS NULL AND currency_code IS NULL AND pricing_version_ref IS NULL)
        OR (cost_amount_minor >= 0 AND currency_code IS NOT NULL AND pricing_version_ref IS NOT NULL)
    ),
    CONSTRAINT ck_ai_ops_attempt_error CHECK (
        (outcome = 'SUCCEEDED' AND error_code IS NULL)
        OR (outcome IN ('FAILED', 'CANCELLED') AND error_code IS NOT NULL)
    )
) ENGINE=InnoDB;

CREATE TABLE cloudmold_ai_ops_outcome_feedback (
    feedback_id VARCHAR(64) NOT NULL,
    tenant_id BIGINT NOT NULL,
    feedback_key VARCHAR(128) NOT NULL,
    run_id VARCHAR(64) NOT NULL,
    feedback_type VARCHAR(32) NOT NULL,
    outcome_code VARCHAR(24) NOT NULL,
    evaluator_type VARCHAR(24) NOT NULL,
    evidence_ref VARCHAR(256) NULL,
    occurred_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (feedback_id),
    UNIQUE KEY uk_ai_ops_feedback_tenant_id (tenant_id, feedback_id),
    UNIQUE KEY uk_ai_ops_feedback_tenant_key (tenant_id, feedback_key),
    KEY idx_ai_ops_feedback_run_time (tenant_id, run_id, occurred_at),
    CONSTRAINT fk_ai_ops_feedback_run FOREIGN KEY (tenant_id, run_id)
        REFERENCES cloudmold_ai_ops_workflow_run (tenant_id, run_id),
    CONSTRAINT ck_ai_ops_feedback_type CHECK (feedback_type IN ('QUALITY', 'CORRECTNESS', 'BUSINESS_OUTCOME', 'SAFETY')),
    CONSTRAINT ck_ai_ops_feedback_outcome CHECK (outcome_code IN ('POSITIVE', 'NEGATIVE', 'NEUTRAL', 'UNKNOWN')),
    CONSTRAINT ck_ai_ops_feedback_evaluator CHECK (evaluator_type IN ('HUMAN', 'AUTOMATED', 'BUSINESS_SYSTEM'))
) ENGINE=InnoDB;

CREATE TABLE cloudmold_ai_ops_status_history (
    history_id VARCHAR(64) NOT NULL,
    tenant_id BIGINT NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    aggregate_version BIGINT NOT NULL,
    operation_id BIGINT UNSIGNED NOT NULL,
    operation_type VARCHAR(64) NOT NULL,
    previous_status VARCHAR(24) NULL,
    current_status VARCHAR(24) NOT NULL,
    error_code VARCHAR(64) NULL,
    occurred_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (history_id),
    UNIQUE KEY uk_ai_ops_history_aggregate_version (tenant_id, aggregate_type, aggregate_id, aggregate_version),
    UNIQUE KEY uk_ai_ops_history_operation (tenant_id, operation_id),
    KEY idx_ai_ops_history_time (tenant_id, occurred_at),
    CONSTRAINT fk_ai_ops_history_operation FOREIGN KEY (tenant_id, operation_id)
        REFERENCES cloudmold_ai_ops_operation (tenant_id, operation_id),
    CONSTRAINT ck_ai_ops_history_type CHECK (aggregate_type IN ('ai_application', 'ai_workflow_run')),
    CONSTRAINT ck_ai_ops_history_version CHECK (aggregate_version > 0)
) ENGINE=InnoDB;
