-- CloudMold governed gamification authority (first complete vertical slice).
-- Money, mall points, coupons and Token are intentionally absent: this schema owns only GAME_COIN_* and GAME_FRAGMENT_*.

CREATE TABLE cloudmold_gamification_operation (
  operation_id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  idempotency_key VARCHAR(128) NOT NULL,
  command_type VARCHAR(64) NOT NULL,
  request_hash CHAR(64) NOT NULL,
  attempt_token CHAR(36) NOT NULL,
  status TINYINT NOT NULL,
  aggregate_id VARCHAR(128) NULL,
  result_json JSON NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (operation_id),
  UNIQUE KEY uk_gamification_operation_tenant_key (tenant_id,idempotency_key),
  UNIQUE KEY uk_gamification_operation_tenant_id (tenant_id,operation_id),
  CONSTRAINT ck_gamification_operation_status CHECK (status IN (0,10))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE cloudmold_gamification_game (
  game_id VARCHAR(128) NOT NULL, tenant_id BIGINT NOT NULL, game_code VARCHAR(64) NOT NULL,
  game_name VARCHAR(128) NOT NULL, status VARCHAR(16) NOT NULL, current_version BIGINT NOT NULL,
  virtual_currency_code VARCHAR(64) NULL, assist_daily_limit INT NULL, max_rounds_per_session INT NULL,
  session_ttl_seconds INT NULL, created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (game_id), UNIQUE KEY uk_gamification_game_tenant_id (tenant_id,game_id),
  UNIQUE KEY uk_gamification_game_tenant_code (tenant_id,game_code),
  CONSTRAINT ck_gamification_game_status CHECK (status IN ('DRAFT','PUBLISHED','RETIRED')),
  CONSTRAINT ck_gamification_game_version CHECK (current_version >= 0),
  CONSTRAINT ck_gamification_game_publish_config CHECK (
    (status='DRAFT' AND current_version=0 AND virtual_currency_code IS NULL) OR
    (status IN ('PUBLISHED','RETIRED') AND current_version>0 AND
      virtual_currency_code REGEXP '^GAME_COIN_[A-Z0-9_]{2,40}$' AND virtual_currency_code NOT REGEXP '(TOKEN|COUPON|POINT|CNY|RMB|USD|MONEY)' AND assist_daily_limit BETWEEN 0 AND 100 AND
      max_rounds_per_session BETWEEN 1 AND 1000 AND session_ttl_seconds BETWEEN 60 AND 86400))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE cloudmold_gamification_game_version (
  game_version_id VARCHAR(128) NOT NULL, tenant_id BIGINT NOT NULL, game_id VARCHAR(128) NOT NULL,
  game_version BIGINT NOT NULL, definition_sha256 CHAR(64) NOT NULL, virtual_currency_code VARCHAR(64) NOT NULL,
  assist_daily_limit INT NOT NULL, max_rounds_per_session INT NOT NULL, session_ttl_seconds INT NOT NULL,
  published_at DATETIME(3) NOT NULL,
  PRIMARY KEY (game_version_id), UNIQUE KEY uk_gamification_game_version (tenant_id,game_id,game_version),
  CONSTRAINT fk_gamification_game_version_game FOREIGN KEY (tenant_id,game_id)
    REFERENCES cloudmold_gamification_game (tenant_id,game_id),
  CONSTRAINT ck_gamification_game_version_values CHECK (game_version>0 AND virtual_currency_code REGEXP '^GAME_COIN_[A-Z0-9_]{2,40}$' AND virtual_currency_code NOT REGEXP '(TOKEN|COUPON|POINT|CNY|RMB|USD|MONEY)' AND assist_daily_limit BETWEEN 0 AND 100 AND max_rounds_per_session BETWEEN 1 AND 1000 AND session_ttl_seconds BETWEEN 60 AND 86400)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE cloudmold_gamification_currency_account (
  account_id VARCHAR(128) NOT NULL, tenant_id BIGINT NOT NULL, game_id VARCHAR(128) NOT NULL,
  owner_type VARCHAR(16) NOT NULL, owner_ref VARCHAR(128) NOT NULL, asset_class VARCHAR(32) NOT NULL,
  currency_code VARCHAR(64) NOT NULL, balance_microunits BIGINT NOT NULL, status VARCHAR(16) NOT NULL,
  version BIGINT NOT NULL, created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (account_id), UNIQUE KEY uk_gamification_account_tenant_id (tenant_id,account_id),
  UNIQUE KEY uk_gamification_account_owner (tenant_id,game_id,owner_type,owner_ref),
  CONSTRAINT fk_gamification_account_game FOREIGN KEY (tenant_id,game_id) REFERENCES cloudmold_gamification_game (tenant_id,game_id),
  CONSTRAINT ck_gamification_account_boundary CHECK (owner_type IN ('PLAYER','TREASURY') AND asset_class='GAME_VIRTUAL_CURRENCY' AND currency_code REGEXP '^GAME_COIN_[A-Z0-9_]{2,40}$' AND currency_code NOT REGEXP '(TOKEN|COUPON|POINT|CNY|RMB|USD|MONEY)' AND status='ACTIVE' AND version>0),
  CONSTRAINT ck_gamification_player_nonnegative CHECK (owner_type='TREASURY' OR balance_microunits>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE cloudmold_gamification_currency_transaction (
  ledger_transaction_id VARCHAR(128) NOT NULL, tenant_id BIGINT NOT NULL, game_id VARCHAR(128) NOT NULL,
  currency_code VARCHAR(64) NOT NULL, business_type VARCHAR(32) NOT NULL, business_id VARCHAR(128) NOT NULL,
  amount_microunits BIGINT NOT NULL, occurred_at DATETIME(3) NOT NULL, created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (ledger_transaction_id), UNIQUE KEY uk_gamification_tx_tenant_id (tenant_id,ledger_transaction_id),
  UNIQUE KEY uk_gamification_tx_business (tenant_id,business_type,business_id),
  CONSTRAINT fk_gamification_tx_game FOREIGN KEY (tenant_id,game_id) REFERENCES cloudmold_gamification_game (tenant_id,game_id),
  CONSTRAINT ck_gamification_tx_boundary CHECK (currency_code REGEXP '^GAME_COIN_[A-Z0-9_]{2,40}$' AND currency_code NOT REGEXP '(TOKEN|COUPON|POINT|CNY|RMB|USD|MONEY)' AND amount_microunits>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE cloudmold_gamification_currency_ledger_entry (
  ledger_entry_id VARCHAR(128) NOT NULL, tenant_id BIGINT NOT NULL, ledger_transaction_id VARCHAR(128) NOT NULL,
  entry_sequence INT NOT NULL, account_id VARCHAR(128) NOT NULL, delta_microunits BIGINT NOT NULL,
  balance_after_microunits BIGINT NOT NULL, created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (ledger_entry_id), UNIQUE KEY uk_gamification_entry_sequence (tenant_id,ledger_transaction_id,entry_sequence),
  UNIQUE KEY uk_gamification_entry_account (tenant_id,ledger_transaction_id,account_id),
  CONSTRAINT fk_gamification_entry_tx FOREIGN KEY (tenant_id,ledger_transaction_id) REFERENCES cloudmold_gamification_currency_transaction (tenant_id,ledger_transaction_id),
  CONSTRAINT fk_gamification_entry_account FOREIGN KEY (tenant_id,account_id) REFERENCES cloudmold_gamification_currency_account (tenant_id,account_id),
  CONSTRAINT ck_gamification_entry_values CHECK (entry_sequence IN (1,2) AND delta_microunits<>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE cloudmold_gamification_session (
  session_id VARCHAR(128) NOT NULL, tenant_id BIGINT NOT NULL, game_id VARCHAR(128) NOT NULL, game_version BIGINT NOT NULL,
  principal_id VARCHAR(128) NOT NULL, status VARCHAR(16) NOT NULL, round_count INT NOT NULL, version BIGINT NOT NULL,
  expires_at DATETIME(3) NOT NULL, created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (session_id), UNIQUE KEY uk_gamification_session_tenant_id (tenant_id,session_id),
  CONSTRAINT fk_gamification_session_game_version FOREIGN KEY (tenant_id,game_id,game_version) REFERENCES cloudmold_gamification_game_version (tenant_id,game_id,game_version),
  CONSTRAINT ck_gamification_session_values CHECK (status IN ('OPEN','CLOSED','EXPIRED') AND round_count>=0 AND version>0 AND expires_at>created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE cloudmold_gamification_round (
  round_id VARCHAR(128) NOT NULL, tenant_id BIGINT NOT NULL, session_id VARCHAR(128) NOT NULL, game_id VARCHAR(128) NOT NULL,
  game_version BIGINT NOT NULL, principal_id VARCHAR(128) NOT NULL, round_number INT NOT NULL, status VARCHAR(16) NOT NULL,
  outcome VARCHAR(16) NULL, score BIGINT NULL, version BIGINT NOT NULL, started_at DATETIME(3) NOT NULL,
  completed_at DATETIME(3) NULL, updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (round_id), UNIQUE KEY uk_gamification_round_tenant_id (tenant_id,round_id),
  UNIQUE KEY uk_gamification_round_number (tenant_id,session_id,round_number),
  CONSTRAINT fk_gamification_round_session FOREIGN KEY (tenant_id,session_id) REFERENCES cloudmold_gamification_session (tenant_id,session_id),
  CONSTRAINT ck_gamification_round_state CHECK ((status='ACTIVE' AND outcome IS NULL AND completed_at IS NULL) OR (status='COMPLETED' AND outcome IN ('WIN','LOSE','DRAW','ABORTED') AND score>=0 AND completed_at IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE cloudmold_gamification_reward_definition (
  reward_definition_id VARCHAR(128) NOT NULL, tenant_id BIGINT NOT NULL, game_id VARCHAR(128) NOT NULL,
  reward_code VARCHAR(64) NOT NULL, reward_version BIGINT NOT NULL, reward_kind VARCHAR(16) NOT NULL,
  asset_class VARCHAR(32) NOT NULL, asset_code VARCHAR(64) NOT NULL, currency_amount_microunits BIGINT NULL,
  fragment_quantity BIGINT NULL, definition_sha256 CHAR(64) NOT NULL, published_at DATETIME(3) NOT NULL,
  PRIMARY KEY (tenant_id,reward_definition_id,reward_version),
  UNIQUE KEY uk_gamification_reward_code_version (tenant_id,game_id,reward_code,reward_version),
  CONSTRAINT fk_gamification_reward_game FOREIGN KEY (tenant_id,game_id) REFERENCES cloudmold_gamification_game (tenant_id,game_id),
  CONSTRAINT ck_gamification_reward_separation CHECK (
    (reward_kind='CURRENCY' AND asset_class='GAME_VIRTUAL_CURRENCY' AND asset_code REGEXP '^GAME_COIN_[A-Z0-9_]{2,40}$' AND asset_code NOT REGEXP '(TOKEN|COUPON|POINT|CNY|RMB|USD|MONEY)' AND currency_amount_microunits>0 AND fragment_quantity IS NULL) OR
    (reward_kind='FRAGMENT' AND asset_class='GAME_FRAGMENT' AND asset_code REGEXP '^GAME_FRAGMENT_[A-Z0-9_]{2,40}$' AND fragment_quantity>0 AND currency_amount_microunits IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE cloudmold_gamification_reward_grant (
  reward_grant_id VARCHAR(128) NOT NULL, tenant_id BIGINT NOT NULL, game_id VARCHAR(128) NOT NULL, principal_id VARCHAR(128) NOT NULL,
  reward_definition_id VARCHAR(128) NOT NULL, reward_version BIGINT NOT NULL, source_type VARCHAR(32) NOT NULL,
  source_id VARCHAR(128) NOT NULL, ledger_transaction_id VARCHAR(128) NULL, granted_currency_microunits BIGINT NULL,
  granted_fragment_quantity BIGINT NULL, occurred_at DATETIME(3) NOT NULL, created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (reward_grant_id), UNIQUE KEY uk_gamification_grant_tenant_id (tenant_id,reward_grant_id),
  UNIQUE KEY uk_gamification_grant_source (tenant_id,source_type,source_id,reward_definition_id,reward_version),
  CONSTRAINT fk_gamification_grant_reward FOREIGN KEY (tenant_id,reward_definition_id,reward_version) REFERENCES cloudmold_gamification_reward_definition (tenant_id,reward_definition_id,reward_version),
  CONSTRAINT fk_gamification_grant_tx FOREIGN KEY (tenant_id,ledger_transaction_id) REFERENCES cloudmold_gamification_currency_transaction (tenant_id,ledger_transaction_id),
  CONSTRAINT ck_gamification_grant_separation CHECK ((granted_currency_microunits>0 AND granted_fragment_quantity IS NULL) OR (granted_fragment_quantity>0 AND granted_currency_microunits IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE cloudmold_gamification_draw_pool (
  draw_pool_id VARCHAR(128) NOT NULL, tenant_id BIGINT NOT NULL, game_id VARCHAR(128) NOT NULL, draw_pool_code VARCHAR(64) NOT NULL,
  pool_version BIGINT NOT NULL, status VARCHAR(16) NOT NULL, price_microunits BIGINT NOT NULL, total_weight INT NOT NULL,
  definition_sha256 CHAR(64) NOT NULL, published_at DATETIME(3) NOT NULL,
  PRIMARY KEY (tenant_id,draw_pool_id,pool_version),
  UNIQUE KEY uk_gamification_pool_code_version (tenant_id,game_id,draw_pool_code,pool_version),
  CONSTRAINT fk_gamification_pool_game FOREIGN KEY (tenant_id,game_id) REFERENCES cloudmold_gamification_game (tenant_id,game_id),
  CONSTRAINT ck_gamification_pool_values CHECK (pool_version>0 AND status='PUBLISHED' AND price_microunits>0 AND total_weight>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE cloudmold_gamification_draw_pool_item (
  draw_pool_item_id VARCHAR(128) NOT NULL, tenant_id BIGINT NOT NULL, draw_pool_id VARCHAR(128) NOT NULL, pool_version BIGINT NOT NULL,
  item_sequence INT NOT NULL, reward_definition_id VARCHAR(128) NOT NULL, reward_version BIGINT NOT NULL,
  weight INT NOT NULL, cumulative_weight INT NOT NULL,
  PRIMARY KEY (draw_pool_item_id), UNIQUE KEY uk_gamification_pool_item (tenant_id,draw_pool_id,pool_version,item_sequence),
  CONSTRAINT fk_gamification_pool_item_pool FOREIGN KEY (tenant_id,draw_pool_id,pool_version) REFERENCES cloudmold_gamification_draw_pool (tenant_id,draw_pool_id,pool_version),
  CONSTRAINT fk_gamification_pool_item_reward FOREIGN KEY (tenant_id,reward_definition_id,reward_version) REFERENCES cloudmold_gamification_reward_definition (tenant_id,reward_definition_id,reward_version),
  CONSTRAINT ck_gamification_pool_item_weight CHECK (item_sequence>0 AND weight>0 AND cumulative_weight>=weight)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE cloudmold_gamification_draw_request (
  draw_request_id VARCHAR(128) NOT NULL, tenant_id BIGINT NOT NULL, game_id VARCHAR(128) NOT NULL, principal_id VARCHAR(128) NOT NULL,
  draw_pool_id VARCHAR(128) NOT NULL, pool_version BIGINT NOT NULL, charge_transaction_id VARCHAR(128) NOT NULL,
  price_microunits BIGINT NOT NULL, status VARCHAR(16) NOT NULL, occurred_at DATETIME(3) NOT NULL, created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (draw_request_id), UNIQUE KEY uk_gamification_draw_tenant_id (tenant_id,draw_request_id),
  UNIQUE KEY uk_gamification_draw_charge (tenant_id,charge_transaction_id),
  CONSTRAINT fk_gamification_draw_pool FOREIGN KEY (tenant_id,draw_pool_id,pool_version) REFERENCES cloudmold_gamification_draw_pool (tenant_id,draw_pool_id,pool_version),
  CONSTRAINT fk_gamification_draw_charge FOREIGN KEY (tenant_id,charge_transaction_id) REFERENCES cloudmold_gamification_currency_transaction (tenant_id,ledger_transaction_id),
  CONSTRAINT ck_gamification_draw_request CHECK (status='COMPLETED' AND price_microunits>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE cloudmold_gamification_draw_result (
  draw_result_id VARCHAR(128) NOT NULL, tenant_id BIGINT NOT NULL, draw_request_id VARCHAR(128) NOT NULL,
  selected_ticket INT NOT NULL, total_weight INT NOT NULL, entropy_sha256 CHAR(64) NOT NULL,
  draw_pool_item_id VARCHAR(128) NOT NULL, reward_grant_id VARCHAR(128) NOT NULL, created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (draw_result_id), UNIQUE KEY uk_gamification_draw_result_request (tenant_id,draw_request_id),
  CONSTRAINT fk_gamification_result_request FOREIGN KEY (tenant_id,draw_request_id) REFERENCES cloudmold_gamification_draw_request (tenant_id,draw_request_id),
  CONSTRAINT fk_gamification_result_grant FOREIGN KEY (tenant_id,reward_grant_id) REFERENCES cloudmold_gamification_reward_grant (tenant_id,reward_grant_id),
  CONSTRAINT ck_gamification_draw_result CHECK (total_weight>0 AND selected_ticket BETWEEN 1 AND total_weight)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE cloudmold_gamification_assist_quota (
  assist_quota_id VARCHAR(128) NOT NULL, tenant_id BIGINT NOT NULL, game_id VARCHAR(128) NOT NULL,
  beneficiary_principal_id VARCHAR(128) NOT NULL, quota_date DATE NOT NULL, assist_limit INT NOT NULL,
  assists_used INT NOT NULL, version BIGINT NOT NULL, created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (assist_quota_id), UNIQUE KEY uk_gamification_assist_quota (tenant_id,game_id,beneficiary_principal_id,quota_date),
  CONSTRAINT fk_gamification_assist_quota_game FOREIGN KEY (tenant_id,game_id) REFERENCES cloudmold_gamification_game (tenant_id,game_id),
  CONSTRAINT ck_gamification_assist_quota CHECK (assist_limit BETWEEN 0 AND 100 AND assists_used BETWEEN 0 AND assist_limit AND version>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE cloudmold_gamification_assist_record (
  assist_record_id VARCHAR(128) NOT NULL, tenant_id BIGINT NOT NULL, game_id VARCHAR(128) NOT NULL,
  helper_principal_id VARCHAR(128) NOT NULL, beneficiary_principal_id VARCHAR(128) NOT NULL, quota_date DATE NOT NULL,
  ordinal INT NOT NULL, occurred_at DATETIME(3) NOT NULL, created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (assist_record_id), UNIQUE KEY uk_gamification_assist_helper_day (tenant_id,game_id,helper_principal_id,beneficiary_principal_id,quota_date),
  UNIQUE KEY uk_gamification_assist_ordinal (tenant_id,game_id,beneficiary_principal_id,quota_date,ordinal),
  CONSTRAINT fk_gamification_assist_game FOREIGN KEY (tenant_id,game_id) REFERENCES cloudmold_gamification_game (tenant_id,game_id),
  CONSTRAINT ck_gamification_assist_record CHECK (helper_principal_id<>beneficiary_principal_id AND ordinal>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE cloudmold_gamification_task_definition (
  task_definition_id VARCHAR(128) NOT NULL, tenant_id BIGINT NOT NULL, game_id VARCHAR(128) NOT NULL, task_code VARCHAR(64) NOT NULL,
  task_version BIGINT NOT NULL, target_units BIGINT NOT NULL, reward_definition_id VARCHAR(128) NOT NULL,
  reward_version BIGINT NOT NULL, definition_sha256 CHAR(64) NOT NULL, published_at DATETIME(3) NOT NULL,
  PRIMARY KEY (tenant_id,task_definition_id,task_version),
  UNIQUE KEY uk_gamification_task_code_version (tenant_id,game_id,task_code,task_version),
  CONSTRAINT fk_gamification_task_game FOREIGN KEY (tenant_id,game_id) REFERENCES cloudmold_gamification_game (tenant_id,game_id),
  CONSTRAINT fk_gamification_task_reward FOREIGN KEY (tenant_id,reward_definition_id,reward_version) REFERENCES cloudmold_gamification_reward_definition (tenant_id,reward_definition_id,reward_version),
  CONSTRAINT ck_gamification_task_definition CHECK (task_version>0 AND target_units>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE cloudmold_gamification_task_progress (
  task_progress_id VARCHAR(128) NOT NULL, tenant_id BIGINT NOT NULL, task_definition_id VARCHAR(128) NOT NULL,
  task_version BIGINT NOT NULL, game_id VARCHAR(128) NOT NULL, principal_id VARCHAR(128) NOT NULL,
  completed_units BIGINT NOT NULL, target_units BIGINT NOT NULL, status VARCHAR(16) NOT NULL,
  reward_grant_id VARCHAR(128) NULL, version BIGINT NOT NULL, created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (task_progress_id), UNIQUE KEY uk_gamification_progress_tenant_id (tenant_id,task_progress_id),
  UNIQUE KEY uk_gamification_task_progress (tenant_id,task_definition_id,task_version,principal_id),
  CONSTRAINT fk_gamification_progress_task FOREIGN KEY (tenant_id,task_definition_id,task_version) REFERENCES cloudmold_gamification_task_definition (tenant_id,task_definition_id,task_version),
  CONSTRAINT fk_gamification_progress_grant FOREIGN KEY (tenant_id,reward_grant_id) REFERENCES cloudmold_gamification_reward_grant (tenant_id,reward_grant_id),
  CONSTRAINT ck_gamification_progress_state CHECK (target_units>0 AND completed_units BETWEEN 0 AND target_units AND version>0 AND ((status='IN_PROGRESS' AND completed_units<target_units AND reward_grant_id IS NULL) OR (status='COMPLETED' AND completed_units=target_units AND reward_grant_id IS NOT NULL)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE cloudmold_gamification_fragment_balance (
  fragment_balance_id VARCHAR(128) NOT NULL, tenant_id BIGINT NOT NULL, game_id VARCHAR(128) NOT NULL,
  principal_id VARCHAR(128) NOT NULL, asset_class VARCHAR(32) NOT NULL, fragment_code VARCHAR(64) NOT NULL,
  quantity BIGINT NOT NULL, version BIGINT NOT NULL, created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (fragment_balance_id), UNIQUE KEY uk_gamification_fragment_tenant_id (tenant_id,fragment_balance_id),
  UNIQUE KEY uk_gamification_fragment_owner (tenant_id,game_id,principal_id,fragment_code),
  CONSTRAINT fk_gamification_fragment_game FOREIGN KEY (tenant_id,game_id) REFERENCES cloudmold_gamification_game (tenant_id,game_id),
  CONSTRAINT ck_gamification_fragment_boundary CHECK (asset_class='GAME_FRAGMENT' AND fragment_code REGEXP '^GAME_FRAGMENT_[A-Z0-9_]{2,40}$' AND quantity>=0 AND version>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE cloudmold_gamification_fragment_ledger_entry (
  fragment_entry_id VARCHAR(128) NOT NULL, tenant_id BIGINT NOT NULL, fragment_balance_id VARCHAR(128) NOT NULL,
  reward_grant_id VARCHAR(128) NOT NULL, delta_quantity BIGINT NOT NULL, balance_after_quantity BIGINT NOT NULL,
  occurred_at DATETIME(3) NOT NULL, created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (fragment_entry_id), UNIQUE KEY uk_gamification_fragment_grant (tenant_id,reward_grant_id),
  CONSTRAINT fk_gamification_fragment_entry_balance FOREIGN KEY (tenant_id,fragment_balance_id) REFERENCES cloudmold_gamification_fragment_balance (tenant_id,fragment_balance_id),
  CONSTRAINT fk_gamification_fragment_entry_grant FOREIGN KEY (tenant_id,reward_grant_id) REFERENCES cloudmold_gamification_reward_grant (tenant_id,reward_grant_id),
  CONSTRAINT ck_gamification_fragment_entry CHECK (delta_quantity<>0 AND balance_after_quantity>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE cloudmold_gamification_gift_transfer (
  gift_transfer_id VARCHAR(128) NOT NULL, tenant_id BIGINT NOT NULL, game_id VARCHAR(128) NOT NULL,
  currency_code VARCHAR(64) NOT NULL, from_principal_id VARCHAR(128) NOT NULL, to_principal_id VARCHAR(128) NOT NULL,
  amount_microunits BIGINT NOT NULL, ledger_transaction_id VARCHAR(128) NOT NULL, reason_code VARCHAR(64) NOT NULL,
  occurred_at DATETIME(3) NOT NULL, created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (gift_transfer_id), UNIQUE KEY uk_gamification_gift_tenant_id (tenant_id,gift_transfer_id),
  UNIQUE KEY uk_gamification_gift_tx (tenant_id,ledger_transaction_id),
  CONSTRAINT fk_gamification_gift_game FOREIGN KEY (tenant_id,game_id) REFERENCES cloudmold_gamification_game (tenant_id,game_id),
  CONSTRAINT fk_gamification_gift_tx FOREIGN KEY (tenant_id,ledger_transaction_id) REFERENCES cloudmold_gamification_currency_transaction (tenant_id,ledger_transaction_id),
  CONSTRAINT ck_gamification_gift_boundary CHECK (currency_code REGEXP '^GAME_COIN_[A-Z0-9_]{2,40}$' AND currency_code NOT REGEXP '(TOKEN|COUPON|POINT|CNY|RMB|USD|MONEY)' AND from_principal_id<>to_principal_id AND amount_microunits>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE cloudmold_gamification_status_history (
  history_id BIGINT NOT NULL AUTO_INCREMENT, tenant_id BIGINT NOT NULL, aggregate_type VARCHAR(32) NOT NULL,
  aggregate_id VARCHAR(128) NOT NULL, aggregate_version BIGINT NOT NULL, previous_status VARCHAR(32) NULL,
  current_status VARCHAR(32) NOT NULL, operation_id BIGINT NOT NULL, occurred_at DATETIME(3) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (history_id), UNIQUE KEY uk_gamification_history_version (tenant_id,aggregate_type,aggregate_id,aggregate_version),
  CONSTRAINT fk_gamification_history_operation FOREIGN KEY (tenant_id,operation_id) REFERENCES cloudmold_gamification_operation (tenant_id,operation_id),
  CONSTRAINT ck_gamification_history_version CHECK (aggregate_version>=0 AND (previous_status IS NULL OR previous_status<>current_status))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Immutable season/series definitions. Leaderboards are deliberately absent: they are lakehouse-derived read models.
CREATE TABLE cloudmold_gamification_season_series (
  season_series_id VARCHAR(128) NOT NULL, tenant_id BIGINT NOT NULL, game_id VARCHAR(128) NOT NULL,
  series_code VARCHAR(64) NOT NULL, series_version BIGINT NOT NULL, series_name VARCHAR(128) NOT NULL,
  definition_sha256 CHAR(64) NOT NULL, published_at DATETIME(3) NOT NULL,
  PRIMARY KEY (tenant_id,season_series_id,series_version),
  UNIQUE KEY uk_gamification_series_code_version (tenant_id,game_id,series_code,series_version),
  CONSTRAINT fk_gamification_series_game FOREIGN KEY (tenant_id,game_id) REFERENCES cloudmold_gamification_game (tenant_id,game_id),
  CONSTRAINT ck_gamification_series_version CHECK (series_version>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE cloudmold_gamification_season (
  season_id VARCHAR(128) NOT NULL, tenant_id BIGINT NOT NULL, game_id VARCHAR(128) NOT NULL,
  season_series_id VARCHAR(128) NOT NULL, series_version BIGINT NOT NULL, season_code VARCHAR(64) NOT NULL,
  season_version BIGINT NOT NULL, season_name VARCHAR(128) NOT NULL, status VARCHAR(16) NOT NULL,
  starts_at DATETIME(3) NOT NULL, ends_at DATETIME(3) NOT NULL, definition_sha256 CHAR(64) NOT NULL,
  published_at DATETIME(3) NOT NULL,
  PRIMARY KEY (tenant_id,season_id,season_version),
  UNIQUE KEY uk_gamification_season_code_version (tenant_id,game_id,season_code,season_version),
  CONSTRAINT fk_gamification_season_series FOREIGN KEY (tenant_id,season_series_id,series_version)
    REFERENCES cloudmold_gamification_season_series (tenant_id,season_series_id,series_version),
  CONSTRAINT ck_gamification_season_values CHECK (season_version>0 AND status='PUBLISHED' AND ends_at>starts_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE cloudmold_gamification_collectible_definition (
  collectible_definition_id VARCHAR(128) NOT NULL, tenant_id BIGINT NOT NULL, game_id VARCHAR(128) NOT NULL,
  collectible_code VARCHAR(64) NOT NULL, collectible_version BIGINT NOT NULL, collectible_kind VARCHAR(16) NOT NULL,
  collectible_name VARCHAR(128) NOT NULL, definition_sha256 CHAR(64) NOT NULL, published_at DATETIME(3) NOT NULL,
  PRIMARY KEY (tenant_id,collectible_definition_id,collectible_version),
  UNIQUE KEY uk_gamification_collectible_code_version (tenant_id,game_id,collectible_code,collectible_version),
  CONSTRAINT fk_gamification_collectible_game FOREIGN KEY (tenant_id,game_id) REFERENCES cloudmold_gamification_game (tenant_id,game_id),
  CONSTRAINT ck_gamification_collectible_values CHECK (collectible_version>0 AND collectible_kind IN ('GK','FIGURE','COLLECTIBLE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE cloudmold_gamification_collectible_ownership (
  ownership_id VARCHAR(128) NOT NULL, tenant_id BIGINT NOT NULL, game_id VARCHAR(128) NOT NULL,
  principal_id VARCHAR(128) NOT NULL, collectible_definition_id VARCHAR(128) NOT NULL,
  collectible_version BIGINT NOT NULL, quantity BIGINT NOT NULL, version BIGINT NOT NULL,
  created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (ownership_id), UNIQUE KEY uk_gamification_ownership_tenant_id (tenant_id,ownership_id),
  UNIQUE KEY uk_gamification_collectible_owner (tenant_id,game_id,principal_id,collectible_definition_id,collectible_version),
  CONSTRAINT fk_gamification_ownership_definition FOREIGN KEY (tenant_id,collectible_definition_id,collectible_version)
    REFERENCES cloudmold_gamification_collectible_definition (tenant_id,collectible_definition_id,collectible_version),
  CONSTRAINT ck_gamification_ownership_values CHECK (quantity>=0 AND version>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE cloudmold_gamification_collectible_ledger_entry (
  collectible_entry_id VARCHAR(128) NOT NULL, tenant_id BIGINT NOT NULL, ownership_id VARCHAR(128) NOT NULL,
  source_type VARCHAR(32) NOT NULL, source_id VARCHAR(128) NOT NULL, delta_quantity BIGINT NOT NULL,
  balance_after_quantity BIGINT NOT NULL, occurred_at DATETIME(3) NOT NULL, created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (collectible_entry_id), UNIQUE KEY uk_gamification_collectible_source (tenant_id,source_type,source_id),
  CONSTRAINT fk_gamification_collectible_entry_owner FOREIGN KEY (tenant_id,ownership_id)
    REFERENCES cloudmold_gamification_collectible_ownership (tenant_id,ownership_id),
  CONSTRAINT ck_gamification_collectible_entry CHECK (delta_quantity<>0 AND balance_after_quantity>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Cross-domain exchange stores opaque adapter references only: no mall balance, mall points, coupon, Token or fiat amount.
CREATE TABLE cloudmold_gamification_redemption_intent (
  redemption_intent_id VARCHAR(128) NOT NULL, tenant_id BIGINT NOT NULL, game_id VARCHAR(128) NOT NULL,
  principal_id VARCHAR(128) NOT NULL, collectible_definition_id VARCHAR(128) NOT NULL,
  collectible_version BIGINT NOT NULL, quantity BIGINT NOT NULL, adapter_code VARCHAR(32) NOT NULL,
  external_intent_ref VARCHAR(128) NOT NULL, external_result_ref VARCHAR(128) NULL, status VARCHAR(16) NOT NULL,
  version BIGINT NOT NULL, occurred_at DATETIME(3) NOT NULL, created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (redemption_intent_id), UNIQUE KEY uk_gamification_redemption_tenant_id (tenant_id,redemption_intent_id),
  UNIQUE KEY uk_gamification_redemption_external_intent (tenant_id,adapter_code,external_intent_ref),
  UNIQUE KEY uk_gamification_redemption_external_result (tenant_id,adapter_code,external_result_ref),
  CONSTRAINT fk_gamification_redemption_definition FOREIGN KEY (tenant_id,collectible_definition_id,collectible_version)
    REFERENCES cloudmold_gamification_collectible_definition (tenant_id,collectible_definition_id,collectible_version),
  CONSTRAINT ck_gamification_redemption_values CHECK (quantity>0 AND adapter_code='MALL_REDEMPTION_V1' AND version>0 AND
    ((status='PENDING' AND external_result_ref IS NULL) OR (status IN ('SUCCEEDED','REJECTED') AND external_result_ref IS NOT NULL)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE cloudmold_gamification_reward_claim (
  reward_claim_id VARCHAR(128) NOT NULL, tenant_id BIGINT NOT NULL, game_id VARCHAR(128) NOT NULL,
  principal_id VARCHAR(128) NOT NULL, reward_definition_id VARCHAR(128) NOT NULL, reward_version BIGINT NOT NULL,
  source_type VARCHAR(32) NOT NULL, source_id VARCHAR(128) NOT NULL, status VARCHAR(16) NOT NULL,
  reward_grant_id VARCHAR(128) NULL, version BIGINT NOT NULL, claim_expires_at DATETIME(3) NOT NULL,
  claimed_at DATETIME(3) NULL, created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (reward_claim_id), UNIQUE KEY uk_gamification_claim_tenant_id (tenant_id,reward_claim_id),
  UNIQUE KEY uk_gamification_claim_source (tenant_id,source_type,source_id,reward_definition_id,reward_version),
  UNIQUE KEY uk_gamification_claim_grant (tenant_id,reward_grant_id),
  CONSTRAINT fk_gamification_claim_reward FOREIGN KEY (tenant_id,reward_definition_id,reward_version)
    REFERENCES cloudmold_gamification_reward_definition (tenant_id,reward_definition_id,reward_version),
  CONSTRAINT fk_gamification_claim_grant FOREIGN KEY (tenant_id,reward_grant_id)
    REFERENCES cloudmold_gamification_reward_grant (tenant_id,reward_grant_id),
  CONSTRAINT ck_gamification_claim_state CHECK (version>0 AND claim_expires_at>created_at AND
    ((status='CLAIMABLE' AND reward_grant_id IS NULL AND claimed_at IS NULL) OR
     (status='CLAIMED' AND reward_grant_id IS NOT NULL AND claimed_at IS NOT NULL) OR
     (status='EXPIRED' AND reward_grant_id IS NULL AND claimed_at IS NULL)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
