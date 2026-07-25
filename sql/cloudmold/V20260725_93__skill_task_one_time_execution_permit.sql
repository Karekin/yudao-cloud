-- Persist one-time, claims-bound Skill Task execution permit consumption.

ALTER TABLE cloudmold_skill_task_instance
  MODIFY COLUMN approval_ref VARCHAR(2048) NULL;

CREATE TABLE IF NOT EXISTS cloudmold_skill_task_permit_consumption (
  tenant_id BIGINT NOT NULL,
  permit_id VARCHAR(191) NOT NULL,
  approval_id VARCHAR(191) NOT NULL,
  work_order_id VARCHAR(191) NOT NULL,
  root_request_identity CHAR(64) NOT NULL,
  client_request_key VARCHAR(191) NOT NULL,
  task_id VARCHAR(64) NOT NULL,
  approval_ref_sha256 CHAR(64) NOT NULL,
  definition_closure_sha256 CHAR(64) NOT NULL,
  input_sha256 CHAR(64) NOT NULL,
  risk_level VARCHAR(2) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (tenant_id, permit_id),
  UNIQUE KEY uk_skill_task_permit_task (tenant_id, task_id),
  CONSTRAINT fk_skill_task_permit_task FOREIGN KEY (tenant_id, task_id)
    REFERENCES cloudmold_skill_task_instance (tenant_id, task_id),
  CONSTRAINT chk_skill_task_permit_risk CHECK (risk_level IN ('R2','R3')),
  CONSTRAINT chk_skill_task_permit_root_identity CHECK (
    root_request_identity REGEXP '^[0-9a-f]{64}$'
  ),
  CONSTRAINT chk_skill_task_permit_ref_sha CHECK (
    approval_ref_sha256 REGEXP '^[0-9a-f]{64}$'
  ),
  CONSTRAINT chk_skill_task_permit_closure_sha CHECK (
    definition_closure_sha256 REGEXP '^[0-9a-f]{64}$'
  ),
  CONSTRAINT chk_skill_task_permit_input_sha CHECK (
    input_sha256 REGEXP '^[0-9a-f]{64}$'
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
