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
