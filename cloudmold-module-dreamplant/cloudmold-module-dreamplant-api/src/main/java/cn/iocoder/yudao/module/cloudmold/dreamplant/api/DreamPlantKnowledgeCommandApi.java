package cn.iocoder.yudao.module.cloudmold.dreamplant.api;

import java.time.Instant;

public interface DreamPlantKnowledgeCommandApi {
    DreamPlantCommandResult upsertAsset(DreamPlantAssetCommand command);

    DreamPlantCommandResult linkAssets(DreamPlantRelationCommand command);

    DreamPlantCommandResult recordEvidence(DreamPlantEvidenceCommand command);

    DreamPlantCommandResult recordMetric(DreamPlantMetricCommand command);

    DreamPlantCommandResult upsertSyncCheckpoint(DreamPlantSyncCommand command);

    DreamPlantCommandResult recordDrift(DreamPlantDriftCommand command);

    DreamPlantCommandResult rebuildProjection(String mapKey, String projectionKey,
                                              String idempotencyKey, String runTraceId, Instant requestedAt);

    DreamPlantCommandResult runExplorationNow(String mapKey, String explorationRunId,
                                              String idempotencyKey, String runTraceId, Instant requestedAt);
}
