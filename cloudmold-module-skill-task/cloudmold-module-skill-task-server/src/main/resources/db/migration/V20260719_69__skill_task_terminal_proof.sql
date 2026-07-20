-- Immutable Skill definition and terminal result proofs used by Agent Control execution bindings.

ALTER TABLE cloudmold_skill_task_instance
  ADD COLUMN definition_sha256 CHAR(64) NULL AFTER input_sha256,
  ADD COLUMN definition_closure_sha256 CHAR(64) NULL AFTER definition_sha256,
  ADD COLUMN terminal_result_sha256 CHAR(64) NULL AFTER definition_closure_sha256,
  ADD KEY idx_skill_task_terminal_proof (tenant_id, task_id, status, terminal_result_sha256);

