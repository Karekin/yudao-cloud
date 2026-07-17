-- Every result column must be zero. Historical product qualification and revocation require
-- two immutable, role-separated approvals and can never enable import directly.

SELECT 'legacy_trade_product_identity_request_source_mismatch' AS check_name, COUNT(*) violation_count
FROM cloudmold_order_product_identity_qualification_request request
LEFT JOIN cloudmold_order_benefit_migration_item source_item
  ON source_item.tenant_id=request.tenant_id
 AND BINARY source_item.migration_run_id=BINARY request.source_migration_run_id
 AND BINARY source_item.item_evidence_id=BINARY request.item_evidence_id
LEFT JOIN cloudmold_order_benefit_migration_run source_run
  ON source_run.tenant_id=request.tenant_id
 AND BINARY source_run.migration_run_id=BINARY request.source_migration_run_id
WHERE source_item.item_evidence_id IS NULL OR source_run.migration_run_id IS NULL
   OR source_run.policy_version<>'legacy-trade-benefit-v4' OR source_run.item_evidence_complete<>1
   OR source_item.legacy_order_item_id<>request.legacy_order_item_id
   OR source_item.legacy_spu_id<>request.historical_spu_id
   OR source_item.legacy_sku_id<>request.historical_sku_id
   OR BINARY source_item.legacy_item_snapshot_hash<>BINARY request.source_item_evidence_hash
   OR request.source_evidence_uri NOT REGEXP '^(s3|oss|restricted|evidence)://'
   OR request.qualification_ref NOT REGEXP '^(evidence|ticket|change|review):';

SELECT 'legacy_trade_product_identity_approval_separation_mismatch' AS check_name, COUNT(*) violation_count
FROM cloudmold_order_product_identity_qualification_approval approval
JOIN cloudmold_order_product_identity_qualification_request request
  ON request.tenant_id=approval.tenant_id AND BINARY request.request_id=BINARY approval.request_id
WHERE BINARY approval.scope_hash<>BINARY request.scope_hash
   OR approval.approver_id=request.requester_id
   OR approval.evidence_ref NOT REGEXP '^(evidence|ticket|change|review):'
   OR (approval.approval_role='DATA_OWNER' AND approval.expected_request_version NOT IN (1,2))
   OR (approval.approval_role='CHANGE_MANAGER' AND approval.expected_request_version NOT IN (1,2));

SELECT 'legacy_trade_product_identity_request_approval_cardinality' AS check_name, COUNT(*) violation_count
FROM (
  SELECT request.tenant_id,request.request_id,request.status,request.approval_count,request.version,
         COUNT(approval.approval_id) actual_count,
         COUNT(DISTINCT approval.approval_role) role_count,
         COUNT(DISTINCT approval.approver_id) actor_count,
         COUNT(DISTINCT approval.expected_request_version) approval_version_count,
         COUNT(DISTINCT CASE WHEN approval.approval_role='DATA_OWNER' THEN approval.approval_id END) owner_count,
         COUNT(DISTINCT CASE WHEN approval.approval_role='CHANGE_MANAGER' THEN approval.approval_id END) manager_count
  FROM cloudmold_order_product_identity_qualification_request request
  LEFT JOIN cloudmold_order_product_identity_qualification_approval approval
    ON approval.tenant_id=request.tenant_id AND BINARY approval.request_id=BINARY request.request_id
  GROUP BY request.tenant_id,request.request_id,request.status,request.approval_count,request.version
  HAVING actual_count<>request.approval_count OR role_count<>actual_count OR actor_count<>actual_count
     OR (request.status='PENDING' AND actual_count<>0)
     OR (request.status='PARTIALLY_APPROVED' AND actual_count<>1)
     OR (request.status='APPLIED' AND (actual_count<>2 OR owner_count<>1 OR manager_count<>1
         OR approval_version_count<>2))
) mismatch;

SELECT 'legacy_trade_product_identity_qualification_workflow_mismatch' AS check_name, COUNT(*) violation_count
FROM cloudmold_order_product_identity_qualification qualification
LEFT JOIN cloudmold_order_product_identity_qualification_request request
  ON request.tenant_id=qualification.tenant_id AND BINARY request.request_id=BINARY qualification.request_id
LEFT JOIN (
  SELECT approval.tenant_id,approval.request_id,
         SHA2(CONCAT('\n',GROUP_CONCAT(
           CONCAT(approval.approval_role,'|',approval.approver_id,'|',approval.scope_hash,'|',
                  approval.evidence_ref,'|',approval.request_hash)
           ORDER BY CONCAT(approval.approval_role,'|',approval.approver_id,'|',approval.scope_hash,'|',
                           approval.evidence_ref,'|',approval.request_hash) SEPARATOR '\n')),256) approval_set_hash,
         COUNT(*) approval_count
  FROM cloudmold_order_product_identity_qualification_approval approval
  GROUP BY approval.tenant_id,approval.request_id
) approval_set
  ON approval_set.tenant_id=qualification.tenant_id
 AND BINARY approval_set.request_id=BINARY qualification.request_id
WHERE request.request_id IS NULL OR request.action_type<>'QUALIFY' OR request.status<>'APPLIED'
   OR BINARY request.qualification_id<>BINARY qualification.qualification_id
   OR approval_set.approval_count<>2
   OR BINARY approval_set.approval_set_hash<>BINARY qualification.approval_set_hash
   OR BINARY request.source_migration_run_id<>BINARY qualification.source_migration_run_id
   OR BINARY request.item_evidence_id<>BINARY qualification.item_evidence_id
   OR request.legacy_order_item_id<>qualification.legacy_order_item_id
   OR request.historical_spu_id<>qualification.historical_spu_id
   OR request.historical_sku_id<>qualification.historical_sku_id
   OR BINARY request.source_item_evidence_hash<>BINARY qualification.source_item_evidence_hash
   OR BINARY request.historical_product_snapshot_hash<>BINARY qualification.historical_product_snapshot_hash;

SELECT 'legacy_trade_product_identity_revocation_workflow_mismatch' AS check_name, COUNT(*) violation_count
FROM cloudmold_order_product_identity_qualification qualification
LEFT JOIN cloudmold_order_product_identity_qualification_request request
  ON request.tenant_id=qualification.tenant_id
 AND BINARY request.request_id=BINARY qualification.revocation_request_id
LEFT JOIN (
  SELECT approval.tenant_id,approval.request_id,
         SHA2(CONCAT('\n',GROUP_CONCAT(
           CONCAT(approval.approval_role,'|',approval.approver_id,'|',approval.scope_hash,'|',
                  approval.evidence_ref,'|',approval.request_hash)
           ORDER BY CONCAT(approval.approval_role,'|',approval.approver_id,'|',approval.scope_hash,'|',
                           approval.evidence_ref,'|',approval.request_hash) SEPARATOR '\n')),256) approval_set_hash,
         COUNT(*) approval_count
  FROM cloudmold_order_product_identity_qualification_approval approval
  GROUP BY approval.tenant_id,approval.request_id
) approval_set
  ON approval_set.tenant_id=qualification.tenant_id
 AND BINARY approval_set.request_id=BINARY qualification.revocation_request_id
WHERE qualification.status='REVOKED' AND (
      request.request_id IS NULL OR request.action_type<>'REVOKE' OR request.status<>'APPLIED'
   OR BINARY request.target_qualification_id<>BINARY qualification.qualification_id
   OR BINARY request.qualification_id<>BINARY qualification.qualification_id
   OR approval_set.approval_count<>2
   OR BINARY approval_set.approval_set_hash<>BINARY qualification.revocation_approval_set_hash
   OR qualification.revoked_at<>request.applied_at);

SELECT 'legacy_trade_product_identity_review_event_cardinality' AS check_name, COUNT(*) violation_count
FROM (
  SELECT request.tenant_id,request.request_id,request.version,COUNT(event.event_id) event_count,
         COUNT(DISTINCT event.aggregate_version) event_version_count,
         MIN(event.aggregate_version) minimum_event_version,
         MAX(event.aggregate_version) maximum_event_version
  FROM cloudmold_order_product_identity_qualification_request request
  LEFT JOIN cloudmold_event_outbox event
    ON event.tenant_id=request.tenant_id
   AND event.event_type='order.migration.legacy_trade_product_identity_qualification_reviewed'
   AND event.schema_version=1
   AND event.aggregate_type='legacy_trade_product_identity_qualification_request'
   AND BINARY event.aggregate_id=BINARY request.request_id
  GROUP BY request.tenant_id,request.request_id,request.version
  HAVING event_count<>request.version OR event_version_count<>request.version
     OR minimum_event_version<>1 OR maximum_event_version<>request.version
) mismatch;

SELECT 'legacy_trade_product_identity_latest_review_event_mismatch' AS check_name, COUNT(*) violation_count
FROM cloudmold_order_product_identity_qualification_request request
LEFT JOIN cloudmold_event_outbox event
  ON event.tenant_id=request.tenant_id
 AND event.event_type='order.migration.legacy_trade_product_identity_qualification_reviewed'
 AND event.schema_version=1
 AND BINARY event.aggregate_id=BINARY request.request_id
 AND event.aggregate_version=request.version
WHERE event.event_id IS NULL
   OR BINARY JSON_UNQUOTE(JSON_EXTRACT(event.payload,'$.scope_hash'))<>BINARY request.scope_hash
   OR JSON_EXTRACT(event.payload,'$.approval_count')<>request.approval_count
   OR BINARY JSON_UNQUOTE(JSON_EXTRACT(event.payload,'$.request_status'))<>BINARY request.status
   OR BINARY JSON_UNQUOTE(JSON_EXTRACT(event.payload,'$.qualification_id'))
        <>BINARY COALESCE(request.qualification_id,'null')
   OR JSON_EXTRACT(event.payload,'$.canonical_import_allowed')<>CAST('false' AS JSON)
   OR JSON_EXTRACT(event.payload,'$.production_migration_enabled')<>CAST('false' AS JSON);
