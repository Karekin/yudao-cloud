CREATE TABLE IF NOT EXISTS cloudmold_engagement_operation (
    operation_id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    command_type VARCHAR(64) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    attempt_token CHAR(36) NOT NULL,
    status SMALLINT NOT NULL,
    aggregate_id CHAR(36) NULL,
    result_json JSON NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (operation_id),
    UNIQUE KEY uk_engagement_operation_tenant_key (tenant_id, idempotency_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_engagement_favorite (
    favorite_id CHAR(36) NOT NULL,
    tenant_id BIGINT NOT NULL,
    principal_id CHAR(36) NOT NULL,
    canonical_spu_id CHAR(36) NOT NULL,
    status VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL,
    source_system VARCHAR(64) NOT NULL,
    source_type VARCHAR(64) NOT NULL,
    source_id VARCHAR(128) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (favorite_id),
    UNIQUE KEY uk_engagement_favorite_business (tenant_id, principal_id, canonical_spu_id),
    UNIQUE KEY uk_engagement_favorite_tenant_id (tenant_id, favorite_id),
    KEY idx_engagement_favorite_spu_status (tenant_id, canonical_spu_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_engagement_favorite_behavior (
    behavior_id CHAR(36) NOT NULL,
    tenant_id BIGINT NOT NULL,
    favorite_id CHAR(36) NOT NULL,
    principal_id CHAR(36) NOT NULL,
    canonical_spu_id CHAR(36) NOT NULL,
    behavior_type VARCHAR(16) NOT NULL,
    favorite_version BIGINT NOT NULL,
    source_system VARCHAR(64) NOT NULL,
    source_type VARCHAR(64) NOT NULL,
    source_id VARCHAR(128) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (behavior_id),
    UNIQUE KEY uk_engagement_favorite_behavior_version (tenant_id, favorite_id, favorite_version),
    KEY idx_engagement_favorite_behavior_principal_time (tenant_id, principal_id, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_engagement_notification_campaign (
    campaign_id CHAR(36) NOT NULL,
    tenant_id BIGINT NOT NULL,
    campaign_code VARCHAR(64) NOT NULL,
    campaign_name VARCHAR(128) NOT NULL,
    channel VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL,
    source_system VARCHAR(64) NOT NULL,
    source_type VARCHAR(64) NOT NULL,
    source_id VARCHAR(128) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (campaign_id),
    UNIQUE KEY uk_engagement_campaign_business (tenant_id, campaign_code),
    UNIQUE KEY uk_engagement_campaign_tenant_id (tenant_id, campaign_id),
    KEY idx_engagement_campaign_status_channel (tenant_id, status, channel)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_engagement_notification_delivery (
    delivery_id CHAR(36) NOT NULL,
    tenant_id BIGINT NOT NULL,
    delivery_key VARCHAR(128) NOT NULL,
    campaign_id CHAR(36) NOT NULL,
    principal_id CHAR(36) NOT NULL,
    channel VARCHAR(16) NOT NULL,
    destination_token VARCHAR(512) NOT NULL,
    status VARCHAR(16) NOT NULL,
    attempt_count INT NOT NULL,
    receipt_count INT NOT NULL,
    version BIGINT NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (delivery_id),
    UNIQUE KEY uk_engagement_delivery_business (tenant_id, delivery_key),
    UNIQUE KEY uk_engagement_delivery_tenant_id (tenant_id, delivery_id),
    KEY idx_engagement_delivery_campaign_status (tenant_id, campaign_id, status),
    KEY idx_engagement_delivery_principal_time (tenant_id, principal_id, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_engagement_notification_attempt (
    attempt_id CHAR(36) NOT NULL,
    tenant_id BIGINT NOT NULL,
    delivery_id CHAR(36) NOT NULL,
    attempt_no INT NOT NULL,
    provider_code VARCHAR(64) NOT NULL,
    provider_reference VARCHAR(128) NULL,
    outcome VARCHAR(16) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (attempt_id),
    UNIQUE KEY uk_engagement_attempt_sequence (tenant_id, delivery_id, attempt_no),
    KEY idx_engagement_attempt_provider_reference (tenant_id, provider_code, provider_reference)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_engagement_notification_receipt (
    receipt_id CHAR(36) NOT NULL,
    tenant_id BIGINT NOT NULL,
    delivery_id CHAR(36) NOT NULL,
    external_receipt_id VARCHAR(128) NOT NULL,
    receipt_status VARCHAR(16) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (receipt_id),
    UNIQUE KEY uk_engagement_receipt_external (tenant_id, external_receipt_id),
    KEY idx_engagement_receipt_delivery_time (tenant_id, delivery_id, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_engagement_community_content (
    content_id CHAR(36) NOT NULL,
    tenant_id BIGINT NOT NULL,
    author_principal_id CHAR(36) NOT NULL,
    content_type VARCHAR(16) NOT NULL,
    body_ref VARCHAR(512) NOT NULL,
    status VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL,
    source_system VARCHAR(64) NULL,
    source_type VARCHAR(64) NULL,
    source_id VARCHAR(128) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (content_id),
    UNIQUE KEY uk_engagement_content_tenant_id (tenant_id, content_id),
    KEY idx_engagement_content_author_status (tenant_id, author_principal_id, status, created_at),
    KEY idx_engagement_content_source (tenant_id, source_system, source_type, source_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_engagement_community_interaction (
    interaction_id CHAR(36) NOT NULL,
    tenant_id BIGINT NOT NULL,
    actor_principal_id CHAR(36) NOT NULL,
    interaction_type VARCHAR(16) NOT NULL,
    target_type VARCHAR(16) NOT NULL,
    target_id CHAR(36) NOT NULL,
    payload_ref VARCHAR(512) NULL,
    occurred_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (interaction_id),
    UNIQUE KEY uk_engagement_interaction_tenant_id (tenant_id, interaction_id),
    KEY idx_engagement_interaction_target_time (tenant_id, target_type, target_id, occurred_at),
    KEY idx_engagement_interaction_actor_time (tenant_id, actor_principal_id, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_engagement_moderation_case (
    moderation_case_id CHAR(36) NOT NULL,
    tenant_id BIGINT NOT NULL,
    content_id CHAR(36) NOT NULL,
    reporter_principal_id CHAR(36) NOT NULL,
    moderator_principal_id CHAR(36) NULL,
    report_reason_code VARCHAR(64) NOT NULL,
    evidence_ref VARCHAR(512) NULL,
    status VARCHAR(16) NOT NULL,
    decision VARCHAR(16) NULL,
    decision_reason_code VARCHAR(64) NULL,
    version BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (moderation_case_id),
    UNIQUE KEY uk_engagement_moderation_tenant_id (tenant_id, moderation_case_id),
    KEY idx_engagement_moderation_content_status (tenant_id, content_id, status),
    KEY idx_engagement_moderation_status_time (tenant_id, status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
