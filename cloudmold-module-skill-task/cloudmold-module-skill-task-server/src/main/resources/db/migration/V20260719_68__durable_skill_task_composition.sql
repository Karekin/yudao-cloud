-- Durable parent/child orchestration for R3 Skill Tasks.

ALTER TABLE cloudmold_skill_task_instance
  ADD COLUMN approval_scope_skill_id VARCHAR(191) NULL AFTER approval_ref,
  ADD COLUMN approval_scope_skill_version VARCHAR(64) NULL AFTER approval_scope_skill_id,
  ADD COLUMN approval_scope_input_sha256 CHAR(64) NULL AFTER approval_scope_skill_version,
  ADD COLUMN approval_scope_risk_level VARCHAR(2) NULL AFTER approval_scope_input_sha256,
  ADD COLUMN parent_task_id VARCHAR(64) NULL AFTER approval_scope_risk_level,
  ADD COLUMN parent_step_code VARCHAR(128) NULL AFTER parent_task_id,
  ADD KEY idx_skill_task_parent (tenant_id, parent_task_id),
  ADD CONSTRAINT fk_skill_task_parent FOREIGN KEY (tenant_id, parent_task_id)
    REFERENCES cloudmold_skill_task_instance (tenant_id, task_id),
  DROP CHECK chk_skill_task_status,
  ADD CONSTRAINT chk_skill_task_status
    CHECK (status IN ('QUEUED','RUNNING','WAITING','SUCCEEDED','NEEDS_REVIEW'));

ALTER TABLE cloudmold_skill_task_step
  ADD COLUMN step_kind VARCHAR(32) NOT NULL DEFAULT 'CAPABILITY' AFTER step_order,
  ADD COLUMN child_skill_id VARCHAR(191) NULL AFTER argument_template_json,
  ADD COLUMN child_skill_version VARCHAR(64) NULL AFTER child_skill_id,
  ADD COLUMN child_run_id_template VARCHAR(255) NULL AFTER child_skill_version,
  ADD COLUMN child_task_id VARCHAR(64) NULL AFTER child_run_id_template,
  ADD COLUMN poll_interval_seconds INT NULL AFTER child_task_id,
  ADD COLUMN wait_success_json MEDIUMTEXT NULL AFTER poll_interval_seconds,
  ADD COLUMN wait_failure_json MEDIUMTEXT NULL AFTER wait_success_json,
  MODIFY COLUMN capability_id VARCHAR(255) NULL,
  MODIFY COLUMN operation_type VARCHAR(16) NOT NULL,
  DROP CHECK chk_skill_task_step_operation,
  DROP CHECK chk_skill_task_step_status,
  ADD CONSTRAINT chk_skill_task_step_kind
    CHECK (step_kind IN ('CAPABILITY','SUBMIT_CHILD','WAIT_CHILD','WAIT_CAPABILITY')),
  ADD CONSTRAINT chk_skill_task_step_operation
    CHECK (operation_type IN ('READ','WRITE','ORCHESTRATE')),
  ADD CONSTRAINT chk_skill_task_step_status
    CHECK (status IN ('PENDING','RUNNING','WAITING','SUCCEEDED','FAILED_RETRYABLE','NEEDS_REVIEW'));
