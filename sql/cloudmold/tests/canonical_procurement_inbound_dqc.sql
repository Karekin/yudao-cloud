SELECT 'schedule_quantity_conservation' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_warehouse_procurement_schedule_fulfillment
WHERE received_quantity <> pending_quality_quantity + accepted_quantity + rejected_quantity + quarantined_quantity
   OR returned_quantity > rejected_quantity + quarantined_quantity
UNION ALL
SELECT 'receipt_line_quantity_conservation', COUNT(*)
FROM cloudmold_warehouse_procurement_receipt_line
WHERE received_quantity <> pending_quality_quantity + accepted_quantity + rejected_quantity + quarantined_quantity
   OR cumulative_putaway_quantity > accepted_quantity
UNION ALL
SELECT 'policy_hash_shape',
       (SELECT COUNT(*) FROM cloudmold_warehouse_procurement_schedule_fulfillment
        WHERE NOT REGEXP_LIKE(tolerance_policy_hash, '^[0-9a-f]{64}$', 'c'))
       + (SELECT COUNT(*) FROM cloudmold_warehouse_procurement_asn_line
          WHERE NOT REGEXP_LIKE(tolerance_policy_hash, '^[0-9a-f]{64}$', 'c')
             OR NOT REGEXP_LIKE(valuation_policy_hash, '^[0-9a-f]{64}$', 'c'))
       + (SELECT COUNT(*) FROM cloudmold_warehouse_procurement_receipt_line
          WHERE NOT REGEXP_LIKE(valuation_policy_hash, '^[0-9a-f]{64}$', 'c')
             OR NOT REGEXP_LIKE(tolerance_policy_hash, '^[0-9a-f]{64}$', 'c'))
UNION ALL
SELECT 'valuation_cost_shape',
       (SELECT COUNT(*) FROM cloudmold_warehouse_procurement_asn_line
        WHERE unit_cost_amount_minor < 0 OR NOT REGEXP_LIKE(currency_code, '^[A-Z]{3}$', 'c')
           OR rounding_policy_code <> 'HALF_UP')
       + (SELECT COUNT(*) FROM cloudmold_warehouse_procurement_receipt_line
          WHERE unit_cost_amount_minor < 0 OR movement_cost_amount_minor < 0
             OR movement_cost_amount_minor <> ROUND(received_quantity * unit_cost_amount_minor, 0)
             OR NOT REGEXP_LIKE(currency_code, '^[A-Z]{3}$', 'c') OR rounding_policy_code <> 'HALF_UP')
UNION ALL
SELECT 'receipt_aggregate_status', COUNT(*)
FROM cloudmold_warehouse_procurement_receipt receipt
JOIN (
    SELECT tenant_id, receipt_id,
           SUM(pending_quality_quantity) AS pending_quantity,
           SUM(accepted_quantity) AS accepted_quantity,
           SUM(rejected_quantity) AS rejected_quantity,
           SUM(quarantined_quantity) AS quarantined_quantity,
           SUM(cumulative_putaway_quantity) AS putaway_quantity
    FROM cloudmold_warehouse_procurement_receipt_line
    GROUP BY tenant_id, receipt_id
) aggregate_line ON aggregate_line.tenant_id = receipt.tenant_id
    AND aggregate_line.receipt_id = receipt.receipt_id
WHERE (receipt.status = 'PENDING_QUALITY'
        AND NOT (aggregate_line.pending_quantity > 0
            AND aggregate_line.accepted_quantity = 0
            AND aggregate_line.rejected_quantity = 0
            AND aggregate_line.quarantined_quantity = 0
            AND aggregate_line.putaway_quantity = 0))
   OR (receipt.status = 'PARTIAL_QUALITY_DECIDED'
        AND NOT (aggregate_line.pending_quantity > 0
            AND aggregate_line.accepted_quantity + aggregate_line.rejected_quantity
                + aggregate_line.quarantined_quantity > 0
            AND aggregate_line.putaway_quantity = 0))
   OR (receipt.status = 'QUALITY_ACCEPTED'
        AND NOT (aggregate_line.pending_quantity = 0 AND aggregate_line.accepted_quantity > 0
            AND aggregate_line.rejected_quantity = 0 AND aggregate_line.quarantined_quantity = 0
            AND aggregate_line.putaway_quantity = 0))
   OR (receipt.status = 'QUALITY_REJECTED'
        AND NOT (aggregate_line.pending_quantity = 0 AND aggregate_line.accepted_quantity = 0
            AND aggregate_line.rejected_quantity > 0 AND aggregate_line.quarantined_quantity = 0))
   OR (receipt.status = 'QUALITY_QUARANTINED'
        AND NOT (aggregate_line.pending_quantity = 0 AND aggregate_line.accepted_quantity = 0
            AND aggregate_line.rejected_quantity = 0 AND aggregate_line.quarantined_quantity > 0))
   OR (receipt.status = 'QUALITY_MIXED'
        AND NOT (aggregate_line.pending_quantity = 0
            AND ((aggregate_line.accepted_quantity > 0)
                + (aggregate_line.rejected_quantity > 0)
                + (aggregate_line.quarantined_quantity > 0)) > 1
            AND aggregate_line.putaway_quantity = 0))
   OR (receipt.status = 'PARTIALLY_PUTAWAY'
        AND NOT (aggregate_line.putaway_quantity > 0
            AND (aggregate_line.pending_quantity > 0
                OR aggregate_line.putaway_quantity < aggregate_line.accepted_quantity)))
   OR (receipt.status = 'PUTAWAY_COMPLETED'
        AND NOT (aggregate_line.pending_quantity = 0 AND aggregate_line.accepted_quantity > 0
            AND aggregate_line.putaway_quantity = aggregate_line.accepted_quantity))
UNION ALL
SELECT 'quality_effect_arithmetic', COUNT(*)
FROM cloudmold_warehouse_procurement_quality_effect
WHERE pending_quantity_before <> pending_quantity_after + disposition_quantity
   OR (accepted_quantity_after - accepted_quantity_before)
      + (rejected_quantity_after - rejected_quantity_before)
      + (quarantined_quantity_after - quarantined_quantity_before) <> disposition_quantity
   OR receipt_version_after <> receipt_version_before + 1
   OR receipt_line_version_after <> receipt_line_version_before + 1
   OR schedule_fulfillment_version_after <> schedule_fulfillment_version_before + 1
UNION ALL
SELECT 'putaway_ledger_reference', COUNT(*)
FROM cloudmold_warehouse_procurement_putaway_line
WHERE inventory_operation_id <= 0 OR inventory_ledger_tx_id <= 0
   OR inventory_movement_group_id = '' OR inventory_target_balance_id = ''
UNION ALL
SELECT 'finance_receipt_evidence_reference', COUNT(*)
FROM cloudmold_warehouse_procurement_receipt_line
WHERE finance_receipt_evidence_operation_id IS NULL OR finance_receipt_evidence_operation_id <= 0
   OR finance_receipt_evidence_id IS NULL OR finance_receipt_evidence_id = ''
   OR finance_receipt_evidence_version IS NULL OR finance_receipt_evidence_version <= 0;
