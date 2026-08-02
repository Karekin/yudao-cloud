SELECT 'purchase_requisition_missing_header_authority' AS check_name, COUNT(*) AS violations
FROM cloudmold_purchase_requisition
WHERE legal_entity_id IS NULL
   OR tax_calculation_policy_code IS NULL
   OR rounding_policy_code IS NULL
UNION ALL
SELECT 'purchase_requisition_missing_line_valuation', COUNT(*)
FROM cloudmold_purchase_requisition_line
WHERE valuation_policy_id IS NULL
   OR valuation_policy_version IS NULL
   OR valuation_policy_hash IS NULL
UNION ALL
SELECT 'award_snapshot_missing_header_authority', COUNT(*)
FROM cloudmold_procurement_award_snapshot
WHERE requisition_id IS NULL
   OR requisition_version IS NULL
   OR legal_entity_id IS NULL
   OR tax_calculation_policy_code IS NULL
   OR rounding_policy_code IS NULL
UNION ALL
SELECT 'award_snapshot_missing_line_authority', COUNT(*)
FROM cloudmold_procurement_award_snapshot_line
WHERE requisition_line_id IS NULL
   OR requisition_schedule_id IS NULL
   OR valuation_policy_id IS NULL
   OR valuation_policy_version IS NULL
   OR valuation_policy_hash IS NULL
UNION ALL
SELECT 'procurement_order_duplicate_source_supplier_currency', COUNT(*)
FROM (
    SELECT tenant_id, source_business_type, source_business_ref, supplier_id, currency_code
    FROM cloudmold_procurement_order
    GROUP BY tenant_id, source_business_type, source_business_ref, supplier_id, currency_code
    HAVING COUNT(*) > 1
) duplicates;
