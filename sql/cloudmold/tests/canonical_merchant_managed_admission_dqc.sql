-- Every query must return zero. Run against the canonical local/test schema only.

SELECT COUNT(*) AS merchant_managed_admission_orphan_violation
FROM cloudmold_merchant_managed_admission a
LEFT JOIN cloudmold_merchant_onboarding_application app
  ON app.tenant_id=a.tenant_id AND app.application_id=a.application_id
LEFT JOIN cloudmold_merchant_account m
  ON m.tenant_id=a.tenant_id AND m.merchant_id=a.merchant_id
LEFT JOIN cloudmold_merchant_shop s
  ON s.tenant_id=a.tenant_id AND s.shop_id=a.shop_id
WHERE app.application_id IS NULL OR m.merchant_id IS NULL OR s.shop_id IS NULL;

SELECT COUNT(*) AS merchant_managed_diagnostic_without_evidence_violation
FROM cloudmold_merchant_ai_diagnostic d
LEFT JOIN cloudmold_merchant_managed_evidence_package p
  ON p.tenant_id=d.tenant_id AND p.evidence_package_id=d.evidence_package_id
WHERE p.evidence_package_id IS NULL;

SELECT COUNT(*) AS merchant_managed_inspection_without_diagnostic_violation
FROM cloudmold_merchant_factory_inspection_task t
LEFT JOIN cloudmold_merchant_ai_diagnostic d
  ON d.tenant_id=t.tenant_id AND d.diagnostic_id=t.diagnostic_id
WHERE d.diagnostic_id IS NULL;

SELECT COUNT(*) AS merchant_managed_final_review_without_gate_violation
FROM cloudmold_merchant_managed_final_review r
LEFT JOIN cloudmold_merchant_managed_admission a
  ON a.tenant_id=r.tenant_id AND a.admission_id=r.admission_id
WHERE a.admission_id IS NULL
   OR a.status NOT IN ('FINAL_APPROVED','FINAL_REJECTED');

SELECT COUNT(*) AS merchant_managed_terminal_without_review_violation
FROM cloudmold_merchant_managed_admission
WHERE status IN ('FINAL_APPROVED','FINAL_REJECTED') AND final_review_id IS NULL;

SELECT COUNT(*) AS merchant_managed_inspection_state_contract_violation
FROM cloudmold_merchant_factory_inspection_task
WHERE status NOT IN (
  'PENDING_CLAIM','PENDING_SCHEDULE','PENDING_INSPECTION','PENDING_QA_INSPECTION',
  'PENDING_REMEDIATION','PENDING_FIRST_REVIEW','PENDING_FINAL_REVIEW','COMPLETED','CANCELLED'
);

SELECT COUNT(*) AS merchant_managed_inspection_evidence_gate_violation
FROM cloudmold_merchant_factory_inspection_task
WHERE status IN ('PENDING_QA_INSPECTION','PENDING_REMEDIATION','PENDING_FIRST_REVIEW',
                 'PENDING_FINAL_REVIEW','COMPLETED')
  AND (evidence_ref IS NULL OR TRIM(evidence_ref) = '');
