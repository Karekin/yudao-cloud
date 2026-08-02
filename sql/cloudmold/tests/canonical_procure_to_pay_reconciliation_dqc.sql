-- Canonical P2P controls. Every statement must return zero rows.

-- Supplier invoice header must equal immutable line totals.
SELECT i.tenant_id, i.supplier_invoice_id, i.net_amount_minor, i.tax_amount_minor, i.gross_amount_minor,
       SUM(l.net_amount_minor) AS line_net_amount_minor,
       SUM(l.tax_amount_minor) AS line_tax_amount_minor,
       SUM(l.gross_amount_minor) AS line_gross_amount_minor
  FROM cloudmold_finance_supplier_invoice i
  JOIN cloudmold_finance_supplier_invoice_line l
    ON l.tenant_id=i.tenant_id AND l.supplier_invoice_id=i.supplier_invoice_id
 GROUP BY i.tenant_id,i.supplier_invoice_id,i.net_amount_minor,i.tax_amount_minor,i.gross_amount_minor
HAVING i.net_amount_minor<>SUM(l.net_amount_minor)
    OR i.tax_amount_minor<>SUM(l.tax_amount_minor)
    OR i.gross_amount_minor<>SUM(l.gross_amount_minor);

-- Active three-way allocations may not exceed the exact accepted inventory movement snapshot.
SELECT a.tenant_id,a.inventory_movement_id,a.inventory_movement_version,
       SUM(a.allocated_quantity) AS allocated_quantity,i.movement_quantity
  FROM cloudmold_finance_invoice_match_receipt_allocation a
  JOIN cloudmold_finance_inventory_movement_evidence i
    ON i.tenant_id=a.tenant_id AND i.inventory_movement_id=a.inventory_movement_id
   AND i.source_version=a.inventory_movement_version AND i.disposition='ACCEPTED'
 WHERE a.status='ACTIVE'
 GROUP BY a.tenant_id,a.inventory_movement_id,a.inventory_movement_version,i.movement_quantity
HAVING SUM(a.allocated_quantity)>i.movement_quantity;

-- Every immutable source event must own exactly one typed evidence row.
SELECT x.tenant_id,x.inbox_id,COUNT(*) AS typed_evidence_count
  FROM (
        SELECT tenant_id,inbox_id FROM cloudmold_finance_po_line_evidence
        UNION ALL SELECT tenant_id,inbox_id FROM cloudmold_finance_receipt_line_evidence
        UNION ALL SELECT tenant_id,inbox_id FROM cloudmold_finance_quality_disposition_evidence
        UNION ALL SELECT tenant_id,inbox_id FROM cloudmold_finance_inventory_movement_evidence
       ) x
 GROUP BY x.tenant_id,x.inbox_id
HAVING COUNT(*)<>1;

SELECT e.tenant_id,e.inbox_id,e.source_event_id
  FROM cloudmold_finance_event_inbox e
  LEFT JOIN (
        SELECT tenant_id,inbox_id FROM cloudmold_finance_po_line_evidence
        UNION ALL SELECT tenant_id,inbox_id FROM cloudmold_finance_receipt_line_evidence
        UNION ALL SELECT tenant_id,inbox_id FROM cloudmold_finance_quality_disposition_evidence
        UNION ALL SELECT tenant_id,inbox_id FROM cloudmold_finance_inventory_movement_evidence
       ) x ON x.tenant_id=e.tenant_id AND x.inbox_id=e.inbox_id
 WHERE x.inbox_id IS NULL;

-- A posted invoice must atomically own one AP item and one posted balanced journal.
SELECT i.tenant_id,i.supplier_invoice_id,i.posted_journal_entry_id,
       COUNT(DISTINCT a.ap_open_item_id) AS ap_count,
       COUNT(DISTINCT j.journal_entry_id) AS journal_count
  FROM cloudmold_finance_supplier_invoice i
  LEFT JOIN cloudmold_finance_ap_open_item a
    ON a.tenant_id=i.tenant_id AND a.supplier_invoice_id=i.supplier_invoice_id
  LEFT JOIN cloudmold_finance_journal_entry j
    ON j.tenant_id=i.tenant_id AND j.journal_entry_id=i.posted_journal_entry_id
   AND j.source_type='SUPPLIER_INVOICE' AND j.source_id=i.supplier_invoice_id
 WHERE i.lifecycle_status='POSTED'
 GROUP BY i.tenant_id,i.supplier_invoice_id,i.posted_journal_entry_id
HAVING COUNT(DISTINCT a.ap_open_item_id)<>1 OR COUNT(DISTINCT j.journal_entry_id)<>1;

-- AP subledger and installment schedule must reconcile exactly.
SELECT a.tenant_id,a.ap_open_item_id,a.original_amount_minor,a.settled_amount_minor,a.open_amount_minor,
       COALESCE(SUM(s.amount_minor),0) AS installment_amount_minor,
       COALESCE(SUM(s.settled_amount_minor),0) AS installment_settled_minor
  FROM cloudmold_finance_ap_open_item a
  LEFT JOIN cloudmold_finance_ap_installment s
    ON s.tenant_id=a.tenant_id AND s.ap_open_item_id=a.ap_open_item_id
 GROUP BY a.tenant_id,a.ap_open_item_id,a.original_amount_minor,a.settled_amount_minor,a.open_amount_minor
HAVING a.original_amount_minor<>a.settled_amount_minor+a.open_amount_minor
    OR a.original_amount_minor<>COALESCE(SUM(s.amount_minor),0)
    OR a.settled_amount_minor<>COALESCE(SUM(s.settled_amount_minor),0);

-- Payment instruction, AP allocation and bank settlement must reconcile.
SELECT p.tenant_id,p.payment_instruction_id,p.total_amount_minor,
       COALESCE(a.allocated_amount_minor,0) AS allocated_amount_minor,
       COALESCE(s.settled_amount_minor,0) AS settled_amount_minor
  FROM cloudmold_finance_supplier_payment_instruction p
  LEFT JOIN (SELECT tenant_id,payment_instruction_id,SUM(amount_minor) allocated_amount_minor
               FROM cloudmold_finance_supplier_payment_allocation
              GROUP BY tenant_id,payment_instruction_id) a
    ON a.tenant_id=p.tenant_id AND a.payment_instruction_id=p.payment_instruction_id
  LEFT JOIN (SELECT tenant_id,payment_instruction_id,SUM(settled_amount_minor) settled_amount_minor
               FROM cloudmold_finance_supplier_payment_settlement
              GROUP BY tenant_id,payment_instruction_id) s
    ON s.tenant_id=p.tenant_id AND s.payment_instruction_id=p.payment_instruction_id
 WHERE p.total_amount_minor<>COALESCE(a.allocated_amount_minor,0)
    OR (p.status='SETTLED' AND p.total_amount_minor<>COALESCE(s.settled_amount_minor,0));

-- General-ledger headers and immutable lines must remain balanced.
SELECT j.tenant_id,j.journal_entry_id,j.debit_total_minor,j.credit_total_minor,
       COALESCE(SUM(l.debit_amount_minor),0) AS line_debit_minor,
       COALESCE(SUM(l.credit_amount_minor),0) AS line_credit_minor
  FROM cloudmold_finance_journal_entry j
  LEFT JOIN cloudmold_finance_journal_line l
    ON l.tenant_id=j.tenant_id AND l.journal_entry_id=j.journal_entry_id
 WHERE j.status='POSTED'
 GROUP BY j.tenant_id,j.journal_entry_id,j.debit_total_minor,j.credit_total_minor
HAVING j.debit_total_minor<>j.credit_total_minor
    OR j.debit_total_minor<>COALESCE(SUM(l.debit_amount_minor),0)
    OR j.credit_total_minor<>COALESCE(SUM(l.credit_amount_minor),0);

-- Inventory valuation evidence and qualified-receipt accounting must agree.
SELECT v.tenant_id,v.inventory_movement_id,v.amount_minor,e.movement_cost_amount_minor,
       l.total_cost_amount_minor,j.debit_total_minor,j.credit_total_minor
  FROM cloudmold_finance_inventory_valuation_effect v
  JOIN cloudmold_finance_inventory_movement_evidence e
    ON e.tenant_id=v.tenant_id AND e.inventory_movement_id=v.inventory_movement_id
   AND e.source_version=v.inventory_movement_version
  JOIN cloudmold_finance_inventory_valuation_layer l
    ON l.tenant_id=v.tenant_id AND l.valuation_layer_id=v.valuation_layer_id
  LEFT JOIN cloudmold_finance_journal_entry j
    ON j.tenant_id=v.tenant_id AND j.journal_entry_id=v.journal_entry_id
 WHERE v.effect_type='QUALIFIED_RECEIPT'
   AND (v.amount_minor<>e.movement_cost_amount_minor
     OR v.amount_minor<>l.total_cost_amount_minor
     OR (v.amount_minor=0 AND v.journal_entry_id IS NOT NULL)
     OR (v.amount_minor>0 AND (j.journal_entry_id IS NULL
       OR v.amount_minor<>j.debit_total_minor OR v.amount_minor<>j.credit_total_minor)));

-- GR/IR control account: posted receipt credits less invoice debits must equal its GL balance.
SELECT x.tenant_id,x.ledger_id,x.account_id,
       SUM(x.receipt_credit_minor)-SUM(x.invoice_debit_minor) AS expected_grir_credit_balance_minor,
       SUM(x.gl_credit_minor)-SUM(x.gl_debit_minor) AS actual_grir_credit_balance_minor
  FROM (
        SELECT l.tenant_id,j.ledger_id,l.account_id,
               CASE WHEN j.source_type='QUALIFIED_RECEIPT' THEN l.credit_amount_minor ELSE 0 END receipt_credit_minor,
               CASE WHEN j.source_type='SUPPLIER_INVOICE' THEN l.debit_amount_minor ELSE 0 END invoice_debit_minor,
               l.credit_amount_minor gl_credit_minor,l.debit_amount_minor gl_debit_minor
          FROM cloudmold_finance_journal_line l
          JOIN cloudmold_finance_journal_entry j
            ON j.tenant_id=l.tenant_id AND j.journal_entry_id=l.journal_entry_id AND j.status='POSTED'
          JOIN (SELECT DISTINCT tenant_id,account_id
                  FROM cloudmold_finance_posting_rule_line
                 WHERE account_role='GRIR') prl
            ON prl.tenant_id=l.tenant_id AND prl.account_id=l.account_id
         WHERE j.source_type IN ('QUALIFIED_RECEIPT','SUPPLIER_INVOICE')
       ) x
 GROUP BY x.tenant_id,x.ledger_id,x.account_id
HAVING expected_grir_credit_balance_minor<>actual_grir_credit_balance_minor;
