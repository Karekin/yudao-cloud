SELECT 'partner_marketing_amount_conservation' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_partner_marketing_case
WHERE gross_settlement_amount_minor IS NOT NULL
  AND (
    platform_fee_amount_minor IS NULL
    OR tax_withholding_amount_minor IS NULL
    OR net_payable_amount_minor IS NULL
    OR gross_settlement_amount_minor <> platform_fee_amount_minor + tax_withholding_amount_minor + net_payable_amount_minor
    OR net_payable_amount_minor <= 0
  );

SELECT 'partner_marketing_maker_checker_drift' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_partner_marketing_case
WHERE settlement_approved_by_principal_id IS NOT NULL
  AND (
    settlement_approved_by_principal_id = creator_principal_id
    OR settlement_approved_by_principal_id = settlement_requested_by_principal_id
  );

SELECT 'partner_marketing_attribution_reference_gap' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_partner_marketing_case
WHERE status IN ('SETTLEMENT_PENDING_APPROVAL','SETTLEMENT_APPROVED','SETTLEMENT_PAID','CLOSED')
  AND (
    attributed_order_count IS NULL OR attributed_order_count <= 0
    OR attributed_order_id IS NULL
    OR attributed_payment_id IS NULL
    OR attribution_source_ref IS NULL
  );

SELECT 'partner_marketing_closed_case_invariant_gap' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_partner_marketing_case
WHERE status='CLOSED'
  AND (
    closed_at IS NULL
    OR settlement_paid_at IS NULL
    OR settlement_reference IS NULL
    OR settlement_approval_evidence_sha256 IS NULL
    OR settlement_payment_evidence_sha256 IS NULL
    OR disclosure_verified <> b'1'
    OR external_publish_ref IS NULL
    OR external_publish_url IS NULL
    OR publish_verified_at IS NULL
    OR attributed_order_id IS NULL
    OR attributed_payment_id IS NULL
    OR attribution_source_ref IS NULL
  );

SELECT 'partner_marketing_history_version_drift' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_partner_marketing_case c
LEFT JOIN (
    SELECT tenant_id,case_id,MAX(aggregate_version) AS max_version
    FROM cloudmold_partner_marketing_case_history
    GROUP BY tenant_id,case_id
) h
ON h.tenant_id = c.tenant_id AND h.case_id = c.case_id
WHERE h.max_version IS NULL OR h.max_version <> c.version;
