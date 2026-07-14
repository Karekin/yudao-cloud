-- Every query must return zero. Run against the canonical local/test schema only.

SELECT COUNT(*) AS cancellation_saga_history_version_violation
FROM cloudmold_order_cancellation_saga_history h
WHERE h.aggregate_version <> (
  SELECT COUNT(*) FROM cloudmold_order_cancellation_saga_history x
  WHERE x.tenant_id=h.tenant_id AND x.saga_id=h.saga_id
    AND x.aggregate_version <= h.aggregate_version
);

SELECT COUNT(*) AS completed_saga_order_violation
FROM cloudmold_order_cancellation_saga s
LEFT JOIN cloudmold_order_header o
  ON o.tenant_id=s.tenant_id AND o.order_id=s.order_id
WHERE s.cancellation_mode='UNPAID_RESERVED' AND s.status='COMPLETED'
  AND (o.order_id IS NULL OR o.status<>'CANCELLED'
    OR o.cancellation_saga_id<>s.saga_id OR o.pre_cancellation_status<>'INVENTORY_RESERVED');

SELECT COUNT(*) AS completed_saga_reservation_violation
FROM cloudmold_order_cancellation_saga s
JOIN cloudmold_order_cancellation_saga_item i
  ON i.tenant_id=s.tenant_id AND i.saga_id=s.saga_id
LEFT JOIN cloudmold_inventory_reservation r
  ON r.tenant_id=i.tenant_id AND r.reservation_id=i.reservation_id
WHERE s.status='COMPLETED'
  AND (i.status<>'RELEASED' OR r.status<>30);

SELECT COUNT(*) AS fenced_order_without_active_saga
FROM cloudmold_order_header o
LEFT JOIN cloudmold_order_cancellation_saga s
  ON s.tenant_id=o.tenant_id AND s.saga_id=o.cancellation_saga_id AND s.order_id=o.order_id
WHERE o.status='CANCELLATION_PENDING'
  AND (s.saga_id IS NULL OR s.status='COMPLETED');

SELECT COUNT(*) AS cancellation_saga_item_identity_violation
FROM cloudmold_order_cancellation_saga_item i
JOIN cloudmold_order_cancellation_saga s
  ON s.tenant_id=i.tenant_id AND s.saga_id=i.saga_id
LEFT JOIN cloudmold_order_item oi
  ON oi.tenant_id=i.tenant_id AND oi.order_id=s.order_id AND oi.order_item_id=i.order_item_id
LEFT JOIN cloudmold_inventory_reservation r
  ON r.tenant_id=i.tenant_id AND r.reservation_id=i.reservation_id
WHERE oi.order_item_id IS NULL OR oi.reservation_id<>i.reservation_id
   OR oi.canonical_sku_id<>i.canonical_sku_id OR oi.quantity<>i.quantity
   OR r.reservation_id IS NULL OR r.business_type<>'TRADE_ORDER'
   OR r.business_id<>s.order_id OR r.business_item_id<>i.order_item_id OR r.quantity<>i.quantity;

SELECT COUNT(*) AS cancellation_saga_inventory_snapshot_violation
FROM cloudmold_order_cancellation_saga_item i
JOIN cloudmold_inventory_reservation r
  ON r.tenant_id=i.tenant_id AND r.reservation_id=i.reservation_id
JOIN cloudmold_inventory_balance b
  ON b.tenant_id=r.tenant_id AND b.balance_id=r.balance_id
WHERE i.owner_id<>b.owner_id OR i.canonical_sku_id<>b.canonical_sku_id
   OR i.warehouse_id<>b.warehouse_id OR i.stock_status<>b.stock_status
   OR i.quality_status<>b.quality_status OR i.uom_code<>b.base_uom_code;

SELECT COUNT(*) AS completed_saga_dirty_terminal_violation
FROM cloudmold_order_cancellation_saga
WHERE status='COMPLETED'
  AND (released_reservation_count<>expected_reservation_count OR active_step<>'NONE'
    OR lease_owner IS NOT NULL OR lease_until IS NOT NULL OR next_retry_at IS NOT NULL
    OR last_error_code IS NOT NULL OR last_error_message IS NOT NULL OR completed_at IS NULL);

SELECT COUNT(*) AS manual_review_without_error_violation
FROM cloudmold_order_cancellation_saga
WHERE status='MANUAL_REVIEW'
  AND (last_error_code IS NULL OR last_error_message IS NULL OR attempt_count<max_attempts);

SELECT COUNT(*) AS cancellation_saga_outbox_version_violation
FROM cloudmold_order_cancellation_saga s
LEFT JOIN (
  SELECT tenant_id,aggregate_id,COUNT(*) AS event_count,MAX(aggregate_version) AS max_version
  FROM cloudmold_event_outbox
  WHERE event_type='order.cancellation_saga.status_changed'
  GROUP BY tenant_id,aggregate_id
) e ON e.tenant_id=s.tenant_id AND e.aggregate_id=s.saga_id
WHERE e.aggregate_id IS NULL OR e.event_count<>s.version OR e.max_version<>s.version;

SELECT COUNT(*) AS cancelled_saga_money_fulfillment_violation
FROM cloudmold_order_cancellation_saga s
JOIN cloudmold_order_header o
  ON o.tenant_id=s.tenant_id AND o.order_id=s.order_id
LEFT JOIN cloudmold_payment p
  ON p.tenant_id=s.tenant_id AND p.order_id=s.order_id
LEFT JOIN cloudmold_fulfillment_order f
  ON f.tenant_id=s.tenant_id AND f.order_id=s.order_id
WHERE s.cancellation_mode='UNPAID_RESERVED'
  AND (p.payment_id IS NOT NULL OR f.fulfillment_id IS NOT NULL
   OR o.payment_id IS NOT NULL OR o.fulfillment_id IS NOT NULL OR o.shipment_id IS NOT NULL);

SELECT COUNT(*) AS cancellation_saga_terminal_timing_violation
FROM cloudmold_order_cancellation_saga s
JOIN cloudmold_order_cancellation_saga_item i
  ON i.tenant_id=s.tenant_id AND i.saga_id=s.saga_id
JOIN cloudmold_event_outbox release_event
  ON release_event.tenant_id=s.tenant_id
 AND release_event.event_type='inventory.stock.changed'
 AND JSON_UNQUOTE(JSON_EXTRACT(release_event.payload,'$.movement_type'))='RESERVATION_RELEASE'
 AND JSON_UNQUOTE(JSON_EXTRACT(release_event.payload,'$.reservation_id'))=i.reservation_id
JOIN cloudmold_event_outbox order_event
  ON order_event.tenant_id=s.tenant_id
 AND order_event.event_type='order.status.changed'
 AND order_event.aggregate_id=s.order_id
 AND JSON_UNQUOTE(JSON_EXTRACT(order_event.payload,'$.current_status'))='CANCELLED'
JOIN cloudmold_event_outbox saga_event
  ON saga_event.tenant_id=s.tenant_id
 AND saga_event.event_type='order.cancellation_saga.status_changed'
 AND saga_event.aggregate_id=s.saga_id
 AND JSON_UNQUOTE(JSON_EXTRACT(saga_event.payload,'$.current_status'))='COMPLETED'
WHERE s.status='COMPLETED'
  AND (release_event.recorded_at>order_event.recorded_at
       OR order_event.recorded_at>saga_event.recorded_at);

SELECT COUNT(*) AS cancellation_saga_operation_result_violation
FROM cloudmold_order_cancellation_saga_operation
WHERE status=10 AND (saga_id IS NULL OR result_json IS NULL);
