-- DreamPlant normalized knowledge and autonomous worker invariants. Every query must return zero rows.

SELECT tenant_id,map_key,version FROM cloudmold_dreamplant_snapshot
WHERE canonical_hash_verified<>b'1' OR LOWER(SHA2(payload_json,256))<>payload_sha256;

SELECT tenant_id,exploration_run_id,status FROM cloudmold_dreamplant_exploration
WHERE outcome_hash_verified=b'1' AND (outcome_json IS NULL OR evidence_ref<>CONCAT('sha256:',LOWER(SHA2(outcome_json,256))));

SELECT tenant_id,exploration_run_id,status,lease_owner,lease_until FROM cloudmold_dreamplant_exploration
WHERE (status='RUNNING' AND (lease_owner IS NULL OR lease_until IS NULL))
   OR (status<>'RUNNING' AND (lease_owner IS NOT NULL OR lease_until IS NOT NULL));

SELECT tenant_id,exploration_run_id,status FROM cloudmold_dreamplant_exploration
WHERE status IN ('SUCCEEDED','FAILED','NEEDS_REVIEW','CANCELLED') AND completed_at IS NULL;

SELECT 'CAPABILITY' AS asset_type,tenant_id,map_key,asset_id FROM cloudmold_dreamplant_capability WHERE LOWER(SHA2(details_json,256))<>details_sha256
UNION ALL SELECT 'PRODUCT',tenant_id,map_key,asset_id FROM cloudmold_dreamplant_product WHERE LOWER(SHA2(details_json,256))<>details_sha256
UNION ALL SELECT 'OPERATION_AGENT',tenant_id,map_key,asset_id FROM cloudmold_dreamplant_operation_agent WHERE LOWER(SHA2(details_json,256))<>details_sha256
UNION ALL SELECT 'SOLUTION',tenant_id,map_key,asset_id FROM cloudmold_dreamplant_solution WHERE LOWER(SHA2(details_json,256))<>details_sha256
UNION ALL SELECT 'AGENT_ROLE',tenant_id,map_key,asset_id FROM cloudmold_dreamplant_agent_role WHERE LOWER(SHA2(details_json,256))<>details_sha256
UNION ALL SELECT 'PHASE_ROADMAP',tenant_id,map_key,asset_id FROM cloudmold_dreamplant_phase_roadmap WHERE LOWER(SHA2(details_json,256))<>details_sha256;

SELECT r.tenant_id,r.map_key,r.asset_type,r.asset_id,r.asset_version
FROM cloudmold_dreamplant_asset_revision r
WHERE LOWER(SHA2(r.details_json,256))<>r.details_sha256;

SELECT r.tenant_id,r.map_key,r.relation_id FROM cloudmold_dreamplant_product_capability r
LEFT JOIN cloudmold_dreamplant_product p ON p.tenant_id=r.tenant_id AND p.map_key=r.map_key AND p.asset_id=r.from_asset_id
LEFT JOIN cloudmold_dreamplant_capability c ON c.tenant_id=r.tenant_id AND c.map_key=r.map_key AND c.asset_id=r.to_asset_id
WHERE p.asset_id IS NULL OR c.asset_id IS NULL;

SELECT r.tenant_id,r.map_key,r.relation_id FROM cloudmold_dreamplant_operation_agent_product r
LEFT JOIN cloudmold_dreamplant_operation_agent a ON a.tenant_id=r.tenant_id AND a.map_key=r.map_key AND a.asset_id=r.from_asset_id
LEFT JOIN cloudmold_dreamplant_product p ON p.tenant_id=r.tenant_id AND p.map_key=r.map_key AND p.asset_id=r.to_asset_id
WHERE a.asset_id IS NULL OR p.asset_id IS NULL;

SELECT r.tenant_id,r.map_key,r.relation_id FROM cloudmold_dreamplant_solution_operation_agent r
LEFT JOIN cloudmold_dreamplant_solution s ON s.tenant_id=r.tenant_id AND s.map_key=r.map_key AND s.asset_id=r.from_asset_id
LEFT JOIN cloudmold_dreamplant_operation_agent a ON a.tenant_id=r.tenant_id AND a.map_key=r.map_key AND a.asset_id=r.to_asset_id
WHERE s.asset_id IS NULL OR a.asset_id IS NULL;
