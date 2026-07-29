-- 为补货在途对账与质检放行扩展 inventory V3 命令类型。
-- INTRANSIT_ADD/SETTLE 支持采购确认→在途→收货的量对账（只改 in_transit，不动 on_hand）；
-- QUALITY_RELEASE 预留给质检放行翻转（双账 OUT/IN，counterparty 互指，约束已在 V20260715_16 就绪）。
-- 本迁移仅放宽 command_type 枚举 CHECK，不触碰账本结构。
ALTER TABLE cloudmold_inventory_operation_v3 DROP CONSTRAINT ck_cm_inv_v3_command_type;
ALTER TABLE cloudmold_inventory_operation_v3
    ADD CONSTRAINT ck_cm_inv_v3_command_type
    CHECK (command_type IN ('RECEIVE','RESERVE','SHIP','RETURN','RELEASE',
                            'MIGRATION_OPENING',
                            'INTRANSIT_ADD','INTRANSIT_SETTLE','QUALITY_RELEASE'));

ALTER TABLE cloudmold_inventory_ledger_transaction_v3 DROP CONSTRAINT ck_cm_inv_v3_tx_command;
ALTER TABLE cloudmold_inventory_ledger_transaction_v3
    ADD CONSTRAINT ck_cm_inv_v3_tx_command
    CHECK (command_type IN ('RECEIVE','RESERVE','SHIP','RETURN','RELEASE',
                            'MIGRATION_OPENING',
                            'INTRANSIT_ADD','INTRANSIT_SETTLE','QUALITY_RELEASE'));
