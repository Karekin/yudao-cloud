-- Tokenized, encrypted delivery-address snapshots for the canonical App checkout path.
-- Historical checkout/order rows remain nullable; all new App writes are enforced by service guards.
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cloudmold_app_address_snapshot (
    address_ref VARCHAR(36) NOT NULL,
    tenant_id BIGINT NOT NULL,
    owner_principal_id VARCHAR(36) NOT NULL,
    member_user_id BIGINT NOT NULL,
    source_address_id BIGINT NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    source_fingerprint_sha256 CHAR(64) NOT NULL,
    snapshot_version BIGINT NOT NULL,
    destination_region_code VARCHAR(32) NOT NULL,
    key_id VARCHAR(64) NOT NULL,
    initialization_vector VARBINARY(12) NOT NULL,
    ciphertext BLOB NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (address_ref),
    UNIQUE KEY uk_app_address_tenant_ref (tenant_id, address_ref),
    UNIQUE KEY uk_app_address_owner_idempotency
        (tenant_id, owner_principal_id, idempotency_key),
    KEY idx_app_address_source
        (tenant_id, owner_principal_id, source_address_id, updated_at),
    CONSTRAINT ck_app_address_request_hash
        CHECK (request_hash REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT ck_app_address_source_hash
        CHECK (source_fingerprint_sha256 REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT ck_app_address_version CHECK (snapshot_version > 0),
    CONSTRAINT ck_app_address_iv CHECK (OCTET_LENGTH(initialization_vector) = 12),
    CONSTRAINT ck_app_address_ciphertext CHECK (OCTET_LENGTH(ciphertext) > 16),
    CONSTRAINT ck_app_address_status CHECK (status IN ('ACTIVE','REVOKED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Encrypted, owner-scoped App delivery-address snapshots';

ALTER TABLE cloudmold_app_checkout
    ADD COLUMN address_ref VARCHAR(36) COLLATE utf8mb4_unicode_ci NULL AFTER canonical_sku_id,
    ADD COLUMN address_snapshot_version BIGINT NULL AFTER address_ref,
    ADD COLUMN destination_region_code VARCHAR(32) NULL AFTER address_snapshot_version,
    ADD KEY idx_app_checkout_address (tenant_id, address_ref),
    ADD CONSTRAINT fk_app_checkout_address
        FOREIGN KEY (tenant_id, address_ref)
        REFERENCES cloudmold_app_address_snapshot (tenant_id, address_ref);

ALTER TABLE cloudmold_order_header
    ADD COLUMN address_ref VARCHAR(36) COLLATE utf8mb4_unicode_ci NULL AFTER buyer_id,
    ADD COLUMN address_snapshot_version BIGINT NULL AFTER address_ref,
    ADD COLUMN destination_region_code VARCHAR(32) NULL AFTER address_snapshot_version,
    ADD KEY idx_order_address (tenant_id, address_ref),
    ADD CONSTRAINT fk_order_address
        FOREIGN KEY (tenant_id, address_ref)
        REFERENCES cloudmold_app_address_snapshot (tenant_id, address_ref);
