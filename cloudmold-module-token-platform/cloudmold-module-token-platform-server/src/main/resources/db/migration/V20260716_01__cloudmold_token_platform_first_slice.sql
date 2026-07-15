-- CloudMold Token Platform first slice. All DATETIME values are UTC.
-- Credential values remain in a secret manager; this schema stores metadata only.

CREATE TABLE cloudmold_token_platform_operation (
    operation_id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    operation_type VARCHAR(64) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    attempt_token CHAR(36) NOT NULL,
    status TINYINT NOT NULL,
    aggregate_type VARCHAR(64) NULL,
    aggregate_id VARCHAR(64) NULL,
    result_json JSON NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (operation_id),
    UNIQUE KEY uk_tp_op_tenant_key (tenant_id, idempotency_key),
    UNIQUE KEY uk_tp_op_tenant_id (tenant_id, operation_id),
    CONSTRAINT ck_tp_operation_status CHECK (status IN (0, 10))
) ENGINE=InnoDB COMMENT='Token Platform idempotent command result';

CREATE TABLE cloudmold_token_platform_model_offering (
    offering_id VARCHAR(64) NOT NULL,
    tenant_id BIGINT NOT NULL,
    offering_code VARCHAR(64) NOT NULL,
    provider_code VARCHAR(64) NOT NULL,
    model_code VARCHAR(128) NOT NULL,
    current_pricing_version_id VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (offering_id),
    UNIQUE KEY uk_tp_offer_tenant_id (tenant_id, offering_id),
    UNIQUE KEY uk_tp_offer_tenant_code (tenant_id, offering_code),
    CONSTRAINT ck_tp_offer_status CHECK (status IN ('DRAFT', 'ACTIVE', 'SUSPENDED', 'RETIRED')),
    CONSTRAINT ck_tp_offer_version CHECK (version > 0)
) ENGINE=InnoDB COMMENT='Canonical AI model offering';

CREATE TABLE cloudmold_token_platform_pricing_version (
    pricing_version_id VARCHAR(64) NOT NULL,
    tenant_id BIGINT NOT NULL,
    offering_id VARCHAR(64) NOT NULL,
    pricing_version_no BIGINT NOT NULL,
    input_price_microunits_per_million_tokens BIGINT NOT NULL,
    cached_input_price_microunits_per_million_tokens BIGINT NOT NULL,
    output_price_microunits_per_million_tokens BIGINT NOT NULL,
    effective_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (pricing_version_id),
    UNIQUE KEY uk_tp_price_tenant_id (tenant_id, pricing_version_id),
    UNIQUE KEY uk_tp_price_offer_no (tenant_id, offering_id, pricing_version_no),
    CONSTRAINT fk_tp_price_offer FOREIGN KEY (tenant_id, offering_id)
        REFERENCES cloudmold_token_platform_model_offering (tenant_id, offering_id),
    CONSTRAINT ck_tp_price_version CHECK (pricing_version_no > 0),
    CONSTRAINT ck_tp_price_nonnegative CHECK (
        input_price_microunits_per_million_tokens >= 0
        AND cached_input_price_microunits_per_million_tokens >= 0
        AND output_price_microunits_per_million_tokens >= 0
    )
) ENGINE=InnoDB COMMENT='Immutable AI model pricing version';

CREATE TABLE cloudmold_token_platform_access_credential (
    credential_id VARCHAR(64) NOT NULL,
    tenant_id BIGINT NOT NULL,
    principal_id VARCHAR(64) NOT NULL,
    offering_id VARCHAR(64) NOT NULL,
    credential_fingerprint CHAR(64) NOT NULL,
    secret_ref VARCHAR(256) NOT NULL,
    last4 CHAR(4) NOT NULL,
    key_version INT NOT NULL,
    status VARCHAR(16) NOT NULL,
    expires_at DATETIME(3) NULL,
    version BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (credential_id),
    UNIQUE KEY uk_tp_cred_tenant_id (tenant_id, credential_id),
    UNIQUE KEY uk_tp_cred_fingerprint (tenant_id, credential_fingerprint, key_version),
    KEY idx_tp_cred_principal (tenant_id, principal_id, status),
    CONSTRAINT fk_tp_cred_offer FOREIGN KEY (tenant_id, offering_id)
        REFERENCES cloudmold_token_platform_model_offering (tenant_id, offering_id),
    CONSTRAINT ck_tp_cred_status CHECK (status IN ('INACTIVE', 'ACTIVE', 'REVOKED')),
    CONSTRAINT ck_tp_cred_version CHECK (version > 0 AND key_version > 0),
    CONSTRAINT ck_tp_cred_last4 CHECK (CHAR_LENGTH(last4) = 4),
    CONSTRAINT ck_tp_cred_fingerprint CHECK (CHAR_LENGTH(credential_fingerprint) = 64),
    CONSTRAINT ck_tp_cred_secret_ref CHECK (
        secret_ref LIKE 'vault://%' OR secret_ref LIKE 'secret://%' OR secret_ref LIKE 'kms://%'
    )
) ENGINE=InnoDB COMMENT='Credential metadata without credential value';

CREATE TABLE cloudmold_token_platform_quota_account (
    account_id VARCHAR(64) NOT NULL,
    tenant_id BIGINT NOT NULL,
    principal_id VARCHAR(64) NOT NULL,
    balance_microunits BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (account_id),
    UNIQUE KEY uk_tp_account_tenant_id (tenant_id, account_id),
    UNIQUE KEY uk_tp_account_principal (tenant_id, principal_id),
    CONSTRAINT ck_tp_account_status CHECK (status IN ('ACTIVE', 'CLOSED')),
    CONSTRAINT ck_tp_account_balance CHECK (balance_microunits >= 0),
    CONSTRAINT ck_tp_account_version CHECK (version > 0)
) ENGINE=InnoDB COMMENT='Canonical integer quota account';

CREATE TABLE cloudmold_token_platform_quota_ledger (
    ledger_entry_id VARCHAR(64) NOT NULL,
    tenant_id BIGINT NOT NULL,
    account_id VARCHAR(64) NOT NULL,
    principal_id VARCHAR(64) NOT NULL,
    operation_id BIGINT NOT NULL,
    entry_type VARCHAR(32) NOT NULL,
    signed_delta_microunits BIGINT NOT NULL,
    balance_after_microunits BIGINT NOT NULL,
    account_version BIGINT NOT NULL,
    reference_type VARCHAR(64) NOT NULL,
    reference_id VARCHAR(128) NOT NULL,
    occurred_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (ledger_entry_id),
    UNIQUE KEY uk_tp_ledger_tenant_id (tenant_id, ledger_entry_id),
    UNIQUE KEY uk_tp_ledger_account_ver (tenant_id, account_id, account_version),
    UNIQUE KEY uk_tp_ledger_operation (tenant_id, operation_id),
    UNIQUE KEY uk_tp_ledger_ref (tenant_id, reference_type, reference_id),
    CONSTRAINT fk_tp_ledger_account FOREIGN KEY (tenant_id, account_id)
        REFERENCES cloudmold_token_platform_quota_account (tenant_id, account_id),
    CONSTRAINT fk_tp_ledger_operation FOREIGN KEY (tenant_id, operation_id)
        REFERENCES cloudmold_token_platform_operation (tenant_id, operation_id),
    CONSTRAINT ck_tp_ledger_type CHECK (
        entry_type IN ('OPENING', 'ALLOCATION', 'ADJUSTMENT', 'REVERSAL', 'EXPIRATION', 'INVOCATION_USAGE')
    ),
    CONSTRAINT ck_tp_ledger_delta CHECK (
        (entry_type = 'OPENING' AND signed_delta_microunits = 0)
        OR (entry_type = 'INVOCATION_USAGE' AND signed_delta_microunits <= 0)
        OR (entry_type IN ('ALLOCATION', 'ADJUSTMENT', 'REVERSAL', 'EXPIRATION')
            AND signed_delta_microunits <> 0)
    ),
    CONSTRAINT ck_tp_ledger_balance CHECK (balance_after_microunits >= 0 AND account_version > 0)
) ENGINE=InnoDB COMMENT='Append-only quota ledger';

CREATE TABLE cloudmold_token_platform_invocation_usage (
    usage_id VARCHAR(64) NOT NULL,
    tenant_id BIGINT NOT NULL,
    request_id VARCHAR(128) NOT NULL,
    principal_id VARCHAR(64) NOT NULL,
    account_id VARCHAR(64) NOT NULL,
    credential_id VARCHAR(64) NOT NULL,
    offering_id VARCHAR(64) NOT NULL,
    provider_code VARCHAR(64) NOT NULL,
    model_code VARCHAR(128) NOT NULL,
    result_status VARCHAR(16) NOT NULL,
    input_tokens BIGINT NOT NULL,
    cached_input_tokens BIGINT NOT NULL,
    output_tokens BIGINT NOT NULL,
    total_tokens BIGINT NOT NULL,
    duration_millis BIGINT NOT NULL,
    quota_cost_microunits BIGINT NOT NULL,
    pricing_version_id VARCHAR(64) NOT NULL,
    ledger_entry_id VARCHAR(64) NOT NULL,
    error_code VARCHAR(64) NULL,
    occurred_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (usage_id),
    UNIQUE KEY uk_tp_usage_tenant_id (tenant_id, usage_id),
    UNIQUE KEY uk_tp_usage_request (tenant_id, request_id),
    UNIQUE KEY uk_tp_usage_ledger (tenant_id, ledger_entry_id),
    KEY idx_tp_usage_principal_time (tenant_id, principal_id, occurred_at),
    KEY idx_tp_usage_model_time (tenant_id, offering_id, occurred_at),
    CONSTRAINT fk_tp_usage_account FOREIGN KEY (tenant_id, account_id)
        REFERENCES cloudmold_token_platform_quota_account (tenant_id, account_id),
    CONSTRAINT fk_tp_usage_credential FOREIGN KEY (tenant_id, credential_id)
        REFERENCES cloudmold_token_platform_access_credential (tenant_id, credential_id),
    CONSTRAINT fk_tp_usage_offering FOREIGN KEY (tenant_id, offering_id)
        REFERENCES cloudmold_token_platform_model_offering (tenant_id, offering_id),
    CONSTRAINT fk_tp_usage_pricing FOREIGN KEY (tenant_id, pricing_version_id)
        REFERENCES cloudmold_token_platform_pricing_version (tenant_id, pricing_version_id),
    CONSTRAINT fk_tp_usage_ledger FOREIGN KEY (tenant_id, ledger_entry_id)
        REFERENCES cloudmold_token_platform_quota_ledger (tenant_id, ledger_entry_id),
    CONSTRAINT ck_tp_usage_status CHECK (result_status IN ('SUCCEEDED', 'FAILED')),
    CONSTRAINT ck_tp_usage_counts CHECK (
        input_tokens >= 0 AND cached_input_tokens >= 0 AND cached_input_tokens <= input_tokens
        AND output_tokens >= 0 AND total_tokens = input_tokens + output_tokens
        AND duration_millis >= 0 AND quota_cost_microunits >= 0
    ),
    CONSTRAINT ck_tp_usage_error CHECK (
        (result_status = 'SUCCEEDED' AND error_code IS NULL)
        OR (result_status = 'FAILED' AND error_code IS NOT NULL)
    )
) ENGINE=InnoDB COMMENT='Immutable AI invocation usage and pricing snapshot reference';
