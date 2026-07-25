-- Rollback is safe only when the isolated BPM approval adapter is disabled and
-- the workflow binding/event tables contain no evidence that must be retained.
DROP TABLE IF EXISTS cloudmold_agent_approval_workflow_event;
DROP TABLE IF EXISTS cloudmold_agent_approval_workflow_binding;
