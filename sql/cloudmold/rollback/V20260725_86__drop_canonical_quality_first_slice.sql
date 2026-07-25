-- Destructive manual rollback for V20260725_86.
-- Export the affected tenant rows and stop command traffic before executing.

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS cloudmold_quality_capa;
DROP TABLE IF EXISTS cloudmold_inspection_task_history;
DROP TABLE IF EXISTS cloudmold_inspection_task;
DROP TABLE IF EXISTS cloudmold_authenticator_certification;
DROP TABLE IF EXISTS cloudmold_quality_standard_version;
DROP TABLE IF EXISTS cloudmold_quality_standard;
DROP TABLE IF EXISTS cloudmold_quality_operation;
SET FOREIGN_KEY_CHECKS = 1;
