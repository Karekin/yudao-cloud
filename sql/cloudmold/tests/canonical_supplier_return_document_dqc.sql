SELECT 'supplier_return_operation_incomplete_success' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_supplier_return_operation
WHERE status=10 AND (aggregate_id IS NULL OR result_json IS NULL);

SELECT 'supplier_return_header_receipt_mismatch' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_supplier_return sr
LEFT JOIN cloudmold_warehouse_procurement_receipt receipt
  ON receipt.tenant_id=sr.tenant_id AND receipt.receipt_id=sr.receipt_id
WHERE receipt.receipt_id IS NULL
   OR receipt.procurement_order_id<>sr.purchase_order_id
   OR receipt.supplier_id<>sr.supplier_id
   OR receipt.warehouse_id<>sr.warehouse_id;

SELECT 'supplier_return_line_arithmetic' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_supplier_return_line
WHERE return_quantity<=0
   OR dispatched_quantity<0
   OR outstanding_quantity<0
   OR return_quantity<>dispatched_quantity+outstanding_quantity;

SELECT 'supplier_return_line_reference_mismatch' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_supplier_return_line line
JOIN cloudmold_supplier_return sr
  ON sr.tenant_id=line.tenant_id AND sr.return_id=line.return_id
LEFT JOIN cloudmold_warehouse_procurement_receipt_line receipt_line
  ON receipt_line.tenant_id=line.tenant_id AND receipt_line.receipt_line_id=line.receipt_line_id
LEFT JOIN cloudmold_procurement_receipt_inspection_result_split split_result
  ON split_result.tenant_id=line.tenant_id
 AND split_result.quality_decision_id=line.quality_decision_id
 AND split_result.decision_version=line.decision_version
LEFT JOIN cloudmold_procurement_receipt_inspection_split split
  ON split.tenant_id=split_result.tenant_id
 AND split.inspection_split_id=split_result.inspection_split_id
 AND split.inspection_id=split_result.inspection_id
 AND split.inspection_line_id=split_result.inspection_line_id
LEFT JOIN cloudmold_procurement_receipt_inspection_line inspection_line
  ON inspection_line.tenant_id=split_result.tenant_id
 AND inspection_line.inspection_id=split_result.inspection_id
 AND inspection_line.inspection_line_id=split_result.inspection_line_id
WHERE receipt_line.receipt_line_id IS NULL
   OR split_result.quality_decision_id IS NULL
   OR inspection_line.receipt_line_id<>line.receipt_line_id
   OR inspection_line.purchase_order_id<>sr.purchase_order_id
   OR inspection_line.item_id<>line.purchase_order_item_id
   OR inspection_line.schedule_id<>line.purchase_order_schedule_id
   OR inspection_line.canonical_sku_id<>line.canonical_sku_id
   OR split.warehouse_id<>line.warehouse_id
   OR split.location_id<>line.location_id
   OR COALESCE(split.lot_id,'')<>COALESCE(line.lot_id,'');

SELECT 'supplier_return_quality_allocation_overflow' AS check_name, COUNT(*) AS violation_count
FROM (
    SELECT tenant_id,quality_decision_id,decision_version,inspection_split_id,source_disposition,
           SUM(CASE WHEN status<>'CANCELLED' THEN return_quantity ELSE 0 END) AS allocated_quantity
    FROM cloudmold_supplier_return_line
    GROUP BY tenant_id,quality_decision_id,decision_version,inspection_split_id,source_disposition
) allocated
JOIN cloudmold_procurement_receipt_inspection_result_split split_result
  ON split_result.tenant_id=allocated.tenant_id
 AND split_result.quality_decision_id=allocated.quality_decision_id
 AND split_result.decision_version=allocated.decision_version
JOIN (
    SELECT tenant_id,inspection_split_id,inspection_id,inspection_line_id
    FROM cloudmold_procurement_receipt_inspection_split
) split
  ON split.tenant_id=split_result.tenant_id
 AND split.inspection_split_id=split_result.inspection_split_id
LEFT JOIN (
    SELECT tenant_id,inspection_id,inspection_line_id,
           accepted_quantity,rejected_quantity,quarantined_quantity
    FROM cloudmold_procurement_receipt_inspection_result_split
) quantities
  ON quantities.tenant_id=split_result.tenant_id
 AND quantities.inspection_id=split_result.inspection_id
 AND quantities.inspection_line_id=split_result.inspection_line_id
 AND split_result.quality_decision_id=allocated.quality_decision_id
WHERE allocated.allocated_quantity >
      CASE allocated.source_disposition
          WHEN 'ACCEPTED' THEN split_result.accepted_quantity
          WHEN 'REJECTED' THEN split_result.rejected_quantity
          WHEN 'QUARANTINED' THEN split_result.quarantined_quantity
          ELSE 0
      END;

SELECT 'supplier_return_dispatch_line_consistency' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_supplier_return_dispatch_line dispatch_line
LEFT JOIN cloudmold_supplier_return_line line
  ON line.tenant_id=dispatch_line.tenant_id AND line.return_line_id=dispatch_line.return_line_id
WHERE line.return_line_id IS NULL
   OR dispatch_line.line_number<>line.line_number
   OR dispatch_line.source_disposition<>line.source_disposition
   OR dispatch_line.cumulative_dispatched_quantity>line.return_quantity;

SELECT 'supplier_return_status_history_mismatch' AS check_name, COUNT(*) AS violation_count
FROM (
    SELECT tenant_id,return_id AS business_object_id,'RETURN' AS business_object_type,status,version
    FROM cloudmold_supplier_return
    UNION ALL
    SELECT tenant_id,return_line_id AS business_object_id,'LINE' AS business_object_type,status,version
    FROM cloudmold_supplier_return_line
) current_state
LEFT JOIN cloudmold_supplier_return_status_history history
  ON history.tenant_id=current_state.tenant_id
 AND history.business_object_type=current_state.business_object_type
 AND history.business_object_id=current_state.business_object_id
 AND history.status_version=current_state.version
WHERE history.history_id IS NULL OR history.status<>current_state.status;

SELECT 'supplier_return_legacy_reference_leak' AS check_name, COUNT(*) AS violation_count
FROM information_schema.columns
WHERE table_schema=DATABASE()
  AND table_name IN ('cloudmold_supplier_return','cloudmold_supplier_return_line',
                     'cloudmold_supplier_return_dispatch_batch','cloudmold_supplier_return_dispatch_line')
  AND column_name IN ('erp_return_id','wms_return_id','legacy_supplier_id','projection_source_system',
                      'external_document_id');
