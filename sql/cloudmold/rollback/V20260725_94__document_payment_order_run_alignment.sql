-- Data repair is intentionally non-reversible: the previous payment run_id
-- violated the canonical payment_order_money_currency invariant. Rolling back
-- the application does not restore invalid cross-domain identifiers.
SELECT 'payment/order run_id alignment is non-reversible by design' AS rollback_note;
