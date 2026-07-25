SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_app_product_review (
    review_id CHAR(36) NOT NULL,
    tenant_id BIGINT NOT NULL,
    buyer_principal_id VARCHAR(36) NOT NULL,
    order_id CHAR(36) NOT NULL,
    order_item_id CHAR(36) NOT NULL,
    payment_id CHAR(36) NOT NULL,
    fulfillment_id CHAR(36) NULL,
    listing_id CHAR(36) NOT NULL,
    listing_offer_id CHAR(36) NOT NULL,
    merchant_id VARCHAR(128) NOT NULL,
    shop_id VARCHAR(128) NOT NULL,
    canonical_spu_id CHAR(36) NOT NULL,
    canonical_sku_id CHAR(36) NOT NULL,
    product_score TINYINT NOT NULL,
    service_score TINYINT NOT NULL,
    logistics_score TINYINT NOT NULL,
    overall_score TINYINT NOT NULL,
    content_key_id VARCHAR(64) NOT NULL,
    content_iv VARBINARY(12) NOT NULL,
    content_ciphertext BLOB NOT NULL,
    content_digest_sha256 CHAR(64) NOT NULL,
    public_summary VARCHAR(256) NOT NULL,
    moderation_status VARCHAR(32) NOT NULL,
    moderation_policy VARCHAR(64) NOT NULL,
    approved_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (review_id),
    UNIQUE KEY uk_app_product_review_order_item (tenant_id, order_item_id),
    KEY idx_app_product_review_listing_public (tenant_id, listing_id, moderation_status, approved_at, created_at),
    KEY idx_app_product_review_product_public (tenant_id, canonical_spu_id, moderation_status, approved_at, created_at),
    CONSTRAINT ck_app_product_review_scores CHECK (
        product_score BETWEEN 1 AND 5
        AND service_score BETWEEN 1 AND 5
        AND logistics_score BETWEEN 1 AND 5
        AND overall_score BETWEEN 1 AND 5
    ),
    CONSTRAINT ck_app_product_review_digest CHECK (content_digest_sha256 REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT ck_app_product_review_moderation CHECK (
        moderation_status IN ('APPROVED','PENDING_MODERATION','HIDDEN','REJECTED')
    ),
    CONSTRAINT ck_app_product_review_approval_time CHECK (
        (moderation_status='APPROVED' AND approved_at IS NOT NULL)
        OR (moderation_status<>'APPROVED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Server-authoritative app product review with encrypted body and moderated public surface';
