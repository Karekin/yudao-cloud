package cn.iocoder.yudao.module.cloudmold.dreamplant.service.worker;

import cn.iocoder.yudao.module.cloudmold.dreamplant.dal.dataobject.DreamPlantRecords.ExplorationRun;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;

public interface DreamPlantExplorationGateway {

    List<ExplorationCandidate> selectDueExplorations(LocalDateTime now, int limit);

    boolean claimExploration(Long tenantId, String explorationRunId, Long expectedVersion,
                             String leaseOwner, LocalDateTime leaseUntil, LocalDateTime now);

    ExplorationRun loadExploration(Long tenantId, String explorationRunId);

    void appendExplorationStep(ExplorationStep step);

    void markExplorationRetry(RetryDecision decision);

    void markExplorationTerminal(TerminalDecision decision);

    @Value
    @Builder
    class ExplorationCandidate {
        Long tenantId;
        String explorationRunId;
        Long version;
        Integer attemptCount;
    }

    @Value
    @Builder
    class ExplorationStep {
        Long tenantId;
        String explorationRunId;
        Long expectedVersion;
        String stepCode;
        String stepStatus;
        String detailJson;
        LocalDateTime occurredAt;
    }

    @Value
    @Builder
    class RetryDecision {
        Long tenantId;
        String explorationRunId;
        Long expectedVersion;
        Integer attemptCount;
        String leaseOwner;
        String failureCode;
        String failureMessage;
        String outcomeJson;
        String evidenceRef;
        LocalDateTime nextRunAt;
        LocalDateTime now;
    }

    @Value
    @Builder
    class TerminalDecision {
        Long tenantId;
        String explorationRunId;
        Long expectedVersion;
        String status;
        String outcomeJson;
        String evidenceRef;
        LocalDateTime now;
    }

}
