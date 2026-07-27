package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;

import java.time.Duration;

public class TemporalManagedRunWorkflowImpl implements TemporalManagedRunWorkflow {

    private final TemporalManagedRunActivities activities = Workflow.newActivityStub(
            TemporalManagedRunActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofMinutes(2))
                    .build());

    private TemporalManagedRunState current = TemporalManagedRunState.builder().status("QUEUED").build();
    private String decision;

    @Override
    public TemporalManagedRunState run(TemporalManagedRunRequest request) {
        current = activities.prepare(request, Workflow.getInfo().getWorkflowId(),
                Workflow.getInfo().getRunId());
        if (!"WAITING_APPROVAL".equals(current.getStatus())) {
            return current;
        }
        Workflow.await(() -> decision != null);
        current = "APPROVE".equals(decision)
                ? activities.resumeApproved(request, current)
                : activities.reject(request, current);
        return current;
    }

    @Override
    public void approvalDecision(String decision) {
        if (this.decision == null && ("APPROVE".equals(decision) || "REJECT".equals(decision))) {
            this.decision = decision;
        }
    }

    @Override
    public TemporalManagedRunState state() {
        return current;
    }
}
