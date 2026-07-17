-- Capture the product projection accepted on each legacy Trade Order Item.
-- Existing v1-v4 evidence remains readable but cannot qualify historical product identity.

ALTER TABLE `cloudmold_order_benefit_migration_run`
  ADD COLUMN `product_snapshot_captured_item_count` int DEFAULT NULL
    AFTER `item_evidence_complete`,
  ADD COLUMN `product_snapshot_incomplete_item_count` int DEFAULT NULL
    AFTER `product_snapshot_captured_item_count`,
  ADD COLUMN `product_snapshot_evidence_hash` char(64) DEFAULT NULL
    AFTER `product_snapshot_incomplete_item_count`,
  ADD COLUMN `product_snapshot_evidence_complete` tinyint(1) NOT NULL DEFAULT 0
    AFTER `product_snapshot_evidence_hash`,
  DROP CHECK `ck_cm_order_benefit_mig_run_item_evidence`,
  ADD CONSTRAINT `ck_cm_order_benefit_mig_run_item_evidence` CHECK (
    (`policy_version` IN ('legacy-trade-benefit-v1','legacy-trade-benefit-v2')
      AND `source_item_count` IS NULL AND `active_item_count` IS NULL AND `excluded_item_count` IS NULL
      AND `item_evidence_hash` IS NULL AND `item_evidence_benefit_amount_minor` IS NULL
      AND `item_evidence_complete`=0)
    OR
    (`policy_version` IN ('legacy-trade-benefit-v3','legacy-trade-benefit-v4','legacy-trade-benefit-v5')
      AND `source_item_count`>0 AND `active_item_count`>=0 AND `excluded_item_count`>=0
      AND `source_item_count`=`active_item_count`+`excluded_item_count`
      AND `item_evidence_hash` REGEXP '^[0-9a-f]{64}$'
      AND `item_evidence_benefit_amount_minor` IS NOT NULL
      AND `item_evidence_complete`=1)
  ),
  ADD CONSTRAINT `ck_cm_order_benefit_mig_run_product_snapshot` CHECK (
    (`policy_version` IN ('legacy-trade-benefit-v1','legacy-trade-benefit-v2',
                          'legacy-trade-benefit-v3','legacy-trade-benefit-v4')
      AND `product_snapshot_captured_item_count` IS NULL
      AND `product_snapshot_incomplete_item_count` IS NULL
      AND `product_snapshot_evidence_hash` IS NULL
      AND `product_snapshot_evidence_complete`=0)
    OR
    (`policy_version`='legacy-trade-benefit-v5'
      AND `product_snapshot_captured_item_count`>=0
      AND `product_snapshot_incomplete_item_count`>=0
      AND `product_snapshot_captured_item_count`+`product_snapshot_incomplete_item_count`=`source_item_count`
      AND `product_snapshot_evidence_hash` REGEXP '^[0-9a-f]{64}$'
      AND ((`product_snapshot_evidence_complete`=1
              AND `product_snapshot_captured_item_count`=`source_item_count`
              AND `product_snapshot_incomplete_item_count`=0)
        OR (`product_snapshot_evidence_complete`=0
              AND `product_snapshot_incomplete_item_count`>0)))
  );

ALTER TABLE `cloudmold_order_benefit_migration_item`
  ADD COLUMN `source_created_at` datetime(6) DEFAULT NULL AFTER `legacy_item_snapshot_hash`,
  ADD COLUMN `legacy_spu_name` varchar(255) DEFAULT NULL AFTER `legacy_spu_id`,
  ADD COLUMN `legacy_sku_properties_json` json DEFAULT NULL AFTER `legacy_sku_id`,
  ADD COLUMN `legacy_sku_pic_url` varchar(512) DEFAULT NULL AFTER `legacy_sku_properties_json`,
  ADD COLUMN `historical_product_snapshot_hash` char(64) DEFAULT NULL AFTER `legacy_sku_pic_url`,
  ADD COLUMN `product_snapshot_status` varchar(24) NOT NULL DEFAULT 'NOT_CAPTURED'
    AFTER `historical_product_snapshot_hash`,
  ADD KEY `idx_cm_order_benefit_mig_item_product_snapshot`
    (`tenant_id`,`migration_run_id`,`product_snapshot_status`),
  ADD CONSTRAINT `ck_cm_order_benefit_mig_item_product_snapshot` CHECK (
    (`product_snapshot_status`='NOT_CAPTURED'
      AND `source_created_at` IS NULL AND `legacy_spu_name` IS NULL
      AND `legacy_sku_properties_json` IS NULL AND `legacy_sku_pic_url` IS NULL
      AND `historical_product_snapshot_hash` IS NULL)
    OR
    (`product_snapshot_status`='CAPTURED'
      AND `source_created_at` IS NOT NULL AND `legacy_spu_id`>0 AND `legacy_sku_id`>0
      AND CHAR_LENGTH(TRIM(`legacy_spu_name`))>0 AND `unit_price_minor`>=0
      AND `historical_product_snapshot_hash` REGEXP '^[0-9a-f]{64}$')
    OR
    (`product_snapshot_status`='INCOMPLETE'
      AND `source_created_at` IS NOT NULL AND `historical_product_snapshot_hash` IS NULL)
  );

CREATE OR REPLACE VIEW `cloudmold_order_benefit_migration_component_reconciliation` AS
WITH eligible_item AS (
  SELECT item.* FROM cloudmold_order_benefit_migration_item item
  JOIN cloudmold_order_benefit_migration_run run
    ON run.tenant_id=item.tenant_id AND BINARY run.migration_run_id=BINARY item.migration_run_id
  WHERE run.policy_version IN ('legacy-trade-benefit-v3','legacy-trade-benefit-v4','legacy-trade-benefit-v5')
), item_component_raw AS (
  SELECT tenant_id,migration_run_id,candidate_id,legacy_order_id,item_evidence_id,is_deleted,
         'GENERIC_DISCOUNT' component_type,generic_discount_amount_minor amount_minor FROM eligible_item
  UNION ALL
  SELECT tenant_id,migration_run_id,candidate_id,legacy_order_id,item_evidence_id,is_deleted,
         'COUPON',coupon_amount_minor FROM eligible_item
  UNION ALL
  SELECT tenant_id,migration_run_id,candidate_id,legacy_order_id,item_evidence_id,is_deleted,
         'POINT',point_amount_minor FROM eligible_item
  UNION ALL
  SELECT tenant_id,migration_run_id,candidate_id,legacy_order_id,item_evidence_id,is_deleted,
         'VIP',vip_amount_minor FROM eligible_item
), item_rollup AS (
  SELECT tenant_id,migration_run_id,candidate_id,legacy_order_id,component_type,
         SUM(amount_minor<>0) source_item_component_row_count,
         SUM(amount_minor) source_item_component_amount_minor,
         SUM(is_deleted=0 AND amount_minor<>0) item_component_row_count,
         SUM(CASE WHEN is_deleted=0 THEN amount_minor ELSE 0 END) item_component_amount_minor,
         SUM(is_deleted=1 AND amount_minor<>0) excluded_item_component_row_count,
         SUM(CASE WHEN is_deleted=1 THEN amount_minor ELSE 0 END) excluded_item_component_amount_minor
  FROM item_component_raw
  GROUP BY tenant_id,migration_run_id,candidate_id,legacy_order_id,component_type
  HAVING source_item_component_row_count>0 OR source_item_component_amount_minor<>0
), header_rollup AS (
  SELECT tenant_id,migration_run_id,candidate_id,legacy_order_id,component_type,
         COUNT(*) header_component_count,SUM(component_amount_minor) header_component_amount_minor
  FROM cloudmold_order_benefit_migration_component component
  WHERE EXISTS (SELECT 1 FROM cloudmold_order_benefit_migration_run run
    WHERE run.tenant_id=component.tenant_id
      AND BINARY run.migration_run_id=BINARY component.migration_run_id
      AND run.policy_version IN ('legacy-trade-benefit-v3','legacy-trade-benefit-v4','legacy-trade-benefit-v5'))
  GROUP BY tenant_id,migration_run_id,candidate_id,legacy_order_id,component_type
), component_keys AS (
  SELECT tenant_id,migration_run_id,candidate_id,legacy_order_id,component_type FROM item_rollup
  UNION
  SELECT tenant_id,migration_run_id,candidate_id,legacy_order_id,component_type FROM header_rollup
)
SELECT
  SHA2(CONCAT_WS('|',component_keys.tenant_id,component_keys.migration_run_id,
      component_keys.candidate_id,component_keys.component_type),256) reconciliation_id,
  component_keys.tenant_id,component_keys.migration_run_id,component_keys.candidate_id,
  component_keys.legacy_order_id,candidate.legacy_order_no,component_keys.component_type,
  COALESCE(item_rollup.source_item_component_row_count,0) source_item_component_row_count,
  COALESCE(item_rollup.source_item_component_amount_minor,0) source_item_component_amount_minor,
  COALESCE(item_rollup.item_component_row_count,0) item_component_row_count,
  COALESCE(item_rollup.item_component_amount_minor,0) item_component_amount_minor,
  COALESCE(item_rollup.excluded_item_component_row_count,0) excluded_item_component_row_count,
  COALESCE(item_rollup.excluded_item_component_amount_minor,0) excluded_item_component_amount_minor,
  COALESCE(header_rollup.header_component_count,0) header_component_count,
  COALESCE(header_rollup.header_component_amount_minor,0) header_component_amount_minor,
  COALESCE(item_rollup.item_component_amount_minor,0)
    - COALESCE(header_rollup.header_component_amount_minor,0) amount_gap_minor,
  candidate.assessment_status order_assessment_status,
  CASE
    WHEN candidate.is_deleted=1 THEN 'EXCLUDED_SOURCE_ORDER_DELETED'
    WHEN COALESCE(item_rollup.item_component_row_count,0)=0
      AND COALESCE(item_rollup.excluded_item_component_row_count,0)>0
      AND COALESCE(header_rollup.header_component_count,0)=0 THEN 'EXCLUDED_SOURCE_ITEM_DELETED'
    WHEN COALESCE(item_rollup.item_component_row_count,0)=0 THEN 'MISSING_ITEM_COMPONENT'
    WHEN COALESCE(header_rollup.header_component_count,0)=0 THEN 'MISSING_HEADER_COMPONENT'
    WHEN item_rollup.item_component_amount_minor=header_rollup.header_component_amount_minor THEN 'MATCHED'
    ELSE 'AMOUNT_MISMATCH'
  END reconciliation_status,
  SHA2(CONCAT_WS('|',component_keys.candidate_id,component_keys.component_type,
      COALESCE(item_rollup.source_item_component_row_count,0),
      COALESCE(item_rollup.source_item_component_amount_minor,0),
      COALESCE(item_rollup.item_component_row_count,0),COALESCE(item_rollup.item_component_amount_minor,0),
      COALESCE(item_rollup.excluded_item_component_row_count,0),
      COALESCE(item_rollup.excluded_item_component_amount_minor,0),
      COALESCE(header_rollup.header_component_count,0),
      COALESCE(header_rollup.header_component_amount_minor,0),candidate.assessment_status),256)
    reconciliation_hash,
  0 canonical_import_allowed
FROM component_keys
JOIN cloudmold_order_benefit_migration_candidate candidate
  ON candidate.tenant_id=component_keys.tenant_id
 AND BINARY candidate.migration_run_id=BINARY component_keys.migration_run_id
 AND BINARY candidate.candidate_id=BINARY component_keys.candidate_id
 AND candidate.legacy_order_id=component_keys.legacy_order_id
LEFT JOIN item_rollup
  ON item_rollup.tenant_id=component_keys.tenant_id
 AND BINARY item_rollup.migration_run_id=BINARY component_keys.migration_run_id
 AND BINARY item_rollup.candidate_id=BINARY component_keys.candidate_id
 AND item_rollup.legacy_order_id=component_keys.legacy_order_id
 AND BINARY item_rollup.component_type=BINARY component_keys.component_type
LEFT JOIN header_rollup
  ON header_rollup.tenant_id=component_keys.tenant_id
 AND BINARY header_rollup.migration_run_id=BINARY component_keys.migration_run_id
 AND BINARY header_rollup.candidate_id=BINARY component_keys.candidate_id
 AND header_rollup.legacy_order_id=component_keys.legacy_order_id
 AND BINARY header_rollup.component_type=BINARY component_keys.component_type;
