-- Idempotent extension of the V01 anti-corruption mapping. V01 is immutable.

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()
  AND table_name = 'cloudmold_catalog_source_mapping' AND column_name = 'source_business_key') = 0,
  'ALTER TABLE cloudmold_catalog_source_mapping ADD COLUMN source_business_key varchar(128) DEFAULT NULL AFTER source_id',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()
  AND table_name = 'cloudmold_catalog_source_mapping' AND column_name = 'source_version') = 0,
  'ALTER TABLE cloudmold_catalog_source_mapping ADD COLUMN source_version varchar(128) DEFAULT NULL AFTER projection_version',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()
  AND table_name = 'cloudmold_catalog_source_mapping' AND column_name = 'projection_hash') = 0,
  'ALTER TABLE cloudmold_catalog_source_mapping ADD COLUMN projection_hash char(64) DEFAULT NULL AFTER source_version',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()
  AND table_name = 'cloudmold_catalog_source_mapping' AND column_name = 'last_attempt_at') = 0,
  'ALTER TABLE cloudmold_catalog_source_mapping ADD COLUMN last_attempt_at datetime(6) DEFAULT NULL AFTER last_synced_at',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()
  AND table_name = 'cloudmold_catalog_source_mapping' AND column_name = 'last_error_code') = 0,
  'ALTER TABLE cloudmold_catalog_source_mapping ADD COLUMN last_error_code varchar(64) DEFAULT NULL AFTER last_attempt_at',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()
  AND table_name = 'cloudmold_catalog_source_mapping' AND column_name = 'last_error_summary') = 0,
  'ALTER TABLE cloudmold_catalog_source_mapping ADD COLUMN last_error_summary varchar(512) DEFAULT NULL AFTER last_error_code',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
