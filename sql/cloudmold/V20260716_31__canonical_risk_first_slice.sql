-- CloudMold explainable risk evidence and human-review first slice.
-- Deliberately contains no raw phone/IP/address/device columns and no enforcement action fields.

CREATE TABLE cloudmold_risk_operation (
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
    UNIQUE KEY uk_risk_operation_tenant_id (tenant_id, operation_id),
    UNIQUE KEY uk_risk_operation_tenant_key (tenant_id, idempotency_key),
    CONSTRAINT ck_risk_operation_status CHECK (status IN (0, 10))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Risk command idempotency and replay result';

CREATE TABLE cloudmold_risk_policy (
    policy_id CHAR(36) NOT NULL,
    tenant_id BIGINT NOT NULL,
    policy_code VARCHAR(64) NOT NULL,
    policy_name VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL,
    current_version BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (policy_id),
    UNIQUE KEY uk_risk_policy_tenant_id (tenant_id, policy_id),
    UNIQUE KEY uk_risk_policy_tenant_code (tenant_id, policy_code),
    CONSTRAINT ck_risk_policy_status CHECK (status IN ('DRAFT', 'PUBLISHED')),
    CONSTRAINT ck_risk_policy_version CHECK (current_version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Current explainable risk policy head';

CREATE TABLE cloudmold_risk_policy_version (
    policy_version_id CHAR(36) NOT NULL,
    tenant_id BIGINT NOT NULL,
    policy_id CHAR(36) NOT NULL,
    policy_version BIGINT NOT NULL,
    rules_sha256 CHAR(64) NOT NULL,
    approved_by_principal_id VARCHAR(128) NOT NULL,
    effective_from DATETIME(6) NOT NULL,
    published_at DATETIME(6) NOT NULL,
    PRIMARY KEY (policy_version_id),
    UNIQUE KEY uk_risk_policy_version_tenant_id (tenant_id, policy_version_id),
    UNIQUE KEY uk_risk_policy_version_number (tenant_id, policy_id, policy_version),
    UNIQUE KEY uk_risk_policy_version_identity (tenant_id, policy_version_id, policy_id, policy_version),
    CONSTRAINT fk_risk_policy_version_policy FOREIGN KEY (tenant_id, policy_id)
        REFERENCES cloudmold_risk_policy (tenant_id, policy_id),
    CONSTRAINT ck_risk_policy_version_positive CHECK (policy_version > 0),
    CONSTRAINT ck_risk_policy_version_hash CHECK (rules_sha256 REGEXP '^[0-9a-f]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Immutable published risk policy version';

CREATE TABLE cloudmold_risk_policy_rule (
    rule_id CHAR(36) NOT NULL,
    tenant_id BIGINT NOT NULL,
    policy_version_id CHAR(36) NOT NULL,
    policy_id CHAR(36) NOT NULL,
    policy_version BIGINT NOT NULL,
    rule_sequence INT NOT NULL,
    rule_code VARCHAR(64) NOT NULL,
    signal_type VARCHAR(64) NOT NULL,
    operator_code VARCHAR(8) NOT NULL,
    threshold_value VARCHAR(32) NOT NULL,
    outcome_code VARCHAR(64) NOT NULL,
    explanation_template VARCHAR(256) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (rule_id),
    UNIQUE KEY uk_risk_rule_tenant_id (tenant_id, rule_id),
    UNIQUE KEY uk_risk_rule_version_code (tenant_id, policy_version_id, rule_code),
    UNIQUE KEY uk_risk_rule_version_sequence (tenant_id, policy_version_id, rule_sequence),
    CONSTRAINT fk_risk_rule_version FOREIGN KEY (tenant_id, policy_version_id, policy_id, policy_version)
        REFERENCES cloudmold_risk_policy_version (tenant_id, policy_version_id, policy_id, policy_version),
    CONSTRAINT ck_risk_rule_sequence CHECK (rule_sequence > 0),
    CONSTRAINT ck_risk_rule_operator CHECK (operator_code IN ('EQ','NE','GT','GTE','LT','LTE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Immutable explainable rule snapshot';

CREATE TABLE cloudmold_risk_signal (
    signal_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    subject_principal_id VARCHAR(128) NOT NULL,
    policy_id CHAR(36) NOT NULL,
    policy_version BIGINT NOT NULL,
    signal_type VARCHAR(64) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    evidence_ref VARCHAR(160) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (tenant_id, signal_id),
    KEY idx_risk_signal_subject_time (tenant_id, subject_principal_id, occurred_at),
    CONSTRAINT fk_risk_signal_policy_version FOREIGN KEY (tenant_id, policy_id, policy_version)
        REFERENCES cloudmold_risk_policy_version (tenant_id, policy_id, policy_version),
    CONSTRAINT ck_risk_signal_severity CHECK (severity IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    CONSTRAINT ck_risk_signal_evidence_ref CHECK (
        evidence_ref REGEXP '^(sha256:[0-9a-f]{64}|restricted:[A-Za-z0-9_-]{16,128})$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Tenant-scoped explainable risk signal';

CREATE TABLE cloudmold_risk_relation_edge (
    relation_id CHAR(36) NOT NULL,
    tenant_id BIGINT NOT NULL,
    subject_principal_id VARCHAR(128) NOT NULL,
    related_principal_id VARCHAR(128) NOT NULL,
    medium_type VARCHAR(32) NOT NULL,
    medium_token CHAR(64) NOT NULL,
    key_version INT NOT NULL,
    first_seen_at DATETIME(6) NOT NULL,
    last_seen_at DATETIME(6) NOT NULL,
    confidence_basis_points INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (relation_id),
    UNIQUE KEY uk_risk_relation_tenant_id (tenant_id, relation_id),
    UNIQUE KEY uk_risk_relation_edge (tenant_id, subject_principal_id, related_principal_id,
                                      medium_type, medium_token, key_version),
    CONSTRAINT ck_risk_relation_order CHECK (subject_principal_id < related_principal_id),
    CONSTRAINT ck_risk_relation_medium_type CHECK (
        medium_type IN ('PHONE','DEVICE','IP','ADDRESS','PAYMENT_ACCOUNT')),
    CONSTRAINT ck_risk_relation_token CHECK (medium_token REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT ck_risk_relation_key_version CHECK (key_version > 0),
    CONSTRAINT ck_risk_relation_window CHECK (last_seen_at >= first_seen_at),
    CONSTRAINT ck_risk_relation_confidence CHECK (confidence_basis_points BETWEEN 0 AND 10000)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='PII-safe keyed-HMAC relationship observation';

CREATE TABLE cloudmold_risk_cluster (
    cluster_id CHAR(36) NOT NULL,
    tenant_id BIGINT NOT NULL,
    cluster_code VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    risk_level VARCHAR(16) NOT NULL,
    member_count INT NOT NULL,
    edge_count INT NOT NULL,
    version BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (cluster_id),
    UNIQUE KEY uk_risk_cluster_tenant_id (tenant_id, cluster_id),
    UNIQUE KEY uk_risk_cluster_tenant_code (tenant_id, cluster_code),
    CONSTRAINT ck_risk_cluster_status CHECK (status IN ('OPEN','UNDER_REVIEW','CONFIRMED','DISMISSED','CLOSED')),
    CONSTRAINT ck_risk_cluster_level CHECK (risk_level IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    CONSTRAINT ck_risk_cluster_counts CHECK (member_count >= 0 AND edge_count >= 0 AND edge_count <= member_count),
    CONSTRAINT ck_risk_cluster_version CHECK (version > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Risk evidence cluster; no enforcement semantics';

CREATE TABLE cloudmold_risk_cluster_member (
    cluster_member_id CHAR(36) NOT NULL,
    tenant_id BIGINT NOT NULL,
    cluster_id CHAR(36) NOT NULL,
    member_type VARCHAR(16) NOT NULL,
    member_ref VARCHAR(128) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (cluster_member_id),
    UNIQUE KEY uk_risk_cluster_member_tenant_id (tenant_id, cluster_member_id),
    UNIQUE KEY uk_risk_cluster_member (tenant_id, cluster_id, member_type, member_ref),
    CONSTRAINT fk_risk_cluster_member_cluster FOREIGN KEY (tenant_id, cluster_id)
        REFERENCES cloudmold_risk_cluster (tenant_id, cluster_id),
    CONSTRAINT ck_risk_cluster_member_type CHECK (member_type IN ('PRINCIPAL','RELATION'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Immutable membership in a risk evidence cluster';

CREATE TABLE cloudmold_risk_review_case (
    case_id CHAR(36) NOT NULL,
    tenant_id BIGINT NOT NULL,
    cluster_id CHAR(36) NOT NULL,
    status VARCHAR(16) NOT NULL,
    reviewer_principal_id VARCHAR(128) NOT NULL,
    version BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (case_id),
    UNIQUE KEY uk_risk_review_tenant_id (tenant_id, case_id),
    UNIQUE KEY uk_risk_review_case_cluster (tenant_id, case_id, cluster_id),
    KEY idx_risk_review_cluster (tenant_id, cluster_id, status),
    CONSTRAINT fk_risk_review_cluster FOREIGN KEY (tenant_id, cluster_id)
        REFERENCES cloudmold_risk_cluster (tenant_id, cluster_id),
    CONSTRAINT ck_risk_review_status CHECK (status IN ('OPEN','IN_REVIEW','DECIDED','CLOSED')),
    CONSTRAINT ck_risk_review_version CHECK (version > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Human risk review case';

CREATE TABLE cloudmold_risk_decision (
    decision_id CHAR(36) NOT NULL,
    tenant_id BIGINT NOT NULL,
    case_id CHAR(36) NOT NULL,
    cluster_id CHAR(36) NOT NULL,
    decision_type VARCHAR(20) NOT NULL,
    reason_code VARCHAR(64) NOT NULL,
    decided_by_principal_id VARCHAR(128) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (decision_id),
    UNIQUE KEY uk_risk_decision_tenant_id (tenant_id, decision_id),
    UNIQUE KEY uk_risk_decision_case (tenant_id, case_id),
    UNIQUE KEY uk_risk_decision_case_identity (tenant_id, decision_id, case_id),
    CONSTRAINT fk_risk_decision_review FOREIGN KEY (tenant_id, case_id, cluster_id)
        REFERENCES cloudmold_risk_review_case (tenant_id, case_id, cluster_id),
    CONSTRAINT fk_risk_decision_cluster FOREIGN KEY (tenant_id, cluster_id)
        REFERENCES cloudmold_risk_cluster (tenant_id, cluster_id),
    CONSTRAINT ck_risk_decision_type CHECK (decision_type IN ('DISMISS','MONITOR','ESCALATE','CONFIRM_RISK'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Immutable non-punitive human review decision';

CREATE TABLE cloudmold_risk_feedback (
    feedback_id CHAR(36) NOT NULL,
    tenant_id BIGINT NOT NULL,
    decision_id CHAR(36) NOT NULL,
    case_id CHAR(36) NOT NULL,
    feedback_type VARCHAR(20) NOT NULL,
    reason_code VARCHAR(64) NOT NULL,
    recorded_by_principal_id VARCHAR(128) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (feedback_id),
    UNIQUE KEY uk_risk_feedback_tenant_id (tenant_id, feedback_id),
    UNIQUE KEY uk_risk_feedback_actor (tenant_id, decision_id, recorded_by_principal_id, feedback_type),
    CONSTRAINT fk_risk_feedback_decision FOREIGN KEY (tenant_id, decision_id, case_id)
        REFERENCES cloudmold_risk_decision (tenant_id, decision_id, case_id),
    CONSTRAINT fk_risk_feedback_review FOREIGN KEY (tenant_id, case_id)
        REFERENCES cloudmold_risk_review_case (tenant_id, case_id),
    CONSTRAINT ck_risk_feedback_type CHECK (
        feedback_type IN ('CONFIRMED','CORRECTED','NOT_ACTIONABLE','NEEDS_REVIEW'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Immutable reviewer feedback on a decision';

CREATE TABLE cloudmold_risk_status_history (
    history_id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    aggregate_type VARCHAR(24) NOT NULL,
    aggregate_id VARCHAR(128) NOT NULL,
    aggregate_version BIGINT NOT NULL,
    previous_status VARCHAR(20) NULL,
    current_status VARCHAR(20) NOT NULL,
    operation_id BIGINT NOT NULL,
    reason_code VARCHAR(64) NULL,
    occurred_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (history_id),
    UNIQUE KEY uk_risk_history_version (tenant_id, aggregate_type, aggregate_id, aggregate_version),
    CONSTRAINT fk_risk_history_operation FOREIGN KEY (tenant_id, operation_id)
        REFERENCES cloudmold_risk_operation (tenant_id, operation_id),
    CONSTRAINT ck_risk_history_version CHECK (aggregate_version > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Immutable policy/cluster/review status history';
