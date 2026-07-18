package cn.iocoder.yudao.module.cloudmold.metadata.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.metadata.api.*;
import cn.iocoder.yudao.module.cloudmold.metadata.dal.dataobject.MetadataRecords.*;
import cn.iocoder.yudao.module.cloudmold.metadata.dal.mysql.MetadataStoreMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class MetadataCommandServiceImpl implements MetadataCommandApi, MetadataQueryApi {
    static final int OPERATION_SUCCEEDED = 10;
    private static final Pattern ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.-]{1,127}");
    private static final Pattern CODE = Pattern.compile("[A-Za-z][A-Za-z0-9_.-]{1,127}");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern SAFE_REF = Pattern.compile("(?:sha256:[0-9a-f]{64}|restricted:[A-Za-z0-9][A-Za-z0-9._/-]{7,159})");
    private static final Pattern QUALIFIED_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.$-]{1,255}");
    private static final Set<String> SOURCE_TYPES = Set.of("MYSQL", "STARROCKS", "FLINK", "OBJECT_STORAGE", "API", "OTHER");
    private static final Set<String> ENVIRONMENTS = Set.of("DEV", "TEST", "STAGING", "PROD");
    private static final Set<String> DATASET_TYPES = Set.of("TABLE", "VIEW", "REPORT_DATASET", "STREAM", "FILESET");
    private static final Set<String> TASK_TYPES = Set.of("BATCH_SQL", "STREAMING", "INGESTION", "QUALITY", "REPORT", "OTHER");
    private static final Set<String> DEPENDENCY_TYPES = Set.of("DATA", "CONTROL", "SLA");
    private static final Set<String> RUN_STATUSES = Set.of("SCHEDULED", "RUNNING", "SUCCEEDED", "FAILED", "CANCELLED");
    private static final Set<String> DQC_RULE_TYPES = Set.of("NOT_NULL", "UNIQUE", "ROW_COUNT", "RANGE", "FRESHNESS", "RECONCILIATION", "CUSTOM_HASHED");
    private static final Set<String> DQC_SEVERITIES = Set.of("INFO", "WARNING", "ERROR", "CRITICAL");
    private static final Set<String> DQC_RESULT_STATUSES = Set.of("PASS", "WARN", "FAIL", "ERROR");
    private static final Set<String> COMPARATORS = Set.of("EQ", "NE", "GT", "GTE", "LT", "LTE");
    private static final Set<String> AGGREGATIONS = Set.of("SUM", "COUNT", "COUNT_DISTINCT", "MIN", "MAX", "AVG", "RATIO", "SNAPSHOT");

    private final MetadataStoreMapper mapper;
    private final MetadataEventService eventService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MetadataView execute(MetadataCommand command) {
        validateCommon(command);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String requestHash = DigestUtil.sha256Hex(tenantId + "|" + JsonUtils.toJsonString(command));
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Instant occurredAt = command.getOccurredAt() == null ? now.toInstant(ZoneOffset.UTC) : command.getOccurredAt();
        require(!occurredAt.isAfter(Instant.now().plusSeconds(300)), "occurredAt cannot be materially in the future");
        String attemptToken = UUID.randomUUID().toString();
        mapper.insertOrResolveOperation(tenantId, command.getIdempotencyKey(), command.getOperation().name(),
                requestHash, attemptToken, now);
        Long operationId = mapper.selectLastInsertId();
        require(operationId != null && operationId > 0, "failed to resolve metadata operation");
        Operation operation = mapper.selectOperationForUpdate(operationId, tenantId);
        require(operation != null, "metadata operation disappeared");
        if (!attemptToken.equals(operation.getAttemptToken())) {
            require(Objects.equals(operation.getRequestHash(), requestHash),
                    "idempotency key conflicts with a different metadata payload");
            require(operation.getStatus() == OPERATION_SUCCEEDED && operation.getResultJson() != null,
                    "existing metadata operation is not complete");
            MetadataView replay = JsonUtils.parseObject(operation.getResultJson(), MetadataView.class);
            replay.setDuplicate(true);
            return replay;
        }

        MetadataView result = switch (command.getOperation()) {
            case PUBLISH_DATA_SOURCE -> publishDataSource(tenantId, operationId, command, occurredAt, now);
            case PUBLISH_DATASET -> publishDataset(tenantId, operationId, command, occurredAt, now);
            case PUBLISH_TASK -> publishTask(tenantId, operationId, command, occurredAt, now);
            case OBSERVE_TASK_RUN -> observeTaskRun(tenantId, operationId, command, occurredAt, now);
            case PUBLISH_LINEAGE -> publishLineage(tenantId, operationId, command, occurredAt, now);
            case PUBLISH_DQC_RULE -> publishDqcRule(tenantId, operationId, command, occurredAt, now);
            case RECORD_DQC_RESULT -> recordDqcResult(tenantId, operationId, command, occurredAt, now);
            case PUBLISH_METRIC -> publishMetric(tenantId, operationId, command, occurredAt, now);
        };
        String aggregateId = firstNonNull(result.getResultId(), result.getRunId(), result.getDefinitionId());
        require(mapper.markOperationSucceeded(operationId, tenantId, aggregateId, JsonUtils.toJsonString(result), now) == 1,
                "metadata operation completion conflict");
        return result;
    }

    @Override
    public MetadataView getDefinition(String definitionId) {
        requireId(definitionId, "definitionId");
        Definition definition = mapper.selectDefinition(TenantContextHolder.getRequiredTenantId(), definitionId);
        require(definition != null, "metadata definition does not exist");
        return definitionView(null, definition, false);
    }

    @Override
    @Transactional(readOnly = true)
    public MetadataDatasetReference validateDatasetVersion(String datasetId, Long datasetVersion,
                                                            String qualifiedName, String schemaSha256) {
        requireId(datasetId, "datasetId");
        require(datasetVersion != null && datasetVersion > 0, "datasetVersion must be positive");
        require(qualifiedName != null && QUALIFIED_NAME.matcher(qualifiedName).matches(),
                "qualifiedName must be a safe physical dataset name");
        requireSha256(schemaSha256, "schemaSha256");
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        DatasetVersion dataset = mapper.selectDatasetVersion(tenantId, datasetId, datasetVersion);
        require(dataset != null, "metadata dataset version does not exist in the same tenant");
        require(qualifiedName.equals(dataset.getQualifiedName()), "metadata dataset qualifiedName mismatch");
        require(schemaSha256.equals(dataset.getSchemaSha256()), "metadata dataset schemaSha256 mismatch");
        List<MetadataDatasetReference.FieldReference> fields = mapper
                .selectDatasetFields(tenantId, datasetId, datasetVersion).stream()
                .map(field -> MetadataDatasetReference.FieldReference.builder()
                        .ordinalPosition(field.getOrdinalPosition()).fieldCode(field.getFieldCode())
                        .dataType(field.getDataType()).nullable(field.getNullable())
                        .primaryKeyPart(field.getPrimaryKeyPart()).semanticType(field.getSemanticType())
                        .classification(field.getClassification()).build())
                .toList();
        require(!fields.isEmpty(), "metadata dataset version has no fields");
        return MetadataDatasetReference.builder().datasetId(dataset.getDefinitionId())
                .datasetVersion(dataset.getDefinitionVersion()).dataSourceId(dataset.getDataSourceId())
                .dataSourceVersion(dataset.getDataSourceVersion()).datasetType(dataset.getDatasetType())
                .qualifiedName(dataset.getQualifiedName()).layerCode(dataset.getLayerCode())
                .grainCode(dataset.getGrainCode()).schemaSha256(dataset.getSchemaSha256()).fields(fields).build();
    }

    @Override
    public MetadataView getTaskRun(String runId) {
        requireId(runId, "runId");
        TaskRunObservation run = mapper.selectLatestTaskRun(TenantContextHolder.getRequiredTenantId(), runId);
        require(run != null, "metadata task run does not exist");
        return taskRunView(null, run, false);
    }

    @Override
    public MetadataView getDqcResult(String resultId) {
        requireId(resultId, "resultId");
        DqcResult result = mapper.selectDqcResult(TenantContextHolder.getRequiredTenantId(), resultId);
        require(result != null, "metadata DQC result does not exist");
        return new MetadataView().setDuplicate(false).setResultId(result.getResultId())
                .setResultStatus(result.getStatus());
    }

    private MetadataView publishDataSource(Long tenantId, Long operationId, MetadataCommand command,
                                           Instant occurredAt, LocalDateTime now) {
        require(SOURCE_TYPES.contains(command.getSourceType()), "unsupported sourceType");
        require(ENVIRONMENTS.contains(command.getEnvironment()), "unsupported environment");
        requireSafeRef(command.getEndpointRef(), "endpointRef");
        requireSafeRef(command.getCredentialRef(), "credentialRef");
        requireSafeRef(command.getNamespaceRef(), "namespaceRef");
        Published published = beginPublish(tenantId, command, "DATA_SOURCE", now);
        DataSourceVersion value = new DataSourceVersion().setTenantId(tenantId)
                .setDefinitionId(published.definition().getDefinitionId())
                .setDefinitionVersion(published.version().getDefinitionVersion()).setSourceType(command.getSourceType())
                .setEnvironment(command.getEnvironment()).setEndpointRef(command.getEndpointRef())
                .setCredentialRef(command.getCredentialRef()).setNamespaceRef(command.getNamespaceRef());
        require(mapper.insertDataSourceVersion(value) == 1, "failed to persist immutable data-source version");
        completePublish(published, command, now);
        Map<String, Object> payload = ordered("source_type", value.getSourceType(), "environment", value.getEnvironment(),
                "endpoint_ref", value.getEndpointRef(), "credential_ref", value.getCredentialRef(),
                "namespace_ref", value.getNamespaceRef());
        appendPublished(operationId, published, command, occurredAt, now,
                "metadata.datasource.version_published", payload);
        return definitionView(operationId, published.definition(), false);
    }

    private MetadataView publishDataset(Long tenantId, Long operationId, MetadataCommand command,
                                        Instant occurredAt, LocalDateTime now) {
        requireDefinitionVersion(tenantId, command.getDataSourceId(), "DATA_SOURCE", command.getDataSourceVersion());
        require(DATASET_TYPES.contains(command.getDatasetType()), "unsupported datasetType");
        require(command.getQualifiedName() != null && QUALIFIED_NAME.matcher(command.getQualifiedName()).matches(),
                "qualifiedName must be a bounded physical name, not a connection string");
        requireCode(command.getLayerCode(), "layerCode");
        requireCode(command.getGrainCode(), "grainCode");
        requireSha256(command.getSchemaSha256(), "schemaSha256");
        requireSafeRef(command.getStorageLocationRef(), "storageLocationRef");
        require(command.getRetentionDays() != null && command.getRetentionDays() > 0
                && command.getRetentionDays() <= 36500, "retentionDays must be between 1 and 36500");
        require(command.getFields() != null && !command.getFields().isEmpty() && command.getFields().size() <= 2000,
                "dataset fields must contain between 1 and 2000 entries");
        Set<String> fieldCodes = new HashSet<>();
        for (MetadataCommand.FieldDefinition field : command.getFields()) {
            validateField(field);
            require(fieldCodes.add(field.getFieldCode()), "dataset fieldCode must be unique");
        }
        Published published = beginPublish(tenantId, command, "DATASET", now);
        DatasetVersion value = new DatasetVersion().setTenantId(tenantId)
                .setDefinitionId(published.definition().getDefinitionId())
                .setDefinitionVersion(published.version().getDefinitionVersion())
                .setDataSourceId(command.getDataSourceId()).setDataSourceVersion(command.getDataSourceVersion())
                .setDatasetType(command.getDatasetType()).setQualifiedName(command.getQualifiedName())
                .setLayerCode(command.getLayerCode()).setGrainCode(command.getGrainCode())
                .setSchemaSha256(command.getSchemaSha256()).setStorageLocationRef(command.getStorageLocationRef())
                .setRetentionDays(command.getRetentionDays());
        require(mapper.insertDatasetVersion(value) == 1, "failed to persist immutable dataset version");
        List<FieldVersion> fieldVersions = new ArrayList<>(command.getFields().size());
        int ordinal = 0;
        for (MetadataCommand.FieldDefinition field : command.getFields()) {
            FieldVersion record = new FieldVersion().setTenantId(tenantId).setDatasetId(value.getDefinitionId())
                    .setDatasetVersion(value.getDefinitionVersion()).setOrdinalPosition(++ordinal)
                    .setFieldCode(field.getFieldCode()).setDataType(field.getDataType()).setNullable(field.getNullable())
                    .setPrimaryKeyPart(field.getPrimaryKeyPart()).setSemanticType(field.getSemanticType())
                    .setClassification(field.getClassification());
            require(mapper.insertFieldVersion(record) == 1, "failed to persist immutable dataset field");
            fieldVersions.add(record);
        }
        completePublish(published, command, now);
        Map<String, Object> payload = ordered("data_source_id", value.getDataSourceId(),
                "data_source_version", value.getDataSourceVersion(), "dataset_type", value.getDatasetType(),
                "qualified_name", value.getQualifiedName(), "layer_code", value.getLayerCode(),
                "grain_code", value.getGrainCode(), "schema_sha256", value.getSchemaSha256(),
                "storage_location_ref", value.getStorageLocationRef(), "retention_days", value.getRetentionDays(),
                "field_count", command.getFields().size());
        appendPublished(operationId, published, command, occurredAt, now,
                "metadata.dataset.version_published", payload);
        fieldVersions.forEach(field -> eventService.appendDatasetField(field, command, occurredAt));
        return definitionView(operationId, published.definition(), false);
    }

    private MetadataView publishTask(Long tenantId, Long operationId, MetadataCommand command,
                                     Instant occurredAt, LocalDateTime now) {
        long graphRevision = lockGraph(tenantId, "TASK_DEPENDENCY", now);
        require(TASK_TYPES.contains(command.getTaskType()), "unsupported taskType");
        requireSafeRef(command.getExecutableArtifactRef(), "executableArtifactRef");
        requireSha256(command.getCodeSha256(), "codeSha256");
        requireSha256(command.getScheduleSha256(), "scheduleSha256");
        requireSafeRef(command.getResourceGroupRef(), "resourceGroupRef");
        List<MetadataCommand.TaskDependency> dependencies = command.getDependencies() == null
                ? List.of() : command.getDependencies();
        require(dependencies.size() <= 500, "task dependencies cannot exceed 500 direct edges");
        Set<String> dependencyKeys = new HashSet<>();
        for (MetadataCommand.TaskDependency dependency : dependencies) {
            require(dependency != null, "task dependency is required");
            requireId(dependency.getUpstreamTaskId(), "upstreamTaskId");
            require(!dependency.getUpstreamTaskId().equals(command.getDefinitionId()), "task self-dependency is forbidden");
            requireDefinitionVersion(tenantId, dependency.getUpstreamTaskId(), "TASK", dependency.getUpstreamTaskVersion());
            require(DEPENDENCY_TYPES.contains(dependency.getDependencyType()), "unsupported dependencyType");
            require(dependency.getRequired() != null, "required dependency flag is required");
            require(dependencyKeys.add(dependency.getUpstreamTaskId() + "|" + dependency.getDependencyType()),
                    "duplicate direct task dependency");
            require(mapper.countTaskDependencyPath(tenantId, dependency.getUpstreamTaskId(),
                    dependency.getUpstreamTaskVersion(), command.getDefinitionId()) == 0,
                    "task dependency would create an indirect version-bound cycle");
        }
        validateSla(command.getSla());
        Published published = beginPublish(tenantId, command, "TASK", now);
        TaskVersion value = new TaskVersion().setTenantId(tenantId)
                .setDefinitionId(published.definition().getDefinitionId())
                .setDefinitionVersion(published.version().getDefinitionVersion()).setTaskType(command.getTaskType())
                .setExecutableArtifactRef(command.getExecutableArtifactRef()).setCodeSha256(command.getCodeSha256())
                .setScheduleSha256(command.getScheduleSha256()).setResourceGroupRef(command.getResourceGroupRef());
        require(mapper.insertTaskVersion(value) == 1, "failed to persist immutable task version");
        List<TaskDependency> dependencyRecords = new ArrayList<>(dependencies.size());
        int sequence = 0;
        for (MetadataCommand.TaskDependency dependency : dependencies) {
            TaskDependency record = new TaskDependency().setTenantId(tenantId).setTaskId(value.getDefinitionId())
                    .setTaskVersion(value.getDefinitionVersion()).setDependencySequence(++sequence)
                    .setUpstreamTaskId(dependency.getUpstreamTaskId())
                    .setUpstreamTaskVersion(dependency.getUpstreamTaskVersion())
                    .setDependencyType(dependency.getDependencyType()).setRequired(dependency.getRequired());
            require(mapper.insertTaskDependency(record) == 1, "failed to persist immutable task dependency");
            dependencyRecords.add(record);
        }
        MetadataCommand.TaskSla sla = command.getSla();
        TaskSla slaRecord = new TaskSla().setTenantId(tenantId).setTaskId(value.getDefinitionId())
                .setTaskVersion(value.getDefinitionVersion()).setServiceLevelCode(sla.getServiceLevelCode())
                .setDeadlineMinuteUtc(sla.getDeadlineMinuteUtc())
                .setMaximumDurationMillis(sla.getMaximumDurationMillis())
                .setMaximumFreshnessMillis(sla.getMaximumFreshnessMillis())
                .setApprovedByPrincipalId(sla.getApprovedByPrincipalId());
        require(mapper.insertTaskSla(slaRecord) == 1, "failed to persist immutable task SLA");
        require(mapper.advanceGraphRevision(tenantId, "TASK_DEPENDENCY", graphRevision, now) == 1,
                "task dependency graph revision conflict");
        completePublish(published, command, now);
        Map<String, Object> payload = ordered("task_type", value.getTaskType(),
                "executable_artifact_ref", value.getExecutableArtifactRef(), "code_sha256", value.getCodeSha256(),
                "schedule_sha256", value.getScheduleSha256(), "resource_group_ref", value.getResourceGroupRef(),
                "dependency_count", dependencies.size(), "service_level_code", sla.getServiceLevelCode(),
                "deadline_minute_utc", sla.getDeadlineMinuteUtc(),
                "maximum_duration_millis", sla.getMaximumDurationMillis(),
                "maximum_freshness_millis", sla.getMaximumFreshnessMillis(),
                "sla_approved_by_principal_id", sla.getApprovedByPrincipalId());
        appendPublished(operationId, published, command, occurredAt, now,
                "metadata.task.version_published", payload);
        dependencyRecords.forEach(dependency -> eventService.appendTaskDependency(dependency, command, occurredAt));
        return definitionView(operationId, published.definition(), false);
    }

    private MetadataView observeTaskRun(Long tenantId, Long operationId, MetadataCommand command,
                                        Instant occurredAt, LocalDateTime now) {
        requireId(command.getTaskRunId(), "taskRunId");
        requireDefinitionVersion(tenantId, command.getTaskId(), "TASK", command.getTaskVersion());
        require(command.getAttempt() != null && command.getAttempt() > 0, "attempt must be positive");
        require(command.getExpectedObservationSequence() != null && command.getExpectedObservationSequence() >= 0,
                "expectedObservationSequence is required");
        require(RUN_STATUSES.contains(command.getRunStatus()), "unsupported runStatus");
        validateRunMeasurements(command);
        TaskRunObservation previous = mapper.selectLatestTaskRunForUpdate(tenantId, command.getTaskRunId());
        long previousSequence = previous == null ? 0 : previous.getObservationSequence();
        require(previousSequence == command.getExpectedObservationSequence(), "task-run observation sequence conflict");
        if (previous == null) {
            require(Set.of("SCHEDULED", "RUNNING").contains(command.getRunStatus()),
                    "first task-run observation must be SCHEDULED or RUNNING");
            require(command.getAttempt() == 1, "first task-run attempt must be 1");
        } else {
            require(previous.getTaskId().equals(command.getTaskId())
                    && previous.getTaskVersion().equals(command.getTaskVersion()),
                    "task-run identity and task version are immutable");
            validateRunTransition(previous, command);
        }
        TaskRunObservation value = new TaskRunObservation().setTenantId(tenantId).setRunId(command.getTaskRunId())
                .setTaskId(command.getTaskId()).setTaskVersion(command.getTaskVersion()).setAttempt(command.getAttempt())
                .setObservationSequence(previousSequence + 1).setStatus(command.getRunStatus())
                .setScheduledAt(utc(command.getScheduledAt())).setStartedAt(utc(command.getStartedAt()))
                .setFinishedAt(utc(command.getFinishedAt())).setDurationMillis(command.getDurationMillis())
                .setComputeCostMinor(command.getComputeCostMinor()).setCostCurrency(command.getCostCurrency())
                .setResourceMillis(command.getResourceMillis()).setRowsRead(command.getRowsRead())
                .setRowsWritten(command.getRowsWritten()).setSourceCheckpointRef(command.getSourceCheckpointRef())
                .setOutputSnapshotRef(command.getOutputSnapshotRef()).setErrorRef(command.getErrorRef())
                .setObservedAt(LocalDateTime.ofInstant(occurredAt, ZoneOffset.UTC)).setOperationId(operationId);
        require(mapper.insertTaskRunObservation(value) == 1, "failed to append task-run observation");
        eventService.appendTaskRun(operationId, value, previous == null ? null : previous.getStatus(), command,
                occurredAt, now);
        return taskRunView(operationId, value, false);
    }

    private MetadataView publishLineage(Long tenantId, Long operationId, MetadataCommand command,
                                        Instant occurredAt, LocalDateTime now) {
        long graphRevision = lockGraph(tenantId, "LINEAGE", now);
        requireDefinitionVersion(tenantId, command.getSourceDatasetId(), "DATASET", command.getSourceDatasetVersion());
        requireDefinitionVersion(tenantId, command.getTargetDatasetId(), "DATASET", command.getTargetDatasetVersion());
        require(!command.getSourceDatasetId().equals(command.getTargetDatasetId()), "lineage self-loop is forbidden");
        require("SOURCE_TO_TARGET".equals(command.getLineageDirection()),
                "lineageDirection must be SOURCE_TO_TARGET");
        requireSha256(command.getTransformSha256(), "transformSha256");
        requireSafeRef(command.getTransformationRef(), "transformationRef");
        require(mapper.countLineagePath(tenantId, command.getTargetDatasetId(), command.getTargetDatasetVersion(),
                command.getSourceDatasetId(), command.getSourceDatasetVersion()) == 0,
                "lineage edge would create an indirect version-bound cycle");
        Published published = beginPublish(tenantId, command, "LINEAGE", now);
        LineageVersion value = new LineageVersion().setTenantId(tenantId)
                .setDefinitionId(published.definition().getDefinitionId())
                .setDefinitionVersion(published.version().getDefinitionVersion())
                .setSourceDatasetId(command.getSourceDatasetId()).setSourceDatasetVersion(command.getSourceDatasetVersion())
                .setTargetDatasetId(command.getTargetDatasetId()).setTargetDatasetVersion(command.getTargetDatasetVersion())
                .setDirection(command.getLineageDirection()).setTransformSha256(command.getTransformSha256())
                .setTransformationRef(command.getTransformationRef());
        require(mapper.insertLineageVersion(value) == 1, "failed to persist immutable lineage version");
        require(mapper.advanceGraphRevision(tenantId, "LINEAGE", graphRevision, now) == 1,
                "lineage graph revision conflict");
        completePublish(published, command, now);
        Map<String, Object> payload = ordered("source_dataset_id", value.getSourceDatasetId(),
                "source_dataset_version", value.getSourceDatasetVersion(), "target_dataset_id", value.getTargetDatasetId(),
                "target_dataset_version", value.getTargetDatasetVersion(), "direction", value.getDirection(),
                "transform_sha256", value.getTransformSha256(), "transformation_ref", value.getTransformationRef());
        appendPublished(operationId, published, command, occurredAt, now,
                "metadata.lineage.version_published", payload);
        return definitionView(operationId, published.definition(), false);
    }

    private MetadataView publishDqcRule(Long tenantId, Long operationId, MetadataCommand command,
                                        Instant occurredAt, LocalDateTime now) {
        requireDefinitionVersion(tenantId, command.getTargetDatasetId(), "DATASET", command.getTargetDatasetVersion());
        if (command.getTargetDatasetField() != null) {
            requireCode(command.getTargetDatasetField(), "targetDatasetField");
            require(mapper.countFieldVersion(tenantId, command.getTargetDatasetId(),
                    command.getTargetDatasetVersion(), command.getTargetDatasetField()) == 1,
                    "targetDatasetField does not exist on the exact dataset version");
        }
        require(DQC_RULE_TYPES.contains(command.getRuleType()), "unsupported ruleType");
        require(DQC_SEVERITIES.contains(command.getSeverity()), "unsupported severity");
        requireSha256(command.getExpressionSha256(), "expressionSha256");
        require(command.getThresholdValue() != null, "thresholdValue is required");
        require(COMPARATORS.contains(command.getThresholdComparator()), "unsupported thresholdComparator");
        Published published = beginPublish(tenantId, command, "DQC_RULE", now);
        DqcRuleVersion value = new DqcRuleVersion().setTenantId(tenantId)
                .setDefinitionId(published.definition().getDefinitionId())
                .setDefinitionVersion(published.version().getDefinitionVersion())
                .setDatasetId(command.getTargetDatasetId()).setDatasetVersion(command.getTargetDatasetVersion())
                .setDatasetField(command.getTargetDatasetField()).setRuleType(command.getRuleType())
                .setSeverity(command.getSeverity()).setExpressionSha256(command.getExpressionSha256())
                .setThresholdValue(command.getThresholdValue()).setThresholdComparator(command.getThresholdComparator());
        require(mapper.insertDqcRuleVersion(value) == 1, "failed to persist immutable DQC rule version");
        completePublish(published, command, now);
        Map<String, Object> payload = ordered("dataset_id", value.getDatasetId(),
                "dataset_version", value.getDatasetVersion(), "dataset_field", value.getDatasetField(),
                "rule_type", value.getRuleType(), "severity", value.getSeverity(),
                "expression_sha256", value.getExpressionSha256(),
                "threshold_value", value.getThresholdValue().toPlainString(),
                "threshold_comparator", value.getThresholdComparator());
        appendPublished(operationId, published, command, occurredAt, now,
                "metadata.dqc_rule.version_published", payload);
        return definitionView(operationId, published.definition(), false);
    }

    private MetadataView recordDqcResult(Long tenantId, Long operationId, MetadataCommand command,
                                         Instant occurredAt, LocalDateTime now) {
        requireId(command.getDqcResultId(), "dqcResultId");
        requireDefinitionVersion(tenantId, command.getDqcRuleId(), "DQC_RULE", command.getDqcRuleVersion());
        requireDefinitionVersion(tenantId, command.getTargetDatasetId(), "DATASET", command.getTargetDatasetVersion());
        require(mapper.countDqcRuleTarget(tenantId, command.getDqcRuleId(), command.getDqcRuleVersion(),
                command.getTargetDatasetId(), command.getTargetDatasetVersion()) == 1,
                "DQC result dataset must match the exact DQC rule target version");
        if (command.getTaskRunId() != null) {
            require(command.getTaskRunObservationSequence() != null && command.getTaskRunObservationSequence() > 0,
                    "DQC taskRunObservationSequence is required with taskRunId");
            require(mapper.selectTaskRunObservation(tenantId, command.getTaskRunId(),
                    command.getTaskRunObservationSequence()) != null,
                    "DQC result must reference an exact tenant-scoped task-run observation");
        } else {
            require(command.getTaskRunObservationSequence() == null,
                    "taskRunObservationSequence cannot be set without taskRunId");
        }
        require(DQC_RESULT_STATUSES.contains(command.getDqcResultStatus()), "unsupported DQC result status");
        require(command.getExpectedValue() != null && command.getActualValue() != null,
                "DQC result requires exact expectedValue and actualValue");
        requireNonNegative(command.getEvaluatedRows(), "evaluatedRows");
        requireNonNegative(command.getViolationCount(), "violationCount");
        require(command.getViolationCount() <= command.getEvaluatedRows(),
                "violationCount cannot exceed evaluatedRows");
        requireSafeRef(command.getEvidenceRef(), "evidenceRef");
        if ("PASS".equals(command.getDqcResultStatus())) require(command.getViolationCount() == 0,
                "PASS DQC result must have zero violations");
        if (Set.of("WARN", "FAIL").contains(command.getDqcResultStatus())) require(command.getViolationCount() > 0,
                "WARN or FAIL DQC result must have at least one violation");
        DqcResult value = new DqcResult().setTenantId(tenantId).setResultId(command.getDqcResultId())
                .setDqcRuleId(command.getDqcRuleId()).setDqcRuleVersion(command.getDqcRuleVersion())
                .setDatasetId(command.getTargetDatasetId()).setDatasetVersion(command.getTargetDatasetVersion())
                .setTaskRunId(command.getTaskRunId())
                .setTaskRunObservationSequence(command.getTaskRunObservationSequence())
                .setStatus(command.getDqcResultStatus())
                .setExpectedValue(command.getExpectedValue()).setActualValue(command.getActualValue())
                .setEvaluatedRows(command.getEvaluatedRows()).setViolationCount(command.getViolationCount())
                .setEvidenceRef(command.getEvidenceRef()).setObservedAt(LocalDateTime.ofInstant(occurredAt, ZoneOffset.UTC))
                .setOperationId(operationId);
        require(mapper.insertDqcResult(value) == 1, "failed to append exact DQC result");
        eventService.appendDqcResult(value, command, occurredAt);
        return new MetadataView().setOperationId(operationId).setDuplicate(false).setResultId(value.getResultId())
                .setResultStatus(value.getStatus());
    }

    private MetadataView publishMetric(Long tenantId, Long operationId, MetadataCommand command,
                                       Instant occurredAt, LocalDateTime now) {
        requireCode(command.getGrainCode(), "grainCode");
        requireCode(command.getMetricUnit(), "metricUnit");
        require(AGGREGATIONS.contains(command.getAggregationType()), "unsupported aggregationType");
        requireSha256(command.getMetricExpressionSha256(), "metricExpressionSha256");
        requireSha256(command.getFilterSha256(), "filterSha256");
        requireSha256(command.getDimensionsSha256(), "dimensionsSha256");
        require(command.getSemanticVersion() != null
                && command.getSemanticVersion().matches("[1-9][0-9]{0,8}\\.[0-9]{1,9}\\.[0-9]{1,9}"),
                "semanticVersion must be a bounded major.minor.patch version");
        Published published = beginPublish(tenantId, command, "METRIC", now);
        MetricVersion value = new MetricVersion().setTenantId(tenantId)
                .setDefinitionId(published.definition().getDefinitionId())
                .setDefinitionVersion(published.version().getDefinitionVersion()).setGrainCode(command.getGrainCode())
                .setMetricUnit(command.getMetricUnit()).setAggregationType(command.getAggregationType())
                .setExpressionSha256(command.getMetricExpressionSha256()).setFilterSha256(command.getFilterSha256())
                .setDimensionsSha256(command.getDimensionsSha256()).setSemanticVersion(command.getSemanticVersion());
        require(mapper.insertMetricVersion(value) == 1, "failed to persist immutable metric version");
        completePublish(published, command, now);
        Map<String, Object> payload = ordered("grain_code", value.getGrainCode(), "metric_unit", value.getMetricUnit(),
                "aggregation_type", value.getAggregationType(), "expression_sha256", value.getExpressionSha256(),
                "filter_sha256", value.getFilterSha256(), "dimensions_sha256", value.getDimensionsSha256(),
                "semantic_version", value.getSemanticVersion());
        appendPublished(operationId, published, command, occurredAt, now,
                "metadata.metric.version_published", payload);
        return definitionView(operationId, published.definition(), false);
    }

    private Published beginPublish(Long tenantId, MetadataCommand command, String kind, LocalDateTime now) {
        requireId(command.getDefinitionId(), "definitionId");
        requireCode(command.getDefinitionCode(), "definitionCode");
        require(command.getDisplayName() != null && command.getDisplayName().length() >= 3
                && command.getDisplayName().length() <= 160, "displayName must be between 3 and 160 characters");
        requireExpectedVersion(command);
        requireId(command.getOwnerPrincipalId(), "ownerPrincipalId");
        requireSha256(command.getSpecificationSha256(), "specificationSha256");
        requireSafeRef(command.getArtifactRef(), "artifactRef");
        Definition definition = mapper.selectDefinitionForUpdate(tenantId, command.getDefinitionId());
        String previousStatus;
        if (definition == null) {
            require(command.getExpectedVersion() == 0, "first definition version requires expectedVersion 0");
            definition = new Definition().setDefinitionId(command.getDefinitionId()).setTenantId(tenantId)
                    .setDefinitionKind(kind).setDefinitionCode(command.getDefinitionCode())
                    .setDisplayName(command.getDisplayName()).setStatus("DRAFT").setCurrentVersion(0L)
                    .setOwnerPrincipalId(command.getOwnerPrincipalId()).setCreatedAt(now).setUpdatedAt(now);
            require(mapper.insertDefinition(definition) == 1, "failed to create metadata definition");
            previousStatus = "DRAFT";
        } else {
            require(kind.equals(definition.getDefinitionKind()), "definition kind is immutable");
            require(command.getDefinitionCode().equals(definition.getDefinitionCode()), "definitionCode is immutable");
            require(Objects.equals(definition.getCurrentVersion(), command.getExpectedVersion()),
                    "metadata definition version conflict");
            previousStatus = definition.getStatus();
        }
        long nextVersion = command.getExpectedVersion() + 1;
        DefinitionVersion version = new DefinitionVersion().setTenantId(tenantId)
                .setDefinitionId(definition.getDefinitionId()).setDefinitionVersion(nextVersion)
                .setSpecificationSha256(command.getSpecificationSha256()).setArtifactRef(command.getArtifactRef())
                .setPublishedByPrincipalId(command.getOwnerPrincipalId()).setPublishedAt(now);
        require(mapper.insertDefinitionVersion(version) == 1, "failed to persist immutable definition version");
        return new Published(definition, version, previousStatus);
    }

    private void completePublish(Published published, MetadataCommand command, LocalDateTime now) {
        Definition definition = published.definition();
        require(mapper.publishDefinition(definition.getTenantId(), definition.getDefinitionId(),
                definition.getDefinitionKind(), command.getExpectedVersion(), command.getDisplayName(),
                command.getOwnerPrincipalId(), now) == 1, "metadata definition publish conflict");
        definition.setDisplayName(command.getDisplayName()).setStatus("PUBLISHED")
                .setCurrentVersion(published.version().getDefinitionVersion())
                .setOwnerPrincipalId(command.getOwnerPrincipalId()).setUpdatedAt(now);
    }

    private void appendPublished(Long operationId, Published published, MetadataCommand command, Instant occurredAt,
                                 LocalDateTime now, String eventType, Map<String, Object> payload) {
        eventService.appendDefinition(operationId, published.definition(), published.version(),
                published.previousStatus(), eventType, command, occurredAt, now, payload);
    }

    private void requireDefinitionVersion(Long tenantId, String id, String kind, Long version) {
        requireId(id, kind.toLowerCase(Locale.ROOT) + "Id");
        require(version != null && version > 0, kind.toLowerCase(Locale.ROOT) + "Version must be positive");
        require(mapper.countDefinitionVersion(tenantId, id, kind, version) == 1,
                "referenced " + kind + " version does not exist in the same tenant");
    }

    private long lockGraph(Long tenantId, String graphType, LocalDateTime now) {
        mapper.ensureGraphGuard(tenantId, graphType, now);
        Long revision = mapper.selectGraphRevisionForUpdate(tenantId, graphType);
        require(revision != null && revision >= 0, "metadata graph guard disappeared");
        return revision;
    }

    private static void validateField(MetadataCommand.FieldDefinition field) {
        require(field != null, "dataset field is required");
        requireCode(field.getFieldCode(), "fieldCode");
        requireCode(field.getDataType(), "dataType");
        require(field.getNullable() != null, "field nullable flag is required");
        require(field.getPrimaryKeyPart() != null, "field primaryKeyPart flag is required");
        requireCode(field.getSemanticType(), "semanticType");
        require(Set.of("PUBLIC", "INTERNAL", "CONFIDENTIAL", "RESTRICTED").contains(field.getClassification()),
                "unsupported field classification");
    }

    private static void validateSla(MetadataCommand.TaskSla sla) {
        require(sla != null, "task SLA is required");
        requireCode(sla.getServiceLevelCode(), "serviceLevelCode");
        require(sla.getDeadlineMinuteUtc() != null && sla.getDeadlineMinuteUtc() >= 0
                && sla.getDeadlineMinuteUtc() < 1440, "deadlineMinuteUtc must be between 0 and 1439");
        requirePositive(sla.getMaximumDurationMillis(), "maximumDurationMillis");
        requirePositive(sla.getMaximumFreshnessMillis(), "maximumFreshnessMillis");
        requireId(sla.getApprovedByPrincipalId(), "approvedByPrincipalId");
    }

    private static void validateRunMeasurements(MetadataCommand command) {
        require(command.getScheduledAt() != null, "scheduledAt is required");
        if (command.getStartedAt() != null) require(!command.getStartedAt().isBefore(command.getScheduledAt()),
                "startedAt cannot precede scheduledAt");
        if (command.getFinishedAt() != null) {
            require(command.getStartedAt() != null && !command.getFinishedAt().isBefore(command.getStartedAt()),
                    "finishedAt requires and cannot precede startedAt");
            require(command.getDurationMillis() != null
                            && Duration.between(command.getStartedAt(), command.getFinishedAt()).toMillis()
                            == command.getDurationMillis(),
                    "durationMillis must exactly match startedAt and finishedAt");
        }
        requireNonNegative(command.getDurationMillis(), "durationMillis");
        requireNonNegative(command.getComputeCostMinor(), "computeCostMinor");
        requireIsoCurrency(command.getCostCurrency(), "costCurrency");
        requireNonNegative(command.getResourceMillis(), "resourceMillis");
        requireNonNegative(command.getRowsRead(), "rowsRead");
        requireNonNegative(command.getRowsWritten(), "rowsWritten");
        optionalSafeRef(command.getSourceCheckpointRef(), "sourceCheckpointRef");
        optionalSafeRef(command.getOutputSnapshotRef(), "outputSnapshotRef");
        optionalSafeRef(command.getErrorRef(), "errorRef");
        if (Set.of("SUCCEEDED", "FAILED", "CANCELLED").contains(command.getRunStatus())) {
            require(command.getFinishedAt() != null, "terminal task run requires finishedAt");
        }
        if ("SUCCEEDED".equals(command.getRunStatus())) {
            require(command.getOutputSnapshotRef() != null, "successful task run requires outputSnapshotRef");
            require(command.getErrorRef() == null, "successful task run cannot carry errorRef");
        }
        if ("FAILED".equals(command.getRunStatus())) require(command.getErrorRef() != null,
                "failed task run requires opaque errorRef");
    }

    private static void validateRunTransition(TaskRunObservation previous, MetadataCommand command) {
        String before = previous.getStatus();
        String after = command.getRunStatus();
        if (Set.of("SUCCEEDED", "FAILED", "CANCELLED").contains(before)) {
            require("SCHEDULED".equals(after) && command.getAttempt() == previous.getAttempt() + 1,
                    "terminal task run can only begin the next scheduled attempt");
        } else {
            require(command.getAttempt().equals(previous.getAttempt()), "attempt is immutable within an active attempt");
            boolean valid = ("SCHEDULED".equals(before) && Set.of("RUNNING", "CANCELLED").contains(after))
                    || ("RUNNING".equals(before) && Set.of("SUCCEEDED", "FAILED", "CANCELLED").contains(after));
            require(valid, "invalid task-run status transition from " + before + " to " + after);
        }
    }

    private static void validateCommon(MetadataCommand command) {
        require(command != null, "metadata command is required");
        require(command.getOperation() != null, "metadata operation is required");
        require(command.getIdempotencyKey() != null && ID.matcher(command.getIdempotencyKey()).matches(),
                "idempotencyKey must be a stable opaque identifier");
        if (command.getRunTraceId() != null) requireId(command.getRunTraceId(), "runTraceId");
    }

    private static MetadataView definitionView(Long operationId, Definition definition, boolean duplicate) {
        return new MetadataView().setOperationId(operationId).setDuplicate(duplicate)
                .setDefinitionId(definition.getDefinitionId()).setDefinitionKind(definition.getDefinitionKind())
                .setDefinitionVersion(definition.getCurrentVersion()).setDefinitionStatus(definition.getStatus());
    }

    private static MetadataView taskRunView(Long operationId, TaskRunObservation run, boolean duplicate) {
        return new MetadataView().setOperationId(operationId).setDuplicate(duplicate).setRunId(run.getRunId())
                .setAttempt(run.getAttempt()).setObservationSequence(run.getObservationSequence())
                .setRunStatus(run.getStatus());
    }

    private static void requireExpectedVersion(MetadataCommand command) {
        require(command.getExpectedVersion() != null && command.getExpectedVersion() >= 0,
                "expectedVersion is required");
    }

    private static void requireId(String value, String field) {
        require(value != null && ID.matcher(value).matches(), field + " must be a bounded opaque identifier");
    }

    private static void requireCode(String value, String field) {
        require(value != null && CODE.matcher(value).matches(), field + " must be a bounded code");
    }

    private static void requireSha256(String value, String field) {
        require(value != null && SHA256.matcher(value).matches(), field + " must be lowercase SHA-256");
    }

    private static void requireIsoCurrency(String value, String field) {
        require(value != null && value.matches("[A-Z]{3}"),
                field + " must be an uppercase ISO-4217 code");
        try {
            Currency.getInstance(value);
        } catch (IllegalArgumentException ignored) {
            throw new IllegalArgumentException(field + " must be a recognized ISO-4217 code");
        }
    }

    private static void requireSafeRef(String value, String field) {
        require(value != null && SAFE_REF.matcher(value).matches(),
                field + " must be an opaque restricted: reference or sha256: digest");
    }

    private static void optionalSafeRef(String value, String field) {
        if (value != null) requireSafeRef(value, field);
    }

    private static void requireNonNegative(Long value, String field) {
        require(value != null && value >= 0, field + " must be non-negative");
    }

    private static void requirePositive(Long value, String field) {
        require(value != null && value > 0, field + " must be positive");
    }

    private static LocalDateTime utc(Instant value) {
        return value == null ? null : LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static String firstNonNull(String... values) {
        return Arrays.stream(values).filter(Objects::nonNull).findFirst().orElseThrow();
    }

    private static Map<String, Object> ordered(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) result.put((String) pairs[i], pairs[i + 1]);
        return result;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    private record Published(Definition definition, DefinitionVersion version, String previousStatus) {
    }
}
