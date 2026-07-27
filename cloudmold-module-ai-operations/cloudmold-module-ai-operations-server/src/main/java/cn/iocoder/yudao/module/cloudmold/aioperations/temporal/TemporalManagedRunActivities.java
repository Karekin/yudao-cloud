package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import io.temporal.activity.ActivityInterface;

@ActivityInterface
public interface TemporalManagedRunActivities {

    TemporalManagedRunState prepare(TemporalManagedRunRequest request, String workflowId, String runId);

    TemporalManagedRunState resumeApproved(TemporalManagedRunRequest request,
                                           TemporalManagedRunState prepared);

    TemporalManagedRunState reject(TemporalManagedRunRequest request,
                                   TemporalManagedRunState prepared);
}
