package cn.iocoder.yudao.module.cloudmold.metadata.api;

public interface MetadataQueryApi {
    MetadataView getDefinition(String definitionId);
    MetadataView getTaskRun(String runId);
    MetadataView getDqcResult(String resultId);
}
