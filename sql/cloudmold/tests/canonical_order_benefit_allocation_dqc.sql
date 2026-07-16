SELECT 'order_benefit_header_application_balance' AS check_name, COUNT(*) AS violations
FROM (
  SELECT h.tenant_id,h.order_id,h.discount_amount_minor,
         COALESCE(SUM(a.amount_minor),0) application_amount_minor
  FROM cloudmold_order_header h
  LEFT JOIN cloudmold_order_benefit_application a
    ON a.tenant_id=h.tenant_id AND a.order_id=h.order_id
  GROUP BY h.tenant_id,h.order_id,h.discount_amount_minor
  HAVING application_amount_minor <> h.discount_amount_minor
) invalid
UNION ALL
SELECT 'order_benefit_application_allocation_balance', COUNT(*)
FROM (
  SELECT a.tenant_id,a.benefit_application_id,a.amount_minor,
         COALESCE(SUM(l.amount_minor),0) allocation_amount_minor
  FROM cloudmold_order_benefit_application a
  LEFT JOIN cloudmold_order_benefit_allocation l
    ON l.tenant_id=a.tenant_id AND l.benefit_application_id=a.benefit_application_id
  GROUP BY a.tenant_id,a.benefit_application_id,a.amount_minor
  HAVING allocation_amount_minor <> a.amount_minor
) invalid
UNION ALL
SELECT 'order_benefit_allocation_funding_balance', COUNT(*)
FROM (
  SELECT l.tenant_id,l.benefit_allocation_id,l.amount_minor,
         COALESCE(SUM(f.amount_minor),0) funding_amount_minor
  FROM cloudmold_order_benefit_allocation l
  LEFT JOIN cloudmold_order_benefit_funding f
    ON f.tenant_id=l.tenant_id AND f.benefit_allocation_id=l.benefit_allocation_id
  GROUP BY l.tenant_id,l.benefit_allocation_id,l.amount_minor
  HAVING funding_amount_minor <> l.amount_minor
) invalid
UNION ALL
SELECT 'order_benefit_item_net_balance', COUNT(*)
FROM (
  SELECT i.tenant_id,i.order_id,i.order_item_id,i.line_amount_minor,
         i.discount_amount_minor,i.net_amount_minor,COALESCE(SUM(l.amount_minor),0) allocated_amount_minor
  FROM cloudmold_order_item i
  LEFT JOIN cloudmold_order_benefit_allocation l
    ON l.tenant_id=i.tenant_id AND l.order_id=i.order_id AND l.order_item_id=i.order_item_id
  GROUP BY i.tenant_id,i.order_id,i.order_item_id,i.line_amount_minor,
           i.discount_amount_minor,i.net_amount_minor
  HAVING allocated_amount_minor <> i.discount_amount_minor
      OR i.net_amount_minor <> i.line_amount_minor - i.discount_amount_minor
) invalid
UNION ALL
SELECT 'order_benefit_event_application_parity', COUNT(*)
FROM (
  SELECT a.tenant_id,a.order_id,a.benefit_application_id,COUNT(e.event_id) event_count
  FROM cloudmold_order_benefit_application a
  LEFT JOIN cloudmold_event_outbox e
    ON e.tenant_id=a.tenant_id AND e.aggregate_type='order' AND e.aggregate_id=a.order_id
   AND e.event_type='order.benefit_application.recorded'
   AND JSON_UNQUOTE(JSON_EXTRACT(e.payload,'$.benefit_application_id'))=a.benefit_application_id
  GROUP BY a.tenant_id,a.order_id,a.benefit_application_id
  HAVING event_count <> 1
) invalid;
