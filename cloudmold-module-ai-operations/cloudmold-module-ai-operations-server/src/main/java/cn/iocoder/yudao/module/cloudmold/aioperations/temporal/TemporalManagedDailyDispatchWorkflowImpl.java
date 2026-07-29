package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import io.temporal.activity.ActivityOptions;
import io.temporal.api.enums.v1.ParentClosePolicy;
import io.temporal.api.enums.v1.WorkflowIdReusePolicy;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Async;
import io.temporal.workflow.ChildWorkflowOptions;
import io.temporal.workflow.Workflow;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

public class TemporalManagedDailyDispatchWorkflowImpl implements TemporalManagedDailyDispatchWorkflow {

    private final TemporalDailyDispatchActivities activities =
            Workflow.newActivityStub(TemporalDailyDispatchActivities.class,
                    ActivityOptions.newBuilder()
                            .setStartToCloseTimeout(Duration.ofMinutes(2))
                            .setRetryOptions(RetryOptions.newBuilder()
                                    .setInitialInterval(Duration.ofSeconds(1))
                                    .setBackoffCoefficient(2.0d)
                                    .setMaximumAttempts(3)
                                    .build())
                            .build());

    @Override
    public TemporalDailyDispatchResult run(TemporalDailyDispatchRequest request) {
        String businessDate = Instant.ofEpochMilli(Workflow.getInfo().getRunStartedTimestampMillis())
                .atZone(ZoneId.of(request.getTimeZone()))
                .toLocalDate()
                .toString();
        String leaseOwner = Workflow.getInfo().getRunId();
        List<TemporalAutomationCandidate> candidates =
                activities.claimCandidates(request, businessDate, leaseOwner);
        List<String> workflowIds = new ArrayList<>();
        int failed = 0;
        for (TemporalAutomationCandidate candidate : candidates) {
            String workflowId = TemporalManagedWorkflowIds.automationCandidateWorkflowId(
                    request, candidate, businessDate);
            try {
                TemporalManagedRunWorkflow child = Workflow.newChildWorkflowStub(
                        TemporalManagedRunWorkflow.class,
                        ChildWorkflowOptions.newBuilder()
                                .setWorkflowId(workflowId)
                                .setWorkflowIdReusePolicy(
                                        WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_ALLOW_DUPLICATE_FAILED_ONLY)
                                .setParentClosePolicy(ParentClosePolicy.PARENT_CLOSE_POLICY_ABANDON)
                                .build());
                Async.function(child::run, toManagedRunRequest(request, candidate));
                Workflow.getWorkflowExecution(child).get();
                activities.markDispatched(
                        request, candidate.getCandidateId(), leaseOwner, workflowId);
                workflowIds.add(workflowId);
            } catch (RuntimeException exception) {
                if (activities.workflowExists(workflowId)) {
                    activities.markDispatched(
                            request, candidate.getCandidateId(), leaseOwner, workflowId);
                    workflowIds.add(workflowId);
                } else {
                    failed++;
                    activities.releaseCandidate(
                            request, candidate.getCandidateId(), leaseOwner, exception.getMessage());
                }
            }
        }
        int dispatched = workflowIds.size();
        TemporalDailyDispatchResult result = TemporalDailyDispatchResult.builder()
                .outcomeCode(candidates.isEmpty() ? "NO_ACTION_DUE"
                        : failed == 0 ? "DISPATCHED" : "PARTIAL_DISPATCH")
                .businessDate(businessDate)
                .candidateCount(candidates.size())
                .dispatchedCount(dispatched)
                .failedCount(failed)
                .workflowIds(List.copyOf(workflowIds))
                .build();
        activities.recordDispatchResult(
                request, result, Workflow.getInfo().getRunId());
        return result;
    }

    private static TemporalManagedRunRequest toManagedRunRequest(
            TemporalDailyDispatchRequest request, TemporalAutomationCandidate candidate) {
        return TemporalManagedRunRequest.builder()
                .tenantId(request.getTenantId())
                .scheduleId(request.getScheduleId())
                .skillId(request.getSkillId())
                .skillVersion(request.getSkillVersion())
                .inputJson(candidate.getInputJson())
                .operatorUserId(request.getOperatorUserId())
                .operatorUserType(request.getOperatorUserType())
                .roleCode(request.getRoleCode())
                .actionCode(request.getActionCode())
                .approvalTimeoutSeconds(request.getApprovalTimeoutSeconds())
                .businessEventTimeoutSeconds(request.getBusinessEventTimeoutSeconds())
                .build();
    }
}
