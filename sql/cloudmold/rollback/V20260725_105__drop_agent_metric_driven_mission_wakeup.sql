SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

DROP TABLE IF EXISTS cloudmold_agent_metric_observation_inbox;
DROP TABLE IF EXISTS cloudmold_agent_metric_subscription;

ALTER TABLE cloudmold_agent_work_order
    DROP CHECK ck_agent_work_order_status,
    ADD CONSTRAINT ck_agent_work_order_status CHECK (status IN (
        'WAITING_DEPENDENCY','READY','IN_PROGRESS','WAITING_EVENT','WAITING_TIMER','WAITING_APPROVAL',
        'WAITING_HANDOFF','BLOCKED','NEEDS_REVIEW','COMPLETED','CANCELLED'));
