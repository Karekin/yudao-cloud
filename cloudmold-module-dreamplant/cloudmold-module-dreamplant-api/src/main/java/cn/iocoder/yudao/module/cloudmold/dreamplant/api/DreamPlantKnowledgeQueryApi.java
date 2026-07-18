package cn.iocoder.yudao.module.cloudmold.dreamplant.api;

import java.util.List;

public interface DreamPlantKnowledgeQueryApi {
    DreamPlantAssetView getAsset(String mapKey, String assetId);

    List<DreamPlantAssetView> listAssets(String mapKey, DreamPlantAssetType assetType, String status, Integer limit);

    DreamPlantRelationView getRelation(String mapKey, String relationId);

    List<DreamPlantRelationView> listRelations(String mapKey, String assetId, String relationType, Integer limit);

    DreamPlantEvidenceView getEvidence(String mapKey, String evidenceId);

    List<DreamPlantEvidenceView> listEvidence(String mapKey, String subjectType, String subjectId, Integer limit);

    DreamPlantMetricView getMetric(String mapKey, String metricId);

    List<DreamPlantMetricView> listMetrics(String mapKey, String subjectType, String subjectId,
                                           String metricCode, Integer limit);

    DreamPlantSyncView getSyncCheckpoint(String mapKey, String syncKey);

    List<DreamPlantSyncView> listSyncCheckpoints(String mapKey, String sourceSystem, Integer limit);

    DreamPlantDriftView getDrift(String mapKey, String driftId);

    List<DreamPlantDriftView> listDrift(String mapKey, String subjectType, String subjectId,
                                        String severity, Integer limit);
}
