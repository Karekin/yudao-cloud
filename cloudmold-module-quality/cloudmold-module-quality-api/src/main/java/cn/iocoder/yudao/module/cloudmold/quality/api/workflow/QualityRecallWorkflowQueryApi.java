package cn.iocoder.yudao.module.cloudmold.quality.api.workflow;

/**
 * Governed, repeatable read contract for quality-recall workflows.
 */
public interface QualityRecallWorkflowQueryApi {

    QualityRecallWorkflowResult inspect(String recallActionId);
}
