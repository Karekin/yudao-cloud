-- Explicit rollback. This permanently discards encrypted delivery snapshots.
ALTER TABLE cloudmold_order_header
    DROP FOREIGN KEY fk_order_address,
    DROP INDEX idx_order_address,
    DROP COLUMN destination_region_code,
    DROP COLUMN address_snapshot_version,
    DROP COLUMN address_ref;

ALTER TABLE cloudmold_app_checkout
    DROP FOREIGN KEY fk_app_checkout_address,
    DROP INDEX idx_app_checkout_address,
    DROP COLUMN destination_region_code,
    DROP COLUMN address_snapshot_version,
    DROP COLUMN address_ref;

DROP TABLE IF EXISTS cloudmold_app_address_snapshot;
