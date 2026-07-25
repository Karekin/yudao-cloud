-- Roll back one-time Skill Task execution permit persistence.
-- The narrowing ALTER intentionally runs first and fails closed if long cma3
-- references still exist, preserving the consumption ledger for investigation.

ALTER TABLE cloudmold_skill_task_instance
  MODIFY COLUMN approval_ref VARCHAR(191) NULL;

DROP TABLE IF EXISTS cloudmold_skill_task_permit_consumption;
