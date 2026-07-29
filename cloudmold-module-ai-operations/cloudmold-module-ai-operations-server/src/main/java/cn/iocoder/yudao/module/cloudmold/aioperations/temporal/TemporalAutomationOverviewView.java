package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class TemporalAutomationOverviewView {

    int registeredCount;
    int scheduledCount;
    int healthyScheduleCount;
    double scheduleCoverageRate;
    int candidateSourceConnectedCount;
    double candidateSourceCoverageRate;
    int autonomyProvenCount;
    double autonomyProofCoverageRate;
    List<WorkflowAutomationView> workflows;

    @Value
    @Builder
    public static class WorkflowAutomationView {
        String skillId;
        String skillVersion;
        String displayName;
        String scheduleState;
        String discoverySource;
        String lastDispatchOutcome;
        int candidateCount;
        int dispatchedCount;
        int failedCount;
        String businessAutonomyState;
        List<String> gapCodes;
        String proofRef;
    }
}
