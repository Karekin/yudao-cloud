package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface ApprovalGateChildWorkflow {

    @WorkflowMethod
    TemporalManagedRunState run(TemporalManagedRunRequest request, TemporalManagedRunState state);

    @SignalMethod
    void approvalDecision(String decision);

    @SignalMethod
    void pause(String reason);

    @SignalMethod
    void resume(String reason);

    @SignalMethod
    void cancel(String reason);

    @QueryMethod
    TemporalManagedRunState state();
}
