-- Freeze executable action identity and bind work-order completion to verified Skill Task terminal proof.

ALTER TABLE cloudmold_agent_role_action_policy
  ADD COLUMN execution_required BIT(1) NOT NULL DEFAULT b'0' AFTER enabled,
  ADD COLUMN skill_id VARCHAR(191) NULL AFTER execution_required,
  ADD COLUMN skill_version VARCHAR(64) NULL AFTER skill_id,
  ADD COLUMN skill_definition_closure_sha256 CHAR(64) NULL AFTER skill_version,
  ADD CONSTRAINT ck_agent_policy_execution CHECK (
    (execution_required = b'0' AND skill_id IS NULL AND skill_version IS NULL
      AND skill_definition_closure_sha256 IS NULL) OR
    (execution_required = b'1' AND skill_id IS NOT NULL AND skill_version IS NOT NULL
      AND skill_definition_closure_sha256 REGEXP '^[0-9a-f]{64}$'));

ALTER TABLE cloudmold_agent_work_order
  ADD COLUMN action_policy_id VARCHAR(64) NULL AFTER approval_id,
  ADD COLUMN action_policy_version BIGINT NULL AFTER action_policy_id,
  ADD COLUMN risk_level VARCHAR(8) NULL AFTER action_policy_version,
  ADD COLUMN execution_required BIT(1) NOT NULL DEFAULT b'0' AFTER risk_level,
  ADD COLUMN skill_id VARCHAR(191) NULL AFTER execution_required,
  ADD COLUMN skill_version VARCHAR(64) NULL AFTER skill_id,
  ADD COLUMN skill_definition_closure_sha256 CHAR(64) NULL AFTER skill_version,
  ADD COLUMN execution_input_sha256 CHAR(64) NULL AFTER skill_definition_closure_sha256,
  ADD CONSTRAINT ck_agent_work_order_execution CHECK (
    execution_required = b'0' OR
    (action_policy_id IS NOT NULL AND action_policy_version > 0 AND risk_level IS NOT NULL
      AND skill_id IS NOT NULL AND skill_version IS NOT NULL
      AND skill_definition_closure_sha256 REGEXP '^[0-9a-f]{64}$'
      AND execution_input_sha256 REGEXP '^[0-9a-f]{64}$'));

CREATE TABLE IF NOT EXISTS cloudmold_agent_execution_binding (
  binding_id VARCHAR(64) NOT NULL,
  tenant_id BIGINT NOT NULL,
  work_order_id VARCHAR(64) NOT NULL,
  execution_generation INT NOT NULL,
  skill_task_id VARCHAR(64) NOT NULL,
  skill_id VARCHAR(191) NOT NULL,
  skill_version VARCHAR(64) NOT NULL,
  skill_definition_closure_sha256 CHAR(64) NOT NULL,
  input_sha256 CHAR(64) NOT NULL,
  terminal_result_sha256 CHAR(64) NULL,
  status VARCHAR(32) NOT NULL,
  version BIGINT NOT NULL,
  bound_at DATETIME(6) NOT NULL,
  accepted_at DATETIME(6) NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (binding_id),
  UNIQUE KEY uk_agent_binding_tenant_id (tenant_id,binding_id),
  UNIQUE KEY uk_agent_binding_generation (tenant_id,work_order_id,execution_generation),
  UNIQUE KEY uk_agent_binding_task (tenant_id,skill_task_id),
  CONSTRAINT fk_agent_binding_work_order FOREIGN KEY (tenant_id,work_order_id)
    REFERENCES cloudmold_agent_work_order (tenant_id,work_order_id),
  CONSTRAINT ck_agent_binding_generation CHECK (execution_generation > 0),
  CONSTRAINT ck_agent_binding_status CHECK (status IN ('BOUND','EXECUTION_SUCCEEDED','SUPERSEDED')),
  CONSTRAINT ck_agent_binding_terminal CHECK (
    (status='BOUND' AND terminal_result_sha256 IS NULL AND accepted_at IS NULL) OR
    (status='EXECUTION_SUCCEEDED' AND terminal_result_sha256 REGEXP '^[0-9a-f]{64}$' AND accepted_at IS NOT NULL) OR
    status='SUPERSEDED'),
  CONSTRAINT ck_agent_binding_version CHECK (version > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

