-- Every query must return zero. Run against the canonical local/test schema only.

SELECT COUNT(*) AS listing_history_version_violation
FROM cloudmold_listing_status_history h
WHERE h.aggregate_version <> (
  SELECT COUNT(*) FROM cloudmold_listing_status_history x
  WHERE x.tenant_id=h.tenant_id AND x.listing_id=h.listing_id
    AND x.aggregate_version <= h.aggregate_version
);

SELECT COUNT(*) AS published_listing_approval_violation
FROM cloudmold_listing_header
WHERE status='PUBLISHED'
  AND NOT (completion_passed=b'1' AND business_approved=b'1' AND risk_approved=b'1');

SELECT COUNT(*) AS listing_offer_catalog_violation
FROM cloudmold_listing_offer o
JOIN cloudmold_listing_header h
  ON h.tenant_id=o.tenant_id AND h.listing_id=o.listing_id
LEFT JOIN cloudmold_catalog_sku s
  ON s.tenant_id=o.tenant_id AND s.sku_id=o.canonical_sku_id AND s.spu_id=h.canonical_spu_id
-- Catalog persists the ACTIVE enum as code 10; do not compare the physical column with the API label.
WHERE s.sku_id IS NULL OR s.status <> 10 OR o.price_minor < 0 OR o.currency_code <> 'CNY';

SELECT COUNT(*) AS order_listing_snapshot_violation
FROM cloudmold_order_item i
LEFT JOIN cloudmold_listing_offer o
  ON o.tenant_id=i.tenant_id AND o.listing_id=i.listing_id AND o.listing_offer_id=i.listing_offer_id
WHERE i.listing_id IS NOT NULL
  AND (o.listing_offer_id IS NULL OR o.canonical_sku_id<>i.canonical_sku_id
    OR o.price_minor<>i.unit_price_minor OR o.revision<>i.listing_revision
    OR i.listing_version IS NULL OR i.channel_code IS NULL OR i.shop_id IS NULL);

SELECT COUNT(*) AS fulfillment_history_version_violation
FROM cloudmold_fulfillment_status_history h
WHERE h.aggregate_version <> (
  SELECT COUNT(*) FROM cloudmold_fulfillment_status_history x
  WHERE x.tenant_id=h.tenant_id AND x.fulfillment_id=h.fulfillment_id
    AND x.aggregate_version <= h.aggregate_version
);

SELECT COUNT(*) AS fulfillment_item_order_mismatch
FROM cloudmold_fulfillment_item f
LEFT JOIN cloudmold_fulfillment_order h
  ON h.tenant_id=f.tenant_id AND h.fulfillment_id=f.fulfillment_id
LEFT JOIN cloudmold_order_item i
  ON i.tenant_id=f.tenant_id AND i.order_id=h.order_id AND i.order_item_id=f.order_item_id
WHERE i.order_item_id IS NULL OR i.canonical_sku_id<>f.canonical_sku_id
   OR i.quantity<>f.quantity OR i.reservation_id<>f.reservation_id;

SELECT COUNT(*) AS shipment_item_quantity_violation
FROM cloudmold_fulfillment_item f
LEFT JOIN (
  SELECT si.tenant_id,si.fulfillment_item_id,SUM(si.quantity) AS shipped_quantity
  FROM cloudmold_shipment_item si GROUP BY si.tenant_id,si.fulfillment_item_id
) s ON s.tenant_id=f.tenant_id AND s.fulfillment_item_id=f.fulfillment_item_id
JOIN cloudmold_fulfillment_order h
  ON h.tenant_id=f.tenant_id AND h.fulfillment_id=f.fulfillment_id
WHERE h.status IN ('SHIPPED','IN_TRANSIT','DELIVERED')
  AND (s.fulfillment_item_id IS NULL OR s.shipped_quantity<>f.quantity);

SELECT COUNT(*) AS shipment_milestone_violation
FROM cloudmold_shipment
WHERE (status IN ('IN_TRANSIT','DELIVERED') AND in_transit_at IS NULL)
   OR (status='DELIVERED' AND delivered_at IS NULL)
   OR in_transit_at < shipped_at OR delivered_at < in_transit_at;

SELECT COUNT(*) AS order_fulfillment_state_violation
FROM cloudmold_order_header o
LEFT JOIN cloudmold_fulfillment_order f
  ON f.tenant_id=o.tenant_id AND f.fulfillment_id=o.fulfillment_id AND f.order_id=o.order_id
LEFT JOIN cloudmold_shipment s
  ON s.tenant_id=o.tenant_id AND s.fulfillment_id=f.fulfillment_id AND s.shipment_id=o.shipment_id
WHERE o.status IN ('SHIPPED','COMPLETED','REFUNDED','RETURNED')
  AND EXISTS (SELECT 1 FROM cloudmold_order_item i
              WHERE i.tenant_id=o.tenant_id AND i.order_id=o.order_id AND i.listing_id IS NOT NULL)
  AND (f.fulfillment_id IS NULL OR s.shipment_id IS NULL
    OR f.status NOT IN ('SHIPPED','IN_TRANSIT','DELIVERED'));

SELECT COUNT(*) AS completed_before_delivery_violation
FROM cloudmold_order_status_history oh
JOIN cloudmold_order_header o
  ON o.tenant_id=oh.tenant_id AND o.order_id=oh.order_id
LEFT JOIN cloudmold_fulfillment_status_history fh
  ON fh.tenant_id=o.tenant_id AND fh.fulfillment_id=o.fulfillment_id AND fh.current_status='DELIVERED'
WHERE oh.current_status='COMPLETED'
  AND EXISTS (SELECT 1 FROM cloudmold_order_item i
              WHERE i.tenant_id=o.tenant_id AND i.order_id=o.order_id AND i.listing_id IS NOT NULL)
  AND (fh.history_id IS NULL OR fh.occurred_at>oh.occurred_at);

SELECT COUNT(*) AS cancelled_reservation_violation
FROM cloudmold_order_header o
JOIN cloudmold_order_item i
  ON i.tenant_id=o.tenant_id AND i.order_id=o.order_id
LEFT JOIN cloudmold_inventory_reservation r
  ON r.tenant_id=i.tenant_id AND r.reservation_id=i.reservation_id
WHERE o.status='CANCELLED' AND i.reservation_id IS NOT NULL
  AND (r.reservation_id IS NULL OR r.status<>30 OR r.business_type<>'TRADE_ORDER'
    OR r.business_id<>o.order_id OR r.business_item_id<>i.order_item_id);
