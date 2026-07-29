package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import io.temporal.activity.ActivityInterface;

import java.util.List;

@ActivityInterface
public interface TemporalDailyDispatchActivities {

    List<TemporalAutomationCandidate> claimCandidates(
            TemporalDailyDispatchRequest request, String businessDate, String leaseOwner);

    void markDispatched(TemporalDailyDispatchRequest request, String candidateId,
                        String leaseOwner, String workflowId);

    void releaseCandidate(TemporalDailyDispatchRequest request, String candidateId,
                          String leaseOwner, String error);

    boolean workflowExists(String workflowId);

    void recordDispatchResult(TemporalDailyDispatchRequest request,
                              TemporalDailyDispatchResult result,
                              String temporalRunId);
}
