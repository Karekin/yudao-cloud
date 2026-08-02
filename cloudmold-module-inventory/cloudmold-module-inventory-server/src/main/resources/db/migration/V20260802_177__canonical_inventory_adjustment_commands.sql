-- Admit the canonical stock-count and scrap movements to the immutable V3 ledger.
-- These are first-class inventory commands owned by a normal V3 operation; they
-- must not be disguised as receipt, release, or return movements.

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

ALTER TABLE `cloudmold_inventory_ledger_transaction_v3`
  DROP CONSTRAINT `ck_cm_inv_v3_tx_command`,
  ADD CONSTRAINT `ck_cm_inv_v3_tx_command` CHECK (`command_type` IN (
    'RECEIVE','RESERVE','SHIP','RETURN','RELEASE','MIGRATION_OPENING','INTRANSIT_ADD','INTRANSIT_SETTLE',
    'QUALITY_RELEASE','RELOCATE','STOCK_TRANSFER_DISPATCH','STOCK_TRANSFER_RECEIVE',
    'RECEIVE_PENDING_QUALITY','ACCEPT_QUALITY','REJECT_QUALITY','QUARANTINE_QUALITY','RETURN_TO_SUPPLIER',
    'STOCK_COUNT_ADJUST','SCRAP_DISPOSITION'
  ));
