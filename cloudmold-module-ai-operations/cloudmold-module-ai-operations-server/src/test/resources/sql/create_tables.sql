CREATE TABLE IF NOT EXISTS cloudmold_ai_ops_application (
  application_id varchar(64) NOT NULL,
  tenant_id bigint NOT NULL,
  application_code varchar(64) NOT NULL,
  name varchar(128) NOT NULL,
  status varchar(24) NOT NULL,
  version bigint NOT NULL,
  created_at timestamp NOT NULL,
  updated_at timestamp NOT NULL,
  PRIMARY KEY (application_id, tenant_id)
);

CREATE TABLE IF NOT EXISTS cloudmold_ai_ops_workflow_definition (
  workflow_id varchar(64) NOT NULL,
  tenant_id bigint NOT NULL,
  application_id varchar(64) NOT NULL,
  workflow_code varchar(64) NOT NULL,
  current_version bigint NOT NULL,
  created_at timestamp NOT NULL,
  updated_at timestamp NOT NULL,
  PRIMARY KEY (workflow_id, tenant_id)
);

CREATE TABLE IF NOT EXISTS cloudmold_ai_ops_workflow_version (
  workflow_version_id varchar(64) NOT NULL,
  tenant_id bigint NOT NULL,
  workflow_id varchar(64) NOT NULL,
  application_id varchar(64) NOT NULL,
  workflow_version bigint NOT NULL,
  definition_ref varchar(256) NOT NULL,
  definition_sha256 char(64) NOT NULL,
  published_at timestamp NOT NULL,
  created_at timestamp NOT NULL,
  PRIMARY KEY (workflow_version_id, tenant_id)
);

CREATE TABLE IF NOT EXISTS cloudmold_ai_ops_workflow_run (
  run_id varchar(64) NOT NULL,
  tenant_id bigint NOT NULL,
  run_key varchar(128) NOT NULL,
  application_id varchar(64) NOT NULL,
  workflow_id varchar(64) NOT NULL,
  workflow_version_id varchar(64) NOT NULL,
  workflow_version bigint NOT NULL,
  trigger_type varchar(32) NOT NULL,
  business_ref varchar(256),
  status varchar(24) NOT NULL,
  expected_invocation_count int NOT NULL,
  started_at timestamp NOT NULL,
  finished_at timestamp,
  error_code varchar(64),
  version bigint NOT NULL,
  created_at timestamp NOT NULL,
  updated_at timestamp NOT NULL,
  PRIMARY KEY (run_id, tenant_id)
);

CREATE TABLE IF NOT EXISTS cloudmold_ai_ops_invocation_attempt (
  attempt_id varchar(64) NOT NULL,
  tenant_id bigint NOT NULL,
  attempt_key varchar(128) NOT NULL,
  run_id varchar(64) NOT NULL,
  application_id varchar(64) NOT NULL,
  workflow_id varchar(64) NOT NULL,
  step_ref varchar(128) NOT NULL,
  attempt_no int NOT NULL,
  provider_code varchar(64) NOT NULL,
  model_code varchar(128) NOT NULL,
  provider_request_ref varchar(256),
  outcome varchar(24) NOT NULL,
  input_tokens bigint NOT NULL,
  cached_input_tokens bigint NOT NULL,
  output_tokens bigint NOT NULL,
  total_tokens bigint NOT NULL,
  latency_millis bigint NOT NULL,
  cost_amount_minor bigint,
  currency_code char(3),
  pricing_version_ref varchar(128),
  error_code varchar(64),
  occurred_at timestamp NOT NULL,
  created_at timestamp NOT NULL,
  PRIMARY KEY (attempt_id, tenant_id)
);

CREATE TABLE IF NOT EXISTS cloudmold_ai_ops_outcome_feedback (
  feedback_id varchar(64) NOT NULL,
  tenant_id bigint NOT NULL,
  feedback_key varchar(128) NOT NULL,
  run_id varchar(64) NOT NULL,
  feedback_type varchar(32) NOT NULL,
  outcome_code varchar(24) NOT NULL,
  evaluator_type varchar(24) NOT NULL,
  evidence_ref varchar(256),
  occurred_at timestamp NOT NULL,
  created_at timestamp NOT NULL,
  PRIMARY KEY (feedback_id, tenant_id)
);

CREATE TABLE IF NOT EXISTS cloudmold_ai_ops_status_history (
  history_id varchar(64) NOT NULL,
  tenant_id bigint NOT NULL,
  aggregate_type varchar(64) NOT NULL,
  aggregate_id varchar(64) NOT NULL,
  aggregate_version bigint NOT NULL,
  operation_id bigint NOT NULL,
  operation_type varchar(64) NOT NULL,
  previous_status varchar(24),
  current_status varchar(24) NOT NULL,
  error_code varchar(64),
  occurred_at timestamp NOT NULL,
  created_at timestamp NOT NULL,
  PRIMARY KEY (history_id, tenant_id)
);
