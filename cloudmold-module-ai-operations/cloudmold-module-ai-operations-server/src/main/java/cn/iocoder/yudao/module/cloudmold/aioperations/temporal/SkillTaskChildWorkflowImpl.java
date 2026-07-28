package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import io.temporal.workflow.Workflow;

import java.time.Duration;

public class SkillTaskChildWorkflowImpl implements SkillTaskChildWorkflow {

    private final TemporalManagedRunActivities activities;

    private TemporalManagedRunState current;
    private String pauseReason;
    private String resumeReason;
    private String cancelReason;

    public SkillTaskChildWorkflowImpl() {
        this(Workflow.newActivityStub(TemporalManagedRunActivities.class,
                io.temporal.activity.ActivityOptions.newBuilder()
                        .setStartToCloseTimeout(Duration.ofMinutes(2))
                        .setRetryOptions(io.temporal.common.RetryOptions.newBuilder()
                                .setInitialInterval(Duration.ofSeconds(1))
                                .setBackoffCoefficient(2.0d)
                                .setMaximumAttempts(3)
                                .build())
                        .build()));
    }

    SkillTaskChildWorkflowImpl(TemporalManagedRunActivities activities) {
        this.activities = activities;
    }

    @Override
    public TemporalManagedRunState run(TemporalManagedRunRequest request, TemporalManagedRunState state) {
        current = state;
        if (!"SUBMITTED".equals(current.getStatus()) || current.getTaskId() == null) {
            current = activities.resumeApproved(request, current);
        }
        while (current != null && !current.isTerminal()) {
            if ("WAITING_EVENT".equals(current.getStatus())) {
                return current;
            }
            if (current.isPaused()) {
                Workflow.await(() -> resumeReason != null || cancelReason != null);
                if (cancelReason != null) {
                    current = activities.cancel(request, current, consumeCancelReason());
                } else {
                    current = activities.resume(request, current, consumeResumeReason());
                }
                continue;
            }
            if (cancelReason != null) {
                current = activities.cancel(request, current, consumeCancelReason());
                continue;
            }
            if (pauseReason != null) {
                current = activities.pause(request, current, consumePauseReason());
                continue;
            }
            Workflow.sleep(Duration.ofSeconds(2));
            current = activities.refreshSkillTask(request, current);
        }
        return current;
    }

    @Override
    public void pause(String reason) {
        if (pauseReason == null && current != null && !current.isTerminal()) {
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
