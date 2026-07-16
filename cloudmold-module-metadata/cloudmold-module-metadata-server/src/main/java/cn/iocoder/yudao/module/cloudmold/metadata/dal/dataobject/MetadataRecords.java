package cn.iocoder.yudao.module.cloudmold.metadata.dal.dataobject;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class MetadataRecords {
    private MetadataRecords() {
    }

    @Data @Accessors(chain = true)
    public static class Operation {
        private Long operationId; private Long tenantId; private String idempotencyKey; private String commandType;
        private String requestHash; private String attemptToken; private Integer status; private String aggregateId;
        private String resultJson; private LocalDateTime createdAt; private LocalDateTime updatedAt;
    }

    @Data @Accessors(chain = true)
    public static class Definition {
        private String definitionId; private Long tenantId; private String definitionKind; private String definitionCode;
        private String displayName; private String status; private Long currentVersion; private String ownerPrincipalId;
        private LocalDateTime createdAt; private LocalDateTime updatedAt;
    }

    @Data @Accessors(chain = true)
    public static class DefinitionVersion {
        private Long tenantId; private String definitionId; private Long definitionVersion;
        private String specificationSha256; private String artifactRef; private String publishedByPrincipalId;
        private LocalDateTime publishedAt;
    }

    @Data @Accessors(chain = true)
    public static class DataSourceVersion {
        private Long tenantId; private String definitionId; private Long definitionVersion; private String sourceType;
        private String environment; private String endpointRef; private String credentialRef; private String namespaceRef;
    }

    @Data @Accessors(chain = true)
    public static class DatasetVersion {
        private Long tenantId; private String definitionId; private Long definitionVersion; private String dataSourceId;
        private Long dataSourceVersion; private String datasetType; private String qualifiedName; private String layerCode;
        private String grainCode; private String schemaSha256; private String storageLocationRef; private Integer retentionDays;
    }

    @Data @Accessors(chain = true)
    public static class FieldVersion {
        private Long tenantId; private String datasetId; private Long datasetVersion; private Integer ordinalPosition;
        private String fieldCode; private String dataType; private Boolean nullable; private Boolean primaryKeyPart;
        private String semanticType; private String classification;
    }

    @Data @Accessors(chain = true)
    public static class TaskVersion {
        private Long tenantId; private String definitionId; private Long definitionVersion; private String taskType;
        private String executableArtifactRef; private String codeSha256; private String scheduleSha256;
        private String resourceGroupRef;
    }

    @Data @Accessors(chain = true)
    public static class TaskDependency {
        private Long tenantId; private String taskId; private Long taskVersion; private Integer dependencySequence;
        private String upstreamTaskId; private Long upstreamTaskVersion; private String dependencyType;
        private Boolean required;
    }

    @Data @Accessors(chain = true)
    public static class TaskSla {
        private Long tenantId; private String taskId; private Long taskVersion; private String serviceLevelCode;
        private Integer deadlineMinuteUtc; private Long maximumDurationMillis; private Long maximumFreshnessMillis;
        private String approvedByPrincipalId;
    }

    @Data @Accessors(chain = true)
    public static class TaskRunObservation {
        private Long tenantId; private String runId; private String taskId; private Long taskVersion;
        private Integer attempt; private Long observationSequence; private String status; private LocalDateTime scheduledAt;
        private LocalDateTime startedAt; private LocalDateTime finishedAt; private Long durationMillis;
        private Long computeCostMinor; private String costCurrency; private Long resourceMillis; private Long rowsRead;
        private Long rowsWritten; private String sourceCheckpointRef; private String outputSnapshotRef;
        private String errorRef; private LocalDateTime observedAt; private Long operationId;
    }

    @Data @Accessors(chain = true)
    public static class LineageVersion {
        private Long tenantId; private String definitionId; private Long definitionVersion;
        private String sourceDatasetId; private Long sourceDatasetVersion; private String targetDatasetId;
        private Long targetDatasetVersion; private String direction; private String transformSha256;
        private String transformationRef;
    }

    @Data @Accessors(chain = true)
    public static class DqcRuleVersion {
        private Long tenantId; private String definitionId; private Long definitionVersion; private String datasetId;
        private Long datasetVersion; private String datasetField; private String ruleType; private String severity;
        private String expressionSha256; private BigDecimal thresholdValue; private String thresholdComparator;
    }

    @Data @Accessors(chain = true)
    public static class DqcResult {
        private Long tenantId; private String resultId; private String dqcRuleId; private Long dqcRuleVersion;
        private String datasetId; private Long datasetVersion; private String taskRunId;
        private Long taskRunObservationSequence; private String status;
        private BigDecimal expectedValue; private BigDecimal actualValue; private Long evaluatedRows;
        private Long violationCount; private String evidenceRef; private LocalDateTime observedAt; private Long operationId;
    }

    @Data @Accessors(chain = true)
    public static class MetricVersion {
        private Long tenantId; private String definitionId; private Long definitionVersion; private String grainCode;
        private String metricUnit; private String aggregationType; private String expressionSha256; private String filterSha256;
        private String dimensionsSha256; private String semanticVersion;
    }

    @Data @Accessors(chain = true)
    public static class StatusHistory {
        private Long tenantId; private String aggregateType; private String aggregateId; private Long aggregateVersion;
        private String previousStatus; private String currentStatus; private Long operationId; private String reasonCode;
        private LocalDateTime occurredAt; private LocalDateTime createdAt;
    }
}
