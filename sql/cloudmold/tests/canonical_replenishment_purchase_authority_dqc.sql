-- Expected result: every query returns zero rows.

SELECT proposal_id AS invalid_purchase_authority_proposal_id
FROM cloudmold_replenishment_execution_proposal
WHERE (target_type = 'PURCHASE_REQUEST'
       AND (legal_entity_id IS NULL OR legal_entity_id = ''
         OR tax_calculation_policy_code NOT REGEXP '^[A-Z][A-Z0-9_]{0,63}$'
         OR rounding_policy_code NOT REGEXP '^[A-Z][A-Z0-9_]{0,63}$'
         OR valuation_policy_id IS NULL OR valuation_policy_id = ''
         OR valuation_policy_version IS NULL OR valuation_policy_version = ''
         OR valuation_policy_hash NOT REGEXP '^[0-9a-f]{64}$'))
   OR (target_type = 'TRANSFER_REQUEST'
       AND (legal_entity_id IS NOT NULL
         OR tax_calculation_policy_code IS NOT NULL
         OR rounding_policy_code IS NOT NULL
         OR valuation_policy_id IS NOT NULL
         OR valuation_policy_version IS NOT NULL
         OR valuation_policy_hash IS NOT NULL));

SELECT expected.column_name AS missing_authority_column
FROM (
    SELECT 'legal_entity_id' column_name
    UNION ALL SELECT 'tax_calculation_policy_code'
    UNION ALL SELECT 'rounding_policy_code'
    UNION ALL SELECT 'valuation_policy_id'
    UNION ALL SELECT 'valuation_policy_version'
    UNION ALL SELECT 'valuation_policy_hash'
) expected
LEFT JOIN information_schema.columns actual
  ON actual.table_schema = DATABASE()
 AND actual.table_name = 'cloudmold_replenishment_execution_proposal'
 AND actual.column_name = expected.column_name
WHERE actual.column_name IS NULL;
