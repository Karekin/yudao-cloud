-- Durable Mission Runtime for long-running, cross-role Agent operations.

CREATE TABLE IF NOT EXISTS cloudmold_agent_business_mission (
  mission_id VARCHAR(64) NOT NULL, tenant_id BIGINT NOT NULL, mission_type VARCHAR(128) NOT NULL,
  template_version VARCHAR(64) NOT NULL, title VARCHAR(256) NOT NULL, objective_json JSON NOT NULL,
  correlation_id VARCHAR(256) NOT NULL, status VARCHAR(32) NOT NULL, supervisor_user_id BIGINT NOT NULL,
  version BIGINT NOT NULL, started_at DATETIME(6) NOT NULL, deadline_at DATETIME(6) NOT NULL,
  completed_at DATETIME(6) NULL, updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (mission_id), UNIQUE KEY uk_agent_mission_tenant_id (tenant_id,mission_id),
  UNIQUE KEY uk_agent_mission_correlation (tenant_id,correlation_id,mission_type),
  CONSTRAINT ck_agent_mission_status CHECK (status IN ('DRAFT','ACTIVE','PAUSED','COMPLETED','CANCELLED','NEEDS_REVIEW')),
  CONSTRAINT ck_agent_mission_completion CHECK ((status='COMPLETED' AND completed_at IS NOT NULL) OR status<>'COMPLETED')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_agent_mission_goal (
  goal_id VARCHAR(64) NOT NULL, tenant_id BIGINT NOT NULL, mission_id VARCHAR(64) NOT NULL,
  goal_code VARCHAR(128) NOT NULL, title VARCHAR(256) NOT NULL, status VARCHAR(32) NOT NULL,
  version BIGINT NOT NULL, created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (goal_id), UNIQUE KEY uk_agent_goal_tenant_id (tenant_id,goal_id),
  UNIQUE KEY uk_agent_goal_code (tenant_id,mission_id,goal_code),
  CONSTRAINT fk_agent_goal_mission FOREIGN KEY (tenant_id,mission_id)
    REFERENCES cloudmold_agent_business_mission (tenant_id,mission_id),
  CONSTRAINT ck_agent_goal_status CHECK (status IN ('PLANNED','ACTIVE','ACHIEVED','FAILED','WAIVED','CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE cloudmold_agent_work_order
  DROP CHECK ck_agent_work_order_status,
  ADD COLUMN mission_id VARCHAR(64) NULL AFTER execution_input_sha256,
  ADD COLUMN goal_id VARCHAR(64) NULL AFTER mission_id,
  ADD COLUMN parent_work_order_id VARCHAR(64) NULL AFTER goal_id,
  ADD COLUMN deadline_at DATETIME(6) NULL AFTER parent_work_order_id,
  ADD COLUMN ready_at DATETIME(6) NULL AFTER deadline_at,
  ADD COLUMN waiting_reason_code VARCHAR(64) NULL AFTER ready_at,
  ADD COLUMN active_run_id VARCHAR(64) NULL AFTER waiting_reason_code,
  ADD KEY idx_agent_mission_work_queue (tenant_id,mission_id,status,ready_at),
  ADD CONSTRAINT fk_agent_work_mission FOREIGN KEY (tenant_id,mission_id)
    REFERENCES cloudmold_agent_business_mission (tenant_id,mission_id),
  ADD CONSTRAINT fk_agent_work_goal FOREIGN KEY (tenant_id,goal_id)
    REFERENCES cloudmold_agent_mission_goal (tenant_id,goal_id),
  ADD CONSTRAINT fk_agent_work_parent FOREIGN KEY (tenant_id,parent_work_order_id)
    REFERENCES cloudmold_agent_work_order (tenant_id,work_order_id),
  ADD CONSTRAINT ck_agent_work_order_status CHECK (status IN (
    'WAITING_DEPENDENCY','READY','IN_PROGRESS','WAITING_EVENT','WAITING_TIMER','WAITING_APPROVAL',
    'WAITING_HANDOFF','BLOCKED','NEEDS_REVIEW','COMPLETED','CANCELLED'));

ALTER TABLE cloudmold_agent_role_handoff
  ADD COLUMN target_work_order_id VARCHAR(64) NULL AFTER work_order_id,
  ADD KEY idx_agent_handoff_target (tenant_id,target_work_order_id),
  ADD CONSTRAINT fk_agent_handoff_target FOREIGN KEY (tenant_id,target_work_order_id)
    REFERENCES cloudmold_agent_work_order (tenant_id,work_order_id);

CREATE TABLE IF NOT EXISTS cloudmold_agent_work_dependency (
  dependency_id VARCHAR(64) NOT NULL, tenant_id BIGINT NOT NULL, mission_id VARCHAR(64) NOT NULL,
  predecessor_work_order_id VARCHAR(64) NOT NULL, successor_work_order_id VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL, satisfied_by_result_id VARCHAR(64) NULL, version BIGINT NOT NULL,
  satisfied_at DATETIME(6) NULL, created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (dependency_id), UNIQUE KEY uk_agent_dependency_edge (tenant_id,predecessor_work_order_id,successor_work_order_id),
  KEY idx_agent_dependency_successor (tenant_id,successor_work_order_id,status),
  CONSTRAINT fk_agent_dependency_mission FOREIGN KEY (tenant_id,mission_id)
    REFERENCES cloudmold_agent_business_mission (tenant_id,mission_id),
  CONSTRAINT fk_agent_dependency_predecessor FOREIGN KEY (tenant_id,predecessor_work_order_id)
    REFERENCES cloudmold_agent_work_order (tenant_id,work_order_id),
  CONSTRAINT fk_agent_dependency_successor FOREIGN KEY (tenant_id,successor_work_order_id)
    REFERENCES cloudmold_agent_work_order (tenant_id,work_order_id),
  CONSTRAINT ck_agent_dependency_no_self CHECK (predecessor_work_order_id<>successor_work_order_id),
  CONSTRAINT ck_agent_dependency_status CHECK (status IN ('WAITING','SATISFIED','UNSATISFIABLE','CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_agent_event_subscription (
  subscription_id VARCHAR(64) NOT NULL, tenant_id BIGINT NOT NULL, mission_id VARCHAR(64) NOT NULL,
  work_order_id VARCHAR(64) NOT NULL, event_type VARCHAR(128) NOT NULL, schema_version VARCHAR(64) NOT NULL,
  source_system VARCHAR(128) NOT NULL, aggregate_type VARCHAR(128) NOT NULL, aggregate_id VARCHAR(191) NOT NULL,
  correlation_id VARCHAR(256) NULL, matcher_code VARCHAR(64) NOT NULL, status VARCHAR(32) NOT NULL,
  matched_event_id VARCHAR(191) NULL, matched_payload_sha256 CHAR(64) NULL, matched_at DATETIME(6) NULL,
  version BIGINT NOT NULL, created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (subscription_id), UNIQUE KEY uk_agent_subscription_tenant_id (tenant_id,subscription_id),
  KEY idx_agent_subscription_match (tenant_id,status,event_type,source_system,aggregate_type,aggregate_id),
  CONSTRAINT fk_agent_subscription_mission FOREIGN KEY (tenant_id,mission_id)
    REFERENCES cloudmold_agent_business_mission (tenant_id,mission_id),
  CONSTRAINT fk_agent_subscription_work FOREIGN KEY (tenant_id,work_order_id)
    REFERENCES cloudmold_agent_work_order (tenant_id,work_order_id),
  CONSTRAINT ck_agent_subscription_status CHECK (status IN ('ACTIVE','MATCHED','EXPIRED','CANCELLED')),
  CONSTRAINT ck_agent_subscription_matcher CHECK (matcher_code='EXACT_AGGREGATE')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_agent_mission_timer (
  timer_id VARCHAR(64) NOT NULL, tenant_id BIGINT NOT NULL, mission_id VARCHAR(64) NOT NULL,
  work_order_id VARCHAR(64) NOT NULL, timer_type VARCHAR(32) NOT NULL, due_at DATETIME(6) NOT NULL,
  generation INT NOT NULL, status VARCHAR(32) NOT NULL, fired_at DATETIME(6) NULL, version BIGINT NOT NULL,
  created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (timer_id), UNIQUE KEY uk_agent_timer_tenant_id (tenant_id,timer_id),
  KEY idx_agent_timer_due (status,due_at),
  CONSTRAINT fk_agent_timer_mission FOREIGN KEY (tenant_id,mission_id)
    REFERENCES cloudmold_agent_business_mission (tenant_id,mission_id),
  CONSTRAINT fk_agent_timer_work FOREIGN KEY (tenant_id,work_order_id)
    REFERENCES cloudmold_agent_work_order (tenant_id,work_order_id),
  CONSTRAINT ck_agent_timer_status CHECK (status IN ('SCHEDULED','FIRED','CANCELLED','SUPERSEDED')),
  CONSTRAINT ck_agent_timer_generation CHECK (generation>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_agent_run_lease (
  tenant_id BIGINT NOT NULL, work_order_id VARCHAR(64) NOT NULL, mission_id VARCHAR(64) NOT NULL,
  run_id VARCHAR(64) NOT NULL, trigger_type VARCHAR(32) NOT NULL, trigger_id VARCHAR(191) NULL,
  actor_user_id BIGINT NOT NULL, role_code VARCHAR(64) NOT NULL, lease_owner VARCHAR(191) NOT NULL,
  lease_token VARCHAR(64) NOT NULL, fencing_token BIGINT NOT NULL, lease_until DATETIME(6) NOT NULL,
  status VARCHAR(16) NOT NULL, version BIGINT NOT NULL, started_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (tenant_id,work_order_id), UNIQUE KEY uk_agent_run_id (tenant_id,run_id),
  CONSTRAINT fk_agent_run_work FOREIGN KEY (tenant_id,work_order_id)
    REFERENCES cloudmold_agent_work_order (tenant_id,work_order_id),
  CONSTRAINT ck_agent_run_status CHECK (status IN ('ACTIVE','RELEASED','COMPLETED','EXPIRED')),
  CONSTRAINT ck_agent_run_fencing CHECK (fencing_token>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_agent_run (
  run_id VARCHAR(64) NOT NULL, tenant_id BIGINT NOT NULL, mission_id VARCHAR(64) NOT NULL,
  work_order_id VARCHAR(64) NOT NULL, trigger_type VARCHAR(32) NOT NULL, trigger_id VARCHAR(191) NULL,
  actor_user_id BIGINT NOT NULL, role_code VARCHAR(64) NOT NULL, fencing_token BIGINT NOT NULL,
  status VARCHAR(16) NOT NULL, started_at DATETIME(6) NOT NULL, completed_at DATETIME(6) NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (run_id), UNIQUE KEY uk_agent_run_tenant_id (tenant_id,run_id),
  KEY idx_agent_run_work (tenant_id,work_order_id,started_at),
  CONSTRAINT fk_agent_run_history_work FOREIGN KEY (tenant_id,work_order_id)
    REFERENCES cloudmold_agent_work_order (tenant_id,work_order_id),
  CONSTRAINT ck_agent_run_history_status CHECK (status IN ('ACTIVE','WAITING','COMPLETED','NEEDS_REVIEW','EXPIRED')),
  CONSTRAINT ck_agent_run_history_fencing CHECK (fencing_token>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_agent_mission_checkpoint (
  checkpoint_id VARCHAR(64) NOT NULL, tenant_id BIGINT NOT NULL, mission_id VARCHAR(64) NOT NULL,
  work_order_id VARCHAR(64) NOT NULL, run_id VARCHAR(64) NOT NULL, fencing_token BIGINT NOT NULL,
  decision_code VARCHAR(128) NOT NULL, decision_json JSON NOT NULL, decision_sha256 CHAR(64) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (checkpoint_id), UNIQUE KEY uk_agent_checkpoint_tenant_id (tenant_id,checkpoint_id),
  KEY idx_agent_checkpoint_run (tenant_id,run_id,created_at),
  CONSTRAINT fk_agent_checkpoint_run FOREIGN KEY (tenant_id,run_id)
    REFERENCES cloudmold_agent_run (tenant_id,run_id),
  CONSTRAINT ck_agent_checkpoint_fencing CHECK (fencing_token>0),
  CONSTRAINT ck_agent_checkpoint_hash CHECK (decision_sha256 REGEXP '^[0-9a-f]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_agent_event_inbox (
  tenant_id BIGINT NOT NULL, event_id VARCHAR(191) NOT NULL, event_type VARCHAR(128) NOT NULL,
  payload_sha256 CHAR(64) NOT NULL, received_at DATETIME(6) NOT NULL,
  PRIMARY KEY (tenant_id,event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_agent_outbox (
  event_id VARCHAR(64) NOT NULL, tenant_id BIGINT NOT NULL, aggregate_type VARCHAR(64) NOT NULL,
  aggregate_id VARCHAR(64) NOT NULL, event_type VARCHAR(128) NOT NULL, payload_json JSON NOT NULL,
  status VARCHAR(16) NOT NULL, created_at DATETIME(6) NOT NULL, published_at DATETIME(6) NULL,
  PRIMARY KEY (event_id), KEY idx_agent_outbox_publish (status,created_at),
  CONSTRAINT ck_agent_outbox_status CHECK (status IN ('NEW','PUBLISHED','FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
