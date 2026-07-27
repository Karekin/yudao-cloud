package cn.iocoder.yudao.module.cloudmold.order.api.workflow;

/**
 * Business-visible state returned to Temporal/SkillTask orchestration.
 */
public enum CommerceWorkflowStatus {
    PREPARE,
    WAITING,
    RUNNING,
    SUCCEEDED,
    COMPENSATED,
    MANUAL_REVIEW,
    FAILED
}
