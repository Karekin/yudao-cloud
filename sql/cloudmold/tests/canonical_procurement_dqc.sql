SELECT 'procurement_header_arithmetic_mismatch' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_procurement_order
WHERE header_net_amount_minor < 0
   OR header_tax_amount_minor < 0
   OR header_gross_amount_minor <> header_net_amount_minor + header_tax_amount_minor
   OR tax_calculation_policy_code <> 'STANDARD_V1'
   OR rounding_policy_code <> 'HALF_UP';

SELECT 'procurement_header_line_conservation_mismatch' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_procurement_order orders
LEFT JOIN (
    SELECT tenant_id,
           order_id,
           COUNT(*) AS item_count,
           SUM(line_net_amount_minor) AS net_amount_minor,
           SUM(line_tax_amount_minor) AS tax_amount_minor,
           SUM(line_gross_amount_minor) AS gross_amount_minor
    FROM cloudmold_procurement_order_item
    GROUP BY tenant_id, order_id
) items
  ON items.tenant_id = orders.tenant_id
 AND items.order_id = orders.order_id
WHERE COALESCE(items.item_count, 0) = 0
   OR orders.header_net_amount_minor <> items.net_amount_minor
   OR orders.header_tax_amount_minor <> items.tax_amount_minor
   OR orders.header_gross_amount_minor <> items.gross_amount_minor;

SELECT 'procurement_line_arithmetic_mismatch' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_procurement_order_item
WHERE ordered_quantity <= 0
   OR unit_net_price_minor < 0
   OR tax_rate_bps < 0
   OR tax_rate_bps > 10000
   OR line_net_amount_minor <> ROUND(ordered_quantity * unit_net_price_minor, 0)
   OR line_tax_amount_minor <> ROUND(line_net_amount_minor * tax_rate_bps / 10000, 0)
   OR line_gross_amount_minor <> line_net_amount_minor + line_tax_amount_minor;

SELECT 'procurement_schedule_quantity_mismatch' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_procurement_order_item items
LEFT JOIN (
    SELECT tenant_id, item_id, COUNT(*) AS schedule_count, SUM(scheduled_quantity) AS scheduled_quantity
    FROM cloudmold_procurement_order_delivery_schedule
    GROUP BY tenant_id, item_id
) schedules
  ON schedules.tenant_id = items.tenant_id
 AND schedules.item_id = items.item_id
WHERE COALESCE(schedules.schedule_count, 0) = 0
   OR items.ordered_quantity <> schedules.scheduled_quantity;

SELECT 'procurement_reference_authority_violation' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_procurement_order orders
LEFT JOIN cloudmold_supplier_profile suppliers
  ON suppliers.tenant_id = orders.tenant_id
 AND suppliers.supplier_id = orders.supplier_id
WHERE suppliers.supplier_id IS NULL
   OR suppliers.status <> 'ACTIVE'
   OR suppliers.admission_status <> 'ADMITTED';

SELECT 'procurement_sku_authority_violation' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_procurement_order_item items
LEFT JOIN cloudmold_catalog_sku sku
  ON sku.tenant_id = items.tenant_id
 AND BINARY sku.sku_id = BINARY items.canonical_sku_id
 AND sku.status = 10
LEFT JOIN cloudmold_catalog_spu spu
  ON spu.tenant_id = sku.tenant_id AND spu.spu_id = sku.spu_id AND spu.status = 30
LEFT JOIN cloudmold_catalog_style style
  ON style.tenant_id = spu.tenant_id AND style.style_id = spu.style_id AND style.status = 10
LEFT JOIN cloudmold_catalog_color color
  ON color.tenant_id = sku.tenant_id AND color.color_id = sku.color_id AND color.status = 10
LEFT JOIN cloudmold_catalog_size size_value
  ON size_value.tenant_id = sku.tenant_id AND size_value.size_id = sku.size_id AND size_value.status = 10
LEFT JOIN cloudmold_catalog_size_group size_group
  ON size_group.tenant_id = size_value.tenant_id
 AND size_group.size_group_id = size_value.size_group_id
 AND size_group.status = 10
LEFT JOIN cloudmold_catalog_barcode barcode
  ON barcode.tenant_id = sku.tenant_id
 AND barcode.sku_id = sku.sku_id
 AND barcode.is_primary = b'1'
 AND barcode.status = 10
 AND barcode.valid_from <= UTC_TIMESTAMP(6)
 AND (barcode.valid_to IS NULL OR barcode.valid_to > UTC_TIMESTAMP(6))
WHERE sku.sku_id IS NULL
   OR spu.spu_id IS NULL
   OR style.style_id IS NULL
   OR color.color_id IS NULL
   OR size_value.size_id IS NULL
   OR size_group.size_group_id IS NULL
   OR barcode.barcode_id IS NULL;

SELECT 'procurement_warehouse_authority_violation' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_procurement_order_delivery_schedule schedules
LEFT JOIN cloudmold_warehouse warehouses
  ON warehouses.tenant_id = schedules.tenant_id
 AND BINARY warehouses.warehouse_id = BINARY schedules.canonical_warehouse_id
 AND warehouses.status = 'ACTIVE'
WHERE warehouses.warehouse_id IS NULL;

SELECT 'procurement_external_projection_table_remaining' AS check_name, COUNT(*) AS violation_count
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name = 'cloudmold_procurement_external_projection';

SELECT 'procurement_terminal_timestamp_mismatch' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_procurement_order
WHERE (status = 'DISPATCHED' AND dispatched_at IS NULL)
   OR (status = 'SUPPLIER_CONFIRMED' AND supplier_confirmed_at IS NULL)
   OR (status = 'CANCELLED' AND cancelled_at IS NULL)
   OR (status = 'CLOSED' AND closed_at IS NULL);

SELECT 'procurement_status_history_mismatch' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_procurement_order orders
LEFT JOIN cloudmold_procurement_order_status_history history
  ON history.tenant_id = orders.tenant_id
 AND history.order_id = orders.order_id
 AND history.aggregate_version = orders.version
WHERE history.history_id IS NULL
   OR history.status <> orders.status;

SELECT 'procurement_source_ref_uniqueness_drift' AS check_name, COUNT(*) AS violation_count
FROM (
    SELECT tenant_id, source_business_type, source_business_ref, COUNT(*) AS item_count
    FROM cloudmold_procurement_order
    GROUP BY tenant_id, source_business_type, source_business_ref
    HAVING COUNT(*) > 1
) duplicate_source_ref;

SELECT 'procurement_legacy_header_columns_remaining' AS check_name, COUNT(*) AS violation_count
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'cloudmold_procurement_order'
  AND column_name IN (
      'supplier_ref',
      'canonical_sku_id',
      'canonical_warehouse_id',
      'ordered_quantity',
      'uom_code',
      'unit_cost_minor',
      'total_amount_minor',
      'required_delivery_date',
      'projection_source_system',
      'projection_document_type',
      'projection_external_document_id',
      'projection_external_document_no',
      'projection_document_status',
      'projection_evidence_sha256'
  );

SELECT 'purchase_requisition_line_conservation_mismatch' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_purchase_requisition requisitions
LEFT JOIN (
    SELECT tenant_id, requisition_id, COUNT(*) AS line_count
    FROM cloudmold_purchase_requisition_line
    GROUP BY tenant_id, requisition_id
) requisition_lines
  ON requisition_lines.tenant_id = requisitions.tenant_id
 AND requisition_lines.requisition_id = requisitions.requisition_id
WHERE COALESCE(requisition_lines.line_count, 0) = 0;

SELECT 'purchase_requisition_schedule_quantity_mismatch' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_purchase_requisition_line requisition_lines
LEFT JOIN (
    SELECT tenant_id, line_id, COUNT(*) AS schedule_count, SUM(scheduled_quantity) AS scheduled_quantity
    FROM cloudmold_purchase_requisition_delivery_schedule
    GROUP BY tenant_id, line_id
) requisition_schedules
  ON requisition_schedules.tenant_id = requisition_lines.tenant_id
 AND requisition_schedules.line_id = requisition_lines.line_id
WHERE COALESCE(requisition_schedules.schedule_count, 0) = 0
   OR requisition_lines.requested_quantity <> requisition_schedules.scheduled_quantity;

SELECT 'purchase_requisition_status_history_mismatch' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_purchase_requisition requisitions
LEFT JOIN cloudmold_purchase_requisition_status_history history
  ON history.tenant_id = requisitions.tenant_id
 AND history.requisition_id = requisitions.requisition_id
 AND history.aggregate_version = requisitions.version
WHERE history.history_id IS NULL
   OR history.status <> requisitions.status;

SELECT 'purchase_requisition_legacy_reference_leak' AS check_name, COUNT(*) AS violation_count
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name IN (
      'cloudmold_purchase_requisition',
      'cloudmold_purchase_requisition_line',
      'cloudmold_purchase_requisition_delivery_schedule'
  )
  AND column_name IN (
      'supplier_id',
      'supplier_ref',
      'erp_supplier_id',
      'erp_product_id',
      'erp_product_unit_id',
      'wms_sku_id',
      'projection_external_document_id'
  );
