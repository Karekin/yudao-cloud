ALTER TABLE cloudmold_ai_ops_workflow_registry_pointer
    DROP FOREIGN KEY fk_ai_ops_wfrp_stable,
    DROP FOREIGN KEY fk_ai_ops_wfrp_candidate;
ALTER TABLE cloudmold_ai_ops_workflow_registry_version
    ADD UNIQUE KEY uk_ai_ops_wfrv_tenant_version (tenant_id, registry_version_id),
    ADD UNIQUE KEY uk_ai_ops_wfrv_tenant_semantic (tenant_id, skill_id, skill_semantic_version);
ALTER TABLE cloudmold_ai_ops_workflow_registry_pointer
    ADD CONSTRAINT fk_ai_ops_wfrp_stable_tenant FOREIGN KEY (tenant_id, stable_version_id)
        REFERENCES cloudmold_ai_ops_workflow_registry_version (tenant_id, registry_version_id),
    ADD CONSTRAINT fk_ai_ops_wfrp_candidate_tenant FOREIGN KEY (tenant_id, candidate_version_id)
        REFERENCES cloudmold_ai_ops_workflow_registry_version (tenant_id, registry_version_id),
    ADD COLUMN kill_switch_enabled TINYINT(1) NOT NULL DEFAULT 0 AFTER pointer_version;

CREATE TABLE cloudmold_ai_ops_workflow_registry_validation_request (
    validation_request_id VARCHAR(64) NOT NULL,
    tenant_id BIGINT NOT NULL,
    skill_id VARCHAR(191) NOT NULL,
    registry_version_id VARCHAR(64) NOT NULL,
    pointer_version BIGINT NOT NULL,
    challenge VARCHAR(64) NOT NULL,
    request_status VARCHAR(16) NOT NULL,
    requested_by_subject VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(191) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    consumed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (validation_request_id),
    UNIQUE KEY uk_ai_ops_wfrq_tenant_request (tenant_id, validation_request_id),
    UNIQUE KEY uk_ai_ops_wfrq_tenant_idempotency (tenant_id, idempotency_key),
    KEY idx_ai_ops_wfrq_tenant_skill (tenant_id, skill_id, created_at),
    CONSTRAINT fk_ai_ops_wfrq_registry_version_tenant FOREIGN KEY (tenant_id, registry_version_id)
        REFERENCES cloudmold_ai_ops_workflow_registry_version (tenant_id, registry_version_id),
    CONSTRAINT ck_ai_ops_wfrq_status CHECK (request_status IN ('REQUESTED', 'COMPLETED', 'EXPIRED')),
    CONSTRAINT ck_ai_ops_wfrq_pointer CHECK (pointer_version >= 0)
);

CREATE TABLE cloudmold_ai_ops_workflow_registry_evaluation (
    evaluation_id VARCHAR(64) NOT NULL,
    tenant_id BIGINT NOT NULL,
    skill_id VARCHAR(191) NOT NULL,
    registry_version_id VARCHAR(64) NOT NULL,
    validation_request_id VARCHAR(64) NOT NULL,
    stable_version_id VARCHAR(64) NULL,
    pointer_version BIGINT NOT NULL,
    risk_level VARCHAR(8) NOT NULL,
    evaluation_status VARCHAR(24) NOT NULL,
    proposed_by_subject VARCHAR(64) NOT NULL,
    evaluator_subject VARCHAR(64) NOT NULL,
    evaluator_run_id VARCHAR(191) NOT NULL,
    dataset_sha256 CHAR(64) NOT NULL,
    sample_count INT NOT NULL,
    observation_started_at DATETIME(6) NOT NULL,
    observation_ended_at DATETIME(6) NOT NULL,
    replay_passed TINYINT(1) NOT NULL,
    shadow_passed TINYINT(1) NOT NULL,
    canary_passed TINYINT(1) NOT NULL,
    guardrails_passed TINYINT(1) NOT NULL,
    sample_passed TINYINT(1) NOT NULL,
    dqc_passed TINYINT(1) NOT NULL,
    observation_window_passed TINYINT(1) NOT NULL,
    evidence_refs_json MEDIUMTEXT NOT NULL,
    metrics_json MEDIUMTEXT NOT NULL,
    failure_samples_json MEDIUMTEXT NOT NULL,
    validator_attestation_json MEDIUMTEXT NOT NULL,
    idempotency_key VARCHAR(191) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (evaluation_id),
    UNIQUE KEY uk_ai_ops_wfre_tenant_evaluation (tenant_id, evaluation_id),
    UNIQUE KEY uk_ai_ops_wfre_tenant_idempotency (tenant_id, idempotency_key),
    KEY idx_ai_ops_wfre_tenant_skill (tenant_id, skill_id, created_at),
    CONSTRAINT fk_ai_ops_wfre_registry_version_tenant FOREIGN KEY (tenant_id, registry_version_id)
        REFERENCES cloudmold_ai_ops_workflow_registry_version (tenant_id, registry_version_id),
    CONSTRAINT fk_ai_ops_wfre_validation_request_tenant FOREIGN KEY (tenant_id, validation_request_id)
        REFERENCES cloudmold_ai_ops_workflow_registry_validation_request (tenant_id, validation_request_id),
    CONSTRAINT fk_ai_ops_wfre_stable_version_tenant FOREIGN KEY (tenant_id, stable_version_id)
        REFERENCES cloudmold_ai_ops_workflow_registry_version (tenant_id, registry_version_id),
    CONSTRAINT ck_ai_ops_wfre_risk CHECK (risk_level IN ('E0', 'E1', 'E2', 'E3')),
    CONSTRAINT ck_ai_ops_wfre_sample CHECK (sample_count > 0),
    CONSTRAINT ck_ai_ops_wfre_status CHECK (evaluation_status IN
        ('VALIDATING', 'VALIDATED', 'SHADOW', 'CANDIDATE', 'CANARY', 'ACTIVE', 'REJECTED', 'ROLLED_BACK', 'RETIRED'))
);

CREATE TABLE cloudmold_ai_ops_workflow_registry_approval (
    approval_id VARCHAR(64) NOT NULL,
    tenant_id BIGINT NOT NULL,
    skill_id VARCHAR(191) NOT NULL,
    registry_version_id VARCHAR(64) NOT NULL,
    evaluation_id VARCHAR(64) NOT NULL,
    risk_level VARCHAR(8) NOT NULL,
    approval_decision VARCHAR(16) NOT NULL,
    proposed_by_subject VARCHAR(64) NOT NULL,
    evaluator_subject VARCHAR(64) NOT NULL,
    approver_subject VARCHAR(64) NOT NULL,
    rationale VARCHAR(512) NULL,
    evidence_refs_json MEDIUMTEXT NOT NULL,
    metrics_json MEDIUMTEXT NOT NULL,
    idempotency_key VARCHAR(191) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (approval_id),
    UNIQUE KEY uk_ai_ops_wfra_tenant_idempotency (tenant_id, idempotency_key),
    KEY idx_ai_ops_wfra_tenant_skill (tenant_id, skill_id, created_at),
    CONSTRAINT fk_ai_ops_wfra_registry_version_tenant FOREIGN KEY (tenant_id, registry_version_id)
        REFERENCES cloudmold_ai_ops_workflow_registry_version (tenant_id, registry_version_id),
    CONSTRAINT fk_ai_ops_wfra_evaluation_tenant FOREIGN KEY (tenant_id, evaluation_id)
        REFERENCES cloudmold_ai_ops_workflow_registry_evaluation (tenant_id, evaluation_id),
    CONSTRAINT ck_ai_ops_wfra_risk CHECK (risk_level IN ('E2', 'E3')),
    CONSTRAINT ck_ai_ops_wfra_decision CHECK (approval_decision IN ('APPROVE', 'REJECT'))
);

CREATE TABLE cloudmold_ai_ops_workflow_registry_release (
    release_id VARCHAR(64) NOT NULL,
    tenant_id BIGINT NOT NULL,
    skill_id VARCHAR(191) NOT NULL,
    registry_version_id VARCHAR(64) NOT NULL,
    previous_stable_version_id VARCHAR(64) NULL,
    target_status VARCHAR(24) NOT NULL,
    release_reason VARCHAR(32) NOT NULL,
    actor_subject VARCHAR(64) NOT NULL,
    approval_required TINYINT(1) NOT NULL,
    kill_switch_armed TINYINT(1) NOT NULL,
    source_idempotency_key VARCHAR(191) NOT NULL,
    evidence_refs_json MEDIUMTEXT NOT NULL,
    metrics_json MEDIUMTEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (release_id),
    KEY idx_ai_ops_wfrr_tenant_skill (tenant_id, skill_id, created_at),
    KEY idx_ai_ops_wfrr_source_key (tenant_id, source_idempotency_key, release_reason),
    CONSTRAINT fk_ai_ops_wfrr_registry_version_tenant FOREIGN KEY (tenant_id, registry_version_id)
        REFERENCES cloudmold_ai_ops_workflow_registry_version (tenant_id, registry_version_id),
    CONSTRAINT fk_ai_ops_wfrr_previous_stable_tenant FOREIGN KEY (tenant_id, previous_stable_version_id)
        REFERENCES cloudmold_ai_ops_workflow_registry_version (tenant_id, registry_version_id),
    CONSTRAINT ck_ai_ops_wfrr_status CHECK (target_status IN
        ('VALIDATING', 'VALIDATED', 'SHADOW', 'CANDIDATE', 'CANARY', 'ACTIVE', 'BLOCKED', 'REJECTED', 'ROLLED_BACK', 'RETIRED')),
    CONSTRAINT ck_ai_ops_wfrr_reason CHECK (release_reason IN
        ('VALIDATION_REQUESTED', 'EVALUATION_RECORDED', 'EVALUATION_VALIDATED', 'SHADOW_PASSED', 'CANDIDATE_READY', 'CANARY_PASSED',
         'AUTO_PROMOTED', 'APPROVAL_APPROVED', 'APPROVAL_REJECTED', 'EVALUATION_FAILED', 'CANARY_FAILED',
         'ACTIVE_GUARDRAIL_FAILED', 'ROLLBACK_RESTORED', 'KILL_SWITCH', 'KILL_SWITCH_ARMED', 'KILL_SWITCH_DISARMED'))
);
