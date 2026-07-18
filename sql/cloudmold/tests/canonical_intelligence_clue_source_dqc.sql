-- Every query must return zero. This gate distinguishes semantic source versions from physical DF/RI deliveries.

SELECT 'clue_source_business_revision_duplicate' AS check_name, COUNT(*) AS violations
FROM (
    SELECT tenant_id, source_system, source_biz_id, business_revision
    FROM cloudmold_intelligence_clue_source_version
    GROUP BY tenant_id, source_system, source_biz_id, business_revision
    HAVING COUNT(*) <> 1
) x;

SELECT 'clue_source_revision_or_supersedes_invalid' AS check_name, COUNT(*) AS violations
FROM cloudmold_intelligence_clue_source_version current_version
LEFT JOIN cloudmold_intelligence_clue_source_version previous_version
  ON previous_version.tenant_id = current_version.tenant_id
 AND previous_version.source_version_id = current_version.supersedes_source_version_id
WHERE (current_version.business_revision = 1 AND current_version.supersedes_source_version_id IS NOT NULL)
   OR (current_version.business_revision > 1 AND (
       previous_version.source_version_id IS NULL
       OR previous_version.source_system <> current_version.source_system
       OR previous_version.source_biz_id <> current_version.source_biz_id
       OR previous_version.business_revision + 1 <> current_version.business_revision
       OR previous_version.source_deleted = 1));

SELECT 'clue_source_deleted_or_time_invalid' AS check_name, COUNT(*) AS violations
FROM cloudmold_intelligence_clue_source_version
WHERE (source_deleted = 1 AND source_valid = 1)
   OR source_published_at > source_observed_at
   OR clue_info_item_count NOT BETWEEN 0 AND 10000;

SELECT 'clue_source_hash_invalid' AS check_name, COUNT(*) AS violations
FROM cloudmold_intelligence_clue_source_version
WHERE title_sha256 NOT REGEXP '^[0-9a-f]{64}$'
   OR summary_sha256 NOT REGEXP '^[0-9a-f]{64}$'
   OR clue_info_sha256 NOT REGEXP '^[0-9a-f]{64}$'
   OR semantic_payload_sha256 NOT REGEXP '^[0-9a-f]{64}$';

SELECT 'clue_source_post_delete_version' AS check_name, COUNT(*) AS violations
FROM cloudmold_intelligence_clue_source_version deleted_version
JOIN cloudmold_intelligence_clue_source_version later_version
  ON later_version.tenant_id = deleted_version.tenant_id
 AND later_version.source_system = deleted_version.source_system
 AND later_version.source_biz_id = deleted_version.source_biz_id
 AND later_version.business_revision > deleted_version.business_revision
WHERE deleted_version.source_deleted = 1;

SELECT 'clue_source_delivery_identity_duplicate' AS check_name, COUNT(*) AS violations
FROM (
    SELECT tenant_id, source_dataset_id, source_dataset_version, source_record_key, source_record_version
    FROM cloudmold_intelligence_clue_source_delivery
    GROUP BY tenant_id, source_dataset_id, source_dataset_version, source_record_key, source_record_version
    HAVING COUNT(*) <> 1
) x;

SELECT 'clue_source_delivery_time_invalid' AS check_name, COUNT(*) AS violations
FROM cloudmold_intelligence_clue_source_delivery delivery
JOIN cloudmold_intelligence_clue_source_version source_version
  ON source_version.tenant_id = delivery.tenant_id
 AND source_version.source_version_id = delivery.source_version_id
WHERE delivery.source_observed_at < source_version.source_observed_at;

SELECT 'clue_source_delivery_metadata_mismatch' AS check_name, COUNT(*) AS violations
FROM cloudmold_intelligence_clue_source_delivery delivery
LEFT JOIN cloudmold_metadata_dataset_version dataset
  ON dataset.tenant_id = delivery.tenant_id
 AND dataset.definition_id = delivery.source_dataset_id
 AND dataset.definition_version = delivery.source_dataset_version
WHERE dataset.definition_id IS NULL
   OR dataset.qualified_name <> delivery.physical_source_asset
   OR dataset.schema_sha256 <> delivery.source_schema_sha256;

SELECT 'yshopping_clue_source_mapping_invalid' AS check_name, COUNT(*) AS violations
FROM cloudmold_intelligence_clue_source_delivery delivery
JOIN cloudmold_intelligence_clue_source_version source_version
  ON source_version.tenant_id = delivery.tenant_id
 AND source_version.source_version_id = delivery.source_version_id
JOIN cloudmold_metadata_dataset_version dataset
  ON dataset.tenant_id = delivery.tenant_id
 AND dataset.definition_id = delivery.source_dataset_id
 AND dataset.definition_version = delivery.source_dataset_version
WHERE source_version.source_system = 'YSHOPPING'
  AND NOT (
      (delivery.source_transport = 'SNAPSHOT_DF'
       AND delivery.declared_source_asset = 'ods_intelligence_clue_df'
       AND delivery.physical_source_asset = 'ods_intelligence_clues_df'
       AND dataset.dataset_type = 'TABLE')
      OR
      (delivery.source_transport = 'KAFKA_RI'
       AND delivery.declared_source_asset = 'ods_intelligence_clue_ri'
       AND delivery.physical_source_asset = 'ods_intelligence_clue_ri'
       AND dataset.dataset_type = 'STREAM')
  );

SELECT 'yshopping_clue_snapshot_schema_invalid' AS check_name, COUNT(*) AS violations
FROM cloudmold_intelligence_clue_source_delivery delivery
JOIN cloudmold_intelligence_clue_source_version source_version
  ON source_version.tenant_id = delivery.tenant_id
 AND source_version.source_version_id = delivery.source_version_id
WHERE source_version.source_system = 'YSHOPPING'
  AND delivery.source_transport = 'SNAPSHOT_DF'
  AND (
      (SELECT COUNT(*) FROM cloudmold_metadata_field_version field_version
       WHERE field_version.tenant_id = delivery.tenant_id
         AND field_version.dataset_id = delivery.source_dataset_id
         AND field_version.dataset_version = delivery.source_dataset_version) <> 13
      OR
      (SELECT COUNT(*) FROM cloudmold_metadata_field_version field_version
       WHERE field_version.tenant_id = delivery.tenant_id
         AND field_version.dataset_id = delivery.source_dataset_id
         AND field_version.dataset_version = delivery.source_dataset_version
         AND field_version.field_code IN
             ('id','create_time','modify_time','biz_id','title','summary','data_source','intelligence_type',
              'is_valid','publish_time','clues_info','is_del','pt')) <> 13
  );

SELECT 'yshopping_clue_stream_schema_invalid' AS check_name, COUNT(*) AS violations
FROM cloudmold_intelligence_clue_source_delivery delivery
JOIN cloudmold_intelligence_clue_source_version source_version
  ON source_version.tenant_id = delivery.tenant_id
 AND source_version.source_version_id = delivery.source_version_id
WHERE source_version.source_system = 'YSHOPPING'
  AND delivery.source_transport = 'KAFKA_RI'
  AND (
      (SELECT COUNT(*) FROM cloudmold_metadata_field_version field_version
       WHERE field_version.tenant_id = delivery.tenant_id
         AND field_version.dataset_id = delivery.source_dataset_id
         AND field_version.dataset_version = delivery.source_dataset_version) <> 1
      OR
      (SELECT COUNT(*) FROM cloudmold_metadata_field_version field_version
       WHERE field_version.tenant_id = delivery.tenant_id
         AND field_version.dataset_id = delivery.source_dataset_id
         AND field_version.dataset_version = delivery.source_dataset_version
         AND field_version.field_code = 'message') <> 1
  );

SELECT 'valid_clue_source_missing_delivery' AS check_name, COUNT(*) AS violations
FROM (
    SELECT source_version.tenant_id, source_version.source_version_id
    FROM cloudmold_intelligence_clue_source_version source_version
    LEFT JOIN cloudmold_intelligence_clue_source_delivery delivery
      ON delivery.tenant_id = source_version.tenant_id
     AND delivery.source_version_id = source_version.source_version_id
    WHERE source_version.source_valid = 1 AND source_version.source_deleted = 0
    GROUP BY source_version.tenant_id, source_version.source_version_id
    HAVING COUNT(delivery.delivery_id) = 0
) missing_delivery;

SELECT 'canonical_clue_source_reference_invalid' AS check_name, COUNT(*) AS violations
FROM (
    SELECT clue.tenant_id, clue.clue_id
    FROM cloudmold_intelligence_clue clue
    LEFT JOIN cloudmold_intelligence_clue_source_version source_version
      ON source_version.tenant_id = clue.tenant_id
     AND source_version.source_version_id = clue.source_version_id
    LEFT JOIN cloudmold_intelligence_clue_source_delivery delivery
      ON delivery.tenant_id = source_version.tenant_id
     AND delivery.source_version_id = source_version.source_version_id
    WHERE clue.source_version_id IS NOT NULL
    GROUP BY clue.tenant_id, clue.clue_id, source_version.source_version_id,
             source_version.source_valid, source_version.source_deleted
    HAVING source_version.source_version_id IS NULL
        OR source_version.source_valid <> 1
        OR source_version.source_deleted <> 0
        OR COUNT(delivery.delivery_id) = 0
) invalid_reference;

SELECT 'canonical_clue_source_version_reused' AS check_name, COUNT(*) AS violations
FROM (
    SELECT tenant_id, source_version_id
    FROM cloudmold_intelligence_clue
    WHERE source_version_id IS NOT NULL
    GROUP BY tenant_id, source_version_id
    HAVING COUNT(*) <> 1
) x;

SELECT 'new_clue_without_source_version' AS check_name, COUNT(*) AS violations
FROM cloudmold_intelligence_clue clue
WHERE clue.source_version_id IS NULL
  AND clue.created_at >= COALESCE(
      (SELECT MIN(created_at) FROM cloudmold_intelligence_clue_source_version WHERE tenant_id = clue.tenant_id),
      '9999-12-31 23:59:59.999999');
