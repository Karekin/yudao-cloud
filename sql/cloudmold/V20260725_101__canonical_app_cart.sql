-- Canonical server-side cart. One cart per tenant + buyer Principal.
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_app_cart (
    cart_id VARCHAR(36) NOT NULL,
    tenant_id BIGINT NOT NULL,
    buyer_principal_id VARCHAR(36) NOT NULL,
    version BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (cart_id),
    UNIQUE KEY uk_app_cart_owner (tenant_id, buyer_principal_id),
    UNIQUE KEY uk_app_cart_tenant_cart (tenant_id, cart_id),
    CONSTRAINT ck_app_cart_version CHECK (version >= 0),
    CONSTRAINT ck_app_cart_status CHECK (status IN ('ACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Server-authoritative consumer cart header';

CREATE TABLE IF NOT EXISTS cloudmold_app_cart_item (
    line_id VARCHAR(36) NOT NULL,
    tenant_id BIGINT NOT NULL,
    cart_id VARCHAR(36) NOT NULL,
    buyer_principal_id VARCHAR(36) NOT NULL,
    listing_id VARCHAR(36) NOT NULL,
    listing_offer_id VARCHAR(36) NOT NULL,
    canonical_sku_id VARCHAR(36) NOT NULL,
    quantity DECIMAL(20,6) NOT NULL,
    selected TINYINT(1) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (line_id),
    UNIQUE KEY uk_app_cart_item_identity (tenant_id, cart_id, listing_id, listing_offer_id, canonical_sku_id),
    KEY idx_app_cart_item_cart (tenant_id, cart_id, updated_at),
    KEY idx_app_cart_item_selected (tenant_id, cart_id, selected, updated_at),
    CONSTRAINT fk_app_cart_item_header FOREIGN KEY (tenant_id, cart_id)
        REFERENCES cloudmold_app_cart (tenant_id, cart_id),
    CONSTRAINT ck_app_cart_item_quantity CHECK (quantity >= 1 AND quantity <= 99),
    CONSTRAINT ck_app_cart_item_selected CHECK (selected IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Server-authoritative consumer cart lines';
