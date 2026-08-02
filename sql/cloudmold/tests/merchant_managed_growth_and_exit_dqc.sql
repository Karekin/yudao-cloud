-- Every query must return zero. Run against the canonical local/test schema only.

SELECT COUNT(*) AS merchant_invitation_usage_violation
FROM cloudmold_merchant_managed_invitation
WHERE (`status`='USED' AND (`used_admission_id` IS NULL OR `used_at` IS NULL))
   OR (`status`='ISSUED' AND (`used_admission_id` IS NOT NULL OR `used_at` IS NOT NULL));

SELECT COUNT(*) AS merchant_buyer_assignment_orphan_violation
FROM cloudmold_merchant_buyer_assignment x
LEFT JOIN cloudmold_merchant_managed_admission a
  ON a.tenant_id=x.tenant_id AND a.admission_id=x.admission_id
LEFT JOIN cloudmold_merchant_factory_inspection_task t
  ON t.tenant_id=x.tenant_id AND t.inspection_task_id=x.inspection_task_id
WHERE a.admission_id IS NULL OR t.inspection_task_id IS NULL;

SELECT COUNT(*) AS merchant_probation_gate_count_violation
FROM cloudmold_merchant_probation_assessment
WHERE gate_count <> 3;

SELECT COUNT(*) AS merchant_scorecard_item_count_violation
FROM cloudmold_merchant_monthly_scorecard
WHERE item_count <> 9;

SELECT COUNT(*) AS merchant_exit_precedence_violation
FROM cloudmold_merchant_grade_decision g
JOIN cloudmold_merchant_exit_decision e
  ON e.tenant_id=g.tenant_id AND e.merchant_id=g.merchant_id
WHERE e.decision_status='APPROVED'
  AND g.decision_status='APPROVED'
  AND g.created_at >= e.created_at;
