SELECT 'crm_pool_owner_invariant' AS violation, COUNT(*) AS violating_rows
FROM cloudmold_crm_customer
WHERE (pool_status='IN_POOL' AND owner_principal_id IS NOT NULL)
   OR (pool_status='OWNED' AND owner_principal_id IS NULL)
HAVING COUNT(*) > 0;

SELECT 'crm_raw_contact_field_forbidden' AS violation, COUNT(*) AS violating_columns
FROM information_schema.columns
WHERE table_schema=DATABASE() AND table_name IN ('cloudmold_crm_lead','cloudmold_crm_contact')
  AND column_name IN ('phone','mobile','email','telephone','address')
HAVING COUNT(*) > 0;

SELECT 'crm_legacy_authority_forbidden' AS violation, COUNT(*) AS violating_tables
FROM information_schema.tables
WHERE table_schema=DATABASE()
  AND table_name IN ('crm_product','crm_receivable','crm_receivable_plan')
HAVING COUNT(*) > 0;
