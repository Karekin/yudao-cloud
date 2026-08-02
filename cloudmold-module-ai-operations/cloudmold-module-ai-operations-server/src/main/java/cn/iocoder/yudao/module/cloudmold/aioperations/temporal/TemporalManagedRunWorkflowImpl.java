package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import io.temporal.common.RetryOptions;
import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.ChildWorkflowOptions;
import io.temporal.workflow.Workflow;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public class TemporalManagedRunWorkflowImpl implements TemporalManagedRunWorkflow {

    private final TemporalManagedRunActivities activities;

    private TemporalManagedRunState current = TemporalManagedRunState.builder()
            .status("QUEUED").phase("PREPARE").waitingOn("PREPARE").controlEventCount(0).build();
    private ApprovalGateChildWorkflow approvalChild;
    private SkillTaskChildWorkflow skillTaskChild;
    private BusinessEventWaitChildWorkflow businessEventChild;
    private String acceptedApprovalDecision;

    public TemporalManagedRunWorkflowImpl() {
        this(Workflow.newActivityStub(
                TemporalManagedRunActivities.class,
                ActivityOptions.newBuilder()
                        .setStartToCloseTimeout(Duration.ofMinutes(2))
                        .setRetryOptions(RetryOptions.newBuilder()
                                .setInitialInterval(Duration.ofSeconds(1))
                                .setBackoffCoefficient(2.0d)
                                .setMaximumAttempts(3)
                                .build())
                        .build()));
    }

    TemporalManagedRunWorkflowImpl(TemporalManagedRunActivities activities) {
        this.activities = activities;
    }

    @Override
    public TemporalManagedRunState run(TemporalManagedRunRequest request) {
        current = activities.prepare(request, Workflow.getInfo().getWorkflowId(),
                Workflow.getInfo().getRunId());
        refreshVisibility(request);
        if (current.isWaitingApproval()) {
            approvalChild = Workflow.newChildWorkflowStub(ApprovalGateChildWorkflow.class,
                    ChildWorkflowOptions.newBuilder()
                            .setWorkflowId(Workflow.getInfo().getWorkflowId() + "/approval")
                            .build());
            current = approvalChild.run(request, current);
            acceptedApprovalDecision = null;
            current = persistApprovalTerminal(request, current);
            refreshVisibility(request);
        }
        if (!current.isTerminal()) {
            skillTaskChild = Workflow.newChildWorkflowStub(SkillTaskChildWorkflow.class,
                    ChildWorkflowOptions.newBuilder()
                            .setWorkflowId(Workflow.getInfo().getWorkflowId() + "/skilltask")
                            .build());
            current = skillTaskChild.run(request, current);
            refreshVisibility(request);
        }
        if ("WAITING_EVENT".equals(current.getStatus())) {
            businessEventChild = Workflow.newChildWorkflowStub(BusinessEventWaitChildWorkflow.class,
                    ChildWorkflowOptions.newBuilder()
                            .setWorkflowId(Workflow.getInfo().getWorkflowId() + "/business-event")
                            .build());
            current = businessEventChild.run(request, current);
            refreshVisibility(request);
        }
        return current;
    }

    @Override
    public void approvalDecision(String decision) {
        if (approvalChild != null
                && current != null
                && current.isWaitingApproval()
                && acceptedApprovalDecision == null
                && ("APPROVE".equals(decision) || "REJECT".equals(decision))) {
            acceptedApprovalDecision = decision;
            approvalChild.approvalDecision(decision);
        }
    }

    @Override
    public void businessEvent(String eventCode, String eventRef) {
        if (businessEventChild != null && current != null && !current.isTerminal()) {
            businessEventChild.businessEvent(eventCode, eventRef);
        }
    }

    @Override
    public void pause(String reason) {
        if (approvalChild != null && current != null && current.isWaitingApproval()) {
            approvalChild.pause(reason);
        } else if (businessEventChild != null && current != null && "WAITING_EVENT".equals(current.getStatus())) {
            businessEventChild.pause(reason);
        } else if (skillTaskChild != null && current != null && !current.isTerminal()) {
            skillTaskChild.pause(reason);
        }
    }

    @Override
    public void resume(String reason) {
        if (approvalChild != null && current != null && current.isPaused()
                && "APPROVAL_GATE".equals(current.getResumableStatus() == null ? current.getPhase() : "APPROVAL_GATE")) {
            approvalChild.resume(reason);
        } else if (businessEventChild != null && current != null && current.isPaused()
                && "WAITING_EVENT".equals(current.getResumableStatus())) {
            businessEventChild.resume(reason);
        } else if (skillTaskChild != null && current != null && current.isPaused()) {
            skillTaskChild.resume(reason);
        }
    }

    @Override
    public void cancel(String reason) {
        if (approvalChild != null && current != null && current.isWaitingApproval()) {
            approvalChild.cancel(reason);
        } else if (businessEventChild != null && current != null && "WAITING_EVENT".equals(current.getStatus())) {
            businessEventChild.cancel(reason);
        } else if (skillTaskChild != null && current != null && !current.isTerminal()) {
            skillTaskChild.cancel(reason);
        }
    }

    @Override
    public TemporalManagedRunState state() {
        return current;
    }

    private void refreshVisibility(TemporalManagedRunRequest request) {
        Map<String, Object> memo = new HashMap<>();
        memo.put("status", current.getStatus());
        memo.put("phase", current.getPhase());
        memo.put("tenantId", request.getTenantId());
        memo.put("scheduleId", request.getScheduleId());
        memo.put("skillId", request.getSkillId());
        memo.put("workOrderId", current.getWorkOrderId());
        memo.put("approvalId", current.getApprovalId());
        Workflow.upsertMemo(memo);
        Workflow.upsertSearchAttributes(TemporalManagedSearchAttributes.from(request, current));
    }

    private TemporalManagedRunState persistApprovalTerminal(TemporalManagedRunRequest request,
                                                             TemporalManagedRunState state) {
        return switch (state.getStatus()) {
            case "REJECTED" -> activities.reject(request, state);
            case "TIMED_OUT" -> activities.timeout(request, state);
            case "CANCELLED" -> activities.cancel(request, state, state.getCancelReason());
            default -> state;
        };
    }

}
