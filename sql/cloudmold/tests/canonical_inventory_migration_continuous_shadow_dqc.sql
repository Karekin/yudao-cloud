-- V25 continuous shadow is evidence-only. Every query must return violations=0.
SELECT 'inventory_migration_shadow_admission_binding_invalid' AS check_name, COUNT(*) AS violations
FROM cloudmold_inventory_migration_shadow_window w
LEFT JOIN cloudmold_inventory_migration_pilot_batch b
  ON b.tenant_id=w.tenant_id AND b.batch_id=w.batch_id
LEFT JOIN cloudmold_inventory_migration_pilot_checkpoint c
  ON c.checkpoint_id=w.admission_checkpoint_id
LEFT JOIN cloudmold_event_outbox e
  ON e.event_id=w.admission_event_id
WHERE b.batch_id IS NULL OR b.status<>'ADMISSION_PASSED' OR b.version<>4
   OR b.manifest_hash<>w.manifest_hash OR b.policy_hash<>w.policy_hash
   OR c.checkpoint_id IS NULL OR c.tenant_id<>w.tenant_id OR c.batch_id<>w.batch_id
   OR c.checkpoint_type<>'ADMISSION_PASSED' OR c.batch_version<>w.admission_batch_version
   OR c.scope_hash<>w.manifest_hash OR c.policy_hash<>w.policy_hash
   OR c.item_count<>w.expected_item_count
   OR e.event_id IS NULL OR e.tenant_id<>w.tenant_id
   OR e.event_type<>'inventory.migration.pilot_batch_status_changed'
   OR e.aggregate_type<>'inventory_migration_pilot_batch'
   OR BINARY e.aggregate_id<>BINARY w.batch_id OR e.aggregate_version<>4
   OR BINARY JSON_UNQUOTE(JSON_EXTRACT(e.payload,'$.environment_fingerprint'))
      <>BINARY w.environment_fingerprint;

SELECT 'inventory_migration_shadow_actor_separation_invalid' AS check_name, COUNT(*) AS violations
FROM cloudmold_inventory_migration_shadow_window w
JOIN cloudmold_inventory_migration_pilot_batch b
  ON b.tenant_id=w.tenant_id AND b.batch_id=w.batch_id
LEFT JOIN cloudmold_inventory_migration_pilot_approval a
  ON a.tenant_id=w.tenant_id AND a.batch_id=w.batch_id
WHERE w.collector_id IN (b.requester_id,b.executor_id,a.approver_id)
   OR (w.verifier_id IS NOT NULL AND w.verifier_id IN
       (w.collector_id,b.requester_id,b.executor_id,a.approver_id));

SELECT 'inventory_migration_shadow_window_state_invalid' AS check_name, COUNT(*) AS violations
FROM cloudmold_inventory_migration_shadow_window w
WHERE NOT (
  (w.status='OPEN' AND w.verification_result='PENDING' AND w.aggregate_version=1
    AND w.version=1 AND w.observed_round_count=0)
  OR (w.status='OBSERVING' AND w.verification_result='PENDING' AND w.aggregate_version=2
    AND w.version=w.observed_round_count+1 AND w.observed_round_count>0)
  OR (w.status='VERIFIED' AND w.verification_result IN ('MATCH','DIFFERENT','UNCOMPARABLE')
    AND w.aggregate_version=3 AND w.version=w.observed_round_count+2
    AND w.verifier_id IS NOT NULL AND w.finalized_at IS NOT NULL)
);

SELECT 'inventory_migration_shadow_target_contract_invalid' AS check_name, COUNT(*) AS violations
FROM cloudmold_inventory_migration_shadow_window w
WHERE w.target_projection_kind<>'CANONICAL_INVENTORY_V3_SHADOW_PROJECTION'
   OR w.target_projection_version<>1 OR w.target_materialized<>b'0';

SELECT 'inventory_migration_shadow_window_denominator_mismatch' AS check_name, COUNT(*) AS violations
FROM cloudmold_inventory_migration_shadow_window w
LEFT JOIN (
  SELECT tenant_id,window_id,COUNT(*) round_count,SUM(match_count) match_count,
         SUM(different_count) different_count,SUM(uncomparable_count) uncomparable_count
  FROM cloudmold_inventory_migration_shadow_round GROUP BY tenant_id,window_id
) r ON r.tenant_id=w.tenant_id AND r.window_id=w.window_id
WHERE COALESCE(r.round_count,0)<>w.observed_round_count
   OR COALESCE(r.match_count,0)<>w.total_match_count
   OR COALESCE(r.different_count,0)<>w.total_different_count
   OR COALESCE(r.uncomparable_count,0)<>w.total_uncomparable_count;

SELECT 'inventory_migration_shadow_round_sequence_gap' AS check_name, COUNT(*) AS violations
FROM (
  SELECT tenant_id,window_id,round_number,
         ROW_NUMBER() OVER (PARTITION BY tenant_id,window_id ORDER BY round_number) expected_round
  FROM cloudmold_inventory_migration_shadow_round
) r WHERE r.round_number<>r.expected_round;

SELECT 'inventory_migration_shadow_round_watermark_chain_invalid' AS check_name, COUNT(*) AS violations
FROM (
  SELECT r.*,
         LAG(r.source_watermark_value) OVER (PARTITION BY r.tenant_id,r.window_id ORDER BY r.round_number) prior_source,
         LAG(r.target_watermark_value) OVER (PARTITION BY r.tenant_id,r.window_id ORDER BY r.round_number) prior_target,
         LAG(r.source_watermark_captured_at) OVER (PARTITION BY r.tenant_id,r.window_id ORDER BY r.round_number) prior_source_at,
         LAG(r.target_watermark_applied_at) OVER (PARTITION BY r.tenant_id,r.window_id ORDER BY r.round_number) prior_target_at
  FROM cloudmold_inventory_migration_shadow_round r
) r
JOIN cloudmold_inventory_migration_shadow_window w
  ON w.tenant_id=r.tenant_id AND w.window_id=r.window_id
JOIN cloudmold_inventory_migration_pilot_batch b
  ON b.tenant_id=w.tenant_id AND b.batch_id=w.batch_id
WHERE r.previous_source_watermark_value<>COALESCE(r.prior_source,b.source_watermark_value)
   OR r.previous_target_watermark_value<>COALESCE(r.prior_target,b.target_watermark_value)
   OR r.previous_source_watermark_hash<>SHA2(r.previous_source_watermark_value,256)
   OR r.source_watermark_hash<>SHA2(r.source_watermark_value,256)
   OR r.previous_target_watermark_hash<>SHA2(r.previous_target_watermark_value,256)
   OR r.target_watermark_hash<>SHA2(r.target_watermark_value,256)
   OR r.source_watermark_captured_at<COALESCE(r.prior_source_at,b.source_watermark_captured_at)
   OR r.target_watermark_applied_at<COALESCE(r.prior_target_at,b.target_watermark_applied_at)
   OR r.source_monotonic<>b'1' OR r.target_monotonic<>b'1';

SELECT 'inventory_migration_shadow_round_policy_violation_unclassified' AS check_name, COUNT(*) AS violations
FROM cloudmold_inventory_migration_shadow_round r
JOIN cloudmold_inventory_migration_shadow_window w
  ON w.tenant_id=r.tenant_id AND w.window_id=r.window_id
WHERE (r.watermark_valid=b'0' OR r.watermark_lag_seconds>w.max_watermark_lag_seconds
       OR r.round_gap_seconds>w.max_round_interval_seconds)
  AND r.uncomparable_count<>r.expected_item_count;

SELECT 'inventory_migration_shadow_comparison_denominator_incomplete' AS check_name, COUNT(*) AS violations
FROM cloudmold_inventory_migration_shadow_round r
JOIN cloudmold_inventory_migration_shadow_window w
  ON w.tenant_id=r.tenant_id AND w.window_id=r.window_id
JOIN cloudmold_inventory_migration_pilot_item i
  ON i.tenant_id=w.tenant_id AND i.batch_id=w.batch_id
LEFT JOIN cloudmold_inventory_migration_shadow_comparison c
  ON c.tenant_id=r.tenant_id AND c.round_id=r.round_id AND c.pilot_item_id=i.item_id
WHERE c.comparison_id IS NULL OR c.manifest_ordinal<>i.ordinal OR c.item_scope_hash<>i.item_scope_hash;

SELECT 'inventory_migration_shadow_comparison_extra' AS check_name, COUNT(*) AS violations
FROM cloudmold_inventory_migration_shadow_comparison c
LEFT JOIN cloudmold_inventory_migration_shadow_window w
  ON w.tenant_id=c.tenant_id AND w.window_id=c.window_id
LEFT JOIN cloudmold_inventory_migration_pilot_item i
  ON i.tenant_id=c.tenant_id AND i.batch_id=w.batch_id AND i.item_id=c.pilot_item_id
WHERE w.window_id IS NULL OR i.item_id IS NULL;

SELECT 'inventory_migration_shadow_source_version_regression' AS check_name, COUNT(*) AS violations
FROM (
  SELECT c.*,
         LAG(c.source_version) OVER (PARTITION BY c.tenant_id,c.window_id,c.pilot_item_id
                                     ORDER BY c.round_number) prior_version,
         LAG(c.source_snapshot_hash) OVER (PARTITION BY c.tenant_id,c.window_id,c.pilot_item_id
                                           ORDER BY c.round_number) prior_hash
  FROM cloudmold_inventory_migration_shadow_comparison c
) c
WHERE c.prior_version IS NOT NULL
  AND (c.source_version<c.prior_version
       OR (c.source_version=c.prior_version AND c.source_snapshot_hash<>c.prior_hash));

SELECT 'inventory_migration_shadow_comparison_semantics_invalid' AS check_name, COUNT(*) AS violations
FROM cloudmold_inventory_migration_shadow_comparison c
WHERE (c.comparison_result='MATCH' AND (c.comparable<>b'1' OR c.target_available<>b'1'
       OR c.target_canonical_grain_hash<>c.canonical_grain_hash
       OR c.target_on_hand_quantity<>c.source_on_hand_quantity
       OR c.target_reserved_quantity<>c.source_reserved_quantity
       OR c.target_in_transit_quantity<>c.source_in_transit_quantity
       OR JSON_LENGTH(c.difference_fields)<>0 OR JSON_LENGTH(c.reason_codes)<>0))
   OR (c.comparison_result='DIFFERENT' AND (c.comparable<>b'1' OR c.target_available<>b'1'
       OR JSON_LENGTH(c.difference_fields)=0 OR JSON_LENGTH(c.reason_codes)=0))
   OR (c.comparison_result='UNCOMPARABLE' AND (c.comparable<>b'0'
       OR JSON_LENGTH(c.difference_fields)<>0 OR JSON_LENGTH(c.reason_codes)=0));

SELECT 'inventory_migration_shadow_verified_result_invalid' AS check_name, COUNT(*) AS violations
FROM cloudmold_inventory_migration_shadow_window w
WHERE w.status='VERIFIED' AND (
  w.observed_round_count<w.required_round_count
  OR TIMESTAMPDIFF(SECOND,w.started_at,w.finalized_at)<w.minimum_duration_seconds
  OR TIMESTAMPDIFF(SECOND,w.last_observed_at,w.finalized_at)>w.max_round_interval_seconds
  OR (w.verification_result='MATCH' AND (w.total_different_count<>0 OR w.total_uncomparable_count<>0))
  OR (w.verification_result='DIFFERENT'
      AND (w.total_different_count=0 OR w.total_uncomparable_count<>0))
  OR (w.verification_result='UNCOMPARABLE' AND w.total_uncomparable_count=0)
);

SELECT 'inventory_migration_shadow_production_effect_leak' AS check_name, COUNT(*) AS violations
FROM cloudmold_inventory_migration_shadow_window w
JOIN cloudmold_inventory_migration_pilot_item i
  ON i.tenant_id=w.tenant_id AND i.batch_id=w.batch_id
WHERE EXISTS (SELECT 1 FROM cloudmold_inventory_migration_qualification q
              WHERE q.tenant_id=i.tenant_id AND q.source_system='CLOUDMOLD_INVENTORY_V1'
                AND q.source_type='BALANCE' AND q.source_id=i.legacy_balance_id)
   OR EXISTS (SELECT 1 FROM cloudmold_inventory_migration_bridge b
              WHERE b.tenant_id=i.tenant_id AND b.legacy_balance_id=i.legacy_balance_id
                AND b.resolution_status='RESOLVED')
   OR EXISTS (SELECT 1 FROM cloudmold_inventory_balance_v3 t
              WHERE t.tenant_id=i.tenant_id AND t.owner_type=i.owner_type AND t.owner_id=i.owner_id
                AND t.canonical_sku_id=i.canonical_sku_id AND t.warehouse_id=i.warehouse_id
                AND t.location_id=i.location_id AND (t.lot_id=i.lot_id OR (t.lot_id IS NULL AND i.lot_id IS NULL))
                AND t.stock_status=i.stock_status AND t.quality_status=i.quality_status);
