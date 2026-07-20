CREATE TABLE IF NOT EXISTS cloudmold_agent_legacy_purchase_in_bridge (
  tenant_id BIGINT NOT NULL,
  purchase_in_id BIGINT NOT NULL,
  observed_status INT NOT NULL,
  source_updated_at DATETIME(6) NOT NULL,
  event_id VARCHAR(64) NOT NULL,
  captured_at DATETIME(6) NOT NULL,
  PRIMARY KEY (tenant_id,purchase_in_id),
  UNIQUE KEY uk_agent_legacy_purchase_event (tenant_id,event_id),
  KEY idx_agent_legacy_purchase_source (tenant_id,source_updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
