package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import io.temporal.activity.ActivityInterface;

@ActivityInterface
public interface TemporalManagedRunActivities {

    TemporalManagedRunState prepare(TemporalManagedRunRequest request, String workflowId, String runId);

    TemporalManagedRunState resumeApproved(TemporalManagedRunRequest request,
                                           TemporalManagedRunState prepared);

    TemporalManagedRunState reject(TemporalManagedRunRequest request,
                                   TemporalManagedRunState prepared);

    TemporalManagedRunState pause(TemporalManagedRunRequest request,
                                  TemporalManagedRunState current,
                                  String reason);

    TemporalManagedRunState resume(TemporalManagedRunRequest request,
                                   TemporalManagedRunState current,
                                   String reason);

    TemporalManagedRunState cancel(TemporalManagedRunRequest request,
                                   TemporalManagedRunState current,
                                   String reason);

    TemporalManagedRunState timeout(TemporalManagedRunRequest request,
                                    TemporalManagedRunState current);

    TemporalManagedRunState refreshSkillTask(TemporalManagedRunRequest request,
                                             TemporalManagedRunState current);

    TemporalManagedRunState enterBusinessEventWait(TemporalManagedRunRequest request,
                                                   TemporalManagedRunState current,
                                                   String waitReference);

    TemporalManagedRunState refreshBusinessEventWait(TemporalManagedRunRequest request,
                                                     TemporalManagedRunState current,
                                                     String waitReference);

    TemporalManagedRunState timeoutBusinessEventWait(TemporalManagedRunRequest request,
                                                     TemporalManagedRunState current);
}
