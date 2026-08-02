SELECT 'inventory_aging_snapshot_operation_incomplete_success' AS check_name,
       COUNT(*) AS violation_count
FROM cloudmold_inventory_aging_snapshot_operation
WHERE status=10 AND result_json IS NULL;

SELECT 'inventory_aging_snapshot_header_counts_mismatch' AS check_name,
       COUNT(*) AS violation_count
FROM cloudmold_inventory_aging_snapshot snapshot
LEFT JOIN (
    SELECT tenant_id,snapshot_id,
           COUNT(*) line_count,
           SUM(CASE WHEN age_bucket='UNKNOWN' THEN 1 ELSE 0 END) unknown_age_count,
           SUM(CASE WHEN expiry_status='UNKNOWN' THEN 1 ELSE 0 END) unknown_expiry_count
    FROM cloudmold_inventory_aging_snapshot_line
    GROUP BY tenant_id,snapshot_id
) line_agg
  ON line_agg.tenant_id=snapshot.tenant_id
 AND line_agg.snapshot_id=snapshot.snapshot_id
WHERE snapshot.line_count<>COALESCE(line_agg.line_count,0)
   OR snapshot.unknown_age_count<>COALESCE(line_agg.unknown_age_count,0)
   OR snapshot.unknown_expiry_count<>COALESCE(line_agg.unknown_expiry_count,0);

SELECT 'inventory_aging_snapshot_unknown_age_rule_broken' AS check_name,
       COUNT(*) AS violation_count
FROM cloudmold_inventory_aging_snapshot_line
WHERE (age_basis_type='UNKNOWN' AND (age_basis_at IS NOT NULL OR age_days IS NOT NULL OR age_bucket<>'UNKNOWN'))
   OR (age_basis_type<>'UNKNOWN' AND (age_basis_at IS NULL OR age_days IS NULL OR age_days<0));

SELECT 'inventory_aging_snapshot_unknown_expiry_rule_broken' AS check_name,
       COUNT(*) AS violation_count
FROM cloudmold_inventory_aging_snapshot_line
WHERE (expiry_status='UNKNOWN' AND (expiry_days_remaining IS NOT NULL OR expiry_bucket<>'UNKNOWN'))
   OR (expiry_status<>'UNKNOWN' AND expiry_days_remaining IS NULL);

SELECT 'inventory_aging_snapshot_risk_priority_broken' AS check_name,
       COUNT(*) AS violation_count
FROM cloudmold_inventory_aging_snapshot_line
WHERE (expiry_status IN ('CRITICAL','EXPIRED') AND risk_classification<>'EXPIRY_CRITICAL')
   OR (expiry_status NOT IN ('CRITICAL','EXPIRED') AND quality_status IN ('REJECTED','DAMAGED')
       AND risk_classification<>'QUALITY_AT_RISK')
   OR (expiry_status NOT IN ('CRITICAL','EXPIRED') AND quality_status NOT IN ('REJECTED','DAMAGED')
       AND stock_status='NON_SELLABLE' AND risk_classification<>'STOCK_RESTRICTED')
   OR (expiry_status NOT IN ('CRITICAL','EXPIRED') AND quality_status NOT IN ('REJECTED','DAMAGED')
       AND stock_status<>'NON_SELLABLE' AND age_bucket='OBSOLETE' AND risk_classification<>'AGE_OBSOLETE')
   OR (expiry_status='WARNING' AND risk_classification<>'EXPIRY_WARNING')
   OR (expiry_status='HEALTHY' AND age_bucket IN ('AGING','STALE') AND risk_classification<>'AGE_ATTENTION')
   OR ((age_bucket='UNKNOWN' OR expiry_status='UNKNOWN') AND risk_classification='HEALTHY');

SELECT 'inventory_aging_snapshot_event_missing' AS check_name,
       COUNT(*) AS violation_count
FROM cloudmold_inventory_aging_snapshot snapshot
LEFT JOIN cloudmold_event_outbox event
  ON event.tenant_id=snapshot.tenant_id
 AND event.aggregate_type='inventory_aging_snapshot'
 AND BINARY event.aggregate_id=BINARY snapshot.snapshot_id
 AND event.aggregate_version=snapshot.version
 AND event.event_type='inventory.aging_snapshot.captured'
 AND event.source_system='cloudmold-inventory'
WHERE event.event_id IS NULL;

SELECT 'inventory_aging_snapshot_line_balance_missing' AS check_name,
       COUNT(*) AS violation_count
FROM cloudmold_inventory_aging_snapshot_line line
LEFT JOIN cloudmold_inventory_balance_v3 balance
  ON balance.tenant_id=line.tenant_id
 AND balance.balance_id=line.balance_id
WHERE balance.balance_id IS NULL;
