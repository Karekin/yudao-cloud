-- P6 first slice: immutable, policy-bound robust scenario recommendations.
-- Recommendations are advisory and never select a scenario or authorize execution.

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_supply_plan_scenario_recommendation (
    recommendation_id VARCHAR(128) NOT NULL,
    tenant_id BIGINT NOT NULL,
    plan_id VARCHAR(128) NOT NULL,
    recommended_scenario_id VARCHAR(128) NOT NULL,
    candidate_set_sha256 CHAR(64) NOT NULL,
    candidate_scenario_ids_json LONGTEXT NOT NULL,
    policy_sha256 CHAR(64) NOT NULL,
    target_service_level_floor_basis_points INT NOT NULL,
    max_projected_cost_minor BIGINT NOT NULL,
    demand_stress_basis_points INT NOT NULL,
    supply_availability_basis_points INT NOT NULL,
    worst_case_service_level_basis_points INT NOT NULL,
    projected_cost_minor BIGINT NOT NULL,
    projected_shortage_quantity DECIMAL(24, 6) NOT NULL,
    sensitivity_basis_points INT NOT NULL,
    violation_count INT NOT NULL,
    constraint_violations_json LONGTEXT NOT NULL,
    rationale_json LONGTEXT NOT NULL,
    solver_type VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL,
    recommended_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (recommendation_id),
    UNIQUE KEY uk_scenario_recommendation_tenant_id (tenant_id, recommendation_id),
    UNIQUE KEY uk_scenario_recommendation_evidence
        (tenant_id, plan_id, candidate_set_sha256, policy_sha256),
    KEY idx_scenario_recommendation_plan
        (tenant_id, plan_id, status, recommended_at),
    CONSTRAINT fk_scenario_recommendation_plan
        FOREIGN KEY (tenant_id, plan_id)
        REFERENCES cloudmold_supply_plan (tenant_id, plan_id),
    CONSTRAINT fk_scenario_recommendation_selected
        FOREIGN KEY (tenant_id, plan_id, recommended_scenario_id)
        REFERENCES cloudmold_supply_plan_scenario (tenant_id, plan_id, scenario_id),
    CONSTRAINT ck_scenario_recommendation_hashes CHECK (
        candidate_set_sha256 REGEXP '^[0-9a-f]{64}$'
        AND policy_sha256 REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT ck_scenario_recommendation_json CHECK (
        JSON_VALID(candidate_scenario_ids_json)
        AND JSON_TYPE(CAST(candidate_scenario_ids_json AS JSON)) = 'ARRAY'
        AND JSON_LENGTH(candidate_scenario_ids_json) BETWEEN 2 AND 20
        AND JSON_VALID(constraint_violations_json)
        AND JSON_TYPE(CAST(constraint_violations_json AS JSON)) = 'ARRAY'
        AND JSON_VALID(rationale_json)
        AND JSON_TYPE(CAST(rationale_json AS JSON)) = 'ARRAY'),
    CONSTRAINT ck_scenario_recommendation_policy CHECK (
        target_service_level_floor_basis_points BETWEEN 0 AND 10000
        AND max_projected_cost_minor >= 0
        AND demand_stress_basis_points BETWEEN 10000 AND 20000
        AND supply_availability_basis_points BETWEEN 0 AND 10000),
    CONSTRAINT ck_scenario_recommendation_result CHECK (
        worst_case_service_level_basis_points BETWEEN 0 AND 10000
        AND projected_cost_minor >= 0
        AND projected_shortage_quantity >= 0
        AND sensitivity_basis_points BETWEEN 0 AND 10000
        AND violation_count >= 0),
    CONSTRAINT ck_scenario_recommendation_state CHECK (
        solver_type = 'ROBUST_LEXICOGRAPHIC_V1'
        AND status = 'PROPOSED'
        AND version = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Immutable advisory result from bounded robust supply-scenario comparison';
