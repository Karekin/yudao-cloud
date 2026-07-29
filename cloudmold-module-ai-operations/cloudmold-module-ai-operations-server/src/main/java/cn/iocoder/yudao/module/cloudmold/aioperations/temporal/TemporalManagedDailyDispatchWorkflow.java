package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface TemporalManagedDailyDispatchWorkflow {

    @WorkflowMethod
    TemporalDailyDispatchResult run(TemporalDailyDispatchRequest request);
}
