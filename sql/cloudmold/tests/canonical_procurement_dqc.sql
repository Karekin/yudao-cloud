SELECT 'procurement_projection_prepare_only' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_procurement_order
WHERE projection_document_status IS NOT NULL
  AND projection_document_status <> 'PREPARE';

SELECT 'procurement_terminal_timestamp_mismatch' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_procurement_order
WHERE (status='DISPATCHED' AND dispatched_at IS NULL)
   OR (status='SUPPLIER_CONFIRMED' AND supplier_confirmed_at IS NULL)
   OR (status='CANCELLED' AND cancelled_at IS NULL)
   OR (status='CLOSED' AND closed_at IS NULL);

SELECT 'procurement_source_ref_uniqueness_drift' AS check_name, COUNT(*) AS violation_count
FROM (
    SELECT tenant_id,source_business_type,source_business_ref,COUNT(*) item_count
    FROM cloudmold_procurement_order
    GROUP BY tenant_id,source_business_type,source_business_ref
    HAVING COUNT(*) > 1
) duplicate_source_ref;
