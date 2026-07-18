-- Durable retry boundary for governed yudao ERP/WMS/MES Dubbo capabilities.
CREATE TABLE IF NOT EXISTS `cloudmold_yudao_command_operation` (
  `operation_id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL,
  `operation_type` varchar(64) NOT NULL,
  `idempotency_key` varchar(128) NOT NULL,
  `request_hash` char(64) NOT NULL,
  `attempt_token` varchar(36) NOT NULL,
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0 processing, 10 succeeded',
  `result_json` json DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`operation_id`),
  UNIQUE KEY `uk_yudao_command_idempotency` (`tenant_id`, `operation_type`, `idempotency_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Idempotent governed yudao ERP/WMS/MES commands';
