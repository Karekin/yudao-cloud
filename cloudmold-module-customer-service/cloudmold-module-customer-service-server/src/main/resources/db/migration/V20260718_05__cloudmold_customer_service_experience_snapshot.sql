ALTER TABLE cloudmold_customer_service_ticket
    ADD COLUMN sla_policy_code VARCHAR(64) NULL AFTER category_code,
    ADD COLUMN sla_policy_version INT NULL AFTER sla_policy_code,
    ADD COLUMN resolution_deadline_at DATETIME(6) NULL AFTER sla_policy_version,
    ADD COLUMN fcr_window_hours INT NULL AFTER resolution_deadline_at,
    ADD KEY idx_cs_ticket_resolution_deadline (tenant_id, resolution_deadline_at);
