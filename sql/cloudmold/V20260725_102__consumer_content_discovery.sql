ALTER TABLE cloudmold_engagement_community_content
    ADD COLUMN body_key_id VARCHAR(128) NULL AFTER body_ref,
    ADD COLUMN body_iv BINARY(12) NULL AFTER body_key_id,
    ADD COLUMN body_ciphertext BLOB NULL AFTER body_iv,
    ADD COLUMN body_digest_sha256 CHAR(64) NULL AFTER body_ciphertext,
    ADD COLUMN canonical_spu_id CHAR(36) NULL AFTER body_digest_sha256,
    ADD COLUMN canonical_sku_id CHAR(36) NULL AFTER canonical_spu_id,
    ADD COLUMN listing_id CHAR(36) NULL AFTER canonical_sku_id,
    ADD COLUMN listing_offer_id CHAR(36) NULL AFTER listing_id,
    ADD KEY idx_engagement_content_listing_status (tenant_id, listing_id, status, created_at),
    ADD CONSTRAINT ck_engagement_content_encrypted_body CHECK (
        content_type <> 'POST'
        OR (
            body_key_id IS NOT NULL
            AND body_iv IS NOT NULL
            AND body_ciphertext IS NOT NULL
            AND body_digest_sha256 REGEXP '^[0-9a-f]{64}$'
        )
    ),
    ADD CONSTRAINT ck_engagement_content_product_link CHECK (
        content_type <> 'POST'
        OR (
            canonical_spu_id IS NOT NULL
            AND canonical_sku_id IS NOT NULL
            AND listing_id IS NOT NULL
            AND listing_offer_id IS NOT NULL
        )
    );

ALTER TABLE cloudmold_engagement_community_interaction
    ADD COLUMN payload_key_id VARCHAR(128) NULL AFTER payload_ref,
    ADD COLUMN payload_iv BINARY(12) NULL AFTER payload_key_id,
    ADD COLUMN payload_ciphertext BLOB NULL AFTER payload_iv,
    ADD COLUMN payload_digest_sha256 CHAR(64) NULL AFTER payload_ciphertext,
    ADD CONSTRAINT ck_engagement_comment_encrypted_payload CHECK (
        interaction_type <> 'COMMENT'
        OR (
            payload_key_id IS NOT NULL
            AND payload_iv IS NOT NULL
            AND payload_ciphertext IS NOT NULL
            AND payload_digest_sha256 REGEXP '^[0-9a-f]{64}$'
        )
    );

CREATE TABLE IF NOT EXISTS cloudmold_engagement_community_reaction_state (
    reaction_id CHAR(36) NOT NULL,
    tenant_id BIGINT NOT NULL,
    actor_principal_id CHAR(36) NOT NULL,
    reaction_type VARCHAR(16) NOT NULL,
    target_type VARCHAR(16) NOT NULL,
    target_id CHAR(36) NOT NULL,
    status VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (reaction_id),
    UNIQUE KEY uk_engagement_reaction_business (
        tenant_id, actor_principal_id, reaction_type, target_type, target_id
    ),
    UNIQUE KEY uk_engagement_reaction_tenant_id (tenant_id, reaction_id),
    KEY idx_engagement_reaction_target_status (
        tenant_id, reaction_type, target_type, target_id, status
    ),
    CONSTRAINT ck_engagement_reaction_type CHECK (reaction_type = 'LIKE'),
    CONSTRAINT ck_engagement_reaction_target CHECK (target_type = 'CONTENT'),
    CONSTRAINT ck_engagement_reaction_status CHECK (status IN ('ACTIVE', 'REMOVED')),
    CONSTRAINT ck_engagement_reaction_version CHECK (version > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

