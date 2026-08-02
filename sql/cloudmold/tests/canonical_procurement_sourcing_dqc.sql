SELECT 'procurement_award_reviewer_checker_drift' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_procurement_award a
JOIN cloudmold_procurement_award_line al ON al.tenant_id=a.tenant_id AND al.award_id=a.award_id
JOIN cloudmold_procurement_quotation_revision_line ql ON ql.tenant_id=al.tenant_id AND ql.revision_line_id=al.quotation_revision_line_id
JOIN cloudmold_procurement_evaluation_score es ON es.tenant_id=al.tenant_id
 AND es.quotation_revision_id=ql.revision_id AND es.policy_id=al.policy_id AND es.policy_version=al.policy_version
WHERE a.status='APPROVED' AND a.approved_by_principal_id=es.reviewer_principal_id;

SELECT 'procurement_active_revision_drift' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_procurement_quotation q
LEFT JOIN cloudmold_procurement_quotation_revision r ON r.tenant_id=q.tenant_id AND r.revision_id=q.active_revision_id
WHERE (q.active_revision_id IS NOT NULL AND (r.revision_id IS NULL OR r.status<>'SUBMITTED' OR r.revision_number<>q.latest_revision_number))
   OR q.latest_revision_number<>(SELECT MAX(rr.revision_number) FROM cloudmold_procurement_quotation_revision rr
                                  WHERE rr.tenant_id=q.tenant_id AND rr.quotation_id=q.quotation_id);

SELECT 'procurement_award_schedule_allocation_drift' AS check_name, COUNT(*) AS violation_count
FROM (
 SELECT ss.tenant_id,ss.sourcing_schedule_id,ss.requested_quantity,COALESCE(SUM(al.awarded_quantity),0) awarded_quantity
 FROM cloudmold_procurement_sourcing_schedule ss
 JOIN cloudmold_procurement_sourcing_event e ON e.tenant_id=ss.tenant_id AND e.event_id=ss.event_id AND e.status IN ('AWARDED','CLOSED')
 LEFT JOIN cloudmold_procurement_award a ON a.tenant_id=e.tenant_id AND a.event_id=e.event_id AND a.status='APPROVED'
 LEFT JOIN cloudmold_procurement_award_line al ON al.tenant_id=a.tenant_id AND al.award_id=a.award_id AND al.sourcing_schedule_id=ss.sourcing_schedule_id
 GROUP BY ss.tenant_id,ss.sourcing_schedule_id,ss.requested_quantity
 HAVING requested_quantity<>awarded_quantity
) drift;

SELECT 'procurement_award_evaluation_evidence_drift' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_procurement_award_line
WHERE evaluation_summary_sha256 NOT REGEXP '^[0-9a-f]{64}$'
   OR reviewer_evidence_sha256 NOT REGEXP '^[0-9a-f]{64}$'
   OR evaluation_weighted_score_bps NOT BETWEEN 0 AND 10000;

SELECT 'procurement_order_item_valuation_policy_drift' AS check_name, COUNT(*) AS violation_count
FROM cloudmold_procurement_order_item i
JOIN cloudmold_procurement_order o
  ON o.tenant_id=i.tenant_id AND o.order_id=i.order_id
WHERE o.status IN ('RELEASED','DISPATCHED','SUPPLIER_CONFIRMED','CLOSED')
  AND (i.valuation_policy_id IS NULL
    OR NOT REGEXP_LIKE(i.valuation_policy_id,'^[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}$','c')
    OR i.valuation_policy_version IS NULL
    OR NOT REGEXP_LIKE(i.valuation_policy_version,'^[A-Za-z0-9][A-Za-z0-9._:/-]{0,63}$','c')
    OR i.valuation_policy_hash IS NULL
    OR NOT REGEXP_LIKE(i.valuation_policy_hash,'^[0-9a-f]{64}$','c'));
