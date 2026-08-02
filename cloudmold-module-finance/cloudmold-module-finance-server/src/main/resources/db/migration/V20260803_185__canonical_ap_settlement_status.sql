UPDATE `cloudmold_finance_ap_open_item`
SET `status` = CASE
      WHEN `open_amount_minor` = 0 THEN 'SETTLED'
      WHEN `settled_amount_minor` > 0 THEN 'PARTIALLY_SETTLED'
      ELSE 'OPEN'
    END,
    `updated_at` = UTC_TIMESTAMP(6);

UPDATE `cloudmold_finance_ap_installment`
SET `status` = CASE
      WHEN `settled_amount_minor` = `amount_minor` THEN 'SETTLED'
      WHEN `settled_amount_minor` > 0 THEN 'PARTIALLY_SETTLED'
      ELSE 'OPEN'
    END,
    `updated_at` = UTC_TIMESTAMP(6);

UPDATE `cloudmold_finance_supplier_invoice` invoice
JOIN `cloudmold_finance_ap_open_item` open_item
  ON open_item.tenant_id = invoice.tenant_id
 AND open_item.supplier_invoice_id = invoice.supplier_invoice_id
SET invoice.settlement_status = CASE
      WHEN open_item.open_amount_minor = 0 THEN 'PAID'
      WHEN open_item.settled_amount_minor > 0 THEN 'PARTIALLY_PAID'
      ELSE 'UNPAID'
    END,
    invoice.updated_at = UTC_TIMESTAMP(6);

