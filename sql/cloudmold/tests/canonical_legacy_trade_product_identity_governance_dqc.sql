-- Every result column must be zero. Product current state and majority pairs are observations only.

SELECT 'legacy_trade_product_identity_operation_incomplete_success' AS check_name, COUNT(*) violation_count
FROM cloudmold_order_product_identity_operation operation_row
LEFT JOIN cloudmold_order_product_identity_run run
  ON run.tenant_id=operation_row.tenant_id AND BINARY run.identity_run_id=BINARY operation_row.identity_run_id
WHERE operation_row.status=10
  AND (operation_row.identity_run_id IS NULL OR operation_row.result_json IS NULL OR run.identity_run_id IS NULL);

SELECT 'legacy_trade_product_identity_run_denominator_mismatch' AS check_name, COUNT(*) violation_count
FROM cloudmold_order_product_identity_run run
JOIN cloudmold_order_benefit_migration_run source_run
  ON source_run.tenant_id=run.tenant_id
 AND BINARY source_run.migration_run_id=BINARY run.source_migration_run_id
LEFT JOIN (
  SELECT tenant_id,identity_run_id,COUNT(*) source_item_count,
         SUM(source_pair_status<>'EXCLUDED') active_item_count,
         SUM(source_pair_status='EXCLUDED') excluded_item_count,
         SUM(source_pair_status='SOURCE_PAIR_UNAMBIGUOUS_NOT_HISTORICAL_VERSION') unambiguous_count,
         SUM(source_pair_status='SOURCE_SKU_PARENT_CONFLICT') conflict_count,
         SUM(current_reference_status='CURRENT_RELATION_OBSERVED_NOT_HISTORICAL_VERSION') current_count,
         SUM(historical_identity_status='QUALIFIED') qualified_count,
         SUM(identity_admission_allowed=1) admitted_count
  FROM cloudmold_order_product_identity_item GROUP BY tenant_id,identity_run_id
) item ON item.tenant_id=run.tenant_id AND BINARY item.identity_run_id=BINARY run.identity_run_id
WHERE source_run.policy_version NOT IN ('legacy-trade-benefit-v4','legacy-trade-benefit-v5')
   OR source_run.item_evidence_complete<>1
   OR (source_run.policy_version='legacy-trade-benefit-v5'
       AND source_run.product_snapshot_evidence_complete<>1)
   OR item.identity_run_id IS NULL OR run.source_item_count<>source_run.source_item_count
   OR run.source_item_count<>item.source_item_count OR run.active_item_count<>item.active_item_count
   OR run.excluded_item_count<>item.excluded_item_count
   OR run.source_pair_unambiguous_count<>item.unambiguous_count
   OR run.source_parent_conflict_item_count<>item.conflict_count
   OR run.current_relation_observed_count<>item.current_count
   OR run.historical_identity_qualified_count<>item.qualified_count
   OR run.identity_admitted_item_count<>item.admitted_count;

SELECT 'legacy_trade_product_identity_source_lineage_mismatch' AS check_name, COUNT(*) violation_count
FROM cloudmold_order_product_identity_item governed
LEFT JOIN cloudmold_order_benefit_migration_item source_item
  ON source_item.tenant_id=governed.tenant_id
 AND BINARY source_item.migration_run_id=BINARY governed.source_migration_run_id
 AND BINARY source_item.item_evidence_id=BINARY governed.item_evidence_id
WHERE source_item.item_evidence_id IS NULL
   OR BINARY source_item.candidate_id<>BINARY governed.candidate_id
   OR source_item.legacy_order_id<>governed.legacy_order_id
   OR source_item.legacy_order_item_id<>governed.legacy_order_item_id
   OR NOT (source_item.legacy_spu_id<=>governed.legacy_spu_id)
   OR NOT (source_item.legacy_sku_id<=>governed.legacy_sku_id)
   OR BINARY source_item.legacy_item_snapshot_hash<>BINARY governed.source_item_evidence_hash;

SELECT 'legacy_trade_product_identity_parent_cardinality_mismatch' AS check_name, COUNT(*) violation_count
FROM cloudmold_order_product_identity_item governed
WHERE governed.source_pair_status<>'EXCLUDED'
  AND governed.source_parent_cardinality<>(
    SELECT COUNT(DISTINCT source_item.legacy_spu_id)
    FROM cloudmold_order_benefit_migration_item source_item
    JOIN cloudmold_order_benefit_migration_candidate candidate
      ON candidate.tenant_id=source_item.tenant_id
     AND BINARY candidate.migration_run_id=BINARY source_item.migration_run_id
     AND BINARY candidate.candidate_id=BINARY source_item.candidate_id
    WHERE source_item.tenant_id=governed.tenant_id
      AND BINARY source_item.migration_run_id=BINARY governed.source_migration_run_id
      AND source_item.legacy_sku_id=governed.legacy_sku_id
      AND source_item.is_deleted=0 AND candidate.is_deleted=0);

SELECT 'legacy_trade_product_identity_current_observation_shape' AS check_name, COUNT(*) violation_count
FROM cloudmold_order_product_identity_item
WHERE (current_reference_status='CURRENT_RELATION_OBSERVED_NOT_HISTORICAL_VERSION'
       AND current_product_snapshot_hash NOT REGEXP '^[0-9a-f]{64}$')
   OR (current_reference_status<>'CURRENT_RELATION_OBSERVED_NOT_HISTORICAL_VERSION'
       AND current_product_snapshot_hash IS NOT NULL)
   OR (current_reference_status='CURRENT_RELATION_OBSERVED_NOT_HISTORICAL_VERSION'
       AND historical_identity_status='QUALIFIED' AND qualification_id IS NULL);

SELECT 'legacy_trade_product_identity_qualification_mismatch' AS check_name, COUNT(*) violation_count
FROM cloudmold_order_product_identity_item governed
LEFT JOIN cloudmold_order_product_identity_qualification qualification
  ON qualification.tenant_id=governed.tenant_id
 AND BINARY qualification.qualification_id=BINARY governed.qualification_id
WHERE (governed.historical_identity_status='QUALIFIED' AND (
       qualification.qualification_id IS NULL OR qualification.status<>'QUALIFIED'
       OR BINARY qualification.source_migration_run_id<>BINARY governed.source_migration_run_id
       OR BINARY qualification.item_evidence_id<>BINARY governed.item_evidence_id
       OR qualification.legacy_order_item_id<>governed.legacy_order_item_id
       OR qualification.historical_spu_id<>governed.legacy_spu_id
       OR qualification.historical_sku_id<>governed.legacy_sku_id
       OR BINARY qualification.source_item_evidence_hash<>BINARY governed.source_item_evidence_hash))
   OR (governed.historical_identity_status<>'QUALIFIED' AND governed.qualification_id IS NOT NULL);

SELECT 'legacy_trade_product_identity_illegally_enabled' AS check_name, COUNT(*) violation_count
FROM cloudmold_order_product_identity_run run
WHERE (run.target_mapping_enabled=1 AND (
       run.status<>'READY_FOR_TARGET_MAPPING_ASSESSMENT'
       OR run.identity_admitted_item_count<>run.active_item_count))
   OR (run.target_mapping_enabled=0 AND run.status<>'BLOCKED_REQUIRES_HISTORICAL_PRODUCT_IDENTITY')
   OR EXISTS (
       SELECT 1 FROM cloudmold_order_product_identity_item item
       WHERE item.tenant_id=run.tenant_id AND BINARY item.identity_run_id=BINARY run.identity_run_id
         AND item.target_mapping_allowed<>item.identity_admission_allowed);

SELECT 'legacy_trade_product_identity_event_mismatch' AS check_name, COUNT(*) violation_count
FROM cloudmold_order_product_identity_item governed
LEFT JOIN cloudmold_event_outbox event
  ON event.tenant_id=governed.tenant_id
 AND event.event_type='order.migration.legacy_trade_product_identity_assessed'
 AND event.aggregate_type='legacy_trade_product_identity'
 AND BINARY event.aggregate_id=BINARY governed.identity_item_id
 AND event.aggregate_version=governed.version
WHERE event.event_id IS NULL OR event.schema_version<>1
   OR BINARY JSON_UNQUOTE(JSON_EXTRACT(event.payload,'$.identity_run_id'))<>BINARY governed.identity_run_id
   OR BINARY JSON_UNQUOTE(JSON_EXTRACT(event.payload,'$.item_evidence_id'))<>BINARY governed.item_evidence_id
   OR CAST(JSON_UNQUOTE(JSON_EXTRACT(event.payload,'$.legacy_order_item_id')) AS UNSIGNED)
        <>governed.legacy_order_item_id
   OR BINARY JSON_UNQUOTE(JSON_EXTRACT(event.payload,'$.historical_identity_status'))
        <>BINARY governed.historical_identity_status
   OR JSON_EXTRACT(event.payload,'$.identity_admission_allowed')
        <>CAST(IF(governed.identity_admission_allowed=1,'true','false') AS JSON)
   OR JSON_EXTRACT(event.payload,'$.target_mapping_allowed')
        <>CAST(IF(governed.target_mapping_allowed=1,'true','false') AS JSON)
   OR BINARY JSON_UNQUOTE(JSON_EXTRACT(event.payload,'$.evidence_hash'))<>BINARY governed.evidence_hash;

SELECT 'legacy_trade_product_identity_duplicate_event' AS check_name, COUNT(*) violation_count
FROM (
  SELECT tenant_id,aggregate_id,aggregate_version,COUNT(*) event_count
  FROM cloudmold_event_outbox
  WHERE event_type='order.migration.legacy_trade_product_identity_assessed'
  GROUP BY tenant_id,aggregate_id,aggregate_version HAVING COUNT(*)<>1
) duplicate_event;
