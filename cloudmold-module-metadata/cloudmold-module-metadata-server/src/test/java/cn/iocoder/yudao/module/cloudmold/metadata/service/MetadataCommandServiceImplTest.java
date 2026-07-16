package cn.iocoder.yudao.module.cloudmold.metadata.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.metadata.api.*;
import cn.iocoder.yudao.module.cloudmold.metadata.dal.dataobject.MetadataRecords.*;
import cn.iocoder.yudao.module.cloudmold.metadata.dal.mysql.MetadataStoreMapper;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MetadataCommandServiceImplTest {
    private final MetadataStoreMapper mapper = mock(MetadataStoreMapper.class);
    private final MetadataEventService eventService = mock(MetadataEventService.class);
    private final MetadataCommandServiceImpl service = new MetadataCommandServiceImpl(mapper, eventService);
    private final Map<String, Operation> operations = new HashMap<>();
    private final Map<String, Definition> definitions = new HashMap<>();
    private final Set<String> versions = new HashSet<>();
    private final Set<String> fields = new HashSet<>();
    private final Map<String, TaskRunObservation> runObservations = new HashMap<>();
    private final Map<String, DqcResult> dqcResults = new HashMap<>();
    private final AtomicLong operationSequence = new AtomicLong();
    private final AtomicReference<Long> lastOperationId = new AtomicReference<>();
    private final Map<String, Long> graphRevisions = new HashMap<>();

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        wireOperationStore();
        wirePersistence();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldPublishVersionedDataSourceAndDatasetIdempotentlyWithoutRawConnections() {
        assertThatThrownBy(() -> service.execute(dataSource("source-raw", "ds-raw")
                .endpointRef("mysql://host/database").build()))
                .hasMessageContaining("opaque restricted");

        MetadataCommand source = dataSource("source-publish-01", "datasource-main").build();
        MetadataView sourceFirst = service.execute(source);
        MetadataView sourceReplay = service.execute(source);
        assertThat(sourceFirst.getDefinitionVersion()).isEqualTo(1L);
        assertThat(sourceReplay.getDuplicate()).isTrue();
        verify(mapper, times(1)).insertDataSourceVersion(any(DataSourceVersion.class));

        MetadataCommand dataset = dataset("dataset-publish-01", "dataset-orders", "datasource-main").build();
        MetadataView datasetFirst = service.execute(dataset);
        assertThat(datasetFirst.getDefinitionKind()).isEqualTo("DATASET");
        assertThat(datasetFirst.getDefinitionVersion()).isEqualTo(1L);
        verify(mapper, times(2)).insertFieldVersion(any(FieldVersion.class));

        assertThatThrownBy(() -> service.execute(dataset("dataset-stale-01", "dataset-orders", "datasource-main")
                .expectedVersion(0L).build())).hasMessage("metadata definition version conflict");

        TenantContextHolder.setTenantId(2L);
        assertThatThrownBy(() -> service.getDefinition("dataset-orders"))
                .hasMessage("metadata definition does not exist");
    }

    @Test
    void shouldRejectIndirectTaskCyclesAndAppendExactRunObservations() {
        service.execute(task("task-upstream-publish", "task-upstream", List.of()).build());
        service.execute(task("task-downstream-publish", "task-downstream", List.of(
                dependency("task-upstream", 1L))).build());
        when(mapper.countTaskDependencyPath(1L, "task-downstream", 1L, "task-upstream")).thenReturn(1);
        assertThatThrownBy(() -> service.execute(task("task-cycle-publish", "task-upstream", List.of(
                dependency("task-downstream", 1L))).expectedVersion(1L).build()))
                .hasMessage("task dependency would create an indirect version-bound cycle");

        Instant scheduled = Instant.parse("2026-07-16T00:00:00Z");
        MetadataView running = service.execute(run("run-running-01", "task-run-01", 0L, "RUNNING")
                .scheduledAt(scheduled).startedAt(scheduled.plusSeconds(5)).build());
        MetadataView succeeded = service.execute(run("run-success-01", "task-run-01", 1L, "SUCCEEDED")
                .scheduledAt(scheduled).startedAt(scheduled.plusSeconds(5)).finishedAt(scheduled.plusSeconds(65))
                .durationMillis(60_000L).outputSnapshotRef(ref("task-output-01")).build());
        assertThat(running.getObservationSequence()).isEqualTo(1L);
        assertThat(succeeded.getObservationSequence()).isEqualTo(2L);
        assertThat(succeeded.getRunStatus()).isEqualTo("SUCCEEDED");

        assertThatThrownBy(() -> service.execute(run("run-stale-01", "task-run-01", 0L, "FAILED")
                .scheduledAt(scheduled).startedAt(scheduled.plusSeconds(5)).finishedAt(scheduled.plusSeconds(60))
                .durationMillis(55_000L).errorRef(ref("task-error-01")).build()))
                .hasMessage("task-run observation sequence conflict");
        assertThatThrownBy(() -> service.execute(run("run-currency-01", "task-run-currency", 0L, "RUNNING")
                .scheduledAt(scheduled).startedAt(scheduled).costCurrency("usd").build()))
                .hasMessageContaining("ISO-4217");
        assertThatThrownBy(() -> service.execute(run("run-currency-02", "task-run-currency", 0L, "RUNNING")
                .scheduledAt(scheduled).startedAt(scheduled).costCurrency("ZZZ").build()))
                .hasMessageContaining("recognized ISO-4217");
    }

    @Test
    void shouldBindDqcRulesToExactFieldsAndResultsToExactRunObservations() {
        service.execute(dataSource("dqc-source", "dqc-source-def").build());
        service.execute(dataset("dqc-dataset", "dqc-dataset-def", "dqc-source-def").build());
        service.execute(task("dqc-task", "dqc-task-def", List.of()).build());
        Instant scheduled = Instant.parse("2026-07-16T00:00:00Z");
        service.execute(run("dqc-run", "dqc-task-run", 0L, "RUNNING")
                .taskId("dqc-task-def").scheduledAt(scheduled).startedAt(scheduled).build());

        assertThatThrownBy(() -> service.execute(dqcRule("dqc-rule-missing-field", "dqc-rule-def")
                .targetDatasetField("missing_field").build()))
                .hasMessage("targetDatasetField does not exist on the exact dataset version");
        MetadataView rule = service.execute(dqcRule("dqc-rule-publish", "dqc-rule-def")
                .targetDatasetField("order_id").build());
        assertThat(rule.getDefinitionVersion()).isEqualTo(1L);

        MetadataCommand result = dqcResult("dqc-result-record", "dqc-result-01")
                .taskRunId("dqc-task-run").taskRunObservationSequence(1L).build();
        MetadataView recorded = service.execute(result);
        assertThat(recorded.getResultStatus()).isEqualTo("PASS");
        verify(mapper).selectTaskRunObservation(1L, "dqc-task-run", 1L);

        assertThatThrownBy(() -> service.execute(dqcResult("dqc-result-wrong-seq", "dqc-result-02")
                .taskRunId("dqc-task-run").taskRunObservationSequence(99L).build()))
                .hasMessage("DQC result must reference an exact tenant-scoped task-run observation");
        assertThatThrownBy(() -> service.execute(dqcResult("dqc-result-inexact-pass", "dqc-result-03")
                .violationCount(1L).build())).hasMessage("PASS DQC result must have zero violations");
    }

    @Test
    void shouldRejectLineageCyclesAndPublishVersionedMetricSemantics() {
        service.execute(dataSource("lineage-source", "lineage-source-def").build());
        service.execute(dataset("lineage-dataset-a", "dataset-a", "lineage-source-def").build());
        service.execute(dataset("lineage-dataset-b", "dataset-b", "lineage-source-def").build());
        when(mapper.countLineagePath(1L, "dataset-b", 1L, "dataset-a", 1L)).thenReturn(1);
        assertThatThrownBy(() -> service.execute(lineage("lineage-cycle", "lineage-a-b",
                "dataset-a", "dataset-b").build()))
                .hasMessage("lineage edge would create an indirect version-bound cycle");
        when(mapper.countLineagePath(anyLong(), anyString(), anyLong(), anyString(), anyLong())).thenReturn(0);
        MetadataView lineage = service.execute(lineage("lineage-publish", "lineage-a-b",
                "dataset-a", "dataset-b").build());
        assertThat(lineage.getDefinitionKind()).isEqualTo("LINEAGE");

        MetadataView metric = service.execute(base(MetadataOperation.PUBLISH_METRIC, "metric-publish")
                .definitionId("metric-order-count").definitionCode("ORDER_COUNT")
                .displayName("Governed order count").expectedVersion(0L).ownerPrincipalId("principal-data-owner")
                .specificationSha256(hash('a')).artifactRef(ref("metric-spec-01"))
                .grainCode("TENANT_DAY").metricUnit("ORDER").aggregationType("COUNT")
                .metricExpressionSha256(hash('b')).filterSha256(hash('c')).dimensionsSha256(hash('d'))
                .semanticVersion("1.0.0").build());
        assertThat(metric.getDefinitionKind()).isEqualTo("METRIC");
        verify(mapper).insertMetricVersion(any(MetricVersion.class));
    }

    private void wirePersistence() {
        when(mapper.ensureGraphGuard(anyLong(), anyString(), any())).thenAnswer(invocation -> {
            graphRevisions.putIfAbsent(key(invocation.getArgument(0), invocation.getArgument(1)), 0L); return 1;
        });
        when(mapper.selectGraphRevisionForUpdate(anyLong(), anyString())).thenAnswer(invocation ->
                graphRevisions.get(key(invocation.getArgument(0), invocation.getArgument(1))));
        when(mapper.advanceGraphRevision(anyLong(), anyString(), anyLong(), any())).thenAnswer(invocation -> {
            String key = key(invocation.getArgument(0), invocation.getArgument(1));
            Long revision = graphRevisions.get(key);
            if (!Objects.equals(revision, invocation.getArgument(2))) return 0;
            graphRevisions.put(key, revision + 1); return 1;
        });
        when(mapper.insertDefinition(any())).thenAnswer(invocation -> {
            Definition value = invocation.getArgument(0);
            definitions.put(key(value.getTenantId(), value.getDefinitionId()), value);
            return 1;
        });
        when(mapper.selectDefinition(anyLong(), anyString())).thenAnswer(invocation ->
                definitions.get(key(invocation.getArgument(0), invocation.getArgument(1))));
        when(mapper.selectDefinitionForUpdate(anyLong(), anyString())).thenAnswer(invocation ->
                definitions.get(key(invocation.getArgument(0), invocation.getArgument(1))));
        when(mapper.insertDefinitionVersion(any())).thenAnswer(invocation -> {
            DefinitionVersion value = invocation.getArgument(0);
            versions.add(versionKey(value.getTenantId(), value.getDefinitionId(), value.getDefinitionVersion()));
            return 1;
        });
        when(mapper.countDefinitionVersion(anyLong(), anyString(), anyString(), anyLong())).thenAnswer(invocation ->
                versions.contains(versionKey(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(3))) ? 1 : 0);
        when(mapper.publishDefinition(anyLong(), anyString(), anyString(), anyLong(), anyString(), anyString(), any()))
                .thenReturn(1);
        when(mapper.insertDataSourceVersion(any())).thenReturn(1);
        when(mapper.insertDatasetVersion(any())).thenReturn(1);
        when(mapper.insertFieldVersion(any())).thenAnswer(invocation -> {
            FieldVersion value = invocation.getArgument(0);
            fields.add(fieldKey(value.getTenantId(), value.getDatasetId(), value.getDatasetVersion(), value.getFieldCode()));
            return 1;
        });
        when(mapper.countFieldVersion(anyLong(), anyString(), anyLong(), anyString())).thenAnswer(invocation ->
                fields.contains(fieldKey(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2), invocation.getArgument(3))) ? 1 : 0);
        when(mapper.insertTaskVersion(any())).thenReturn(1);
        when(mapper.insertTaskDependency(any())).thenReturn(1);
        when(mapper.insertTaskSla(any())).thenReturn(1);
        when(mapper.insertTaskRunObservation(any())).thenAnswer(invocation -> {
            TaskRunObservation value = invocation.getArgument(0);
            runObservations.put(runKey(value.getTenantId(), value.getRunId(), value.getObservationSequence()), value);
            return 1;
        });
        when(mapper.selectLatestTaskRunForUpdate(anyLong(), anyString())).thenAnswer(invocation -> latestRun(
                invocation.getArgument(0), invocation.getArgument(1)));
        when(mapper.selectLatestTaskRun(anyLong(), anyString())).thenAnswer(invocation -> latestRun(
                invocation.getArgument(0), invocation.getArgument(1)));
        when(mapper.selectTaskRunObservation(anyLong(), anyString(), anyLong())).thenAnswer(invocation ->
                runObservations.get(runKey(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2))));
        when(mapper.insertLineageVersion(any())).thenReturn(1);
        when(mapper.insertDqcRuleVersion(any())).thenReturn(1);
        when(mapper.countDqcRuleTarget(anyLong(), anyString(), anyLong(), anyString(), anyLong())).thenReturn(1);
        when(mapper.insertDqcResult(any())).thenAnswer(invocation -> {
            DqcResult value = invocation.getArgument(0); dqcResults.put(key(value.getTenantId(), value.getResultId()), value); return 1;
        });
        when(mapper.selectDqcResult(anyLong(), anyString())).thenAnswer(invocation ->
                dqcResults.get(key(invocation.getArgument(0), invocation.getArgument(1))));
        when(mapper.insertMetricVersion(any())).thenReturn(1);
    }

    private void wireOperationStore() {
        when(mapper.insertOrResolveOperation(anyLong(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    Long tenantId = invocation.getArgument(0); String idempotencyKey = invocation.getArgument(1);
                    String key = tenantId + "|" + idempotencyKey; Operation existing = operations.get(key);
                    if (existing == null) {
                        long id = operationSequence.incrementAndGet();
                        operations.put(key, new Operation().setOperationId(id).setTenantId(tenantId)
                                .setIdempotencyKey(idempotencyKey).setCommandType(invocation.getArgument(2))
                                .setRequestHash(invocation.getArgument(3)).setAttemptToken(invocation.getArgument(4)).setStatus(0));
                        lastOperationId.set(id);
                    } else lastOperationId.set(existing.getOperationId());
                    return 1;
                });
        when(mapper.selectLastInsertId()).thenAnswer(ignored -> lastOperationId.get());
        when(mapper.selectOperationForUpdate(anyLong(), anyLong())).thenAnswer(invocation -> operations.values().stream()
                .filter(value -> value.getOperationId().equals(invocation.getArgument(0))
                        && value.getTenantId().equals(invocation.getArgument(1))).findFirst().orElse(null));
        when(mapper.markOperationSucceeded(anyLong(), anyLong(), anyString(), anyString(), any())).thenAnswer(invocation -> {
            Operation value = operations.values().stream().filter(operation -> operation.getOperationId().equals(invocation.getArgument(0))
                    && operation.getTenantId().equals(invocation.getArgument(1))).findFirst().orElse(null);
            if (value == null || value.getStatus() != 0) return 0;
            value.setStatus(10).setAggregateId(invocation.getArgument(2)).setResultJson(invocation.getArgument(3)); return 1;
        });
    }

    private TaskRunObservation latestRun(Long tenantId, String runId) {
        return runObservations.values().stream().filter(value -> value.getTenantId().equals(tenantId)
                && value.getRunId().equals(runId)).max(Comparator.comparing(TaskRunObservation::getObservationSequence))
                .orElse(null);
    }

    private static MetadataCommand.MetadataCommandBuilder dataSource(String key, String definitionId) {
        return base(MetadataOperation.PUBLISH_DATA_SOURCE, key).definitionId(definitionId).definitionCode("DS_" + definitionId)
                .displayName("Data source " + definitionId).expectedVersion(0L).ownerPrincipalId("principal-data-owner")
                .specificationSha256(hash('a')).artifactRef(ref("datasource-spec-01"))
                .sourceType("MYSQL").environment("PROD").endpointRef(ref("mysql-endpoint-01"))
                .credentialRef(ref("mysql-credential-01")).namespaceRef(ref("mysql-namespace-01"));
    }

    private static MetadataCommand.MetadataCommandBuilder dataset(String key, String definitionId, String sourceId) {
        return base(MetadataOperation.PUBLISH_DATASET, key).definitionId(definitionId).definitionCode("TABLE_" + definitionId)
                .displayName("Dataset " + definitionId).expectedVersion(0L).ownerPrincipalId("principal-data-owner")
                .specificationSha256(hash('b')).artifactRef(ref("dataset-spec-01"))
                .dataSourceId(sourceId).dataSourceVersion(1L).datasetType("TABLE")
                .qualifiedName("yshopping." + definitionId.replace('-', '_')).layerCode("DWD")
                .grainCode("ORDER_ITEM").schemaSha256(hash('c')).storageLocationRef(ref("dataset-location-01"))
                .retentionDays(365).fields(List.of(field("order_id", true), field("amount_minor", false)));
    }

    private static MetadataCommand.MetadataCommandBuilder task(String key, String definitionId,
                                                                List<MetadataCommand.TaskDependency> dependencies) {
        return base(MetadataOperation.PUBLISH_TASK, key).definitionId(definitionId).definitionCode("TASK_" + definitionId)
                .displayName("Task " + definitionId).expectedVersion(0L).ownerPrincipalId("principal-data-owner")
                .specificationSha256(hash('d')).artifactRef(ref("task-spec-01")).taskType("BATCH_SQL")
                .executableArtifactRef(ref("task-artifact-01")).codeSha256(hash('e')).scheduleSha256(hash('f'))
                .resourceGroupRef(ref("resource-group-01")).dependencies(dependencies)
                .sla(MetadataCommand.TaskSla.builder().serviceLevelCode("T_PLUS_ONE")
                        .deadlineMinuteUtc(480).maximumDurationMillis(3_600_000L)
                        .maximumFreshnessMillis(86_400_000L).approvedByPrincipalId("principal-sla-owner").build());
    }

    private static MetadataCommand.MetadataCommandBuilder run(String key, String runId, Long expectedSequence,
                                                               String status) {
        return base(MetadataOperation.OBSERVE_TASK_RUN, key).taskId("task-upstream").taskVersion(1L)
                .taskRunId(runId).attempt(1).expectedObservationSequence(expectedSequence).runStatus(status)
                .durationMillis(0L).computeCostMinor(0L).costCurrency("CNY").resourceMillis(0L)
                .rowsRead(0L).rowsWritten(0L);
    }

    private static MetadataCommand.MetadataCommandBuilder dqcRule(String key, String definitionId) {
        return base(MetadataOperation.PUBLISH_DQC_RULE, key).definitionId(definitionId).definitionCode("DQC_ORDER_ID")
                .displayName("Order id quality rule").expectedVersion(0L).ownerPrincipalId("principal-data-owner")
                .specificationSha256(hash('1')).artifactRef(ref("dqc-rule-spec-01"))
                .targetDatasetId("dqc-dataset-def").targetDatasetVersion(1L).ruleType("NOT_NULL")
                .severity("ERROR").expressionSha256(hash('2')).thresholdValue(BigDecimal.ZERO)
                .thresholdComparator("EQ");
    }

    private static MetadataCommand.MetadataCommandBuilder dqcResult(String key, String resultId) {
        return base(MetadataOperation.RECORD_DQC_RESULT, key).dqcResultId(resultId)
                .dqcRuleId("dqc-rule-def").dqcRuleVersion(1L).targetDatasetId("dqc-dataset-def")
                .targetDatasetVersion(1L).dqcResultStatus("PASS").expectedValue(BigDecimal.ZERO)
                .actualValue(BigDecimal.ZERO).evaluatedRows(100L).violationCount(0L)
                .evidenceRef(ref("dqc-result-evidence-01"));
    }

    private static MetadataCommand.MetadataCommandBuilder lineage(String key, String definitionId,
                                                                   String sourceId, String targetId) {
        return base(MetadataOperation.PUBLISH_LINEAGE, key).definitionId(definitionId).definitionCode("LINEAGE_A_B")
                .displayName("Dataset A to B lineage").expectedVersion(0L).ownerPrincipalId("principal-data-owner")
                .specificationSha256(hash('3')).artifactRef(ref("lineage-spec-01"))
                .sourceDatasetId(sourceId).sourceDatasetVersion(1L).targetDatasetId(targetId).targetDatasetVersion(1L)
                .lineageDirection("SOURCE_TO_TARGET").transformSha256(hash('4'))
                .transformationRef(ref("lineage-transform-01"));
    }

    private static MetadataCommand.TaskDependency dependency(String taskId, Long version) {
        return MetadataCommand.TaskDependency.builder().upstreamTaskId(taskId).upstreamTaskVersion(version)
                .dependencyType("DATA").required(true).build();
    }

    private static MetadataCommand.FieldDefinition field(String code, boolean pk) {
        return MetadataCommand.FieldDefinition.builder().fieldCode(code).dataType("BIGINT").nullable(!pk)
                .primaryKeyPart(pk).semanticType(pk ? "IDENTIFIER" : "MONEY_MINOR")
                .classification("INTERNAL").build();
    }

    private static MetadataCommand.MetadataCommandBuilder base(MetadataOperation operation, String key) {
        return MetadataCommand.builder().operation(operation).idempotencyKey(key).runTraceId("metadata-run-001")
                .correlationId("metadata-correlation-001").occurredAt(Instant.parse("2026-07-16T00:01:00Z"));
    }

    private static String ref(String value) { return "restricted:" + value; }
    private static String hash(char value) { return String.valueOf(value).repeat(64); }
    private static String key(Object tenantId, Object id) { return tenantId + "|" + id; }
    private static String versionKey(Object tenantId, Object id, Object version) { return tenantId + "|" + id + "|" + version; }
    private static String fieldKey(Object tenantId, Object id, Object version, Object field) { return versionKey(tenantId, id, version) + "|" + field; }
    private static String runKey(Object tenantId, Object id, Object sequence) { return versionKey(tenantId, id, sequence); }
}
