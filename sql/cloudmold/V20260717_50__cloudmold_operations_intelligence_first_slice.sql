-- CloudMold governed observations, reviewed intelligence clues and operations alert lifecycle.
-- Raw content is intentionally excluded; only restricted references and SHA-256 digests are stored.

CREATE TABLE cloudmold_operations_intelligence_operation (
    operation_id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    command_type VARCHAR(64) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    attempt_token CHAR(36) NOT NULL,
    status TINYINT NOT NULL,
    aggregate_type VARCHAR(64) NULL,
    aggregate_id VARCHAR(128) NULL,
    result_json JSON NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (operation_id),
    UNIQUE KEY uk_ops_int_operation_tenant_id (tenant_id, operation_id),
    UNIQUE KEY uk_ops_int_operation_tenant_key (tenant_id, idempotency_key),
    CONSTRAINT ck_ops_int_operation_status CHECK (status IN (0, 10)),
    CONSTRAINT ck_ops_int_operation_hash CHECK (request_hash REGEXP '^[0-9a-f]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Idempotent operations-intelligence command result';

CREATE TABLE cloudmold_intelligence_observation (
    observation_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    source_system VARCHAR(64) NOT NULL,
    source_event_id VARCHAR(256) NOT NULL,
    observation_type VARCHAR(64) NOT NULL,
    subject_type VARCHAR(32) NOT NULL,
    subject_ref VARCHAR(256) NOT NULL,
    evidence_ref VARCHAR(160) NOT NULL,
    content_sha256 CHAR(64) NOT NULL,
    observed_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (tenant_id, observation_id),
    UNIQUE KEY uk_int_observation_source (tenant_id, source_system, source_event_id),
    CONSTRAINT ck_int_observation_subject CHECK (
        subject_type IN ('PRINCIPAL','ORDER','TICKET','MERCHANT','CONTENT','TASK','DATASET','EXTERNAL_SUBJECT')),
    CONSTRAINT ck_int_observation_evidence CHECK (
        evidence_ref REGEXP '^(sha256:[0-9a-f]{64}|restricted:[A-Za-z0-9_-]{16,128})$'),
    CONSTRAINT ck_int_observation_hash CHECK (content_sha256 REGEXP '^[0-9a-f]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Immutable purpose-limited source observation';

CREATE TABLE cloudmold_intelligence_model_result (
    model_result_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    observation_id VARCHAR(128) NOT NULL,
    invocation_attempt_ref VARCHAR(128) NOT NULL,
    model_version_ref VARCHAR(128) NOT NULL,
    outcome_code VARCHAR(16) NOT NULL,
    score_basis_points INT NULL,
    retry_no INT NOT NULL,
    evidence_ref VARCHAR(160) NOT NULL,
    result_sha256 CHAR(64) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (tenant_id, model_result_id),
    UNIQUE KEY uk_int_model_result_observation (tenant_id, model_result_id, observation_id),
    UNIQUE KEY uk_int_model_result_attempt_retry (tenant_id, invocation_attempt_ref, retry_no),
    CONSTRAINT fk_int_model_result_observation FOREIGN KEY (tenant_id, observation_id)
        REFERENCES cloudmold_intelligence_observation (tenant_id, observation_id),
    CONSTRAINT ck_int_model_result_outcome CHECK (outcome_code IN ('SUCCEEDED','FAILED','PARTIAL')),
    CONSTRAINT ck_int_model_result_score CHECK (score_basis_points IS NULL OR score_basis_points BETWEEN 0 AND 10000),
    CONSTRAINT ck_int_model_result_retry CHECK (retry_no BETWEEN 0 AND 100),
    CONSTRAINT ck_int_model_result_evidence CHECK (
        evidence_ref REGEXP '^(sha256:[0-9a-f]{64}|restricted:[A-Za-z0-9_-]{16,128})$'),
    CONSTRAINT ck_int_model_result_hash CHECK (result_sha256 REGEXP '^[0-9a-f]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Immutable AI model-result observation; not a risk decision';

CREATE TABLE cloudmold_intelligence_clue (
    clue_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    observation_id VARCHAR(128) NOT NULL,
    model_result_id VARCHAR(128) NULL,
    clue_type VARCHAR(64) NOT NULL,
    source_code VARCHAR(64) NOT NULL,
    source_published_at DATETIME(6) NOT NULL,
    evidence_ref VARCHAR(160) NOT NULL,
    evidence_sha256 CHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (tenant_id, clue_id),
    UNIQUE KEY uk_int_clue_model_identity (tenant_id, clue_id, observation_id),
    CONSTRAINT fk_int_clue_observation FOREIGN KEY (tenant_id, observation_id)
        REFERENCES cloudmold_intelligence_observation (tenant_id, observation_id),
    CONSTRAINT fk_int_clue_model_result FOREIGN KEY (tenant_id, model_result_id, observation_id)
        REFERENCES cloudmold_intelligence_model_result (tenant_id, model_result_id, observation_id),
    CONSTRAINT ck_int_clue_status CHECK (status IN ('OBSERVED','ACCEPTED','REJECTED')),
    CONSTRAINT ck_int_clue_version CHECK (
        (status='OBSERVED' AND version=1) OR (status IN ('ACCEPTED','REJECTED') AND version=2)),
    CONSTRAINT ck_int_clue_evidence CHECK (
        evidence_ref REGEXP '^(sha256:[0-9a-f]{64}|restricted:[A-Za-z0-9_-]{16,128})$'),
    CONSTRAINT ck_int_clue_hash CHECK (evidence_sha256 REGEXP '^[0-9a-f]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Intelligence clue requiring one explicit human review';

CREATE TABLE cloudmold_intelligence_clue_review (
    review_id CHAR(36) NOT NULL,
    tenant_id BIGINT NOT NULL,
    clue_id VARCHAR(128) NOT NULL,
    decision VARCHAR(16) NOT NULL,
    reason_code VARCHAR(64) NOT NULL,
    reviewer_principal_id VARCHAR(128) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (review_id),
    UNIQUE KEY uk_int_clue_review_tenant_id (tenant_id, review_id),
    UNIQUE KEY uk_int_clue_review_once (tenant_id, clue_id),
    CONSTRAINT fk_int_clue_review_clue FOREIGN KEY (tenant_id, clue_id)
        REFERENCES cloudmold_intelligence_clue (tenant_id, clue_id),
    CONSTRAINT ck_int_clue_review_decision CHECK (decision IN ('ACCEPT','REJECT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Immutable human acceptance or rejection of an intelligence clue';

CREATE TABLE cloudmold_operations_alert (
    alert_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    alert_code VARCHAR(64) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_ref VARCHAR(128) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    category VARCHAR(64) NOT NULL,
    subcategory VARCHAR(64) NOT NULL,
    evidence_ref VARCHAR(160) NOT NULL,
    title_sha256 CHAR(64) NOT NULL,
    status VARCHAR(24) NOT NULL,
    current_actor_principal_id VARCHAR(128) NOT NULL,
    version BIGINT NOT NULL,
    opened_at DATETIME(6) NOT NULL,
    terminal_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (tenant_id, alert_id),
    UNIQUE KEY uk_ops_alert_code (tenant_id, alert_code),
    UNIQUE KEY uk_ops_alert_source (tenant_id, source_type, source_ref, alert_code),
    CONSTRAINT ck_ops_alert_source_type CHECK (
        source_type IN ('OBSERVATION','CLUE','METADATA_TASK','METRIC')),
    CONSTRAINT ck_ops_alert_severity CHECK (severity IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    CONSTRAINT ck_ops_alert_status CHECK (
        status IN ('OPEN','NOTIFIED','CLAIMED','RESOLVED','INVALID','CLOSED_NO_ACTION')),
    CONSTRAINT ck_ops_alert_version CHECK (version > 0 AND version < 32767),
    CONSTRAINT ck_ops_alert_terminal CHECK (
        (status IN ('RESOLVED','INVALID','CLOSED_NO_ACTION') AND terminal_at IS NOT NULL)
        OR (status IN ('OPEN','NOTIFIED','CLAIMED') AND terminal_at IS NULL)),
    CONSTRAINT ck_ops_alert_evidence CHECK (
        evidence_ref REGEXP '^(sha256:[0-9a-f]{64}|restricted:[A-Za-z0-9_-]{16,128})$'),
    CONSTRAINT ck_ops_alert_title_hash CHECK (title_sha256 REGEXP '^[0-9a-f]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Versioned operations alert case without raw title or description';

CREATE TABLE cloudmold_operations_alert_history (
    history_id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    alert_id VARCHAR(128) NOT NULL,
    alert_version BIGINT NOT NULL,
    previous_status VARCHAR(24) NULL,
    current_status VARCHAR(24) NOT NULL,
    actor_principal_id VARCHAR(128) NOT NULL,
    reason_code VARCHAR(64) NULL,
    operation_id BIGINT NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (history_id),
    UNIQUE KEY uk_ops_alert_history_version (tenant_id, alert_id, alert_version),
    CONSTRAINT fk_ops_alert_history_alert FOREIGN KEY (tenant_id, alert_id)
        REFERENCES cloudmold_operations_alert (tenant_id, alert_id),
    CONSTRAINT fk_ops_alert_history_operation FOREIGN KEY (tenant_id, operation_id)
        REFERENCES cloudmold_operations_intelligence_operation (tenant_id, operation_id),
    CONSTRAINT ck_ops_alert_history_version CHECK (alert_version > 0 AND alert_version < 32767),
    CONSTRAINT ck_ops_alert_history_status CHECK (
        current_status IN ('OPEN','NOTIFIED','CLAIMED','RESOLVED','INVALID','CLOSED_NO_ACTION'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Immutable operations alert lifecycle history';
