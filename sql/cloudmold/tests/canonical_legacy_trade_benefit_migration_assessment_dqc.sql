-- Every result column must be zero. Assessment is immutable evidence and cannot authorize import.

SELECT 'legacy_trade_benefit_operation_incomplete_success' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_order_benefit_migration_operation operation_row
LEFT JOIN cloudmold_order_benefit_migration_run run
  ON run.tenant_id=operation_row.tenant_id
 AND BINARY run.migration_run_id=BINARY operation_row.migration_run_id
WHERE operation_row.status=10
  AND (operation_row.migration_run_id IS NULL OR operation_row.result_json IS NULL
       OR run.migration_run_id IS NULL);

SELECT 'legacy_trade_benefit_run_denominator_mismatch' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_order_benefit_migration_run run
LEFT JOIN (
  SELECT tenant_id,migration_run_id,COUNT(*) source_order_count,
         SUM(is_deleted=0) non_deleted_order_count,SUM(is_deleted=1) deleted_excluded_count,
         SUM(assessment_status='NO_BENEFIT') no_benefit_order_count,
         SUM(assessment_status='BENEFIT_REQUIRES_IDENTITY_AND_FUNDING') benefit_evidence_pending_order_count,
         SUM(assessment_status LIKE 'QUARANTINED_%') quarantined_order_count
  FROM cloudmold_order_benefit_migration_candidate GROUP BY tenant_id,migration_run_id
) candidate ON candidate.tenant_id=run.tenant_id AND BINARY candidate.migration_run_id=BINARY run.migration_run_id
WHERE candidate.migration_run_id IS NULL
   OR run.source_order_count<>candidate.source_order_count
   OR run.non_deleted_order_count<>candidate.non_deleted_order_count
   OR run.deleted_excluded_count<>candidate.deleted_excluded_count
   OR run.no_benefit_order_count<>candidate.no_benefit_order_count
   OR run.benefit_evidence_pending_order_count<>candidate.benefit_evidence_pending_order_count
   OR run.quarantined_order_count<>candidate.quarantined_order_count;

SELECT 'legacy_trade_benefit_run_component_mismatch' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_order_benefit_migration_run run
LEFT JOIN (
  SELECT tenant_id,migration_run_id,COUNT(*) component_count,
         SUM(component_amount_minor) component_amount_minor,
         SUM(identity_resolution_status IN ('MISSING_SOURCE_REFERENCE','AMBIGUOUS_SOURCE_REFERENCE',
             'SOURCE_REFERENCE_WITHOUT_VERSION','MISSING_ENTITLEMENT_VERSION','MISSING_BENEFIT_VERSION')) unresolved_identity_count,
         SUM(funding_resolution_status='MISSING_NAMED_FUNDER_BREAKDOWN') unresolved_funding_count,
         SUM(canonical_import_allowed=1) import_allowed_component_count
  FROM cloudmold_order_benefit_migration_component GROUP BY tenant_id,migration_run_id
) component ON component.tenant_id=run.tenant_id AND BINARY component.migration_run_id=BINARY run.migration_run_id
WHERE run.benefit_component_count<>COALESCE(component.component_count,0)
   OR run.component_amount_minor<>COALESCE(component.component_amount_minor,0)
   OR run.unresolved_identity_count<>COALESCE(component.unresolved_identity_count,0)
   OR run.unresolved_funding_count<>COALESCE(component.unresolved_funding_count,0)
   OR run.import_allowed_component_count<>COALESCE(component.import_allowed_component_count,0);

SELECT 'legacy_trade_benefit_source_amount_not_conserved' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_order_benefit_migration_run run
LEFT JOIN (
  SELECT tenant_id,migration_run_id,
         SUM(CASE WHEN is_deleted=0 THEN header_generic_discount_amount_minor+header_coupon_amount_minor
                  +header_point_amount_minor+header_vip_amount_minor ELSE 0 END) source_benefit_amount_minor
  FROM cloudmold_order_benefit_migration_candidate GROUP BY tenant_id,migration_run_id
) source ON source.tenant_id=run.tenant_id AND BINARY source.migration_run_id=BINARY run.migration_run_id
WHERE run.source_benefit_amount_minor<>COALESCE(source.source_benefit_amount_minor,0)
   OR run.source_benefit_amount_minor<>run.component_amount_minor;

SELECT 'legacy_trade_benefit_candidate_reason_shape' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_order_benefit_migration_candidate
WHERE canonical_import_allowed<>0 OR version<>1 OR legacy_snapshot_hash NOT REGEXP '^[0-9a-f]{64}$'
   OR JSON_TYPE(reason_codes)<>'ARRAY'
   OR (assessment_status='NO_BENEFIT' AND JSON_LENGTH(reason_codes)<>0)
   OR (assessment_status<>'NO_BENEFIT' AND JSON_LENGTH(reason_codes)=0);

SELECT 'legacy_trade_benefit_component_governance_open' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_order_benefit_migration_component
WHERE canonical_import_allowed<>0 OR version<>1
   OR funding_resolution_status<>'MISSING_NAMED_FUNDER_BREAKDOWN'
   OR identity_resolution_status NOT IN ('MISSING_SOURCE_REFERENCE','AMBIGUOUS_SOURCE_REFERENCE',
       'SOURCE_REFERENCE_WITHOUT_VERSION','MISSING_ENTITLEMENT_VERSION','MISSING_BENEFIT_VERSION');

SELECT 'legacy_trade_benefit_run_illegally_enabled' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_order_benefit_migration_run
WHERE status<>'BLOCKED_REQUIRES_GOVERNED_EVIDENCE' OR production_migration_enabled<>0
   OR import_allowed_component_count<>0;

SELECT 'legacy_trade_benefit_candidate_missing_event' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_order_benefit_migration_candidate candidate
LEFT JOIN cloudmold_event_outbox event
  ON event.tenant_id=candidate.tenant_id
 AND event.event_type='order.migration.legacy_trade_benefit_assessed'
 AND event.aggregate_type='legacy_trade_benefit_migration_assessment'
 AND BINARY event.aggregate_id=BINARY candidate.candidate_id
 AND event.aggregate_version=candidate.version
WHERE event.event_id IS NULL;

SELECT 'legacy_trade_benefit_candidate_duplicate_event' AS check_name, COUNT(*) AS violation_count
FROM (
  SELECT tenant_id,aggregate_id,aggregate_version,COUNT(*) event_count
  FROM cloudmold_event_outbox
  WHERE event_type='order.migration.legacy_trade_benefit_assessed'
  GROUP BY tenant_id,aggregate_id,aggregate_version HAVING COUNT(*)<>1
) duplicate_event;

SELECT 'legacy_trade_benefit_event_payload_mismatch' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_order_benefit_migration_candidate candidate
JOIN cloudmold_event_outbox event
  ON event.tenant_id=candidate.tenant_id
 AND event.event_type='order.migration.legacy_trade_benefit_assessed'
 AND BINARY event.aggregate_id=BINARY candidate.candidate_id
WHERE BINARY JSON_UNQUOTE(JSON_EXTRACT(event.payload,'$.migration_run_id'))<>BINARY candidate.migration_run_id
   OR CAST(JSON_UNQUOTE(JSON_EXTRACT(event.payload,'$.legacy_order_id')) AS UNSIGNED)<>candidate.legacy_order_id
   OR BINARY JSON_UNQUOTE(JSON_EXTRACT(event.payload,'$.source_snapshot_hash'))<>BINARY candidate.legacy_snapshot_hash
   OR BINARY JSON_UNQUOTE(JSON_EXTRACT(event.payload,'$.assessment_status'))<>BINARY candidate.assessment_status
   OR JSON_LENGTH(JSON_EXTRACT(event.payload,'$.blocker_codes'))<>JSON_LENGTH(candidate.reason_codes)
   OR NOT JSON_CONTAINS(JSON_EXTRACT(event.payload,'$.blocker_codes'),candidate.reason_codes)
   OR NOT JSON_CONTAINS(candidate.reason_codes,JSON_EXTRACT(event.payload,'$.blocker_codes'))
   OR JSON_EXTRACT(event.payload,'$.canonical_import_allowed')<>CAST('false' AS JSON)
   OR JSON_LENGTH(JSON_EXTRACT(event.payload,'$.components'))<>(
        SELECT COUNT(*) FROM cloudmold_order_benefit_migration_component component
        WHERE component.tenant_id=candidate.tenant_id
          AND BINARY component.migration_run_id=BINARY candidate.migration_run_id
          AND BINARY component.candidate_id=BINARY candidate.candidate_id
      )
   OR EXISTS (
        SELECT 1
        FROM cloudmold_order_benefit_migration_component component
        LEFT JOIN JSON_TABLE(event.payload,'$.components[*]' COLUMNS (
          component_id varchar(36) PATH '$.component_id',
          component_type varchar(24) PATH '$.component_type',
          amount_minor bigint PATH '$.amount_minor',
          source_reference varchar(128) PATH '$.source_reference' NULL ON EMPTY,
          identity_resolution_status varchar(40) PATH '$.identity_resolution_status',
          funding_resolution_status varchar(40) PATH '$.funding_resolution_status',
          canonical_import_allowed tinyint PATH '$.canonical_import_allowed'
        )) event_component
          ON BINARY event_component.component_id=BINARY component.component_id
        WHERE component.tenant_id=candidate.tenant_id
          AND BINARY component.migration_run_id=BINARY candidate.migration_run_id
          AND BINARY component.candidate_id=BINARY candidate.candidate_id
          AND (event_component.component_id IS NULL
            OR BINARY event_component.component_type<>BINARY component.component_type
            OR event_component.amount_minor<>component.component_amount_minor
            OR NOT (event_component.source_reference<=>component.source_reference)
            OR BINARY event_component.identity_resolution_status<>BINARY component.identity_resolution_status
            OR BINARY event_component.funding_resolution_status<>BINARY component.funding_resolution_status
            OR event_component.canonical_import_allowed<>component.canonical_import_allowed)
      );

SELECT 'legacy_trade_benefit_v2_assessed_time_mismatch' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_order_benefit_migration_candidate candidate
JOIN cloudmold_order_benefit_migration_run run
  ON run.tenant_id=candidate.tenant_id
 AND BINARY run.migration_run_id=BINARY candidate.migration_run_id
JOIN cloudmold_event_outbox event
  ON event.tenant_id=candidate.tenant_id
 AND event.event_type='order.migration.legacy_trade_benefit_assessed'
 AND BINARY event.aggregate_id=BINARY candidate.candidate_id
WHERE run.policy_version='legacy-trade-benefit-v2'
  AND CAST(REPLACE(REPLACE(JSON_UNQUOTE(JSON_EXTRACT(event.payload,'$.assessed_at')),'T',' '),'Z','') AS DATETIME(6))
       <>candidate.assessed_at;

SELECT 'legacy_trade_benefit_tenant_lineage_mismatch' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_order_benefit_migration_component component
JOIN cloudmold_order_benefit_migration_candidate candidate
  ON BINARY candidate.candidate_id=BINARY component.candidate_id
JOIN cloudmold_order_benefit_migration_run run
  ON BINARY run.migration_run_id=BINARY component.migration_run_id
WHERE component.tenant_id<>candidate.tenant_id
   OR component.tenant_id<>run.tenant_id
   OR BINARY component.migration_run_id<>BINARY candidate.migration_run_id
   OR component.legacy_order_id<>candidate.legacy_order_id;

SELECT 'legacy_trade_benefit_item_denominator_mismatch' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_order_benefit_migration_run run
LEFT JOIN (
  SELECT item.tenant_id,item.migration_run_id,COUNT(*) source_item_count,
         SUM(item.is_deleted=0 AND candidate.is_deleted=0) active_item_count,
         SUM(item.is_deleted=1 OR candidate.is_deleted=1) excluded_item_count,
         SUM(CASE WHEN item.is_deleted=0 AND candidate.is_deleted=0
             THEN item.generic_discount_amount_minor+item.coupon_amount_minor
                 +item.point_amount_minor+item.vip_amount_minor ELSE 0 END) item_benefit_amount_minor
  FROM cloudmold_order_benefit_migration_item item
  JOIN cloudmold_order_benefit_migration_candidate candidate
    ON candidate.tenant_id=item.tenant_id
   AND BINARY candidate.migration_run_id=BINARY item.migration_run_id
   AND BINARY candidate.candidate_id=BINARY item.candidate_id
  GROUP BY item.tenant_id,item.migration_run_id
) item_rollup ON item_rollup.tenant_id=run.tenant_id
 AND BINARY item_rollup.migration_run_id=BINARY run.migration_run_id
WHERE (run.policy_version IN ('legacy-trade-benefit-v1','legacy-trade-benefit-v2')
       AND run.item_evidence_complete<>0)
   OR (run.policy_version='legacy-trade-benefit-v3' AND (
       run.item_evidence_complete<>1 OR item_rollup.migration_run_id IS NULL
       OR run.source_item_count<>item_rollup.source_item_count
       OR run.active_item_count<>item_rollup.active_item_count
       OR run.excluded_item_count<>item_rollup.excluded_item_count
       OR run.item_evidence_benefit_amount_minor<>item_rollup.item_benefit_amount_minor));

SELECT 'legacy_trade_benefit_item_rollup_mismatch' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_order_benefit_migration_candidate candidate
LEFT JOIN (
  SELECT tenant_id,migration_run_id,candidate_id,COUNT(*) item_row_count,SUM(item_quantity) item_quantity,
         SUM(gross_amount_minor) gross_amount_minor,
         SUM(generic_discount_amount_minor) generic_discount_amount_minor,
         SUM(coupon_amount_minor) coupon_amount_minor,SUM(point_amount_minor) point_amount_minor,
         SUM(vip_amount_minor) vip_amount_minor,SUM(delivery_amount_minor) delivery_amount_minor,
         SUM(adjust_amount_minor) adjust_amount_minor,SUM(pay_amount_minor) pay_amount_minor
  FROM cloudmold_order_benefit_migration_item WHERE is_deleted=0
  GROUP BY tenant_id,migration_run_id,candidate_id
) item_rollup ON item_rollup.tenant_id=candidate.tenant_id
 AND BINARY item_rollup.migration_run_id=BINARY candidate.migration_run_id
 AND BINARY item_rollup.candidate_id=BINARY candidate.candidate_id
JOIN cloudmold_order_benefit_migration_run run
  ON run.tenant_id=candidate.tenant_id AND BINARY run.migration_run_id=BINARY candidate.migration_run_id
WHERE run.policy_version='legacy-trade-benefit-v3'
  AND (candidate.item_row_count<>COALESCE(item_rollup.item_row_count,0)
    OR candidate.item_quantity<>COALESCE(item_rollup.item_quantity,0)
    OR candidate.item_gross_amount_minor<>COALESCE(item_rollup.gross_amount_minor,0)
    OR candidate.item_generic_discount_amount_minor<>COALESCE(item_rollup.generic_discount_amount_minor,0)
    OR candidate.item_coupon_amount_minor<>COALESCE(item_rollup.coupon_amount_minor,0)
    OR candidate.item_point_amount_minor<>COALESCE(item_rollup.point_amount_minor,0)
    OR candidate.item_vip_amount_minor<>COALESCE(item_rollup.vip_amount_minor,0)
    OR candidate.item_delivery_amount_minor<>COALESCE(item_rollup.delivery_amount_minor,0)
    OR candidate.item_adjust_amount_minor<>COALESCE(item_rollup.adjust_amount_minor,0)
    OR candidate.item_pay_amount_minor<>COALESCE(item_rollup.pay_amount_minor,0));

SELECT 'legacy_trade_benefit_item_governance_open' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_order_benefit_migration_item
WHERE canonical_import_allowed<>0 OR version<>1
   OR legacy_item_snapshot_hash NOT REGEXP '^[0-9a-f]{64}$'
   OR source_product_identity_status NOT IN ('SOURCE_IDS_PRESENT','MISSING_SOURCE_IDS')
   OR (source_product_identity_status='SOURCE_IDS_PRESENT'
       AND (legacy_spu_id IS NULL OR legacy_spu_id<=0 OR legacy_sku_id IS NULL OR legacy_sku_id<=0));

SELECT 'legacy_trade_benefit_item_event_mismatch' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_order_benefit_migration_candidate candidate
JOIN cloudmold_order_benefit_migration_run run
  ON run.tenant_id=candidate.tenant_id AND BINARY run.migration_run_id=BINARY candidate.migration_run_id
JOIN cloudmold_event_outbox event
  ON event.tenant_id=candidate.tenant_id
 AND event.event_type='order.migration.legacy_trade_benefit_assessed'
 AND BINARY event.aggregate_id=BINARY candidate.candidate_id
WHERE run.policy_version='legacy-trade-benefit-v3'
  AND (event.schema_version<>2
    OR JSON_EXTRACT(event.payload,'$.item_evidence_complete')<>CAST('true' AS JSON)
    OR JSON_LENGTH(JSON_EXTRACT(event.payload,'$.items'))<>(
      SELECT COUNT(*) FROM cloudmold_order_benefit_migration_item item
      WHERE item.tenant_id=candidate.tenant_id
        AND BINARY item.migration_run_id=BINARY candidate.migration_run_id
        AND BINARY item.candidate_id=BINARY candidate.candidate_id)
    OR EXISTS (
      SELECT 1 FROM cloudmold_order_benefit_migration_item item
      LEFT JOIN JSON_TABLE(event.payload,'$.items[*]' COLUMNS (
        item_evidence_id varchar(36) PATH '$.item_evidence_id',
        legacy_order_item_id bigint PATH '$.legacy_order_item_id',
        legacy_item_snapshot_hash char(64) PATH '$.legacy_item_snapshot_hash',
        canonical_import_allowed tinyint PATH '$.canonical_import_allowed'
      )) event_item ON BINARY event_item.item_evidence_id=BINARY item.item_evidence_id
      WHERE item.tenant_id=candidate.tenant_id
        AND BINARY item.migration_run_id=BINARY candidate.migration_run_id
        AND BINARY item.candidate_id=BINARY candidate.candidate_id
        AND (event_item.item_evidence_id IS NULL
          OR event_item.legacy_order_item_id<>item.legacy_order_item_id
          OR BINARY event_item.legacy_item_snapshot_hash<>BINARY item.legacy_item_snapshot_hash
          OR event_item.canonical_import_allowed<>0)));

SELECT 'legacy_trade_benefit_item_component_reconciliation_shape' AS check_name,
       COUNT(*) AS violation_count
FROM cloudmold_order_benefit_migration_component_reconciliation
WHERE reconciliation_id NOT REGEXP '^[0-9a-f]{64}$'
   OR reconciliation_hash NOT REGEXP '^[0-9a-f]{64}$'
   OR component_type NOT IN ('GENERIC_DISCOUNT','COUPON','POINT','VIP')
   OR source_item_component_row_count<0 OR item_component_row_count<0
   OR excluded_item_component_row_count<0
   OR source_item_component_row_count<>item_component_row_count+excluded_item_component_row_count
   OR source_item_component_amount_minor
        <>item_component_amount_minor+excluded_item_component_amount_minor
   OR header_component_count NOT IN (0,1)
   OR reconciliation_status NOT IN ('MATCHED','MISSING_ITEM_COMPONENT','MISSING_HEADER_COMPONENT',
                                     'AMOUNT_MISMATCH','EXCLUDED_SOURCE_ORDER_DELETED',
                                     'EXCLUDED_SOURCE_ITEM_DELETED')
   OR canonical_import_allowed<>0;

SELECT 'legacy_trade_benefit_item_component_reconciliation_missing' AS check_name,
       COUNT(*) AS violation_count
FROM (
  SELECT expected.tenant_id,expected.migration_run_id,expected.candidate_id,expected.component_type
  FROM (
    SELECT tenant_id,migration_run_id,candidate_id,'GENERIC_DISCOUNT' component_type
    FROM cloudmold_order_benefit_migration_item WHERE generic_discount_amount_minor<>0
    UNION SELECT tenant_id,migration_run_id,candidate_id,'COUPON'
    FROM cloudmold_order_benefit_migration_item WHERE coupon_amount_minor<>0
    UNION SELECT tenant_id,migration_run_id,candidate_id,'POINT'
    FROM cloudmold_order_benefit_migration_item WHERE point_amount_minor<>0
    UNION SELECT tenant_id,migration_run_id,candidate_id,'VIP'
    FROM cloudmold_order_benefit_migration_item WHERE vip_amount_minor<>0
    UNION SELECT tenant_id,migration_run_id,candidate_id,component_type
    FROM cloudmold_order_benefit_migration_component
  ) expected
  JOIN cloudmold_order_benefit_migration_run run
    ON run.tenant_id=expected.tenant_id
   AND BINARY run.migration_run_id=BINARY expected.migration_run_id
   AND run.policy_version='legacy-trade-benefit-v3'
  LEFT JOIN cloudmold_order_benefit_migration_component_reconciliation actual
    ON actual.tenant_id=expected.tenant_id
   AND BINARY actual.migration_run_id=BINARY expected.migration_run_id
   AND BINARY actual.candidate_id=BINARY expected.candidate_id
   AND BINARY actual.component_type=BINARY expected.component_type
  WHERE actual.reconciliation_id IS NULL
) missing;

SELECT 'legacy_trade_benefit_unquarantined_item_component_difference' AS check_name,
       COUNT(*) AS violation_count
FROM cloudmold_order_benefit_migration_component_reconciliation
WHERE reconciliation_status NOT IN ('MATCHED','EXCLUDED_SOURCE_ORDER_DELETED',
                                     'EXCLUDED_SOURCE_ITEM_DELETED')
  AND order_assessment_status NOT LIKE 'QUARANTINED_%';

SELECT 'legacy_trade_benefit_item_component_gap_mismatch' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_order_benefit_migration_run run
LEFT JOIN (
  SELECT tenant_id,migration_run_id,SUM(amount_gap_minor) amount_gap_minor
  FROM cloudmold_order_benefit_migration_component_reconciliation
  WHERE reconciliation_status<>'EXCLUDED_SOURCE_ORDER_DELETED'
  GROUP BY tenant_id,migration_run_id
) gap ON gap.tenant_id=run.tenant_id AND BINARY gap.migration_run_id=BINARY run.migration_run_id
WHERE run.policy_version='legacy-trade-benefit-v3'
  AND COALESCE(gap.amount_gap_minor,0)
      <>run.item_evidence_benefit_amount_minor-run.component_amount_minor;
