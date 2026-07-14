-- Every query must return zero. Run against the canonical local/test schema only.

SELECT COUNT(*) AS paid_saga_terminal_shape_violation
FROM cloudmold_order_cancellation_saga s
WHERE s.cancellation_mode='PAID_UNSHIPPED' AND s.status='COMPLETED'
  AND (s.order_status_at_request<>'PAYMENT_CONFIRMED' OR s.version<>9
    OR s.active_step<>'NONE' OR s.payment_status<>'REFUNDED'
    OR s.expected_fulfillment_count<>1 OR s.cancelled_fulfillment_count<>1
    OR s.expected_reservation_count<>s.released_reservation_count
    OR s.payment_refund_transaction_id IS NULL OR s.completed_at IS NULL);

SELECT COUNT(*) AS paid_saga_order_terminal_violation
FROM cloudmold_order_cancellation_saga s
LEFT JOIN cloudmold_order_header o
  ON o.tenant_id=s.tenant_id AND o.order_id=s.order_id
WHERE s.cancellation_mode='PAID_UNSHIPPED' AND s.status='COMPLETED'
  AND (o.order_id IS NULL OR o.status<>'CANCELLED' OR o.version<>5
    OR o.pre_cancellation_status<>'PAYMENT_CONFIRMED' OR o.cancellation_saga_id<>s.saga_id
    OR o.payment_id<>s.payment_id OR o.fulfillment_id IS NULL OR o.shipment_id IS NOT NULL
    OR o.refund_id<>CAST(s.payment_refund_transaction_id AS CHAR));

SELECT COUNT(*) AS paid_saga_payment_terminal_violation
FROM cloudmold_order_cancellation_saga s
LEFT JOIN cloudmold_payment p
  ON p.tenant_id=s.tenant_id AND p.payment_id=s.payment_id AND p.order_id=s.order_id
WHERE s.cancellation_mode='PAID_UNSHIPPED' AND s.status='COMPLETED'
  AND (p.payment_id IS NULL OR p.status<>'REFUNDED' OR p.version<>2
    OR p.captured_amount_minor<>p.refunded_amount_minor OR p.test_mode<>b'1'
    OR p.provider_code<>'INTERNAL_TEST');

SELECT COUNT(*) AS paid_saga_payment_transaction_violation
FROM cloudmold_order_cancellation_saga s
LEFT JOIN (
  SELECT tenant_id,payment_id,COUNT(*) AS transaction_count,
         SUM(CASE WHEN transaction_type='CAPTURE' THEN amount_minor ELSE 0 END) AS captured_amount,
         SUM(CASE WHEN transaction_type='REFUND' THEN amount_minor ELSE 0 END) AS refunded_amount,
         MAX(CASE WHEN transaction_type='REFUND' THEN transaction_id END) AS refund_transaction_id
  FROM cloudmold_payment_transaction
  GROUP BY tenant_id,payment_id
) t ON t.tenant_id=s.tenant_id AND t.payment_id=s.payment_id
WHERE s.cancellation_mode='PAID_UNSHIPPED' AND s.status='COMPLETED'
  AND (t.transaction_count<>2 OR t.captured_amount<>t.refunded_amount
    OR t.refund_transaction_id<>s.payment_refund_transaction_id);

SELECT COUNT(*) AS paid_saga_fulfillment_terminal_violation
FROM cloudmold_order_cancellation_saga s
LEFT JOIN cloudmold_order_cancellation_saga_fulfillment sf
  ON sf.tenant_id=s.tenant_id AND sf.saga_id=s.saga_id
LEFT JOIN cloudmold_fulfillment_order f
  ON f.tenant_id=sf.tenant_id AND f.fulfillment_id=sf.fulfillment_id AND f.order_id=s.order_id
WHERE s.cancellation_mode='PAID_UNSHIPPED' AND s.status='COMPLETED'
  AND (sf.fulfillment_id IS NULL OR sf.status<>'CANCELLED'
    OR f.status<>'CANCELLED' OR f.version<>3 OR f.pre_cancellation_status<>'CREATED'
    OR f.cancellation_saga_id<>s.saga_id);

SELECT COUNT(*) AS paid_saga_shipment_violation
FROM cloudmold_order_cancellation_saga s
JOIN cloudmold_order_cancellation_saga_fulfillment sf
  ON sf.tenant_id=s.tenant_id AND sf.saga_id=s.saga_id
JOIN cloudmold_shipment sh
  ON sh.tenant_id=sf.tenant_id AND sh.fulfillment_id=sf.fulfillment_id
WHERE s.cancellation_mode='PAID_UNSHIPPED';

SELECT COUNT(*) AS paid_saga_reservation_terminal_violation
FROM cloudmold_order_cancellation_saga s
JOIN cloudmold_order_cancellation_saga_item i
  ON i.tenant_id=s.tenant_id AND i.saga_id=s.saga_id
LEFT JOIN cloudmold_inventory_reservation r
  ON r.tenant_id=i.tenant_id AND r.reservation_id=i.reservation_id
LEFT JOIN cloudmold_inventory_balance b
  ON b.tenant_id=r.tenant_id AND b.balance_id=r.balance_id
WHERE s.cancellation_mode='PAID_UNSHIPPED' AND s.status='COMPLETED'
  AND (i.status<>'RELEASED' OR r.status<>30 OR b.reserved_quantity<>0);

SELECT COUNT(*) AS paid_saga_fulfillment_snapshot_violation
FROM cloudmold_order_cancellation_saga s
LEFT JOIN cloudmold_order_cancellation_saga_fulfillment sf
  ON sf.tenant_id=s.tenant_id AND sf.saga_id=s.saga_id
WHERE s.cancellation_mode='PAID_UNSHIPPED'
  AND (sf.fulfillment_id IS NULL OR sf.status_at_request<>'CREATED'
    OR sf.fulfillment_version_at_request<>1
    OR sf.request_idempotency_key NOT LIKE CONCAT('cancel-saga:',s.saga_id,':fulfillment:%:request')
    OR sf.finalize_idempotency_key NOT LIKE CONCAT('cancel-saga:',s.saga_id,':fulfillment:%:finalize'));

SELECT COUNT(*) AS paid_saga_outbox_version_violation
FROM cloudmold_order_cancellation_saga s
LEFT JOIN (
  SELECT tenant_id,aggregate_id,COUNT(*) AS event_count,MAX(aggregate_version) AS max_version,
         MIN(schema_version) AS min_schema,MAX(schema_version) AS max_schema
  FROM cloudmold_event_outbox
  WHERE event_type='order.cancellation_saga.status_changed'
  GROUP BY tenant_id,aggregate_id
) e ON e.tenant_id=s.tenant_id AND e.aggregate_id=s.saga_id
WHERE s.cancellation_mode='PAID_UNSHIPPED'
  AND (e.event_count<>s.version OR e.max_version<>s.version OR e.min_schema<>2 OR e.max_schema<>2);

SELECT COUNT(*) AS paid_saga_participant_event_count_violation
FROM cloudmold_order_cancellation_saga s
LEFT JOIN (
  SELECT tenant_id,JSON_UNQUOTE(JSON_EXTRACT(payload,'$.cancellation_saga_id')) AS saga_id,
         SUM(event_type='fulfillment.status.changed') AS fulfillment_events,
         SUM(event_type='payment.status.changed') AS payment_events,
         SUM(event_type='inventory.stock.changed') AS inventory_events,
         SUM(event_type='order.status.changed') AS order_events
  FROM cloudmold_event_outbox
  WHERE JSON_UNQUOTE(JSON_EXTRACT(payload,'$.cancellation_saga_id')) IS NOT NULL
  GROUP BY tenant_id,JSON_UNQUOTE(JSON_EXTRACT(payload,'$.cancellation_saga_id'))
) e ON e.tenant_id=s.tenant_id AND e.saga_id=s.saga_id
WHERE s.cancellation_mode='PAID_UNSHIPPED' AND s.status='COMPLETED'
  AND (e.fulfillment_events<>2 OR e.payment_events<>1 OR e.inventory_events<>1 OR e.order_events<>2);

SELECT COUNT(*) AS paid_saga_participant_step_violation
FROM cloudmold_order_cancellation_saga s
JOIN cloudmold_event_outbox e
  ON e.tenant_id=s.tenant_id
 AND JSON_UNQUOTE(JSON_EXTRACT(e.payload,'$.cancellation_saga_id'))=s.saga_id
WHERE s.cancellation_mode='PAID_UNSHIPPED'
  AND ((e.event_type='fulfillment.status.changed'
        AND CAST(JSON_UNQUOTE(JSON_EXTRACT(e.payload,'$.cancellation_step_ordinal')) AS UNSIGNED)<>1)
    OR (e.event_type='payment.status.changed'
        AND CAST(JSON_UNQUOTE(JSON_EXTRACT(e.payload,'$.cancellation_step_ordinal')) AS UNSIGNED)<>2)
    OR (e.event_type='inventory.stock.changed'
        AND CAST(JSON_UNQUOTE(JSON_EXTRACT(e.payload,'$.cancellation_step_ordinal')) AS UNSIGNED)<>3)
    OR (e.event_type='order.status.changed'
        AND JSON_UNQUOTE(JSON_EXTRACT(e.payload,'$.current_status'))='CANCELLED'
        AND CAST(JSON_UNQUOTE(JSON_EXTRACT(e.payload,'$.cancellation_step_ordinal')) AS UNSIGNED)<>4));

SELECT COUNT(*) AS paid_saga_participant_timing_violation
FROM cloudmold_order_cancellation_saga s
JOIN cloudmold_event_outbox f ON f.tenant_id=s.tenant_id AND f.aggregate_id=(
  SELECT sf.fulfillment_id FROM cloudmold_order_cancellation_saga_fulfillment sf
  WHERE sf.tenant_id=s.tenant_id AND sf.saga_id=s.saga_id)
  AND f.event_type='fulfillment.status.changed'
  AND JSON_UNQUOTE(JSON_EXTRACT(f.payload,'$.current_status'))='CANCELLED'
JOIN cloudmold_event_outbox p ON p.tenant_id=s.tenant_id AND p.aggregate_id=s.payment_id
  AND p.event_type='payment.status.changed'
  AND JSON_UNQUOTE(JSON_EXTRACT(p.payload,'$.current_status'))='REFUNDED'
JOIN cloudmold_event_outbox i ON i.tenant_id=s.tenant_id
  AND i.event_type='inventory.stock.changed'
  AND JSON_UNQUOTE(JSON_EXTRACT(i.payload,'$.cancellation_saga_id'))=s.saga_id
JOIN cloudmold_event_outbox o ON o.tenant_id=s.tenant_id AND o.aggregate_id=s.order_id
  AND o.event_type='order.status.changed'
  AND JSON_UNQUOTE(JSON_EXTRACT(o.payload,'$.current_status'))='CANCELLED'
JOIN cloudmold_event_outbox se ON se.tenant_id=s.tenant_id AND se.aggregate_id=s.saga_id
  AND se.event_type='order.cancellation_saga.status_changed'
  AND JSON_UNQUOTE(JSON_EXTRACT(se.payload,'$.current_status'))='COMPLETED'
WHERE s.cancellation_mode='PAID_UNSHIPPED' AND s.status='COMPLETED'
  AND (f.recorded_at>p.recorded_at OR p.recorded_at>i.recorded_at
    OR i.recorded_at>o.recorded_at OR o.recorded_at>se.recorded_at);

SELECT COUNT(*) AS paid_saga_operation_result_violation
FROM cloudmold_order_cancellation_saga_operation op
JOIN cloudmold_order_cancellation_saga s
  ON s.tenant_id=op.tenant_id AND s.saga_id=op.saga_id
WHERE s.cancellation_mode='PAID_UNSHIPPED' AND op.status=10 AND op.result_json IS NULL;
