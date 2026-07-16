-- Every query must return zero. Run only against a canonical local/test schema.

SELECT COUNT(*) AS after_sale_terminal_shape_violation
FROM cloudmold_after_sale_case a
JOIN cloudmold_after_sale_resolution_saga s
  ON s.tenant_id=a.tenant_id AND s.saga_id=a.resolution_saga_id
WHERE a.status='COMPLETED'
  AND (a.version<>4 OR a.refund_status<>'SUCCEEDED' OR a.approved_amount_minor IS NULL
    OR a.return_fulfillment_id IS NULL OR a.return_shipment_id IS NULL OR a.inspection_id IS NULL
    OR s.status<>'COMPLETED'
    OR s.version<CASE WHEN s.benefit_amount_minor=0 THEN 10 ELSE 12 END
    OR MOD(s.version-CASE WHEN s.benefit_amount_minor=0 THEN 10 ELSE 12 END,2)<>0
    OR s.active_step<>'NONE' OR s.completed_at IS NULL);

SELECT COUNT(*) AS after_sale_item_link_violation
FROM cloudmold_after_sale_case a
JOIN cloudmold_after_sale_item ai ON ai.tenant_id=a.tenant_id AND ai.after_sale_id=a.after_sale_id
LEFT JOIN cloudmold_order_item oi ON oi.tenant_id=ai.tenant_id
  AND BINARY oi.order_item_id=BINARY ai.order_item_id
WHERE oi.order_item_id IS NULL OR BINARY ai.canonical_sku_id<>BINARY oi.canonical_sku_id
   OR ai.quantity<>oi.quantity OR ai.line_amount_minor<>oi.line_amount_minor
   OR ai.discount_amount_minor<>oi.discount_amount_minor OR ai.net_amount_minor<>oi.net_amount_minor
   OR ai.line_amount_minor<>ai.discount_amount_minor+ai.net_amount_minor
   OR ai.net_amount_minor<>a.approved_amount_minor
   OR (a.status<>'COMPLETED' AND ai.active_guard<>1) OR (a.status='COMPLETED' AND ai.active_guard IS NOT NULL);

SELECT COUNT(*) AS return_fulfillment_item_violation
FROM cloudmold_return_fulfillment r
JOIN cloudmold_return_fulfillment_item ri
  ON ri.tenant_id=r.tenant_id AND ri.return_fulfillment_id=r.return_fulfillment_id
JOIN cloudmold_after_sale_item ai
  ON ai.tenant_id=ri.tenant_id AND ai.after_sale_item_id=ri.after_sale_item_id
WHERE ri.order_item_id<>ai.order_item_id OR ri.canonical_sku_id<>ai.canonical_sku_id
   OR ri.quantity<>ai.quantity;

SELECT COUNT(*) AS return_shipment_item_violation
FROM cloudmold_return_shipment rs
JOIN cloudmold_return_shipment_item rsi
  ON rsi.tenant_id=rs.tenant_id AND rsi.return_shipment_id=rs.return_shipment_id
JOIN cloudmold_return_fulfillment_item rfi
  ON rfi.tenant_id=rsi.tenant_id AND rfi.return_fulfillment_item_id=rsi.return_fulfillment_item_id
WHERE rsi.quantity<>rfi.quantity;

SELECT COUNT(*) AS return_inspection_violation
FROM cloudmold_return_fulfillment r
JOIN cloudmold_return_fulfillment_item ri
  ON ri.tenant_id=r.tenant_id AND ri.return_fulfillment_id=r.return_fulfillment_id
LEFT JOIN cloudmold_return_inspection i
  ON i.tenant_id=r.tenant_id AND i.return_fulfillment_id=r.return_fulfillment_id
WHERE r.status='INSPECTION_ACCEPTED'
  AND (i.inspection_id IS NULL OR i.quality_status<>'QUALIFIED'
    OR i.received_quantity<>ri.quantity OR i.accepted_quantity<>ri.quantity
    OR i.warehouse_id<>r.warehouse_id);

SELECT COUNT(*) AS after_sale_money_violation
FROM cloudmold_after_sale_case a
LEFT JOIN cloudmold_order_header o ON o.tenant_id=a.tenant_id AND BINARY o.order_id=BINARY a.order_id
LEFT JOIN cloudmold_payment p ON p.tenant_id=a.tenant_id AND BINARY p.payment_id=BINARY a.payment_id
WHERE a.status='COMPLETED'
  AND (a.currency_code<>'CNY' OR a.approved_amount_minor<>o.payable_amount_minor
    OR a.approved_amount_minor<>p.captured_amount_minor OR p.captured_amount_minor<>p.refunded_amount_minor
    OR p.status<>'REFUNDED' OR p.provider_code<>'INTERNAL_TEST' OR p.test_mode<>b'1');

SELECT COUNT(*) AS after_sale_saga_money_violation
FROM cloudmold_after_sale_resolution_saga s
JOIN cloudmold_after_sale_item ai
  ON ai.tenant_id=s.tenant_id AND ai.after_sale_item_id=s.after_sale_item_id
WHERE s.status='COMPLETED'
  AND (s.gross_amount_minor<>ai.line_amount_minor
    OR s.benefit_amount_minor<>ai.discount_amount_minor
    OR s.net_amount_minor<>ai.net_amount_minor
    OR s.gross_amount_minor<>s.benefit_amount_minor+s.net_amount_minor
    OR s.approved_amount_minor<>s.net_amount_minor);

SELECT COUNT(*) AS after_sale_inventory_violation
FROM cloudmold_after_sale_resolution_saga s
LEFT JOIN cloudmold_inventory_ledger_transaction t
  ON t.tenant_id=s.tenant_id AND t.ledger_transaction_id=s.inventory_ledger_transaction_id
LEFT JOIN cloudmold_inventory_ledger_entry e
  ON e.tenant_id=t.tenant_id AND e.ledger_transaction_id=t.ledger_transaction_id
WHERE s.status='COMPLETED'
  AND (t.command_type<>'RETURN' OR t.business_type<>'AFTER_SALE_RETURN'
    OR BINARY t.business_id<>BINARY s.after_sale_id
    OR BINARY t.business_item_id<>BINARY s.after_sale_item_id
    OR e.delta_on_hand_quantity<>s.quantity);

SELECT COUNT(*) AS after_sale_order_terminal_violation
FROM cloudmold_after_sale_resolution_saga s
LEFT JOIN cloudmold_order_header o ON o.tenant_id=s.tenant_id AND BINARY o.order_id=BINARY s.order_id
WHERE s.status='COMPLETED'
  AND (o.status<>'RETURNED' OR o.version<>s.order_version
    OR BINARY o.refund_id<>BINARY CAST(s.payment_refund_transaction_id AS CHAR));

SELECT COUNT(*) AS after_sale_saga_checkpoint_violation
FROM cloudmold_after_sale_resolution_saga s
WHERE s.status='COMPLETED'
  AND (s.inventory_operation_id IS NULL OR s.inventory_ledger_transaction_id IS NULL
    OR s.payment_refund_transaction_id IS NULL OR s.order_refund_operation_id IS NULL
    OR s.order_return_operation_id IS NULL
    OR (s.benefit_amount_minor=0 AND (s.benefit_reversal_status<>'NOT_REQUIRED'
      OR s.benefit_reversal_amount_minor<>0 OR s.benefit_reversal_batch_id IS NOT NULL))
    OR (s.benefit_amount_minor>0 AND (s.benefit_reversal_status<>'RECORDED'
      OR s.benefit_reversal_amount_minor<>s.benefit_amount_minor
      OR s.benefit_reversal_batch_id IS NULL OR s.benefit_reversal_occurred_at IS NULL)));

SELECT COUNT(*) AS after_sale_benefit_allocation_reversal_violation
FROM cloudmold_after_sale_benefit_reversal r
LEFT JOIN cloudmold_order_benefit_application a
  ON a.tenant_id=r.tenant_id AND a.benefit_application_id=r.benefit_application_id
LEFT JOIN cloudmold_order_benefit_allocation x
  ON x.tenant_id=r.tenant_id AND x.benefit_allocation_id=r.benefit_allocation_id
WHERE a.benefit_application_id IS NULL OR x.benefit_allocation_id IS NULL
   OR BINARY r.order_id<>BINARY a.order_id OR BINARY r.order_id<>BINARY x.order_id
   OR BINARY r.order_item_id<>BINARY x.order_item_id
   OR BINARY r.benefit_type<>BINARY a.benefit_type
   OR BINARY r.benefit_source_type<>BINARY a.benefit_source_type
   OR BINARY r.benefit_source_id<>BINARY a.benefit_source_id
   OR r.benefit_source_version<>a.benefit_source_version
   OR NOT (BINARY r.entitlement_id<=>BINARY a.entitlement_id)
   OR r.amount_minor<>x.amount_minor OR BINARY r.currency_code<>BINARY x.currency_code;

SELECT COUNT(*) AS after_sale_benefit_entitlement_reversal_violation
FROM cloudmold_after_sale_benefit_reversal r
LEFT JOIN cloudmold_after_sale_resolution_saga s
  ON s.tenant_id=r.tenant_id AND BINARY s.after_sale_id=BINARY r.after_sale_id
LEFT JOIN cloudmold_promotion_coupon_entitlement e
  ON e.tenant_id=r.tenant_id AND BINARY e.entitlement_id=BINARY r.entitlement_id
LEFT JOIN cloudmold_promotion_coupon_entitlement_ledger l
  ON l.tenant_id=r.tenant_id AND BINARY l.entitlement_id=BINARY r.entitlement_id
  AND l.entitlement_version=r.benefit_source_version+1
WHERE (r.entitlement_id IS NULL AND r.entitlement_effect_status<>'NOT_REQUIRED')
   OR (r.entitlement_id IS NOT NULL AND (
        r.entitlement_effect_status<>'RETURNED'
        OR BINARY r.benefit_source_type<>BINARY 'COUPON_ENTITLEMENT'
        OR BINARY r.benefit_source_id<>BINARY r.entitlement_id
        OR s.saga_id IS NULL OR e.entitlement_id IS NULL OR l.ledger_entry_id IS NULL
        OR e.status<>'RETURNED' OR e.version<>r.benefit_source_version+1
        OR BINARY e.order_ref<>BINARY s.run_id
        OR l.operation_type<>'RETURN_COUPON_ENTITLEMENT'
        OR l.previous_status NOT IN ('RESERVED','USED') OR l.current_status<>'RETURNED'
        OR BINARY l.order_ref<>BINARY s.run_id));

SELECT COUNT(*) AS after_sale_benefit_funding_reversal_violation
FROM cloudmold_after_sale_benefit_funding_reversal r
LEFT JOIN cloudmold_after_sale_benefit_reversal p
  ON p.tenant_id=r.tenant_id AND p.benefit_reversal_id=r.benefit_reversal_id
LEFT JOIN cloudmold_order_benefit_funding f
  ON f.tenant_id=r.tenant_id AND f.benefit_funding_id=r.benefit_funding_id
WHERE p.benefit_reversal_id IS NULL OR f.benefit_funding_id IS NULL
   OR BINARY r.after_sale_id<>BINARY p.after_sale_id
   OR BINARY r.reversal_batch_id<>BINARY p.reversal_batch_id
   OR BINARY r.funder_type<>BINARY f.funder_type OR BINARY r.funder_id<>BINARY f.funder_id
   OR r.amount_minor<>f.amount_minor OR BINARY r.currency_code<>BINARY f.currency_code;

SELECT COUNT(*) AS after_sale_benefit_conservation_violation
FROM cloudmold_after_sale_resolution_saga s
LEFT JOIN (
  SELECT tenant_id,after_sale_id,reversal_batch_id,SUM(amount_minor) amount_minor
  FROM cloudmold_after_sale_benefit_reversal
  GROUP BY tenant_id,after_sale_id,reversal_batch_id
) r ON r.tenant_id=s.tenant_id AND r.after_sale_id=s.after_sale_id
  AND r.reversal_batch_id=s.benefit_reversal_batch_id
LEFT JOIN (
  SELECT tenant_id,after_sale_id,reversal_batch_id,SUM(amount_minor) amount_minor
  FROM cloudmold_after_sale_benefit_funding_reversal
  GROUP BY tenant_id,after_sale_id,reversal_batch_id
) f ON f.tenant_id=s.tenant_id AND f.after_sale_id=s.after_sale_id
  AND f.reversal_batch_id=s.benefit_reversal_batch_id
WHERE s.status='COMPLETED' AND s.benefit_amount_minor>0
  AND (r.amount_minor IS NULL OR f.amount_minor IS NULL
    OR r.amount_minor<>s.benefit_amount_minor OR f.amount_minor<>r.amount_minor);

SELECT COUNT(*) AS after_sale_event_version_violation
FROM cloudmold_after_sale_case a
LEFT JOIN (
  SELECT tenant_id,aggregate_id,COUNT(*) event_count,MAX(aggregate_version) max_version
  FROM cloudmold_event_outbox WHERE event_type='after_sale.status.changed'
  GROUP BY tenant_id,aggregate_id
) e ON e.tenant_id=a.tenant_id AND BINARY e.aggregate_id=BINARY a.after_sale_id
WHERE e.event_count<>a.version OR e.max_version<>a.version;

SELECT COUNT(*) AS return_event_version_violation
FROM cloudmold_return_fulfillment r
LEFT JOIN (
  SELECT tenant_id,aggregate_id,COUNT(*) event_count,MAX(aggregate_version) max_version
  FROM cloudmold_event_outbox WHERE event_type='return_fulfillment.status.changed'
  GROUP BY tenant_id,aggregate_id
) e ON e.tenant_id=r.tenant_id AND BINARY e.aggregate_id=BINARY r.return_fulfillment_id
WHERE e.event_count<>r.version OR e.max_version<>r.version;

SELECT COUNT(*) AS after_sale_saga_event_version_violation
FROM cloudmold_after_sale_resolution_saga s
LEFT JOIN (
  SELECT tenant_id,aggregate_id,COUNT(*) event_count,MAX(aggregate_version) max_version
  FROM cloudmold_event_outbox WHERE event_type='after_sale.resolution_saga.status.changed'
  GROUP BY tenant_id,aggregate_id
) e ON e.tenant_id=s.tenant_id AND BINARY e.aggregate_id=BINARY s.saga_id
WHERE e.event_count<>s.version OR e.max_version<>s.version;

SELECT COUNT(*) AS after_sale_effect_order_violation
FROM cloudmold_after_sale_resolution_saga s
JOIN cloudmold_return_fulfillment_item ri ON ri.tenant_id=s.tenant_id
  AND ri.return_fulfillment_id=s.return_fulfillment_id
  AND ri.after_sale_item_id=s.after_sale_item_id
JOIN cloudmold_return_inspection q ON q.tenant_id=s.tenant_id
  AND q.inspection_id=s.inspection_id
  AND q.return_fulfillment_id=s.return_fulfillment_id
  AND q.return_fulfillment_item_id=ri.return_fulfillment_item_id
JOIN cloudmold_return_fulfillment_history h ON h.tenant_id=s.tenant_id
  AND h.return_fulfillment_id=s.return_fulfillment_id
  AND h.current_status='INSPECTION_ACCEPTED'
JOIN cloudmold_event_outbox i ON i.tenant_id=s.tenant_id
  AND i.event_type='inventory.stock.changed'
  AND BINARY JSON_UNQUOTE(JSON_EXTRACT(i.payload,'$.business_id'))=BINARY s.after_sale_id
  AND BINARY JSON_UNQUOTE(JSON_EXTRACT(i.payload,'$.business_item_id'))=BINARY s.after_sale_item_id
JOIN cloudmold_event_outbox p ON p.tenant_id=s.tenant_id AND BINARY p.aggregate_id=BINARY s.payment_id
  AND p.event_type='payment.status.changed'
  AND JSON_UNQUOTE(JSON_EXTRACT(p.payload,'$.current_status'))='REFUNDED'
JOIN cloudmold_event_outbox o ON o.tenant_id=s.tenant_id AND BINARY o.aggregate_id=BINARY s.order_id
  AND o.event_type='order.status.changed'
  AND JSON_UNQUOTE(JSON_EXTRACT(o.payload,'$.current_status'))='RETURNED'
JOIN cloudmold_event_outbox se ON se.tenant_id=s.tenant_id AND BINARY se.aggregate_id=BINARY s.saga_id
  AND se.event_type='after_sale.resolution_saga.status.changed'
  AND JSON_UNQUOTE(JSON_EXTRACT(se.payload,'$.current_status'))='COMPLETED'
WHERE s.status='COMPLETED'
  AND (q.created_at>i.recorded_at OR h.created_at>i.recorded_at
    OR i.recorded_at>p.recorded_at OR p.recorded_at>o.recorded_at OR o.recorded_at>se.recorded_at);

SELECT COUNT(*) AS after_sale_benefit_effect_order_violation
FROM cloudmold_after_sale_resolution_saga s
JOIN cloudmold_event_outbox i ON i.tenant_id=s.tenant_id
  AND i.event_type='inventory.stock.changed'
  AND BINARY JSON_UNQUOTE(JSON_EXTRACT(i.payload,'$.business_id'))=BINARY s.after_sale_id
JOIN cloudmold_event_outbox b ON b.tenant_id=s.tenant_id
  AND b.event_type='after_sale.benefit_reversal.recorded'
  AND BINARY JSON_UNQUOTE(JSON_EXTRACT(b.payload,'$.after_sale_id'))=BINARY s.after_sale_id
JOIN cloudmold_event_outbox p ON p.tenant_id=s.tenant_id AND BINARY p.aggregate_id=BINARY s.payment_id
  AND p.event_type='payment.status.changed'
  AND JSON_UNQUOTE(JSON_EXTRACT(p.payload,'$.current_status'))='REFUNDED'
WHERE s.status='COMPLETED' AND s.benefit_amount_minor>0
  AND (i.recorded_at>b.recorded_at OR b.recorded_at>p.recorded_at);

SELECT COUNT(*) AS after_sale_benefit_event_violation
FROM cloudmold_after_sale_benefit_reversal r
LEFT JOIN cloudmold_event_outbox e ON e.tenant_id=r.tenant_id
  AND e.event_type='after_sale.benefit_reversal.recorded'
  AND BINARY e.aggregate_id=BINARY r.benefit_reversal_id
WHERE e.event_id IS NULL OR e.schema_version<>1 OR e.aggregate_version<>1
   OR JSON_EXTRACT(e.payload,'$.amount_minor')<>r.amount_minor
   OR BINARY JSON_UNQUOTE(JSON_EXTRACT(e.payload,'$.benefit_allocation_id'))<>BINARY r.benefit_allocation_id;
