package cn.iocoder.yudao.module.cloudmold.dreamplant.service.worker;

import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.cloudmold.dreamplant.api.DreamPlantCommand;
import cn.iocoder.yudao.module.cloudmold.dreamplant.api.DreamPlantCommandApi;
import cn.iocoder.yudao.module.cloudmold.dreamplant.api.DreamPlantOperation;
import cn.iocoder.yudao.module.cloudmold.dreamplant.dal.dataobject.DreamPlantRecords.ExplorationRun;
import cn.iocoder.yudao.module.cloudmold.dreamplant.dal.dataobject.DreamPlantRecords.Snapshot;
import cn.iocoder.yudao.module.cloudmold.dreamplant.dal.dataobject.DreamPlantRecords.WorldMap;
import cn.iocoder.yudao.module.cloudmold.dreamplant.dal.mysql.DreamPlantStoreMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DreamPlantWorkerAdapters implements DreamPlantExplorationGateway, DreamPlantWorldMapPort {

    private final DreamPlantStoreMapper mapper;
    @Lazy
    private final DreamPlantCommandApi commandApi;

    @Override
    public List<ExplorationCandidate> selectDueExplorations(LocalDateTime now, int limit) {
        return mapper.selectDueExplorations(now, limit);
    }

    @Override
    public boolean claimExploration(Long tenantId, String explorationRunId, Long expectedVersion,
                                    String leaseOwner, LocalDateTime leaseUntil, LocalDateTime now) {
        return mapper.claimExploration(tenantId, explorationRunId, expectedVersion, leaseOwner, leaseUntil, now) == 1;
    }

    @Override
    public ExplorationRun loadExploration(Long tenantId, String explorationRunId) {
        return mapper.selectExploration(tenantId, explorationRunId);
    }

    @Override
    public void appendExplorationStep(ExplorationStep step) {
        require(mapper.appendExplorationStep(step.getTenantId(), step.getExplorationRunId(), step.getExpectedVersion(),
                step.getStepCode(), step.getStepStatus(), step.getDetailJson(), step.getOccurredAt()) == 1,
                "failed to append DreamPlant exploration step");
    }

    @Override
    public void markExplorationRetry(RetryDecision decision) {
        require(mapper.markExplorationRetry(decision.getTenantId(), decision.getExplorationRunId(),
                decision.getExpectedVersion(), decision.getAttemptCount(), decision.getLeaseOwner(),
                decision.getFailureCode(), truncate(decision.getFailureMessage(), 1000), decision.getOutcomeJson(),
                decision.getEvidenceRef(), decision.getNextRunAt(), decision.getNow()) == 1,
                "failed to schedule DreamPlant exploration retry");
    }

    @Override
    public void markExplorationTerminal(TerminalDecision decision) {
        require(mapper.markExplorationTerminal(decision.getTenantId(), decision.getExplorationRunId(),
                decision.getExpectedVersion(), decision.getStatus(), decision.getOutcomeJson(),
                decision.getEvidenceRef(), decision.getNow()) == 1,
                "failed to complete DreamPlant exploration");
    }

    @Override
    @Transactional(readOnly = true)
    public PublishedWorldMap loadPublishedWorldMap(Long tenantId, String mapKey) {
        WorldMap map = mapper.selectWorldMap(tenantId, mapKey);
        if (map == null || map.getCurrentVersion() == null || map.getCurrentVersion() == 0) {
            return null;
        }
        Snapshot snapshot = mapper.selectSnapshot(tenantId, mapKey, map.getCurrentVersion());
        if (snapshot == null) {
            return null;
        }
        return PublishedWorldMap.builder().tenantId(tenantId).mapKey(mapKey).version(snapshot.getVersion())
                .schemaVersion(snapshot.getSchemaVersion()).payloadJson(snapshot.getPayloadJson())
                .publiclyReadable(map.getPubliclyReadable()).sourceRef(snapshot.getSourceRef()).build();
    }

    @Override
    public void publishWorldMap(PublishWorldMapRequest request) {
        TenantUtils.execute(request.getTenantId(), () -> commandApi.execute(DreamPlantCommand.builder()
                .operation(DreamPlantOperation.PUBLISH_WORLD_MAP)
                .idempotencyKey(request.getIdempotencyKey()).runTraceId(request.getRunTraceId())
                .occurredAt(Instant.now()).mapKey(request.getMapKey()).expectedVersion(request.getExpectedVersion())
                .schemaVersion(request.getSchemaVersion()).payloadJson(request.getPayloadJson())
                .payloadSha256(request.getPayloadSha256()).sourceRef(request.getSourceRef())
                .publiclyReadable(request.getPubliclyReadable()).build()));
    }

    private static String truncate(String value, int limit) {
        return value == null || value.length() <= limit ? value : value.substring(0, limit);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
