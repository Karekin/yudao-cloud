package cn.iocoder.yudao.module.cloudmold.operationsintelligence.api;

public interface OperationsIntelligenceQueryApi {
    OperationsIntelligenceResult getObservation(String observationId);
    OperationsIntelligenceResult getClueSourceVersion(String sourceVersionId);
    OperationsIntelligenceResult getClue(String clueId);
    OperationsIntelligenceResult getAlert(String alertId);
}
