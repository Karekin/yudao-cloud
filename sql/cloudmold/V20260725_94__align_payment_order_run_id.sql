-- Payment belongs to the canonical Order run. Repair rows written by the first
-- App Commerce slice, which used the payment request run instead.
UPDATE cloudmold_payment p
JOIN cloudmold_order_header o
  ON o.tenant_id = p.tenant_id
 AND BINARY o.order_id = BINARY p.order_id
SET p.run_id = o.run_id,
    p.updated_at = UTC_TIMESTAMP(6)
WHERE BINARY p.run_id <> BINARY o.run_id;
