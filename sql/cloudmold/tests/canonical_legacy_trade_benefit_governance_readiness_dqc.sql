-- Every result column must be zero. Current promotion rows are observations only;
-- historical identity, named funding, and quarantine decisions require exact immutable evidence.

SELECT 'legacy_trade_benefit_governance_operation_incomplete_success' AS check_name,
       COUNT(*) AS violation_count
FROM cloudmold_order_benefit_governance_operation operation_row
LEFT JOIN cloudmold_order_benefit_governance_run run
  ON run.tenant_id=operation_row.tenant_id
 AND BINARY run.governance_run_id=BINARY operation_row.governance_run_id
WHERE operation_row.status=10
  AND (operation_row.governance_run_id IS NULL OR operation_row.result_json IS NULL
    OR run.governance_run_id IS NULL);

SELECT 'legacy_trade_benefit_governance_source_denominator_mismatch' AS check_name,
       COUNT(*) AS violation_count
FROM cloudmold_order_benefit_governance_run run
JOIN cloudmold_order_benefit_migration_run source_run
  ON source_run.tenant_id=run.tenant_id
 AND BINARY source_run.migration_run_id=BINARY run.source_migration_run_id
WHERE source_run.policy_version<>'legacy-trade-benefit-v4'
   OR source_run.item_evidence_complete<>1
   OR run.source_component_count<>source_run.benefit_component_count
   OR run.source_quarantine_count<>source_run.quarantined_order_count;

SELECT 'legacy_trade_benefit_governance_run_component_rollup_mismatch' AS check_name,
       COUNT(*) AS violation_count
FROM cloudmold_order_benefit_governance_run run
LEFT JOIN (
  SELECT tenant_id,governance_run_id,COUNT(*) component_count,
         SUM(source_reference IS NOT NULL) source_reference_present_count,
         SUM(current_reference_status='CURRENT_REFERENCE_OBSERVED_NOT_HISTORICAL_VERSION')
           current_reference_observed_count,
         SUM(historical_identity_status='QUALIFIED') historical_identity_qualified_count,
         SUM(historical_identity_status<>'QUALIFIED') identity_blocked_count,
         SUM(funding_resolution_status='QUALIFIED') funding_qualified_count,
         SUM(funding_resolution_status<>'QUALIFIED') funding_blocked_count,
         SUM(governance_admission_allowed=1) admitted_count
  FROM cloudmold_order_benefit_governance_component
  GROUP BY tenant_id,governance_run_id
) component ON component.tenant_id=run.tenant_id
 AND BINARY component.governance_run_id=BINARY run.governance_run_id
WHERE component.governance_run_id IS NULL
   OR run.source_component_count<>component.component_count
   OR run.source_reference_present_count<>component.source_reference_present_count
   OR run.current_reference_observed_count<>component.current_reference_observed_count
   OR run.historical_identity_qualified_count<>component.historical_identity_qualified_count
   OR run.identity_blocked_count<>component.identity_blocked_count
   OR run.funding_qualified_count<>component.funding_qualified_count
   OR run.funding_blocked_count<>component.funding_blocked_count
   OR run.governance_admitted_component_count<>component.admitted_count;

SELECT 'legacy_trade_benefit_governance_run_quarantine_rollup_mismatch' AS check_name,
       COUNT(*) AS violation_count
FROM cloudmold_order_benefit_governance_run run
LEFT JOIN (
  SELECT tenant_id,governance_run_id,COUNT(*) quarantine_count,
         SUM(decision_status='DECIDED') decided_count,
         SUM(decision_status='OPEN') open_count
  FROM cloudmold_order_benefit_governance_quarantine
  GROUP BY tenant_id,governance_run_id
) quarantine ON quarantine.tenant_id=run.tenant_id
 AND BINARY quarantine.governance_run_id=BINARY run.governance_run_id
WHERE run.source_quarantine_count<>COALESCE(quarantine.quarantine_count,0)
   OR run.quarantine_decided_count<>COALESCE(quarantine.decided_count,0)
   OR run.quarantine_open_count<>COALESCE(quarantine.open_count,0);

SELECT 'legacy_trade_benefit_governance_component_source_lineage_mismatch' AS check_name,
       COUNT(*) AS violation_count
FROM cloudmold_order_benefit_governance_component governed
LEFT JOIN cloudmold_order_benefit_migration_component source_component
  ON source_component.tenant_id=governed.tenant_id
 AND BINARY source_component.migration_run_id=BINARY governed.source_migration_run_id
 AND BINARY source_component.component_id=BINARY governed.component_id
WHERE source_component.component_id IS NULL
   OR BINARY source_component.candidate_id<>BINARY governed.candidate_id
   OR source_component.legacy_order_id<>governed.legacy_order_id
   OR BINARY source_component.component_type<>BINARY governed.component_type
   OR source_component.component_amount_minor<>governed.component_amount_minor
   OR NOT (BINARY source_component.source_reference<=>BINARY governed.source_reference)
   OR governed.source_component_evidence_hash NOT REGEXP '^[0-9a-f]{64}$';

SELECT 'legacy_trade_benefit_governance_current_observation_shape' AS check_name,
       COUNT(*) AS violation_count
FROM cloudmold_order_benefit_governance_component
WHERE (current_reference_status='CURRENT_REFERENCE_OBSERVED_NOT_HISTORICAL_VERSION'
       AND (source_reference IS NULL OR observed_source_table IS NULL OR observed_source_id IS NULL
         OR observed_source_created_at IS NULL OR observed_source_updated_at IS NULL
         OR observed_source_deleted IS NULL
         OR current_reference_snapshot_hash NOT REGEXP '^[0-9a-f]{64}$'
         OR historical_identity_status='QUALIFIED' AND identity_qualification_id IS NULL))
   OR (current_reference_status<>'CURRENT_REFERENCE_OBSERVED_NOT_HISTORICAL_VERSION'
       AND (observed_source_table IS NOT NULL OR observed_source_id IS NOT NULL
         OR current_reference_snapshot_hash IS NOT NULL));

SELECT 'legacy_trade_benefit_governance_identity_qualification_mismatch' AS check_name,
       COUNT(*) AS violation_count
FROM cloudmold_order_benefit_governance_component governed
LEFT JOIN cloudmold_order_benefit_identity_qualification qualification
  ON qualification.tenant_id=governed.tenant_id
 AND BINARY qualification.qualification_id=BINARY governed.identity_qualification_id
WHERE (governed.historical_identity_status='QUALIFIED' AND (
       qualification.qualification_id IS NULL OR qualification.status<>'QUALIFIED'
       OR BINARY qualification.source_migration_run_id<>BINARY governed.source_migration_run_id
       OR BINARY qualification.component_id<>BINARY governed.component_id
       OR BINARY qualification.historical_benefit_type<>BINARY governed.component_type
       OR BINARY qualification.source_component_evidence_hash
            <>BINARY governed.source_component_evidence_hash))
   OR (governed.historical_identity_status<>'QUALIFIED'
       AND governed.identity_qualification_id IS NOT NULL);

SELECT 'legacy_trade_benefit_governance_funding_qualification_mismatch' AS check_name,
       COUNT(*) AS violation_count
FROM cloudmold_order_benefit_governance_component governed
LEFT JOIN (
  SELECT tenant_id,source_migration_run_id,component_id,COUNT(*) share_count,
         SUM(amount_minor) amount_minor,
         COUNT(DISTINCT source_component_evidence_hash) evidence_hash_count,
         MAX(source_component_evidence_hash) source_component_evidence_hash
  FROM cloudmold_order_benefit_funding_qualification
  WHERE status='QUALIFIED'
  GROUP BY tenant_id,source_migration_run_id,component_id
) funding ON funding.tenant_id=governed.tenant_id
 AND BINARY funding.source_migration_run_id=BINARY governed.source_migration_run_id
 AND BINARY funding.component_id=BINARY governed.component_id
WHERE (governed.funding_resolution_status='QUALIFIED' AND (
       governed.component_amount_minor<=0 OR funding.component_id IS NULL
       OR governed.funding_share_count<>funding.share_count
       OR governed.funding_amount_minor<>funding.amount_minor
       OR funding.amount_minor<>governed.component_amount_minor
       OR funding.evidence_hash_count<>1
       OR BINARY funding.source_component_evidence_hash
            <>BINARY governed.source_component_evidence_hash))
   OR (governed.funding_resolution_status<>'QUALIFIED'
       AND governed.governance_admission_allowed<>0);

SELECT 'legacy_trade_benefit_governance_quarantine_decision_mismatch' AS check_name,
       COUNT(*) AS violation_count
FROM cloudmold_order_benefit_governance_quarantine governed
LEFT JOIN cloudmold_order_benefit_migration_candidate source_candidate
  ON source_candidate.tenant_id=governed.tenant_id
 AND BINARY source_candidate.migration_run_id=BINARY governed.source_migration_run_id
 AND BINARY source_candidate.candidate_id=BINARY governed.candidate_id
LEFT JOIN cloudmold_order_benefit_quarantine_decision decision_row
  ON decision_row.tenant_id=governed.tenant_id
 AND BINARY decision_row.decision_id=BINARY governed.decision_id
WHERE source_candidate.candidate_id IS NULL
   OR source_candidate.legacy_order_id<>governed.legacy_order_id
   OR BINARY source_candidate.legacy_snapshot_hash<>BINARY governed.source_candidate_evidence_hash
   OR source_candidate.assessment_status NOT IN ('QUARANTINED_MONEY','QUARANTINED_HEADER_ITEM')
   OR (governed.decision_status='DECIDED' AND (
       decision_row.decision_id IS NULL OR decision_row.status<>'QUALIFIED'
       OR decision_row.decision_type<>'EXCLUDE_CONFIRMED_SOURCE_DEFECT'
       OR governed.recommended_action<>'EXCLUDE_CONFIRMED_SOURCE_DEFECT'
       OR BINARY decision_row.source_candidate_evidence_hash
            <>BINARY governed.source_candidate_evidence_hash))
   OR (governed.decision_status='OPEN' AND (
       governed.decision_id IS NOT NULL
       OR governed.recommended_action<>'CORRECT_SOURCE_AND_REASSESS'));

SELECT 'legacy_trade_benefit_governance_illegally_enabled' AS check_name,
       COUNT(*) AS violation_count
FROM cloudmold_order_benefit_governance_run run
LEFT JOIN (
  SELECT tenant_id,governance_run_id,COUNT(*) component_count,
         SUM(governance_admission_allowed=1) admitted_count
  FROM cloudmold_order_benefit_governance_component
  GROUP BY tenant_id,governance_run_id
) component ON component.tenant_id=run.tenant_id
 AND BINARY component.governance_run_id=BINARY run.governance_run_id
WHERE run.production_migration_enabled<>0
   OR (run.status='READY_FOR_COMBINED_ADMISSION' AND (
       component.admitted_count<>component.component_count
       OR run.quarantine_decided_count<>run.source_quarantine_count))
   OR (run.status<>'READY_FOR_COMBINED_ADMISSION'
       AND run.governance_admitted_component_count=run.source_component_count
       AND run.quarantine_open_count=0)
   OR EXISTS (
       SELECT 1 FROM cloudmold_order_benefit_governance_component governed
       WHERE governed.tenant_id=run.tenant_id
         AND BINARY governed.governance_run_id=BINARY run.governance_run_id
         AND governed.canonical_import_allowed<>0)
   OR EXISTS (
       SELECT 1 FROM cloudmold_order_benefit_governance_quarantine governed
       WHERE governed.tenant_id=run.tenant_id
         AND BINARY governed.governance_run_id=BINARY run.governance_run_id
         AND governed.canonical_import_allowed<>0);

SELECT 'legacy_trade_benefit_governance_event_denominator_mismatch' AS check_name,
       COUNT(*) AS violation_count
FROM cloudmold_order_benefit_governance_run run
LEFT JOIN (
  SELECT expected.tenant_id,expected.governance_run_id,COUNT(*) expected_count,
         SUM(event.event_id IS NOT NULL) event_count
  FROM (
    SELECT tenant_id,governance_run_id,candidate_id
    FROM cloudmold_order_benefit_governance_component
    UNION
    SELECT tenant_id,governance_run_id,candidate_id
    FROM cloudmold_order_benefit_governance_quarantine
  ) expected
  LEFT JOIN cloudmold_event_outbox event
    ON event.tenant_id=expected.tenant_id
   AND event.event_type='order.migration.legacy_trade_benefit_governance_assessed'
   AND event.aggregate_type='legacy_trade_benefit_governance_readiness'
   AND BINARY JSON_UNQUOTE(JSON_EXTRACT(event.payload,'$.candidate_id'))
        =BINARY expected.candidate_id
   AND BINARY JSON_UNQUOTE(JSON_EXTRACT(event.headers,'$.governance_run_id'))
        =BINARY expected.governance_run_id
  GROUP BY expected.tenant_id,expected.governance_run_id
) event_rollup ON event_rollup.tenant_id=run.tenant_id
 AND BINARY event_rollup.governance_run_id=BINARY run.governance_run_id
WHERE event_rollup.governance_run_id IS NULL
   OR event_rollup.expected_count<>event_rollup.event_count;

SELECT 'legacy_trade_benefit_governance_duplicate_event' AS check_name,
       COUNT(*) AS violation_count
FROM (
  SELECT tenant_id,aggregate_id,
         JSON_UNQUOTE(JSON_EXTRACT(headers,'$.governance_run_id')) governance_run_id,
         COUNT(*) event_count
  FROM cloudmold_event_outbox
  WHERE event_type='order.migration.legacy_trade_benefit_governance_assessed'
  GROUP BY tenant_id,aggregate_id,
           JSON_UNQUOTE(JSON_EXTRACT(headers,'$.governance_run_id'))
  HAVING COUNT(*)<>1
) duplicate_event;

SELECT 'legacy_trade_benefit_governance_event_aggregate_identity_mismatch' AS check_name,
       COUNT(*) AS violation_count
FROM cloudmold_event_outbox event
WHERE event.event_type='order.migration.legacy_trade_benefit_governance_assessed'
  AND (event.schema_version NOT IN (1,2)
    OR JSON_UNQUOTE(JSON_EXTRACT(event.payload,'$.candidate_id')) IS NULL
    OR (event.schema_version=1
        AND BINARY event.aggregate_id
             <>BINARY JSON_UNQUOTE(JSON_EXTRACT(event.payload,'$.candidate_id')))
    OR (event.schema_version=2
        AND BINARY event.aggregate_id
             =BINARY JSON_UNQUOTE(JSON_EXTRACT(event.payload,'$.candidate_id')))
    OR (event.schema_version=2
        AND BINARY JSON_UNQUOTE(JSON_EXTRACT(event.headers,'$.candidate_id'))
             <>BINARY JSON_UNQUOTE(JSON_EXTRACT(event.payload,'$.candidate_id'))));

SELECT 'legacy_trade_benefit_governance_event_run_payload_mismatch' AS check_name,
       COUNT(*) AS violation_count
FROM cloudmold_event_outbox event
JOIN cloudmold_order_benefit_governance_run run
  ON run.tenant_id=event.tenant_id
 AND BINARY run.governance_run_id
      =BINARY JSON_UNQUOTE(JSON_EXTRACT(event.payload,'$.governance_run_id'))
WHERE event.event_type='order.migration.legacy_trade_benefit_governance_assessed'
  AND (event.schema_version NOT IN (1,2)
    OR BINARY JSON_UNQUOTE(JSON_EXTRACT(event.payload,'$.source_migration_run_id'))
         <>BINARY run.source_migration_run_id
    OR BINARY JSON_UNQUOTE(JSON_EXTRACT(event.payload,'$.governance_evidence_hash'))
         <>BINARY run.governance_evidence_hash
    OR CAST(JSON_UNQUOTE(JSON_EXTRACT(event.payload,'$.run_source_component_count')) AS UNSIGNED)
         <>run.source_component_count
    OR CAST(JSON_UNQUOTE(JSON_EXTRACT(event.payload,'$.run_current_reference_observed_count')) AS UNSIGNED)
         <>run.current_reference_observed_count
    OR CAST(JSON_UNQUOTE(JSON_EXTRACT(event.payload,'$.run_historical_identity_qualified_count')) AS UNSIGNED)
         <>run.historical_identity_qualified_count
    OR CAST(JSON_UNQUOTE(JSON_EXTRACT(event.payload,'$.run_funding_qualified_count')) AS UNSIGNED)
         <>run.funding_qualified_count
    OR CAST(JSON_UNQUOTE(JSON_EXTRACT(event.payload,'$.run_quarantine_open_count')) AS UNSIGNED)
         <>run.quarantine_open_count
    OR CAST(JSON_UNQUOTE(JSON_EXTRACT(event.payload,'$.run_governance_admitted_component_count')) AS UNSIGNED)
         <>run.governance_admitted_component_count
    OR BINARY JSON_UNQUOTE(JSON_EXTRACT(event.payload,'$.run_status'))<>BINARY run.status
    OR JSON_EXTRACT(event.payload,'$.production_migration_enabled')<>CAST('false' AS JSON));

SELECT 'legacy_trade_benefit_governance_event_component_payload_mismatch' AS check_name,
       COUNT(*) AS violation_count
FROM cloudmold_order_benefit_governance_component governed
JOIN cloudmold_event_outbox event
  ON event.tenant_id=governed.tenant_id
 AND event.event_type='order.migration.legacy_trade_benefit_governance_assessed'
 AND BINARY JSON_UNQUOTE(JSON_EXTRACT(event.payload,'$.candidate_id'))
      =BINARY governed.candidate_id
 AND BINARY JSON_UNQUOTE(JSON_EXTRACT(event.payload,'$.governance_run_id'))
      =BINARY governed.governance_run_id
LEFT JOIN JSON_TABLE(event.payload,'$.components[*]' COLUMNS (
  component_governance_id varchar(36) PATH '$.component_governance_id',
  component_id varchar(36) PATH '$.component_id',
  source_component_evidence_hash char(64) PATH '$.source_component_evidence_hash',
  current_reference_status varchar(64) PATH '$.current_reference_status',
  historical_identity_status varchar(48) PATH '$.historical_identity_status',
  funding_resolution_status varchar(48) PATH '$.funding_resolution_status',
  governance_status varchar(16) PATH '$.governance_status',
  governance_admission_allowed tinyint PATH '$.governance_admission_allowed',
  canonical_import_allowed tinyint PATH '$.canonical_import_allowed',
  evidence_hash char(64) PATH '$.evidence_hash'
)) event_component
  ON BINARY event_component.component_governance_id=BINARY governed.component_governance_id
WHERE event_component.component_governance_id IS NULL
   OR BINARY event_component.component_id<>BINARY governed.component_id
   OR BINARY event_component.source_component_evidence_hash
        <>BINARY governed.source_component_evidence_hash
   OR BINARY event_component.current_reference_status<>BINARY governed.current_reference_status
   OR BINARY event_component.historical_identity_status<>BINARY governed.historical_identity_status
   OR BINARY event_component.funding_resolution_status<>BINARY governed.funding_resolution_status
   OR BINARY event_component.governance_status<>BINARY governed.governance_status
   OR event_component.governance_admission_allowed<>governed.governance_admission_allowed
   OR event_component.canonical_import_allowed<>0
   OR BINARY event_component.evidence_hash<>BINARY governed.evidence_hash;

SELECT 'legacy_trade_benefit_governance_event_quarantine_payload_mismatch' AS check_name,
       COUNT(*) AS violation_count
FROM cloudmold_order_benefit_governance_quarantine governed
JOIN cloudmold_event_outbox event
  ON event.tenant_id=governed.tenant_id
 AND event.event_type='order.migration.legacy_trade_benefit_governance_assessed'
 AND BINARY JSON_UNQUOTE(JSON_EXTRACT(event.payload,'$.candidate_id'))
      =BINARY governed.candidate_id
 AND BINARY JSON_UNQUOTE(JSON_EXTRACT(event.payload,'$.governance_run_id'))
      =BINARY governed.governance_run_id
WHERE BINARY JSON_UNQUOTE(JSON_EXTRACT(event.payload,'$.quarantine.quarantine_governance_id'))
        <>BINARY governed.quarantine_governance_id
   OR BINARY JSON_UNQUOTE(JSON_EXTRACT(event.payload,'$.quarantine.source_candidate_evidence_hash'))
        <>BINARY governed.source_candidate_evidence_hash
   OR BINARY JSON_UNQUOTE(JSON_EXTRACT(event.payload,'$.quarantine.decision_status'))
        <>BINARY governed.decision_status
   OR JSON_EXTRACT(event.payload,'$.quarantine.canonical_import_allowed')<>CAST('false' AS JSON)
   OR BINARY JSON_UNQUOTE(JSON_EXTRACT(event.payload,'$.quarantine.evidence_hash'))
        <>BINARY governed.evidence_hash;
