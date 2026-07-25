SELECT 'app_checkout_invalid_owner' AS check_name, COUNT(*) AS violations
FROM cloudmold_app_checkout c
LEFT JOIN cloudmold_identity_principal p
  ON p.tenant_id=c.tenant_id AND p.principal_id=c.buyer_principal_id
WHERE p.principal_id IS NULL OR p.principal_type <> 'MEMBER' OR p.status <> 'ACTIVE'
UNION ALL
SELECT 'app_checkout_invalid_listing_offer', COUNT(*)
FROM cloudmold_app_checkout c
LEFT JOIN cloudmold_listing_header h
  ON h.tenant_id=c.tenant_id
 AND h.listing_id=c.listing_id COLLATE utf8mb4_0900_ai_ci
LEFT JOIN cloudmold_listing_offer o
  ON o.tenant_id=c.tenant_id
 AND o.listing_id=c.listing_id COLLATE utf8mb4_0900_ai_ci
 AND o.listing_offer_id=c.listing_offer_id COLLATE utf8mb4_0900_ai_ci
 AND o.canonical_sku_id=c.canonical_sku_id COLLATE utf8mb4_0900_ai_ci
WHERE h.listing_id IS NULL OR o.listing_offer_id IS NULL
UNION ALL
SELECT 'app_checkout_money_mismatch', COUNT(*)
FROM cloudmold_app_checkout
WHERE payable_amount_minor <> product_amount_minor + shipping_amount_minor - discount_amount_minor
UNION ALL
SELECT 'app_checkout_order_owner_mismatch', COUNT(*)
FROM cloudmold_app_checkout c
JOIN cloudmold_order_header o
  ON o.tenant_id=c.tenant_id
 AND o.order_id=c.order_id COLLATE utf8mb4_0900_ai_ci
WHERE c.status='ORDERED'
  AND o.buyer_id <> c.buyer_principal_id COLLATE utf8mb4_0900_ai_ci;
