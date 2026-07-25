-- Recoverable only before applications depend on the separated aggregate version.

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.table_constraints
  WHERE constraint_schema = DATABASE()
    AND table_name = 'cloudmold_quality_standard'
    AND constraint_name = 'ck_quality_standard_aggregate_version') = 1,
  'ALTER TABLE cloudmold_quality_standard DROP CHECK ck_quality_standard_aggregate_version',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'cloudmold_quality_standard'
    AND column_name = 'aggregate_version') = 1,
  'ALTER TABLE cloudmold_quality_standard DROP COLUMN aggregate_version',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
