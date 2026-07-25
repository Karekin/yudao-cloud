SELECT 'order_head_item_amount_balance' AS check_name, COUNT(*) AS violations
FROM (
  SELECT h.tenant_id,h.order_id
  FROM cloudmold_order_header h
  LEFT JOIN cloudmold_order_item i ON i.tenant_id=h.tenant_id AND i.order_id=h.order_id
  GROUP BY h.tenant_id,h.order_id,h.product_amount_minor,h.total_quantity
  HAVING COALESCE(SUM(i.line_amount_minor),0) <> h.product_amount_minor
     OR COALESCE(SUM(i.quantity),0) <> h.total_quantity
) invalid
UNION ALL
SELECT 'order_history_continuity', COUNT(*)
FROM (
  SELECT tenant_id,order_id
  FROM cloudmold_order_status_history
  GROUP BY tenant_id,order_id
  HAVING MIN(aggregate_version) <> 1 OR MAX(aggregate_version) <> COUNT(*)
) invalid
UNION ALL
SELECT 'order_active_catalog_reference', COUNT(*)
FROM cloudmold_order_item i
JOIN cloudmold_catalog_sku k ON k.tenant_id=i.tenant_id AND k.sku_id=i.canonical_sku_id
JOIN cloudmold_catalog_spu p ON p.tenant_id=k.tenant_id AND p.spu_id=k.spu_id
WHERE k.status <> 10 OR p.status <> 30
UNION ALL
SELECT 'order_reservation_reference', COUNT(*)
FROM cloudmold_order_item i
JOIN cloudmold_order_header h ON h.tenant_id=i.tenant_id AND h.order_id=i.order_id
LEFT JOIN cloudmold_inventory_reservation r
  ON r.tenant_id=i.tenant_id AND r.reservation_id=i.reservation_id
LEFT JOIN cloudmold_inventory_reservation_v3 r3
  ON r3.tenant_id=i.tenant_id AND BINARY r3.reservation_id=BINARY i.reservation_id
WHERE h.status NOT IN ('PLACED','CANCELLED')
  AND NOT (
    (r.reservation_id IS NOT NULL
      AND BINARY r.business_id=BINARY h.order_id
      AND BINARY r.business_item_id=BINARY i.order_item_id)
    OR
    (r3.reservation_id IS NOT NULL
      AND BINARY r3.business_id=BINARY h.order_id
      AND BINARY r3.business_item_id=BINARY i.order_item_id)
  )
UNION ALL
SELECT 'payment_order_money_currency', COUNT(*)
FROM cloudmold_payment p
JOIN cloudmold_order_header o ON o.tenant_id=p.tenant_id AND o.order_id=p.order_id
WHERE p.payable_amount_minor <> o.payable_amount_minor OR p.currency_code <> o.currency_code
   OR p.run_id <> o.run_id
UNION ALL
SELECT 'payment_transaction_balance', COUNT(*)
FROM (
  SELECT p.tenant_id,p.payment_id,p.captured_amount_minor,p.refunded_amount_minor,
         SUM(CASE WHEN t.transaction_type='CAPTURE' THEN t.amount_minor ELSE 0 END) captured,
         SUM(CASE WHEN t.transaction_type='REFUND' THEN t.amount_minor ELSE 0 END) refunded
  FROM cloudmold_payment p
  LEFT JOIN cloudmold_payment_transaction t
    ON t.tenant_id=p.tenant_id AND t.payment_id=p.payment_id
  GROUP BY p.tenant_id,p.payment_id,p.captured_amount_minor,p.refunded_amount_minor
  HAVING captured <> p.captured_amount_minor OR refunded <> p.refunded_amount_minor
) invalid
UNION ALL
SELECT 'order_payment_outbox_version', COUNT(*)
FROM (
  SELECT h.tenant_id,h.order_id,h.version,COUNT(e.event_id) event_count
  FROM cloudmold_order_header h
  LEFT JOIN cloudmold_event_outbox e ON e.tenant_id=h.tenant_id AND e.aggregate_type='order'
    AND e.aggregate_id=h.order_id AND e.event_type='order.status.changed'
  GROUP BY h.tenant_id,h.order_id,h.version
  HAVING event_count <> h.version
  UNION ALL
  SELECT p.tenant_id,p.payment_id,p.version,COUNT(e.event_id)
  FROM cloudmold_payment p
  LEFT JOIN cloudmold_event_outbox e ON e.tenant_id=p.tenant_id AND e.aggregate_type='payment'
    AND e.aggregate_id=p.payment_id AND e.event_type='payment.status.changed'
  GROUP BY p.tenant_id,p.payment_id,p.version
  HAVING COUNT(e.event_id) <> p.version
) invalid;
