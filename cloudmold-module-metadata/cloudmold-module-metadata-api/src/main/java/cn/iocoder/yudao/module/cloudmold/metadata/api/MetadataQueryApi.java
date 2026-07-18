package cn.iocoder.yudao.module.cloudmold.metadata.api;

public interface MetadataQueryApi {
    MetadataView getDefinition(String definitionId);
    MetadataDatasetReference validateDatasetVersion(String datasetId, Long datasetVersion,
                                                     String qualifiedName, String schemaSha256);
    MetadataView getTaskRun(String runId);
    MetadataView getDqcResult(String resultId);
}
