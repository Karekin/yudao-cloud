-- Expected result: every query returns zero rows.

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

SELECT 'missing_scrap_document_table' AS violation
WHERE NOT EXISTS (
  SELECT 1 FROM information_schema.tables
  WHERE table_schema = DATABASE() AND table_name = 'cloudmold_inventory_scrap_document'
);

SELECT 'missing_scrap_line_table' AS violation
WHERE NOT EXISTS (
  SELECT 1 FROM information_schema.tables
  WHERE table_schema = DATABASE() AND table_name = 'cloudmold_inventory_scrap_line'
);

SELECT 'missing_scrap_batch_table' AS violation
WHERE NOT EXISTS (
  SELECT 1 FROM information_schema.tables
  WHERE table_schema = DATABASE() AND table_name = 'cloudmold_inventory_scrap_disposition_batch'
);

SELECT 'missing_scrap_disposition_operation_table' AS violation
WHERE NOT EXISTS (
  SELECT 1 FROM information_schema.tables
  WHERE table_schema = DATABASE() AND table_name = 'cloudmold_inventory_scrap_disposition_operation_v1'
);

SELECT 'invalid_document_quantities' AS violation
FROM cloudmold_inventory_scrap_document
WHERE total_requested_quantity <= 0
   OR total_disposed_quantity < 0
   OR total_disposed_quantity > total_requested_quantity
   OR line_count <= 0;

SELECT 'invalid_line_quantities' AS violation
FROM cloudmold_inventory_scrap_line
WHERE requested_quantity <= 0
   OR disposed_quantity < 0
   OR disposed_quantity > requested_quantity;

SELECT 'invalid_disposition_quantities' AS violation
FROM cloudmold_inventory_scrap_disposition_line
WHERE disposed_quantity <= 0
   OR cumulative_disposed_quantity < disposed_quantity;

SELECT 'missing_inventory_scrap_menu' AS violation
WHERE NOT EXISTS (
  SELECT 1 FROM system_menu
  WHERE id = 9100000000507
    AND permission = 'cloudmold:warehouse:inventory-scrap:query'
    AND deleted = b'0'
);

SELECT 'missing_inventory_scrap_query_button' AS violation
WHERE NOT EXISTS (
  SELECT 1 FROM system_menu
  WHERE id = 9100000000508
    AND permission = 'cloudmold:warehouse:inventory-scrap:query'
    AND deleted = b'0'
);

SELECT 'missing_inventory_scrap_command_button' AS violation
WHERE NOT EXISTS (
  SELECT 1 FROM system_menu
  WHERE id = 9100000000509
    AND permission = 'cloudmold:warehouse:inventory-scrap:command'
    AND deleted = b'0'
);
