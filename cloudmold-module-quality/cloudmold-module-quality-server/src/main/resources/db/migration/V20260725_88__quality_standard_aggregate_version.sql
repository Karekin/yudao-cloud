-- Separate quality-standard document versioning from aggregate concurrency/event versioning.
-- Idempotent because the development/demo database can already contain the V86 first slice.

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'cloudmold_quality_standard'
    AND column_name = 'aggregate_version') = 0,
  'ALTER TABLE cloudmold_quality_standard ADD COLUMN aggregate_version BIGINT NOT NULL DEFAULT 1 AFTER current_version',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE cloudmold_quality_standard
SET aggregate_version = GREATEST(current_version + 1, 1)
WHERE aggregate_version < GREATEST(current_version + 1, 1);

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.table_constraints
  WHERE constraint_schema = DATABASE()
    AND table_name = 'cloudmold_quality_standard'
    AND constraint_name = 'ck_quality_standard_aggregate_version') = 0,
  'ALTER TABLE cloudmold_quality_standard ADD CONSTRAINT ck_quality_standard_aggregate_version CHECK (aggregate_version >= 1)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
