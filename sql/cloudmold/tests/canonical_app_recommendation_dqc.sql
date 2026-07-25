SELECT 'duplicate recommendation token' AS issue, tenant_id, decision_token, COUNT(*) AS duplicate_count
FROM cloudmold_app_recommendation_decision
GROUP BY tenant_id, decision_token
HAVING COUNT(*) > 1;

SELECT 'expired recommendation still active' AS issue, tenant_id, decision_id, status
FROM cloudmold_app_recommendation_decision
WHERE status = 'ACTIVE' AND expires_at <= UTC_TIMESTAMP(6);

SELECT 'recommendation item rank conflict' AS issue, tenant_id, decision_id, rank_no, COUNT(*) AS duplicate_count
FROM cloudmold_app_recommendation_item
GROUP BY tenant_id, decision_id, rank_no
HAVING COUNT(*) > 1;
