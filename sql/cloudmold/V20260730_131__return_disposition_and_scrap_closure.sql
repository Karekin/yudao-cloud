CREATE TABLE IF NOT EXISTS `cloudmold_return_disposition_assessment` (
  `assessment_id` varchar(36) NOT NULL,
  `tenant_id` bigint NOT NULL,
  `after_sale_id` varchar(36) NOT NULL,
  `return_fulfillment_id` varchar(36) NOT NULL,
  `assessor_id` varchar(128) NOT NULL,
  `packaging_score` int NOT NULL,
  `appearance_score` int NOT NULL,
  `function_score` int NOT NULL,
  `safety_risk` bit(1) NOT NULL,
  `counterfeit_risk` bit(1) NOT NULL,
  `estimated_resale_value_minor` bigint NOT NULL,
  `estimated_recovery_cost_minor` bigint NOT NULL,
  `inspection_evidence_ref` varchar(256) NOT NULL,
  `recommended_disposition` varchar(32) NOT NULL,
  `quality_status` varchar(32) NOT NULL,
  `condition_grade` varchar(16) NOT NULL,
  `confidence_score` int NOT NULL,
  `rationale_code` varchar(64) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`assessment_id`),
  UNIQUE KEY `uk_cm_return_disposition_tenant_id` (`tenant_id`,`assessment_id`),
  UNIQUE KEY `uk_cm_return_disposition_after_sale` (`tenant_id`,`after_sale_id`),
  CONSTRAINT `fk_cm_return_disposition_case` FOREIGN KEY (`tenant_id`,`after_sale_id`)
    REFERENCES `cloudmold_after_sale_case` (`tenant_id`,`after_sale_id`),
  CONSTRAINT `fk_cm_return_disposition_fulfillment` FOREIGN KEY (`tenant_id`,`return_fulfillment_id`)
    REFERENCES `cloudmold_return_fulfillment` (`tenant_id`,`return_fulfillment_id`),
  CONSTRAINT `ck_cm_return_disposition_score` CHECK (
    `packaging_score` BETWEEN 0 AND 100 AND
    `appearance_score` BETWEEN 0 AND 100 AND
    `function_score` BETWEEN 0 AND 100 AND
    `confidence_score` BETWEEN 0 AND 100
  ),
  CONSTRAINT `ck_cm_return_disposition_money` CHECK (
    `estimated_resale_value_minor` > 0 AND `estimated_recovery_cost_minor` >= 0
  ),
  CONSTRAINT `ck_cm_return_disposition_recommendation` CHECK (
    `recommended_disposition` IN ('RESTOCK','SCRAP','MANUAL_REVIEW')
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE `cloudmold_return_inspection`
  DROP CHECK `ck_cm_return_inspection_quality`,
  ADD COLUMN `disposition_assessment_id` varchar(36) DEFAULT NULL AFTER `quality_status`,
  ADD COLUMN `disposition_code` varchar(32) DEFAULT NULL AFTER `disposition_assessment_id`,
  ADD COLUMN `condition_grade` varchar(16) DEFAULT NULL AFTER `disposition_code`,
  ADD COLUMN `inspection_evidence_ref` varchar(256) DEFAULT NULL AFTER `condition_grade`,
  ADD CONSTRAINT `ck_cm_return_inspection_quality_v2`
    CHECK (`quality_status` IN ('QUALIFIED','DAMAGED')),
  ADD CONSTRAINT `ck_cm_return_inspection_disposition`
    CHECK ((`quality_status`='QUALIFIED' AND `disposition_code`='RESTOCK')
      OR (`quality_status`='DAMAGED' AND `disposition_code`='SCRAP')),
  ADD CONSTRAINT `fk_cm_return_inspection_disposition`
    FOREIGN KEY (`tenant_id`,`disposition_assessment_id`)
    REFERENCES `cloudmold_return_disposition_assessment` (`tenant_id`,`assessment_id`);

ALTER TABLE `cloudmold_after_sale_resolution_saga`
  ADD COLUMN `disposition_assessment_id` varchar(36) DEFAULT NULL AFTER `inspection_id`,
  ADD COLUMN `disposition_code` varchar(32) NOT NULL DEFAULT 'RESTOCK' AFTER `disposition_assessment_id`,
  ADD COLUMN `return_stock_status` varchar(32) NOT NULL DEFAULT 'SELLABLE' AFTER `disposition_code`,
  ADD COLUMN `return_quality_status` varchar(32) NOT NULL DEFAULT 'QUALIFIED' AFTER `return_stock_status`,
  ADD COLUMN `disposal_operation_id` bigint DEFAULT NULL AFTER `inventory_ledger_transaction_id`,
  ADD COLUMN `disposal_ledger_transaction_id` bigint DEFAULT NULL AFTER `disposal_operation_id`,
  ADD CONSTRAINT `fk_cm_after_sale_saga_disposition`
    FOREIGN KEY (`tenant_id`,`disposition_assessment_id`)
    REFERENCES `cloudmold_return_disposition_assessment` (`tenant_id`,`assessment_id`),
  ADD CONSTRAINT `ck_cm_after_sale_saga_disposition`
    CHECK (`disposition_code` IN ('RESTOCK','SCRAP'));

ALTER TABLE `cloudmold_after_sale_resolution_saga`
  DROP CHECK `ck_cm_after_sale_resolution_status`,
  ADD CONSTRAINT `ck_cm_after_sale_resolution_status_v4` CHECK (`status` IN (
    'REQUESTED','RETURNING_INVENTORY','INVENTORY_RETURNED',
    'DISPOSING_INVENTORY','INVENTORY_DISPOSED',
    'REVERSING_BENEFITS','BENEFITS_REVERSED','REFUNDING_PAYMENT',
    'PAYMENT_REFUNDED','SETTLING_ORDER','ORDER_SETTLED',
    'CONFIRMING_ORDER_REFUND','ORDER_REFUNDED','RETURNING_ORDER',
    'ORDER_RETURNED','RETRY_SCHEDULED','MANUAL_REVIEW','COMPLETED'
  ));
