-- Canonical consumer checkout snapshot. Buyer/tenant are always resolved server-side
-- from an authenticated Member -> Identity Principal mapping.
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_app_checkout (
    checkout_token VARCHAR(36) NOT NULL,
    tenant_id BIGINT NOT NULL,
    buyer_principal_id VARCHAR(36) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    listing_id VARCHAR(36) NOT NULL,
    listing_offer_id VARCHAR(36) NOT NULL,
    canonical_spu_id VARCHAR(36) NOT NULL,
    canonical_sku_id VARCHAR(36) NOT NULL,
    quantity DECIMAL(20,6) NOT NULL,
    unit_price_minor BIGINT NOT NULL,
    product_amount_minor BIGINT NOT NULL,
    shipping_amount_minor BIGINT NOT NULL,
    discount_amount_minor BIGINT NOT NULL,
    payable_amount_minor BIGINT NOT NULL,
    currency_code CHAR(3) NOT NULL,
    status VARCHAR(16) NOT NULL,
    order_id VARCHAR(36) NULL,
    version BIGINT NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (checkout_token),
    UNIQUE KEY uk_app_checkout_tenant_token (tenant_id, checkout_token),
    UNIQUE KEY uk_app_checkout_owner_idempotency (tenant_id, buyer_principal_id, idempotency_key),
    KEY idx_app_checkout_buyer_status (tenant_id, buyer_principal_id, status, updated_at),
    KEY idx_app_checkout_order (tenant_id, order_id),
    CONSTRAINT ck_app_checkout_hash CHECK (request_hash REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT ck_app_checkout_quantity CHECK (quantity > 0),
    CONSTRAINT ck_app_checkout_money CHECK (
        unit_price_minor >= 0 AND product_amount_minor >= 0
        AND shipping_amount_minor >= 0 AND discount_amount_minor >= 0
        AND payable_amount_minor >= 0
        AND payable_amount_minor = product_amount_minor + shipping_amount_minor - discount_amount_minor),
    CONSTRAINT ck_app_checkout_currency CHECK (currency_code = 'CNY'),
    CONSTRAINT ck_app_checkout_status CHECK (status IN ('PREVIEWED','ORDERED','EXPIRED')),
    CONSTRAINT ck_app_checkout_version CHECK (version > 0),
    CONSTRAINT ck_app_checkout_order_state CHECK (
        (status = 'ORDERED' AND order_id IS NOT NULL)
        OR (status <> 'ORDERED' AND order_id IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Server-authoritative consumer checkout snapshot';

CREATE TABLE IF NOT EXISTS cloudmold_app_facade_operation (
    operation_id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    buyer_principal_id VARCHAR(36) NOT NULL,
    operation_type VARCHAR(32) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    attempt_token VARCHAR(36) NOT NULL,
    status TINYINT NOT NULL,
    result_json JSON NULL,
    first_occurred_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (operation_id),
    UNIQUE KEY uk_app_facade_operation_idempotency
        (tenant_id,buyer_principal_id,operation_type,idempotency_key),
    CONSTRAINT ck_app_facade_operation_hash CHECK (request_hash REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT ck_app_facade_operation_status CHECK (status IN (0,10)),
    CONSTRAINT ck_app_facade_operation_result CHECK (
        (status=0 AND result_json IS NULL) OR (status=10 AND result_json IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Replay-safe App facade command ledger';
