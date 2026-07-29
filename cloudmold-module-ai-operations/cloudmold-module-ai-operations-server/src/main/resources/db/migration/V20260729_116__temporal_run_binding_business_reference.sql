-- 为补货业务事件驱动通道在 run_binding 上物化 business_reference_id（即 recommendationId）。
-- TemporalBusinessEventContinuationAdapter 据此按 businessReferenceId 反查 WAITING_EVENT 运行，
-- 向其 workflow 发送 businessEvent signal，把补货等待从 24h 轮询改为事件驱动（约 3s 唤醒）。
ALTER TABLE cloudmold_ai_ops_temporal_run_binding
    ADD COLUMN business_reference_id VARCHAR(128) NULL COMMENT '业务关联键，补货场景为 recommendationId',
    ADD KEY idx_ai_ops_temporal_run_business_ref (tenant_id, business_reference_id, status);
