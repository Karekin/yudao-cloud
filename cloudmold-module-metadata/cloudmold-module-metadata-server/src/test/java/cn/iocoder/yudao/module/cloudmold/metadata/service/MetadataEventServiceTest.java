package cn.iocoder.yudao.module.cloudmold.metadata.service;

import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.*;
import cn.iocoder.yudao.module.cloudmold.metadata.api.*;
import cn.iocoder.yudao.module.cloudmold.metadata.dal.dataobject.MetadataRecords.*;
import cn.iocoder.yudao.module.cloudmold.metadata.dal.mysql.MetadataStoreMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MetadataEventServiceTest {
    private final MetadataStoreMapper mapper = mock(MetadataStoreMapper.class);
    private final OutboxAppender outbox = mock(OutboxAppender.class);
    private final MetadataEventService service = new MetadataEventService(mapper, outbox);

    @Test
    void shouldEmitEightFixedPiiSafeContractsWithExactRuntimeEvidence() {
        when(mapper.insertStatusHistory(any())).thenReturn(1);
        Instant occurredAt = Instant.parse("2026-07-16T00:00:00Z");
        LocalDateTime now = LocalDateTime.ofInstant(occurredAt, ZoneOffset.UTC);
        MetadataCommand command = MetadataCommand.builder().operation(MetadataOperation.PUBLISH_DATASET)
                .idempotencyKey("metadata-event-01").runTraceId("metadata-run-01")
                .correlationId("corr-01").causationId("cause-01").build();
        emitDefinition(1L, "DATA_SOURCE", "metadata.datasource.version_published", command, occurredAt, now,
                map("source_type", "MYSQL", "environment", "PROD", "endpoint_ref", "restricted:endpoint-01",
                        "credential_ref", "restricted:credential-01", "namespace_ref", "restricted:namespace-01"));
        emitDefinition(2L, "DATASET", "metadata.dataset.version_published", command, occurredAt, now,
                map("data_source_id", "source-01", "data_source_version", 1L, "dataset_type", "TABLE",
                        "qualified_name", "dwd.order_item", "layer_code", "DWD", "grain_code", "ORDER_ITEM",
                        "schema_sha256", "b".repeat(64), "storage_location_ref", "restricted:location-01",
                        "retention_days", 365, "field_count", 12));
        emitDefinition(3L, "TASK", "metadata.task.version_published", command, occurredAt, now,
                map("task_type", "BATCH_SQL", "executable_artifact_ref", "restricted:artifact-01",
                        "code_sha256", "c".repeat(64), "schedule_sha256", "d".repeat(64),
                        "resource_group_ref", "restricted:resource-01", "dependency_count", 1,
                        "service_level_code", "T_PLUS_ONE", "deadline_minute_utc", 480,
                        "maximum_duration_millis", 3600000L, "maximum_freshness_millis", 86400000L,
                        "sla_approved_by_principal_id", "principal-sla-owner"));
        emitDefinition(4L, "LINEAGE", "metadata.lineage.version_published", command, occurredAt, now,
                map("source_dataset_id", "dataset-a", "source_dataset_version", 1L,
                        "target_dataset_id", "dataset-b", "target_dataset_version", 1L,
                        "direction", "SOURCE_TO_TARGET", "transform_sha256", "e".repeat(64),
                        "transformation_ref", "restricted:transform-01"));
        emitDefinition(5L, "DQC_RULE", "metadata.dqc_rule.version_published", command, occurredAt, now,
                map("dataset_id", "dataset-b", "dataset_version", 1L, "dataset_field", "order_id",
                        "rule_type", "NOT_NULL", "severity", "ERROR", "expression_sha256", "f".repeat(64),
                        "threshold_value", "0", "threshold_comparator", "EQ"));
        emitDefinition(6L, "METRIC", "metadata.metric.version_published", command, occurredAt, now,
                map("grain_code", "TENANT_DAY", "metric_unit", "ORDER", "aggregation_type", "COUNT",
                        "expression_sha256", "1".repeat(64), "filter_sha256", "2".repeat(64),
                        "dimensions_sha256", "3".repeat(64), "semantic_version", "1.0.0"));

        TaskRunObservation run = new TaskRunObservation().setTenantId(1L).setRunId("task-run-01")
                .setTaskId("task-01").setTaskVersion(2L).setAttempt(1).setObservationSequence(3L)
                .setStatus("SUCCEEDED").setScheduledAt(now.minusMinutes(2)).setStartedAt(now.minusMinutes(1))
                .setFinishedAt(now).setDurationMillis(60000L).setComputeCostMinor(123L).setCostCurrency("CNY")
                .setResourceMillis(1000L).setRowsRead(200L).setRowsWritten(100L)
                .setSourceCheckpointRef("restricted:checkpoint-01").setOutputSnapshotRef("restricted:snapshot-01");
        service.appendTaskRun(7L, run, "RUNNING", command, occurredAt, now);
        DqcResult result = new DqcResult().setTenantId(1L).setResultId("result-01")
                .setDqcRuleId("rule-01").setDqcRuleVersion(2L).setDatasetId("dataset-b").setDatasetVersion(1L)
                .setTaskRunId("task-run-01").setTaskRunObservationSequence(3L).setStatus("PASS")
                .setExpectedValue(new BigDecimal("0.000000000")).setActualValue(new BigDecimal("0.000000000"))
                .setEvaluatedRows(100L).setViolationCount(0L).setEvidenceRef("restricted:evidence-01");
        service.appendDqcResult(result, command, occurredAt);

        ArgumentCaptor<AppendDomainEventCommand> captor = ArgumentCaptor.forClass(AppendDomainEventCommand.class);
        verify(outbox, times(8)).append(captor.capture());
        Map<String, AppendDomainEventCommand> events = new HashMap<>();
        captor.getAllValues().forEach(event -> events.put(event.getEventType(), event));
        assertThat(events).containsOnlyKeys("metadata.datasource.version_published",
                "metadata.dataset.version_published", "metadata.task.version_published",
                "metadata.lineage.version_published", "metadata.dqc_rule.version_published",
                "metadata.metric.version_published", "metadata.task_run.observed",
                "metadata.dqc_result.recorded");
        events.values().forEach(event -> {
            assertThat(event.getSchemaVersion()).isEqualTo(1);
            assertThat(event.getSourceSystem()).isEqualTo("cloudmold-metadata");
            assertThat(event.getTenantId()).isEqualTo(1L);
            assertThat(event.getHeaders()).containsEntry("pii_safe", true)
                    .containsEntry("raw_sql_stored", false).containsEntry("raw_connection_stored", false);
            assertThat(event.getPayload().keySet()).noneMatch(key -> Set.of("sql", "sql_content", "url",
                    "username", "password", "credential", "raw_error").contains(key));
        });
        assertThat(events.get("metadata.task_run.observed").getPayload().keySet()).containsExactlyInAnyOrder(
                "run_id", "task_id", "task_version", "attempt", "observation_sequence", "previous_status",
                "current_status", "scheduled_at", "started_at", "finished_at", "duration_millis",
                "compute_cost_minor", "cost_currency", "resource_millis", "rows_read", "rows_written",
                "source_checkpoint_ref", "output_snapshot_ref", "error_ref");
        assertThat(events.get("metadata.dqc_result.recorded").getPayload())
                .containsEntry("task_run_observation_sequence", 3L)
                .containsEntry("expected_value", "0.000000000")
                .containsEntry("actual_value", "0.000000000")
                .containsEntry("violation_count", 0L);
    }

    private void emitDefinition(Long operationId, String kind, String eventType, MetadataCommand command,
                                Instant occurredAt, LocalDateTime now, Map<String, Object> typedPayload) {
        Definition definition = new Definition().setTenantId(1L).setDefinitionId(kind.toLowerCase(Locale.ROOT) + "-01")
                .setDefinitionKind(kind).setDefinitionCode(kind + "_01").setDisplayName("Metadata " + kind)
                .setOwnerPrincipalId("principal-data-owner").setStatus("PUBLISHED").setCurrentVersion(1L);
        DefinitionVersion version = new DefinitionVersion().setDefinitionVersion(1L)
                .setSpecificationSha256("a".repeat(64)).setArtifactRef("restricted:metadata-artifact-01");
        service.appendDefinition(operationId, definition, version, "DRAFT", eventType, command,
                occurredAt, now, typedPayload);
    }

    private static Map<String, Object> map(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) result.put((String) pairs[i], pairs[i + 1]);
        return result;
    }
}
