-- All statements must return zero rows.

-- Header governance is tight.
SELECT cart_id
FROM cloudmold_app_cart
WHERE status <> 'ACTIVE'
   OR version < 0;

-- Every line must belong to the same tenant/principal cart and keep first-slice quantity bounds.
SELECT i.line_id
FROM cloudmold_app_cart_item i
LEFT JOIN cloudmold_app_cart c
  ON c.tenant_id = i.tenant_id AND BINARY c.cart_id = BINARY i.cart_id
WHERE c.cart_id IS NULL
   OR BINARY c.buyer_principal_id <> BINARY i.buyer_principal_id
   OR i.quantity < 1
   OR i.quantity > 99
   OR i.selected NOT IN (0, 1);

-- One server cart cannot exceed 50 lines in the first slice.
SELECT tenant_id, cart_id, COUNT(*) AS line_count
FROM cloudmold_app_cart_item
GROUP BY tenant_id, cart_id
HAVING COUNT(*) > 50;
