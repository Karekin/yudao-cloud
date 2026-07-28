package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import io.temporal.workflow.Workflow;

import java.time.Duration;
import java.util.Objects;

public class BusinessEventWaitChildWorkflowImpl implements BusinessEventWaitChildWorkflow {

    private final TemporalManagedRunActivities activities;

    private TemporalManagedRunState current;
    private String pauseReason;
    private String resumeReason;
    private String cancelReason;
    private String pendingEventCode;
    private String pendingEventRef;
    private String lastAcceptedEventFingerprint;

    public BusinessEventWaitChildWorkflowImpl() {
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

    BusinessEventWaitChildWorkflowImpl(TemporalManagedRunActivities activities) {
        this.activities = activities;
    }

    @Override
    public TemporalManagedRunState run(TemporalManagedRunRequest request, TemporalManagedRunState state) {
        current = state;
        if (!"WAITING_EVENT".equals(current.getStatus())) {
            current = activities.enterBusinessEventWait(request, current, current.getWaitReference());
        }
        while (current != null && !current.isTerminal()
                && ("WAITING_EVENT".equals(current.getStatus()) || current.isPaused())) {
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
            boolean signaled = Workflow.await(Duration.ofSeconds(businessEventTimeoutSeconds(request)),
                    () -> pendingEventCode != null || pauseReason != null || cancelReason != null);
            if (!signaled) {
                current = activities.timeoutBusinessEventWait(request, current);
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
            if (pendingEventRef != null) {
                current = current.toBuilder()
                        .waitReference(pendingEventRef)
                        .controlEventCount(current.getControlEventCount() == null ? 1 : current.getControlEventCount() + 1)
                        .build();
            } else if (pendingEventCode != null) {
                current = current.toBuilder()
                        .controlEventCount(current.getControlEventCount() == null ? 1 : current.getControlEventCount() + 1)
                        .build();
            }
            consumePendingEvent();
            current = activities.refreshBusinessEventWait(request, current, current.getWaitReference());
        }
        return current;
    }

    @Override
    public void businessEvent(String eventCode, String eventRef) {
        if (current == null || current.isTerminal() || current.isPaused()) {
            return;
        }
        String normalizedCode = normalize(eventCode);
        if (normalizedCode == null) {
            return;
        }
        String fingerprint = normalizedCode + "|" + normalize(eventRef);
        if (Objects.equals(fingerprint, lastAcceptedEventFingerprint)) {
            return;
        }
        if (pendingEventCode == null) {
            pendingEventCode = normalizedCode;
            pendingEventRef = normalize(eventRef);
            lastAcceptedEventFingerprint = fingerprint;
        }
    }

    @Override
    public void pause(String reason) {
        if (pauseReason == null && current != null && "WAITING_EVENT".equals(current.getStatus())) {
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

    private static long businessEventTimeoutSeconds(TemporalManagedRunRequest request) {
        return request.getBusinessEventTimeoutSeconds() == null || request.getBusinessEventTimeoutSeconds() <= 0
                ? 86_400L : request.getBusinessEventTimeoutSeconds();
    }

    private void consumePendingEvent() {
        pendingEventCode = null;
        pendingEventRef = null;
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

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
