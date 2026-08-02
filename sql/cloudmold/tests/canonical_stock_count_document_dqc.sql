SET NAMES utf8mb4;

SELECT 'missing_stock_count_tables' AS check_name,
       COUNT(*) AS violations
FROM (
         SELECT 'cloudmold_stock_count_operation' AS table_name
         UNION ALL SELECT 'cloudmold_stock_count'
         UNION ALL SELECT 'cloudmold_stock_count_line'
         UNION ALL SELECT 'cloudmold_stock_count_execution_batch'
         UNION ALL SELECT 'cloudmold_stock_count_execution_line'
         UNION ALL SELECT 'cloudmold_stock_count_difference_approval'
         UNION ALL SELECT 'cloudmold_stock_count_status_history'
         UNION ALL SELECT 'cloudmold_inventory_stock_count_adjustment_operation'
         UNION ALL SELECT 'cloudmold_inventory_stock_count_adjustment_v3'
     ) expected
LEFT JOIN information_schema.tables actual
       ON actual.table_schema = DATABASE()
      AND actual.table_name = expected.table_name
WHERE actual.table_name IS NULL;

SELECT 'missing_stock_count_foreign_keys' AS check_name,
       COUNT(*) AS violations
FROM (
         SELECT 'fk_cloudmold_stock_count_line_stock_count' AS constraint_name
         UNION ALL SELECT 'fk_cloudmold_stock_count_execution_batch_stock_count'
         UNION ALL SELECT 'fk_cloudmold_stock_count_execution_line_batch'
         UNION ALL SELECT 'fk_cloudmold_stock_count_execution_line_line'
         UNION ALL SELECT 'fk_cloudmold_stock_count_difference_approval_stock_count'
         UNION ALL SELECT 'fk_cloudmold_stock_count_status_history_operation'
         UNION ALL SELECT 'fk_cloudmold_stock_count_status_history_stock_count'
         UNION ALL SELECT 'fk_cloudmold_inventory_stock_count_adjustment_stock_count'
         UNION ALL SELECT 'fk_cloudmold_inventory_stock_count_adjustment_line'
         UNION ALL SELECT 'fk_cloudmold_inventory_stock_count_adjustment_ledger_tx'
     ) expected
LEFT JOIN information_schema.table_constraints actual
       ON actual.table_schema = DATABASE()
      AND actual.constraint_name = expected.constraint_name
      AND actual.constraint_type = 'FOREIGN KEY'
WHERE actual.constraint_name IS NULL;

SELECT 'stock_count_ledger_command_constraint' AS check_name,
       CASE WHEN COUNT(*) = 1
                  AND MAX(check_clause) LIKE '%STOCK_COUNT_ADJUST%'
            THEN 0 ELSE 1 END AS violations
FROM information_schema.check_constraints
WHERE constraint_schema = DATABASE()
  AND constraint_name = 'ck_cm_inv_v3_tx_command';

SELECT 'missing_stock_count_ledger_operation_owner' AS check_name,
       CASE WHEN COUNT(*) = 1 THEN 0 ELSE 1 END AS violations
FROM information_schema.table_constraints
WHERE constraint_schema = DATABASE()
  AND table_name = 'cloudmold_inventory_ledger_transaction_v3'
  AND constraint_name = 'fk_cm_inv_v3_tx_count_adjustment_op'
  AND constraint_type = 'FOREIGN KEY';

SELECT 'missing_stock_count_unique_keys' AS check_name,
       COUNT(*) AS violations
FROM (
         SELECT 'uk_cloudmold_stock_count_operation_tenant_idem' AS index_name
         UNION ALL SELECT 'uk_cloudmold_stock_count_code'
         UNION ALL SELECT 'uk_cloudmold_stock_count_line_no'
         UNION ALL SELECT 'uk_cloudmold_stock_count_execution_batch_no'
         UNION ALL SELECT 'uk_cloudmold_stock_count_status_version'
         UNION ALL SELECT 'uk_cloudmold_inventory_stock_count_adjustment_line'
     ) expected
LEFT JOIN information_schema.statistics actual
       ON actual.table_schema = DATABASE()
      AND actual.index_name = expected.index_name
WHERE actual.index_name IS NULL;

SELECT 'stock_count_check_constraints' AS check_name,
       COUNT(*) AS violations
FROM (
         SELECT 'chk_cloudmold_stock_count_status' AS constraint_name
         UNION ALL SELECT 'chk_cloudmold_stock_count_line_status'
         UNION ALL SELECT 'chk_cloudmold_stock_count_execution_batch_status'
         UNION ALL SELECT 'chk_cloudmold_inventory_stock_count_adjustment_status'
     ) expected
LEFT JOIN information_schema.check_constraints actual
       ON actual.constraint_schema = DATABASE()
      AND actual.constraint_name = expected.constraint_name
WHERE actual.constraint_name IS NULL;
