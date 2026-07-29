ALTER TABLE cloudmold_event_outbox
    ADD KEY idx_outbox_ai_ops_discovery
        (tenant_id, event_type, recorded_at, event_id);
