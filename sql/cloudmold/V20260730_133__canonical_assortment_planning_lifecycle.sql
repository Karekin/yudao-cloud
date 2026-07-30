CREATE TABLE IF NOT EXISTS `cloudmold_assortment_planning_operation` (
  `operation_id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `idempotency_key` varchar(128) NOT NULL,
  `command_type` varchar(64) NOT NULL,
  `request_hash` char(64) NOT NULL,
  `attempt_token` char(36) NOT NULL,
  `status` tinyint NOT NULL,
  `aggregate_type` varchar(64) DEFAULT NULL,
  `aggregate_id` varchar(128) DEFAULT NULL,
  `result_json` json DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`operation_id`),
  UNIQUE KEY `uk_assortment_planning_operation` (`tenant_id`,`idempotency_key`),
  CONSTRAINT `ck_assortment_planning_operation_status` CHECK (`status` IN (0,10))
) ENGINE=InnoDB COMMENT='Idempotent canonical assortment planning command envelope';

CREATE TABLE IF NOT EXISTS `cloudmold_assortment_wave` (
  `wave_id` varchar(128) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `wave_code` varchar(64) NOT NULL,
  `run_id` varchar(128) NOT NULL,
  `planning_year` int NOT NULL,
  `season_code` varchar(32) NOT NULL,
  `category_code` varchar(64) NOT NULL,
  `trend_brief` varchar(1000) NOT NULL,
  `target_audience` varchar(512) NOT NULL,
  `target_style_count` int NOT NULL,
  `target_price_floor_minor` bigint NOT NULL,
  `target_price_ceiling_minor` bigint NOT NULL,
  `target_gross_margin_bps` int NOT NULL,
  `max_return_rate_bps` int NOT NULL,
  `launch_start_date` date NOT NULL,
  `launch_end_date` date NOT NULL,
  `currency_code` char(3) NOT NULL,
  `candidate_count` int NOT NULL DEFAULT 0,
  `evaluated_candidate_count` int NOT NULL DEFAULT 0,
  `selected_style_count` int NOT NULL DEFAULT 0,
  `decision_policy_version` varchar(128) DEFAULT NULL,
  `decision_evidence_sha256` char(64) DEFAULT NULL,
  `decision_summary` varchar(2000) DEFAULT NULL,
  `approved_by_principal_id` varchar(128) DEFAULT NULL,
  `approval_evidence_sha256` char(64) DEFAULT NULL,
  `launch_calendar_ref` varchar(128) DEFAULT NULL,
  `downstream_handoff_ref` varchar(128) DEFAULT NULL,
  `publication_evidence_sha256` char(64) DEFAULT NULL,
  `status` varchar(32) NOT NULL,
  `reason_code` varchar(64) DEFAULT NULL,
  `version` bigint NOT NULL,
  `selected_at` datetime(6) DEFAULT NULL,
  `approved_at` datetime(6) DEFAULT NULL,
  `published_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`wave_id`),
  UNIQUE KEY `uk_assortment_wave_id` (`tenant_id`,`wave_id`),
  UNIQUE KEY `uk_assortment_wave_code` (`tenant_id`,`wave_code`),
  KEY `idx_assortment_wave_status` (`tenant_id`,`status`,`updated_at`),
  CONSTRAINT `ck_assortment_wave_status`
    CHECK (`status` IN ('DRAFT','BUILDING','SELECTED','APPROVED','PUBLISHED')),
  CONSTRAINT `ck_assortment_wave_targets`
    CHECK (`target_style_count` BETWEEN 2 AND 20
      AND `target_price_floor_minor` > 0
      AND `target_price_ceiling_minor` > `target_price_floor_minor`
      AND `target_gross_margin_bps` BETWEEN 1 AND 9999
      AND `max_return_rate_bps` BETWEEN 1 AND 9999
      AND `launch_end_date` >= `launch_start_date`),
  CONSTRAINT `ck_assortment_wave_counts`
    CHECK (`candidate_count` >= 0
      AND `evaluated_candidate_count` BETWEEN 0 AND `candidate_count`
      AND `selected_style_count` BETWEEN 0 AND `candidate_count`),
  CONSTRAINT `ck_assortment_wave_approval`
    CHECK (`status` NOT IN ('APPROVED','PUBLISHED')
      OR (`approved_by_principal_id` IS NOT NULL
        AND `approval_evidence_sha256` IS NOT NULL
        AND `selected_style_count` = `target_style_count`)),
  CONSTRAINT `ck_assortment_wave_published`
    CHECK (`status` <> 'PUBLISHED'
      OR (`published_at` IS NOT NULL
        AND `launch_calendar_ref` IS NOT NULL
        AND `downstream_handoff_ref` IS NOT NULL
        AND `publication_evidence_sha256` IS NOT NULL)),
  CONSTRAINT `ck_assortment_wave_version` CHECK (`version` > 0)
) ENGINE=InnoDB COMMENT='Canonical assortment wave and launch portfolio authority';

CREATE TABLE IF NOT EXISTS `cloudmold_assortment_candidate` (
  `candidate_id` varchar(128) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `wave_id` varchar(128) NOT NULL,
  `candidate_code` varchar(64) NOT NULL,
  `product_concept` varchar(512) NOT NULL,
  `source_signal_type` varchar(32) NOT NULL,
  `source_signal_ref` varchar(128) NOT NULL,
  `price_band_code` varchar(16) NOT NULL,
  `target_price_minor` bigint NOT NULL,
  `expected_unit_cost_minor` bigint NOT NULL,
  `expected_gross_margin_bps` int NOT NULL,
  `trend_score` int DEFAULT NULL,
  `demand_score` int DEFAULT NULL,
  `audience_fit_score` int DEFAULT NULL,
  `supply_risk_score` int DEFAULT NULL,
  `predicted_return_rate_bps` int DEFAULT NULL,
  `weighted_score` int DEFAULT NULL,
  `evidence_sha256` char(64) DEFAULT NULL,
  `rationale` varchar(1000) DEFAULT NULL,
  `status` varchar(32) NOT NULL,
  `version` bigint NOT NULL,
  `evaluated_at` datetime(6) DEFAULT NULL,
  `selected_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`candidate_id`),
  UNIQUE KEY `uk_assortment_candidate_id` (`tenant_id`,`candidate_id`),
  UNIQUE KEY `uk_assortment_candidate_code` (`tenant_id`,`wave_id`,`candidate_code`),
  KEY `idx_assortment_candidate_status` (`tenant_id`,`wave_id`,`status`,`weighted_score`),
  CONSTRAINT `fk_assortment_candidate_wave`
    FOREIGN KEY (`tenant_id`,`wave_id`)
    REFERENCES `cloudmold_assortment_wave` (`tenant_id`,`wave_id`),
  CONSTRAINT `ck_assortment_candidate_status`
    CHECK (`status` IN ('DISCOVERED','EVALUATED','SELECTED','REJECTED')),
  CONSTRAINT `ck_assortment_candidate_price`
    CHECK (`target_price_minor` > 0
      AND `expected_unit_cost_minor` > 0
      AND `expected_unit_cost_minor` < `target_price_minor`
      AND `expected_gross_margin_bps` BETWEEN 1 AND 9999),
  CONSTRAINT `ck_assortment_candidate_scores`
    CHECK ((`status` = 'DISCOVERED'
      AND `trend_score` IS NULL
      AND `demand_score` IS NULL
      AND `audience_fit_score` IS NULL
      AND `supply_risk_score` IS NULL
      AND `predicted_return_rate_bps` IS NULL
      AND `weighted_score` IS NULL)
      OR (`status` IN ('EVALUATED','SELECTED','REJECTED')
        AND `trend_score` BETWEEN 0 AND 100
        AND `demand_score` BETWEEN 0 AND 100
        AND `audience_fit_score` BETWEEN 0 AND 100
        AND `supply_risk_score` BETWEEN 0 AND 100
        AND `predicted_return_rate_bps` BETWEEN 0 AND 10000
        AND `weighted_score` BETWEEN 0 AND 100
        AND `evidence_sha256` IS NOT NULL
        AND `rationale` IS NOT NULL)),
  CONSTRAINT `ck_assortment_candidate_version` CHECK (`version` > 0)
) ENGINE=InnoDB COMMENT='Assortment candidates with auditable AI scores and selection outcome';

CREATE TABLE IF NOT EXISTS `cloudmold_assortment_wave_history` (
  `history_id` bigint NOT NULL AUTO_INCREMENT,
  `wave_id` varchar(128) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `aggregate_version` bigint NOT NULL,
  `command_type` varchar(64) NOT NULL,
  `from_status` varchar(32) DEFAULT NULL,
  `to_status` varchar(32) NOT NULL,
  `actor_principal_id` varchar(128) NOT NULL,
  `reason_code` varchar(64) DEFAULT NULL,
  `evidence_sha256` char(64) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`history_id`),
  UNIQUE KEY `uk_assortment_wave_history_version`
    (`tenant_id`,`wave_id`,`aggregate_version`),
  KEY `idx_assortment_wave_history_status`
    (`tenant_id`,`wave_id`,`to_status`,`created_at`),
  CONSTRAINT `fk_assortment_wave_history_wave`
    FOREIGN KEY (`tenant_id`,`wave_id`)
    REFERENCES `cloudmold_assortment_wave` (`tenant_id`,`wave_id`),
  CONSTRAINT `ck_assortment_wave_history_status`
    CHECK (`to_status` IN ('DRAFT','BUILDING','SELECTED','APPROVED','PUBLISHED')),
  CONSTRAINT `ck_assortment_wave_history_version` CHECK (`aggregate_version` > 0)
) ENGINE=InnoDB COMMENT='Append-only assortment wave decision and status history';
