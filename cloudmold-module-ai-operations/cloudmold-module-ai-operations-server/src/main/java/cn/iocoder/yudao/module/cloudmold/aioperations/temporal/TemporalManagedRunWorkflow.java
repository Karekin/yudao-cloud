package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface TemporalManagedRunWorkflow {

    @WorkflowMethod
    TemporalManagedRunState run(TemporalManagedRunRequest request);

    @SignalMethod
    void approvalDecision(String decision);

    @QueryMethod
    TemporalManagedRunState state();
}
