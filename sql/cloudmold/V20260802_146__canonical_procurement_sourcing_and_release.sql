CREATE TABLE cloudmold_procurement_sourcing_event (
 event_id VARCHAR(128) NOT NULL,tenant_id BIGINT NOT NULL,event_code VARCHAR(64) NOT NULL,requisition_id VARCHAR(128) NOT NULL,
 requisition_version BIGINT NOT NULL,title VARCHAR(255) NOT NULL,status VARCHAR(32) NOT NULL,quotation_deadline DATETIME(6) NOT NULL,
 version BIGINT NOT NULL,created_by_principal_id VARCHAR(128) NOT NULL,published_by_principal_id VARCHAR(128) NULL,
 published_at DATETIME(6) NULL,quoting_opened_by_principal_id VARCHAR(128) NULL,quoting_opened_at DATETIME(6) NULL,
 evaluating_by_principal_id VARCHAR(128) NULL,evaluating_at DATETIME(6) NULL,award_submitted_by_principal_id VARCHAR(128) NULL,
 award_submitted_at DATETIME(6) NULL,awarded_by_principal_id VARCHAR(128) NULL,awarded_at DATETIME(6) NULL,
 closed_by_principal_id VARCHAR(128) NULL,closed_at DATETIME(6) NULL,cancelled_by_principal_id VARCHAR(128) NULL,cancelled_at DATETIME(6) NULL,
 terminal_reason_code VARCHAR(64) NULL,created_at DATETIME(6) NOT NULL,updated_at DATETIME(6) NOT NULL,
 PRIMARY KEY(event_id),UNIQUE KEY uk_sourcing_event_tenant_id(tenant_id,event_id),UNIQUE KEY uk_sourcing_event_code(tenant_id,event_code),
 UNIQUE KEY uk_sourcing_event_pr(tenant_id,requisition_id),FOREIGN KEY(tenant_id,requisition_id) REFERENCES cloudmold_purchase_requisition(tenant_id,requisition_id),
 CHECK(status IN ('DRAFT','PUBLISHED','QUOTING','EVALUATING','AWARD_SUBMITTED','AWARDED','CLOSED','CANCELLED')),CHECK(version>0),
 CHECK((published_by_principal_id IS NULL)=(published_at IS NULL)),
 CHECK((quoting_opened_by_principal_id IS NULL)=(quoting_opened_at IS NULL)),
 CHECK((evaluating_by_principal_id IS NULL)=(evaluating_at IS NULL)),
 CHECK((award_submitted_by_principal_id IS NULL)=(award_submitted_at IS NULL)),
 CHECK((awarded_by_principal_id IS NULL)=(awarded_at IS NULL)),CHECK((closed_by_principal_id IS NULL)=(closed_at IS NULL)),
 CHECK((cancelled_by_principal_id IS NULL)=(cancelled_at IS NULL)),
 CHECK((status='DRAFT' AND published_at IS NULL) OR (status<>'DRAFT')),
 CHECK((status='PUBLISHED' AND published_at IS NOT NULL) OR status<>'PUBLISHED'),
 CHECK((status='QUOTING' AND published_at IS NOT NULL AND quoting_opened_at IS NOT NULL) OR status<>'QUOTING'),
 CHECK((status='EVALUATING' AND evaluating_at IS NOT NULL) OR status<>'EVALUATING'),
 CHECK((status='AWARD_SUBMITTED' AND award_submitted_at IS NOT NULL) OR status<>'AWARD_SUBMITTED'),
 CHECK((status='AWARDED' AND awarded_at IS NOT NULL) OR status<>'AWARDED'),
 CHECK((status='CLOSED' AND closed_at IS NOT NULL AND terminal_reason_code IS NOT NULL) OR status<>'CLOSED'),
 CHECK((status='CANCELLED' AND cancelled_at IS NOT NULL AND terminal_reason_code IS NOT NULL) OR status<>'CANCELLED')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE cloudmold_procurement_sourcing_line (
 sourcing_line_id VARCHAR(128) NOT NULL,tenant_id BIGINT NOT NULL,event_id VARCHAR(128) NOT NULL,line_number INT NOT NULL,
 requisition_line_id VARCHAR(128) NOT NULL,canonical_sku_id VARCHAR(128) NOT NULL,requested_quantity DECIMAL(24,6) NOT NULL,
 uom_code VARCHAR(16) NOT NULL,created_at DATETIME(6) NOT NULL,PRIMARY KEY(sourcing_line_id),
 UNIQUE KEY uk_sourcing_line_tenant_id(tenant_id,sourcing_line_id),UNIQUE KEY uk_sourcing_line_no(tenant_id,event_id,line_number),
 UNIQUE KEY uk_sourcing_line_pr_line(tenant_id,event_id,requisition_line_id),FOREIGN KEY(tenant_id,event_id) REFERENCES cloudmold_procurement_sourcing_event(tenant_id,event_id),
 FOREIGN KEY(tenant_id,requisition_line_id) REFERENCES cloudmold_purchase_requisition_line(tenant_id,line_id),CHECK(line_number>0),CHECK(requested_quantity>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE cloudmold_procurement_sourcing_schedule (
 sourcing_schedule_id VARCHAR(128) NOT NULL,tenant_id BIGINT NOT NULL,event_id VARCHAR(128) NOT NULL,sourcing_line_id VARCHAR(128) NOT NULL,
 schedule_number INT NOT NULL,requisition_schedule_id VARCHAR(128) NOT NULL,canonical_warehouse_id VARCHAR(128) NOT NULL,
 requested_quantity DECIMAL(24,6) NOT NULL,required_delivery_date DATE NOT NULL,created_at DATETIME(6) NOT NULL,
 PRIMARY KEY(sourcing_schedule_id),UNIQUE KEY uk_sourcing_schedule_tenant_id(tenant_id,sourcing_schedule_id),
 UNIQUE KEY uk_sourcing_schedule_no(tenant_id,sourcing_line_id,schedule_number),UNIQUE KEY uk_sourcing_schedule_pr(tenant_id,event_id,requisition_schedule_id),
 FOREIGN KEY(tenant_id,event_id) REFERENCES cloudmold_procurement_sourcing_event(tenant_id,event_id),
 FOREIGN KEY(tenant_id,sourcing_line_id) REFERENCES cloudmold_procurement_sourcing_line(tenant_id,sourcing_line_id),
 FOREIGN KEY(tenant_id,requisition_schedule_id) REFERENCES cloudmold_purchase_requisition_delivery_schedule(tenant_id,schedule_id),
 CHECK(schedule_number>0),CHECK(requested_quantity>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE cloudmold_procurement_supplier_invitation (
 invitation_id VARCHAR(128) NOT NULL,tenant_id BIGINT NOT NULL,event_id VARCHAR(128) NOT NULL,supplier_id VARCHAR(128) NOT NULL,
 status VARCHAR(32) NOT NULL,invited_by_principal_id VARCHAR(128) NOT NULL,invited_at DATETIME(6) NOT NULL,
 PRIMARY KEY(invitation_id),UNIQUE KEY uk_invitation_tenant_id(tenant_id,invitation_id),UNIQUE KEY uk_invitation_supplier(tenant_id,event_id,supplier_id),
 FOREIGN KEY(tenant_id,event_id) REFERENCES cloudmold_procurement_sourcing_event(tenant_id,event_id),CHECK(status='INVITED')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE cloudmold_procurement_quotation (
 quotation_id VARCHAR(128) NOT NULL,tenant_id BIGINT NOT NULL,quotation_code VARCHAR(64) NOT NULL,event_id VARCHAR(128) NOT NULL,
 supplier_id VARCHAR(128) NOT NULL,currency_code CHAR(3) NOT NULL,latest_revision_number INT NOT NULL,active_revision_id VARCHAR(128) NULL,
 created_at DATETIME(6) NOT NULL,PRIMARY KEY(quotation_id),UNIQUE KEY uk_quote_tenant_id(tenant_id,quotation_id),
 UNIQUE KEY uk_quote_code(tenant_id,quotation_code),UNIQUE KEY uk_quote_supplier(tenant_id,event_id,supplier_id),
 FOREIGN KEY(tenant_id,event_id) REFERENCES cloudmold_procurement_sourcing_event(tenant_id,event_id),
 CHECK(currency_code REGEXP '^[A-Z]{3}$'),CHECK(latest_revision_number>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE cloudmold_procurement_quotation_revision (
 revision_id VARCHAR(128) NOT NULL,tenant_id BIGINT NOT NULL,quotation_id VARCHAR(128) NOT NULL,event_id VARCHAR(128) NOT NULL,
 revision_number INT NOT NULL,status VARCHAR(32) NOT NULL,submitted_by_principal_id VARCHAR(128) NOT NULL,submitted_at DATETIME(6) NOT NULL,
 terminal_by_principal_id VARCHAR(128) NULL,terminal_at DATETIME(6) NULL,withdrawal_reason_code VARCHAR(64) NULL,payload_sha256 CHAR(64) NOT NULL,PRIMARY KEY(revision_id),
 UNIQUE KEY uk_quote_revision_tenant_id(tenant_id,revision_id),UNIQUE KEY uk_quote_revision_no(tenant_id,quotation_id,revision_number),
 FOREIGN KEY(tenant_id,quotation_id) REFERENCES cloudmold_procurement_quotation(tenant_id,quotation_id),
 CHECK(revision_number>0),CHECK(status IN ('SUBMITTED','SUPERSEDED','WITHDRAWN')),
 CHECK((terminal_by_principal_id IS NULL)=(terminal_at IS NULL)),
 CHECK((status='SUBMITTED' AND terminal_at IS NULL AND withdrawal_reason_code IS NULL)
    OR (status='SUPERSEDED' AND terminal_at IS NOT NULL AND withdrawal_reason_code IS NULL)
    OR (status='WITHDRAWN' AND terminal_at IS NOT NULL AND withdrawal_reason_code IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE cloudmold_procurement_quotation ADD CONSTRAINT fk_quote_active_revision FOREIGN KEY(tenant_id,active_revision_id)
 REFERENCES cloudmold_procurement_quotation_revision(tenant_id,revision_id);

CREATE TABLE cloudmold_procurement_quotation_revision_line (
 revision_line_id VARCHAR(128) NOT NULL,tenant_id BIGINT NOT NULL,revision_id VARCHAR(128) NOT NULL,event_id VARCHAR(128) NOT NULL,
 line_number INT NOT NULL,sourcing_line_id VARCHAR(128) NOT NULL,offered_quantity DECIMAL(24,6) NOT NULL,uom_code VARCHAR(16) NOT NULL,
 unit_net_price_minor DECIMAL(24,6) NOT NULL,tax_code VARCHAR(32) NOT NULL,tax_rate_bps INT NOT NULL,
 line_net_amount_minor BIGINT NOT NULL,line_tax_amount_minor BIGINT NOT NULL,line_gross_amount_minor BIGINT NOT NULL,
 PRIMARY KEY(revision_line_id),UNIQUE KEY uk_revision_line_tenant_id(tenant_id,revision_line_id),
 UNIQUE KEY uk_revision_line_no(tenant_id,revision_id,line_number),UNIQUE KEY uk_revision_source_line(tenant_id,revision_id,sourcing_line_id),
 FOREIGN KEY(tenant_id,revision_id) REFERENCES cloudmold_procurement_quotation_revision(tenant_id,revision_id),
 FOREIGN KEY(tenant_id,sourcing_line_id) REFERENCES cloudmold_procurement_sourcing_line(tenant_id,sourcing_line_id),
 CHECK(line_number>0),CHECK(offered_quantity>0),CHECK(unit_net_price_minor>=0),CHECK(tax_rate_bps BETWEEN 0 AND 10000),
 CHECK(line_gross_amount_minor=line_net_amount_minor+line_tax_amount_minor)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE cloudmold_procurement_quotation_revision_schedule (
 revision_schedule_id VARCHAR(128) NOT NULL,tenant_id BIGINT NOT NULL,revision_id VARCHAR(128) NOT NULL,revision_line_id VARCHAR(128) NOT NULL,
 schedule_number INT NOT NULL,sourcing_schedule_id VARCHAR(128) NOT NULL,offered_quantity DECIMAL(24,6) NOT NULL,promised_delivery_date DATE NOT NULL,
 PRIMARY KEY(revision_schedule_id),UNIQUE KEY uk_revision_schedule_tenant_id(tenant_id,revision_schedule_id),
 UNIQUE KEY uk_revision_schedule_no(tenant_id,revision_line_id,schedule_number),UNIQUE KEY uk_revision_source_schedule(tenant_id,revision_id,sourcing_schedule_id),
 FOREIGN KEY(tenant_id,revision_id) REFERENCES cloudmold_procurement_quotation_revision(tenant_id,revision_id),
 FOREIGN KEY(tenant_id,revision_line_id) REFERENCES cloudmold_procurement_quotation_revision_line(tenant_id,revision_line_id),
 FOREIGN KEY(tenant_id,sourcing_schedule_id) REFERENCES cloudmold_procurement_sourcing_schedule(tenant_id,sourcing_schedule_id),
 CHECK(schedule_number>0),CHECK(offered_quantity>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE cloudmold_procurement_evaluation_policy (
 policy_id VARCHAR(128) NOT NULL,tenant_id BIGINT NOT NULL,policy_code VARCHAR(64) NOT NULL,event_id VARCHAR(128) NOT NULL,
 active_version INT NOT NULL,created_at DATETIME(6) NOT NULL,PRIMARY KEY(policy_id),UNIQUE KEY uk_eval_policy_tenant_id(tenant_id,policy_id),
 UNIQUE KEY uk_eval_policy_code(tenant_id,policy_code),UNIQUE KEY uk_eval_policy_event(tenant_id,event_id),
 FOREIGN KEY(tenant_id,event_id) REFERENCES cloudmold_procurement_sourcing_event(tenant_id,event_id),CHECK(active_version>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE cloudmold_procurement_evaluation_policy_version (
 policy_id VARCHAR(128) NOT NULL,tenant_id BIGINT NOT NULL,policy_version INT NOT NULL,status VARCHAR(32) NOT NULL,
 created_by_principal_id VARCHAR(128) NOT NULL,created_at DATETIME(6) NOT NULL,policy_sha256 CHAR(64) NOT NULL,
 PRIMARY KEY(tenant_id,policy_id,policy_version),FOREIGN KEY(tenant_id,policy_id) REFERENCES cloudmold_procurement_evaluation_policy(tenant_id,policy_id),
 CHECK(policy_version>0),CHECK(status='ACTIVE')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE cloudmold_procurement_evaluation_dimension (
 dimension_id VARCHAR(128) NOT NULL,tenant_id BIGINT NOT NULL,policy_id VARCHAR(128) NOT NULL,policy_version INT NOT NULL,
 dimension_code VARCHAR(64) NOT NULL,dimension_name VARCHAR(128) NOT NULL,weight_bps INT NOT NULL,maximum_score INT NOT NULL,
 PRIMARY KEY(dimension_id),UNIQUE KEY uk_eval_dimension_tenant_id(tenant_id,dimension_id),
 UNIQUE KEY uk_eval_dimension_code(tenant_id,policy_id,policy_version,dimension_code),
 FOREIGN KEY(tenant_id,policy_id,policy_version) REFERENCES cloudmold_procurement_evaluation_policy_version(tenant_id,policy_id,policy_version),
 CHECK(weight_bps BETWEEN 1 AND 10000),CHECK(maximum_score>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE cloudmold_procurement_evaluation_score (
 score_id VARCHAR(128) NOT NULL,tenant_id BIGINT NOT NULL,event_id VARCHAR(128) NOT NULL,policy_id VARCHAR(128) NOT NULL,
 policy_version INT NOT NULL,quotation_revision_id VARCHAR(128) NOT NULL,reviewer_principal_id VARCHAR(128) NOT NULL,
 weighted_score_bps INT NOT NULL,reviewer_evidence_sha256 CHAR(64) NOT NULL,evaluation_summary_sha256 CHAR(64) NOT NULL,created_at DATETIME(6) NOT NULL,
 PRIMARY KEY(score_id),UNIQUE KEY uk_eval_score_tenant_id(tenant_id,score_id),
 UNIQUE KEY uk_eval_score_reviewer(tenant_id,policy_id,policy_version,quotation_revision_id,reviewer_principal_id),
 FOREIGN KEY(tenant_id,policy_id,policy_version) REFERENCES cloudmold_procurement_evaluation_policy_version(tenant_id,policy_id,policy_version),
 FOREIGN KEY(tenant_id,quotation_revision_id) REFERENCES cloudmold_procurement_quotation_revision(tenant_id,revision_id),CHECK(weighted_score_bps BETWEEN 0 AND 10000)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE cloudmold_procurement_evaluation_dimension_score (
 score_id VARCHAR(128) NOT NULL,tenant_id BIGINT NOT NULL,dimension_id VARCHAR(128) NOT NULL,score INT NOT NULL,
 evidence_reference VARCHAR(512) NOT NULL,PRIMARY KEY(tenant_id,score_id,dimension_id),
 FOREIGN KEY(tenant_id,score_id) REFERENCES cloudmold_procurement_evaluation_score(tenant_id,score_id),
 FOREIGN KEY(tenant_id,dimension_id) REFERENCES cloudmold_procurement_evaluation_dimension(tenant_id,dimension_id),CHECK(score>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE cloudmold_procurement_award (
 award_id VARCHAR(128) NOT NULL,tenant_id BIGINT NOT NULL,award_code VARCHAR(64) NOT NULL,event_id VARCHAR(128) NOT NULL,
 policy_id VARCHAR(128) NOT NULL,policy_version INT NOT NULL,status VARCHAR(32) NOT NULL,decision_reason_code VARCHAR(64) NOT NULL,
 version BIGINT NOT NULL,created_by_principal_id VARCHAR(128) NOT NULL,submitted_by_principal_id VARCHAR(128) NULL,
 approved_by_principal_id VARCHAR(128) NULL,rejected_by_principal_id VARCHAR(128) NULL,submitted_at DATETIME(6) NULL,
 approved_at DATETIME(6) NULL,rejected_at DATETIME(6) NULL,created_at DATETIME(6) NOT NULL,updated_at DATETIME(6) NOT NULL,
 PRIMARY KEY(award_id),UNIQUE KEY uk_award_tenant_id(tenant_id,award_id),UNIQUE KEY uk_award_code(tenant_id,award_code),
 UNIQUE KEY uk_award_event(tenant_id,event_id),FOREIGN KEY(tenant_id,event_id) REFERENCES cloudmold_procurement_sourcing_event(tenant_id,event_id),
 FOREIGN KEY(tenant_id,policy_id,policy_version) REFERENCES cloudmold_procurement_evaluation_policy_version(tenant_id,policy_id,policy_version),
 CHECK(status IN ('DRAFT','SUBMITTED','APPROVED','REJECTED')),CHECK(version>0),
 CHECK((status='DRAFT' AND submitted_by_principal_id IS NULL AND submitted_at IS NULL AND approved_by_principal_id IS NULL AND approved_at IS NULL AND rejected_by_principal_id IS NULL AND rejected_at IS NULL)
    OR (status='SUBMITTED' AND submitted_by_principal_id IS NOT NULL AND submitted_at IS NOT NULL AND approved_by_principal_id IS NULL AND approved_at IS NULL AND rejected_by_principal_id IS NULL AND rejected_at IS NULL)
    OR (status='APPROVED' AND submitted_by_principal_id IS NOT NULL AND submitted_at IS NOT NULL AND approved_by_principal_id IS NOT NULL AND approved_at IS NOT NULL AND rejected_by_principal_id IS NULL AND rejected_at IS NULL)
    OR (status='REJECTED' AND submitted_by_principal_id IS NOT NULL AND submitted_at IS NOT NULL AND approved_by_principal_id IS NULL AND approved_at IS NULL AND rejected_by_principal_id IS NOT NULL AND rejected_at IS NOT NULL)),
 CHECK(approved_by_principal_id IS NULL OR (approved_by_principal_id<>created_by_principal_id AND approved_by_principal_id<>submitted_by_principal_id)),
 CHECK(rejected_by_principal_id IS NULL OR (rejected_by_principal_id<>created_by_principal_id AND rejected_by_principal_id<>submitted_by_principal_id))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE cloudmold_procurement_award_line (
 award_line_id VARCHAR(128) NOT NULL,tenant_id BIGINT NOT NULL,award_id VARCHAR(128) NOT NULL,line_number INT NOT NULL,
 sourcing_line_id VARCHAR(128) NOT NULL,sourcing_schedule_id VARCHAR(128) NOT NULL,quotation_revision_line_id VARCHAR(128) NOT NULL,
 quotation_revision_schedule_id VARCHAR(128) NOT NULL,supplier_id VARCHAR(128) NOT NULL,canonical_sku_id VARCHAR(128) NOT NULL,
 canonical_warehouse_id VARCHAR(128) NOT NULL,awarded_quantity DECIMAL(24,6) NOT NULL,uom_code VARCHAR(16) NOT NULL,
 currency_code CHAR(3) NOT NULL,unit_net_price_minor DECIMAL(24,6) NOT NULL,tax_code VARCHAR(32) NOT NULL,tax_rate_bps INT NOT NULL,
 promised_delivery_date DATE NOT NULL,line_net_amount_minor BIGINT NOT NULL,line_tax_amount_minor BIGINT NOT NULL,line_gross_amount_minor BIGINT NOT NULL,
 policy_id VARCHAR(128) NOT NULL,policy_version INT NOT NULL,evaluation_weighted_score_bps INT NOT NULL,
 evaluation_summary_sha256 CHAR(64) NOT NULL,reviewer_evidence_sha256 CHAR(64) NOT NULL,
 PRIMARY KEY(award_line_id),UNIQUE KEY uk_award_line_tenant_id(tenant_id,award_line_id),UNIQUE KEY uk_award_line_no(tenant_id,award_id,line_number),
 KEY idx_award_source_schedule(tenant_id,award_id,sourcing_schedule_id),FOREIGN KEY(tenant_id,award_id) REFERENCES cloudmold_procurement_award(tenant_id,award_id),
 FOREIGN KEY(tenant_id,sourcing_line_id) REFERENCES cloudmold_procurement_sourcing_line(tenant_id,sourcing_line_id),
 FOREIGN KEY(tenant_id,sourcing_schedule_id) REFERENCES cloudmold_procurement_sourcing_schedule(tenant_id,sourcing_schedule_id),
 FOREIGN KEY(tenant_id,quotation_revision_line_id) REFERENCES cloudmold_procurement_quotation_revision_line(tenant_id,revision_line_id),
 FOREIGN KEY(tenant_id,quotation_revision_schedule_id) REFERENCES cloudmold_procurement_quotation_revision_schedule(tenant_id,revision_schedule_id),
 CHECK(line_number>0),CHECK(awarded_quantity>0),CHECK(tax_rate_bps BETWEEN 0 AND 10000),CHECK(evaluation_weighted_score_bps BETWEEN 0 AND 10000),
 CHECK(line_gross_amount_minor=line_net_amount_minor+line_tax_amount_minor)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE cloudmold_procurement_award_snapshot (
 snapshot_id VARCHAR(160) NOT NULL,tenant_id BIGINT NOT NULL,award_id VARCHAR(128) NOT NULL,award_version BIGINT NOT NULL,
 event_id VARCHAR(128) NOT NULL,event_version BIGINT NOT NULL,status VARCHAR(32) NOT NULL,decision_reason_code VARCHAR(64) NOT NULL,
 approved_by_principal_id VARCHAR(128) NOT NULL,approved_at DATETIME(6) NOT NULL,created_at DATETIME(6) NOT NULL,
 PRIMARY KEY(snapshot_id),UNIQUE KEY uk_award_snapshot_tenant_id(tenant_id,snapshot_id),UNIQUE KEY uk_award_snapshot_version(tenant_id,award_id,award_version),
 FOREIGN KEY(tenant_id,award_id) REFERENCES cloudmold_procurement_award(tenant_id,award_id),CHECK(status='APPROVED')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE cloudmold_procurement_award_snapshot_line LIKE cloudmold_procurement_award_line;
ALTER TABLE cloudmold_procurement_award_snapshot_line DROP PRIMARY KEY,
 ADD snapshot_line_id VARCHAR(256) NOT NULL FIRST,ADD snapshot_id VARCHAR(160) NOT NULL AFTER tenant_id,
 ADD PRIMARY KEY(snapshot_line_id),ADD UNIQUE KEY uk_award_snapshot_line_tenant_id(tenant_id,snapshot_line_id),
 ADD UNIQUE KEY uk_award_snapshot_line_no(tenant_id,snapshot_id,line_number),
 ADD CONSTRAINT fk_award_snapshot_line_snapshot FOREIGN KEY(tenant_id,snapshot_id) REFERENCES cloudmold_procurement_award_snapshot(tenant_id,snapshot_id);

CREATE TABLE cloudmold_procurement_sourcing_history (
 history_id BIGINT NOT NULL AUTO_INCREMENT,tenant_id BIGINT NOT NULL,event_id VARCHAR(128) NOT NULL,operation_id BIGINT NOT NULL,
 aggregate_version BIGINT NOT NULL,status VARCHAR(32) NOT NULL,action_code VARCHAR(64) NOT NULL,actor_principal_id VARCHAR(128) NOT NULL,
 occurred_at DATETIME(6) NOT NULL,created_at DATETIME(6) NOT NULL,PRIMARY KEY(history_id),
 UNIQUE KEY uk_sourcing_history_action(tenant_id,event_id,aggregate_version,action_code),
 FOREIGN KEY(tenant_id,event_id) REFERENCES cloudmold_procurement_sourcing_event(tenant_id,event_id),FOREIGN KEY(operation_id) REFERENCES cloudmold_procurement_operation(operation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE cloudmold_procurement_award_history (
 history_id BIGINT NOT NULL AUTO_INCREMENT,tenant_id BIGINT NOT NULL,award_id VARCHAR(128) NOT NULL,operation_id BIGINT NOT NULL,
 aggregate_version BIGINT NOT NULL,status VARCHAR(32) NOT NULL,action_code VARCHAR(64) NOT NULL,reason_code VARCHAR(64) NULL,
 actor_principal_id VARCHAR(128) NOT NULL,occurred_at DATETIME(6) NOT NULL,created_at DATETIME(6) NOT NULL,PRIMARY KEY(history_id),
 UNIQUE KEY uk_award_history_action(tenant_id,award_id,aggregate_version,action_code),
 FOREIGN KEY(tenant_id,award_id) REFERENCES cloudmold_procurement_award(tenant_id,award_id),FOREIGN KEY(operation_id) REFERENCES cloudmold_procurement_operation(operation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE cloudmold_procurement_order ADD award_id VARCHAR(128) NULL,ADD award_version BIGINT NULL,
 ADD legal_entity_id VARCHAR(128) NOT NULL,
 ADD submitted_by_principal_id VARCHAR(128) NULL,ADD approved_by_principal_id VARCHAR(128) NULL,ADD released_by_principal_id VARCHAR(128) NULL,
 ADD submitted_at DATETIME(6) NULL,ADD approved_at DATETIME(6) NULL,ADD released_at DATETIME(6) NULL,
 ADD KEY idx_procurement_order_award(tenant_id,award_id),ADD CONSTRAINT fk_procurement_order_award FOREIGN KEY(tenant_id,award_id) REFERENCES cloudmold_procurement_award(tenant_id,award_id);
ALTER TABLE cloudmold_procurement_order DROP CHECK ck_procurement_status;
UPDATE cloudmold_procurement_order SET status='DRAFT' WHERE status='CREATED';
ALTER TABLE cloudmold_procurement_order ADD CONSTRAINT ck_procurement_status CHECK(status IN ('DRAFT','SUBMITTED','APPROVED','RELEASED','DISPATCHED','SUPPLIER_CONFIRMED','CANCELLED','CLOSED'));
ALTER TABLE cloudmold_procurement_order_status_history DROP CHECK ck_procurement_status_history_status;
UPDATE cloudmold_procurement_order_status_history SET status='DRAFT' WHERE status='CREATED';
ALTER TABLE cloudmold_procurement_order_status_history ADD CONSTRAINT ck_procurement_status_history_status CHECK(status IN ('DRAFT','SUBMITTED','APPROVED','RELEASED','DISPATCHED','SUPPLIER_CONFIRMED','CANCELLED','CLOSED'));
ALTER TABLE cloudmold_procurement_order_item
 ADD award_line_id VARCHAR(128) NULL,
 ADD valuation_policy_id VARCHAR(128) NOT NULL,
 ADD valuation_policy_version VARCHAR(64) NOT NULL,
 ADD valuation_policy_hash CHAR(64) NOT NULL,
 ADD CONSTRAINT ck_procurement_order_item_valuation_policy_id
  CHECK(REGEXP_LIKE(valuation_policy_id,'^[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}$','c')),
 ADD CONSTRAINT ck_procurement_order_item_valuation_policy_version
  CHECK(REGEXP_LIKE(valuation_policy_version,'^[A-Za-z0-9][A-Za-z0-9._:/-]{0,63}$','c')),
 ADD CONSTRAINT ck_procurement_order_item_valuation_policy_hash
  CHECK(REGEXP_LIKE(valuation_policy_hash,'^[0-9a-f]{64}$','c'));

CREATE TABLE cloudmold_procurement_order_award_source (
 tenant_id BIGINT NOT NULL,order_id VARCHAR(128) NOT NULL,award_id VARCHAR(128) NOT NULL,award_version BIGINT NOT NULL,
 award_line_id VARCHAR(128) NOT NULL,item_id VARCHAR(128) NOT NULL,source_snapshot_id VARCHAR(160) NOT NULL,created_at DATETIME(6) NOT NULL,
 PRIMARY KEY(tenant_id,order_id,award_line_id),UNIQUE KEY uk_po_award_item(tenant_id,item_id),UNIQUE KEY uk_po_award_source_once(tenant_id,award_id,award_line_id),
 FOREIGN KEY(tenant_id,order_id) REFERENCES cloudmold_procurement_order(tenant_id,order_id),
 FOREIGN KEY(tenant_id,item_id) REFERENCES cloudmold_procurement_order_item(tenant_id,item_id),
 FOREIGN KEY(tenant_id,award_id) REFERENCES cloudmold_procurement_award(tenant_id,award_id),
 FOREIGN KEY(tenant_id,award_line_id) REFERENCES cloudmold_procurement_award_line(tenant_id,award_line_id),
 FOREIGN KEY(tenant_id,source_snapshot_id) REFERENCES cloudmold_procurement_award_snapshot(tenant_id,snapshot_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
