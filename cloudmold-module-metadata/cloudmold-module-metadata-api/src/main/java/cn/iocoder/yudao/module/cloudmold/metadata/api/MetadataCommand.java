package cn.iocoder.yudao.module.cloudmold.metadata.api;

import lombok.*;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class MetadataCommand {
    private MetadataOperation operation;
    private String idempotencyKey;
    private String runTraceId;
    private String correlationId;
    private String causationId;
    private Instant occurredAt;

    private String definitionId;
    private String definitionCode;
    private String displayName;
    private Long expectedVersion;
    private String ownerPrincipalId;
    private String specificationSha256;
    private String artifactRef;

    // Data source definition. Raw URL, username and credentials are forbidden.
    private String sourceType;
    private String environment;
    private String endpointRef;
    private String credentialRef;
    private String namespaceRef;

    // Dataset/table definition and immutable fields.
    private String dataSourceId;
    private Long dataSourceVersion;
    private String datasetType;
    private String qualifiedName;
    private String layerCode;
    private String grainCode;
    private String schemaSha256;
    private String storageLocationRef;
    private Integer retentionDays;
    private List<FieldDefinition> fields;

    // Task definition, direct dependencies and approved SLA.
    private String taskType;
    private String executableArtifactRef;
    private String codeSha256;
    private String scheduleSha256;
    private String resourceGroupRef;
    private List<TaskDependency> dependencies;
    private TaskSla sla;

    // Append-only task-run observation.
    private String taskId;
    private Long taskVersion;
    private String taskRunId;
    private Integer attempt;
    private Long expectedObservationSequence;
    private String runStatus;
    private Instant scheduledAt;
    private Instant startedAt;
    private Instant finishedAt;
    private Long durationMillis;
    private Long computeCostMinor;
    private String costCurrency;
    private Long resourceMillis;
    private Long rowsRead;
    private Long rowsWritten;
    private String sourceCheckpointRef;
    private String outputSnapshotRef;
    private String errorRef;

    // Directional, version-bound dataset lineage.
    private String sourceDatasetId;
    private Long sourceDatasetVersion;
    private String targetDatasetId;
    private Long targetDatasetVersion;
    private String lineageDirection;
    private String transformSha256;
    private String transformationRef;

    // DQC rule and exact append-only result.
    private String targetDatasetField;
    private String ruleType;
    private String severity;
    private String expressionSha256;
    private BigDecimal thresholdValue;
    private String thresholdComparator;
    private String dqcRuleId;
    private Long dqcRuleVersion;
    private String dqcResultId;
    private Long taskRunObservationSequence;
    private String dqcResultStatus;
    private BigDecimal expectedValue;
    private BigDecimal actualValue;
    private Long evaluatedRows;
    private Long violationCount;
    private String evidenceRef;

    // Versioned governed metric definition.
    private String metricUnit;
    private String aggregationType;
    private String metricExpressionSha256;
    private String filterSha256;
    private String dimensionsSha256;
    private String semanticVersion;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class FieldDefinition {
        private String fieldCode;
        private String dataType;
        private Boolean nullable;
        private Boolean primaryKeyPart;
        private String semanticType;
        private String classification;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class TaskDependency {
        private String upstreamTaskId;
        private Long upstreamTaskVersion;
        private String dependencyType;
        private Boolean required;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class TaskSla {
        private String serviceLevelCode;
        private Integer deadlineMinuteUtc;
        private Long maximumDurationMillis;
        private Long maximumFreshnessMillis;
        private String approvedByPrincipalId;
    }
}
