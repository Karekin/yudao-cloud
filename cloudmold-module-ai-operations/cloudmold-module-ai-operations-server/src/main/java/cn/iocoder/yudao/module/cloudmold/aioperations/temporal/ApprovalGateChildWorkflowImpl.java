package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import io.temporal.workflow.Workflow;

import java.time.Duration;

public class ApprovalGateChildWorkflowImpl implements ApprovalGateChildWorkflow {

    private TemporalManagedRunState current;
    private String decision;
    private String pauseReason;
    private String resumeReason;
    private String cancelReason;

    @Override
    public TemporalManagedRunState run(TemporalManagedRunRequest request, TemporalManagedRunState state) {
        current = state;
        while (current != null && !current.isTerminal()
                && (current.isWaitingApproval() || current.isPaused())) {
            if (current.isPaused()) {
                Workflow.await(() -> resumeReason != null || cancelReason != null);
                if (cancelReason != null) {
                    current = current.toBuilder().status("CANCELLED").phase("COMPLETED")
                            .waitingOn(null)
                            .cancelReason(consumeCancelReason())
                            .errorCode("MANUAL_CANCELLED")
                            .businessResult(TemporalManagedBusinessResult.builder()
                                    .outcomeCode("MANUAL_CANCELLED")
                                    .summary("审批门在放行前被人工取消")
                                    .domainObjectType("APPROVAL")
                                    .domainObjectId(current.getApprovalId())
                                    .evidenceRef(current.getWorkOrderId())
                                    .build())
                            .build();
                } else {
                    current = current.toBuilder().status("WAITING_APPROVAL").phase("APPROVAL_GATE")
                            .waitingOn("BPM_APPROVAL")
                            .pauseReason(consumeResumeReason())
                            .errorCode(null)
                            .build();
                }
                continue;
            }
            boolean signaled = Workflow.await(Duration.ofSeconds(approvalTimeoutSeconds(request)),
                    () -> decision != null || pauseReason != null || cancelReason != null);
            if (!signaled) {
                current = current.toBuilder().status("TIMED_OUT").phase("COMPLETED")
                        .waitingOn(null).errorCode("APPROVAL_TIMEOUT")
                        .businessResult(TemporalManagedBusinessResult.builder()
                                .outcomeCode("APPROVAL_TIMEOUT")
                                .summary("审批门超时未放行")
                                .domainObjectType("APPROVAL")
                                .domainObjectId(current.getApprovalId())
                                .evidenceRef(current.getWorkOrderId())
                                .build())
                        .build();
                continue;
            }
            if (cancelReason != null) {
                current = current.toBuilder().status("CANCELLED").phase("COMPLETED")
                        .waitingOn(null)
                        .cancelReason(consumeCancelReason())
                        .errorCode("MANUAL_CANCELLED")
                        .businessResult(TemporalManagedBusinessResult.builder()
                                .outcomeCode("MANUAL_CANCELLED")
                                .summary("审批门在放行前被人工取消")
                                .domainObjectType("APPROVAL")
                                .domainObjectId(current.getApprovalId())
                                .evidenceRef(current.getWorkOrderId())
                                .build())
                        .build();
            } else if (pauseReason != null) {
                current = current.toBuilder().status("PAUSED").phase("MANUAL_CONTROL")
                        .waitingOn("RESUME_OR_CANCEL")
                        .resumableStatus("WAITING_APPROVAL")
                        .pauseReason(consumePauseReason())
                        .errorCode("MANUAL_PAUSE")
                        .build();
            } else if ("APPROVE".equals(decision)) {
                current = current.toBuilder().status("APPROVED").phase("APPROVAL_GATE")
                        .waitingOn(null).approvalDecision("APPROVE")
                        .businessResult(TemporalManagedBusinessResult.builder()
                                .outcomeCode("APPROVED")
                                .summary("审批门已放行，进入 SkillTask 执行")
                                .domainObjectType("APPROVAL")
                                .domainObjectId(current.getApprovalId())
                                .evidenceRef(current.getWorkOrderId())
                                .build())
                        .build();
                decision = null;
            } else {
                current = current.toBuilder().status("REJECTED").phase("COMPLETED")
                        .waitingOn(null).approvalDecision("REJECT")
                        .errorCode("BPM_REJECTED")
                        .businessResult(TemporalManagedBusinessResult.builder()
                                .outcomeCode("APPROVAL_REJECTED")
                                .summary("审批门拒绝该高风险动作")
                                .domainObjectType("APPROVAL")
                                .domainObjectId(current.getApprovalId())
                                .evidenceRef(current.getWorkOrderId())
                                .build())
                        .build();
                decision = null;
            }
        }
        return current;
    }

    @Override
    public void approvalDecision(String decision) {
        if (this.decision == null && current != null && current.isWaitingApproval()
                && ("APPROVE".equals(decision) || "REJECT".equals(decision))) {
            this.decision = decision;
        }
    }

    @Override
    public void pause(String reason) {
        if (pauseReason == null && current != null && current.isWaitingApproval()) {
            pauseReason = normalizeReason(reason, "Manual pause requested");
        }
    }

    @Override
    public void resume(String reason) {
        if (resumeReason == null && current != null && current.isPaused()) {
            resumeReason = normalizeReason(reason, "Manual resume requested");
        }
    }

    @Override
    public void cancel(String reason) {
        if (cancelReason == null && current != null && !current.isTerminal()) {
            cancelReason = normalizeReason(reason, "Manual cancellation requested");
        }
    }

    @Override
    public TemporalManagedRunState state() {
        return current;
    }

    private static long approvalTimeoutSeconds(TemporalManagedRunRequest request) {
        return request.getApprovalTimeoutSeconds() == null || request.getApprovalTimeoutSeconds() <= 0
                ? 86_400L : request.getApprovalTimeoutSeconds();
    }

    private String consumePauseReason() {
        String value = pauseReason;
        pauseReason = null;
        return value;
    }

    private String consumeResumeReason() {
        String value = resumeReason;
        resumeReason = null;
        return value;
    }

    private String consumeCancelReason() {
        String value = cancelReason;
        cancelReason = null;
        return value;
    }

    private static String normalizeReason(String reason, String fallback) {
        return reason == null || reason.isBlank() ? fallback : reason.trim();
    }
}
