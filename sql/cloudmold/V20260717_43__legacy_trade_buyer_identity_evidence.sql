-- Preserve source buyer and order-lifecycle identity before any canonical Order mapping is admitted.
-- Existing v1-v3 assessment rows remain immutable and readable; only v4 carries this evidence.

ALTER TABLE `cloudmold_order_benefit_migration_run`
  DROP CHECK `ck_cm_order_benefit_mig_run_item_evidence`,
  ADD CONSTRAINT `ck_cm_order_benefit_mig_run_item_evidence` CHECK (
    (`policy_version` IN ('legacy-trade-benefit-v1','legacy-trade-benefit-v2')
      AND `source_item_count` IS NULL AND `active_item_count` IS NULL AND `excluded_item_count` IS NULL
      AND `item_evidence_hash` IS NULL AND `item_evidence_benefit_amount_minor` IS NULL
      AND `item_evidence_complete`=0)
    OR
    (`policy_version` IN ('legacy-trade-benefit-v3','legacy-trade-benefit-v4')
      AND `source_item_count`>0 AND `active_item_count`>=0 AND `excluded_item_count`>=0
      AND `source_item_count`=`active_item_count`+`excluded_item_count`
      AND `item_evidence_hash` REGEXP '^[0-9a-f]{64}$'
      AND `item_evidence_benefit_amount_minor` IS NOT NULL
      AND `item_evidence_complete`=1)
  );

ALTER TABLE `cloudmold_order_benefit_migration_candidate`
  ADD COLUMN `source_created_at` datetime(6) DEFAULT NULL AFTER `legacy_snapshot_hash`,
  ADD COLUMN `legacy_buyer_id` bigint DEFAULT NULL AFTER `source_created_at`,
  ADD COLUMN `legacy_order_status` int DEFAULT NULL AFTER `legacy_buyer_id`,
  ADD COLUMN `buyer_source_identity_id` varchar(36) DEFAULT NULL AFTER `legacy_order_status`,
  ADD COLUMN `buyer_principal_id` varchar(36) DEFAULT NULL AFTER `buyer_source_identity_id`,
  ADD COLUMN `buyer_identity_version` bigint DEFAULT NULL AFTER `buyer_principal_id`,
  ADD COLUMN `buyer_identity_status` varchar(24) DEFAULT NULL AFTER `buyer_identity_version`,
  ADD KEY `idx_cm_order_benefit_mig_candidate_buyer`
    (`tenant_id`,`buyer_principal_id`,`buyer_identity_status`),
  ADD CONSTRAINT `fk_cm_order_benefit_mig_candidate_buyer_source` FOREIGN KEY
    (`tenant_id`,`buyer_source_identity_id`)
    REFERENCES `cloudmold_identity_source_identity` (`tenant_id`,`source_identity_id`),
  ADD CONSTRAINT `fk_cm_order_benefit_mig_candidate_buyer_principal` FOREIGN KEY
    (`tenant_id`,`buyer_principal_id`)
    REFERENCES `cloudmold_identity_principal` (`tenant_id`,`principal_id`),
  ADD CONSTRAINT `ck_cm_order_benefit_mig_candidate_buyer_status` CHECK (
    `buyer_identity_status` IS NULL OR `buyer_identity_status` IN ('RESOLVED','MISSING','AMBIGUOUS')
  ),
  ADD CONSTRAINT `ck_cm_order_benefit_mig_candidate_buyer_shape` CHECK (
    `buyer_identity_status` IS NULL
    OR (`legacy_buyer_id`>0 AND `source_created_at` IS NOT NULL AND `legacy_order_status` IS NOT NULL
      AND ((`buyer_identity_status`='RESOLVED' AND `buyer_source_identity_id` IS NOT NULL
            AND `buyer_principal_id` IS NOT NULL AND `buyer_identity_version`>0)
        OR (`buyer_identity_status` IN ('MISSING','AMBIGUOUS') AND `buyer_source_identity_id` IS NULL
            AND `buyer_principal_id` IS NULL AND `buyer_identity_version` IS NULL)))
  );

ALTER TABLE `cloudmold_order_benefit_migration_item`
  ADD COLUMN `legacy_buyer_id` bigint DEFAULT NULL AFTER `legacy_order_item_id`,
  ADD CONSTRAINT `ck_cm_order_benefit_mig_item_buyer`
    CHECK (`legacy_buyer_id` IS NULL OR `legacy_buyer_id`>0);

CREATE OR REPLACE VIEW `cloudmold_order_benefit_migration_component_reconciliation` AS
WITH eligible_item AS (
  SELECT item.* FROM cloudmold_order_benefit_migration_item item
  JOIN cloudmold_order_benefit_migration_run run
    ON run.tenant_id=item.tenant_id AND BINARY run.migration_run_id=BINARY item.migration_run_id
  WHERE run.policy_version IN ('legacy-trade-benefit-v3','legacy-trade-benefit-v4')
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
      AND run.policy_version IN ('legacy-trade-benefit-v3','legacy-trade-benefit-v4'))
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
