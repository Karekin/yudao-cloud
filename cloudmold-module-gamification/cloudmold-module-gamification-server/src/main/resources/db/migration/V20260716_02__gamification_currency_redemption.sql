-- Extend governed redemptions to cover game-only virtual-currency exchange without owning mall value.
ALTER TABLE cloudmold_gamification_redemption_intent
  ADD COLUMN source_asset_class VARCHAR(32) NULL AFTER principal_id,
  ADD COLUMN currency_code VARCHAR(64) NULL AFTER quantity,
  ADD COLUMN amount_microunits BIGINT NULL AFTER currency_code,
  ADD COLUMN source_ledger_transaction_id VARCHAR(128) NULL AFTER external_result_ref;

UPDATE cloudmold_gamification_redemption_intent
SET source_asset_class = 'GAME_COLLECTIBLE'
WHERE source_asset_class IS NULL;

ALTER TABLE cloudmold_gamification_redemption_intent
  DROP CHECK ck_gamification_redemption_values,
  MODIFY source_asset_class VARCHAR(32) NOT NULL,
  MODIFY collectible_definition_id VARCHAR(128) NULL,
  MODIFY collectible_version BIGINT NULL,
  MODIFY quantity BIGINT NULL,
  ADD CONSTRAINT fk_gamification_redemption_game
    FOREIGN KEY (tenant_id,game_id)
    REFERENCES cloudmold_gamification_game (tenant_id,game_id),
  ADD CONSTRAINT fk_gamification_redemption_currency_ledger
    FOREIGN KEY (tenant_id,source_ledger_transaction_id)
    REFERENCES cloudmold_gamification_currency_transaction (tenant_id,ledger_transaction_id),
  ADD CONSTRAINT ck_gamification_redemption_values CHECK (
    adapter_code='MALL_REDEMPTION_V1' AND version>0 AND
    ((source_asset_class='GAME_COLLECTIBLE' AND collectible_definition_id IS NOT NULL
      AND collectible_version>0 AND quantity>0 AND currency_code IS NULL AND amount_microunits IS NULL) OR
     (source_asset_class='GAME_VIRTUAL_CURRENCY' AND collectible_definition_id IS NULL
      AND collectible_version IS NULL AND quantity IS NULL
      AND currency_code REGEXP '^GAME_COIN_[A-Z0-9_]{2,40}$'
      AND currency_code NOT REGEXP '(TOKEN|COUPON|POINT|CNY|RMB|USD|MONEY)'
      AND amount_microunits>0)) AND
    ((status='PENDING' AND external_result_ref IS NULL AND source_ledger_transaction_id IS NULL) OR
     (status='REJECTED' AND external_result_ref IS NOT NULL AND source_ledger_transaction_id IS NULL) OR
     (status='SUCCEEDED' AND external_result_ref IS NOT NULL AND
       ((source_asset_class='GAME_COLLECTIBLE' AND source_ledger_transaction_id IS NULL) OR
        (source_asset_class='GAME_VIRTUAL_CURRENCY' AND source_ledger_transaction_id IS NOT NULL))))) ;
