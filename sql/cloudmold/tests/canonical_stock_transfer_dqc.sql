-- Every result column must be zero.

SELECT 'stock_transfer_operation_incomplete_success' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_stock_transfer_operation
WHERE status=10 AND (aggregate_id IS NULL OR result_json IS NULL);

SELECT 'stock_transfer_request_line_conservation_mismatch' AS check_name,
       COUNT(*) AS violation_count
FROM cloudmold_stock_transfer_request request
LEFT JOIN (
    SELECT tenant_id,request_id,COUNT(*) AS line_count
    FROM cloudmold_stock_transfer_request_line
    GROUP BY tenant_id,request_id
) line
  ON line.tenant_id=request.tenant_id AND line.request_id=request.request_id
WHERE COALESCE(line.line_count,0)=0;

SELECT 'stock_transfer_order_request_mismatch' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_stock_transfer_order transfer_order
LEFT JOIN cloudmold_stock_transfer_request request
  ON request.tenant_id=transfer_order.tenant_id
 AND request.request_id=transfer_order.request_id
WHERE request.request_id IS NULL
   OR request.owner_type<>transfer_order.owner_type
   OR request.owner_id<>transfer_order.owner_id
   OR request.source_warehouse_id<>transfer_order.source_warehouse_id
   OR request.target_warehouse_id<>transfer_order.target_warehouse_id;

SELECT 'stock_transfer_order_line_mismatch' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_stock_transfer_order_line order_line
JOIN cloudmold_stock_transfer_order transfer_order
  ON transfer_order.tenant_id=order_line.tenant_id
 AND transfer_order.order_id=order_line.order_id
LEFT JOIN cloudmold_stock_transfer_request_line request_line
  ON request_line.tenant_id=transfer_order.tenant_id
 AND request_line.request_id=transfer_order.request_id
 AND request_line.line_number=order_line.line_number
WHERE request_line.line_id IS NULL
   OR request_line.canonical_sku_id<>order_line.canonical_sku_id
   OR request_line.requested_quantity<>order_line.requested_quantity
   OR request_line.uom_code<>order_line.uom_code;

SELECT 'stock_transfer_missing_order_line' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_stock_transfer_request_line request_line
JOIN cloudmold_stock_transfer_order transfer_order
  ON transfer_order.tenant_id=request_line.tenant_id
 AND transfer_order.request_id=request_line.request_id
LEFT JOIN cloudmold_stock_transfer_order_line order_line
  ON order_line.tenant_id=transfer_order.tenant_id
 AND order_line.order_id=transfer_order.order_id
 AND order_line.line_number=request_line.line_number
WHERE order_line.line_id IS NULL;

SELECT 'stock_transfer_warehouse_authority_violation' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_stock_transfer_request request
LEFT JOIN cloudmold_warehouse source_warehouse
  ON source_warehouse.tenant_id=request.tenant_id
 AND source_warehouse.warehouse_id=request.source_warehouse_id
 AND source_warehouse.status='ACTIVE'
LEFT JOIN cloudmold_warehouse target_warehouse
  ON target_warehouse.tenant_id=request.tenant_id
 AND target_warehouse.warehouse_id=request.target_warehouse_id
 AND target_warehouse.status='ACTIVE'
WHERE source_warehouse.warehouse_id IS NULL
   OR target_warehouse.warehouse_id IS NULL
   OR request.source_warehouse_id=request.target_warehouse_id;

SELECT 'stock_transfer_sku_authority_violation' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_stock_transfer_request_line line
LEFT JOIN cloudmold_catalog_sku sku
  ON sku.tenant_id=line.tenant_id
 AND BINARY sku.sku_id=BINARY line.canonical_sku_id
 AND sku.status=10
WHERE sku.sku_id IS NULL;

SELECT 'stock_transfer_status_history_mismatch' AS check_name, COUNT(*) AS violation_count
FROM (
    SELECT tenant_id,request_id AS business_object_id,'REQUEST' AS business_object_type,
           status,version
    FROM cloudmold_stock_transfer_request
    UNION ALL
    SELECT tenant_id,order_id AS business_object_id,'ORDER' AS business_object_type,
           status,version
    FROM cloudmold_stock_transfer_order
) current_state
LEFT JOIN cloudmold_stock_transfer_status_history history
  ON history.tenant_id=current_state.tenant_id
 AND history.business_object_type=current_state.business_object_type
 AND history.business_object_id=current_state.business_object_id
 AND history.status_version=current_state.version
WHERE history.history_id IS NULL OR history.status<>current_state.status;

SELECT 'stock_transfer_legacy_reference_leak' AS check_name, COUNT(*) AS violation_count
FROM information_schema.columns
WHERE table_schema=DATABASE()
  AND table_name IN ('cloudmold_stock_transfer_request','cloudmold_stock_transfer_request_line',
                     'cloudmold_stock_transfer_order','cloudmold_stock_transfer_order_line')
  AND column_name IN ('wms_order_id','wms_sku_id','erp_warehouse_id','legacy_warehouse_id',
                      'projection_source_system','external_document_id');
