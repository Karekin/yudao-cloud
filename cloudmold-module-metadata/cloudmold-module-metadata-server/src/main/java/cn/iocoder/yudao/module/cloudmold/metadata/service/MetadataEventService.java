package cn.iocoder.yudao.module.cloudmold.metadata.service;

import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.metadata.api.MetadataCommand;
import cn.iocoder.yudao.module.cloudmold.metadata.dal.dataobject.MetadataRecords.*;
import cn.iocoder.yudao.module.cloudmold.metadata.dal.mysql.MetadataStoreMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MetadataEventService {
    static final String SOURCE_SYSTEM = "cloudmold-metadata";

    private final MetadataStoreMapper mapper;
    private final OutboxAppender outboxAppender;

    public void appendDefinition(Long operationId, Definition definition, DefinitionVersion version,
                                 String previousStatus, String eventType, MetadataCommand command,
                                 Instant occurredAt, LocalDateTime now, Map<String, Object> typedPayload) {
        history(operationId, definition.getTenantId(), definition.getDefinitionKind(), definition.getDefinitionId(),
                definition.getCurrentVersion(), previousStatus, definition.getStatus(), "VERSION_PUBLISHED",
                occurredAt, now);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("definition_id", definition.getDefinitionId());
        payload.put("definition_code", definition.getDefinitionCode());
        payload.put("definition_version", definition.getCurrentVersion());
        payload.put("display_name", definition.getDisplayName());
        payload.put("owner_principal_id", definition.getOwnerPrincipalId());
        payload.put("specification_sha256", version.getSpecificationSha256());
        payload.put("artifact_ref", version.getArtifactRef());
        payload.put("previous_status", previousStatus);
        payload.put("current_status", definition.getStatus());
        payload.putAll(typedPayload);
        append(eventType, "metadata_" + definition.getDefinitionKind().toLowerCase(Locale.ROOT),
                definition.getDefinitionId(), definition.getCurrentVersion(), definition.getTenantId(), command,
                occurredAt, payload);
    }

    public void appendTaskRun(Long operationId, TaskRunObservation observation, String previousStatus,
                              MetadataCommand command, Instant occurredAt, LocalDateTime now) {
        history(operationId, observation.getTenantId(), "TASK_RUN", observation.getRunId(),
                observation.getObservationSequence(), previousStatus, observation.getStatus(), "OBSERVED",
                occurredAt, now);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("run_id", observation.getRunId());
        payload.put("task_id", observation.getTaskId());
        payload.put("task_version", observation.getTaskVersion());
        payload.put("attempt", observation.getAttempt());
        payload.put("observation_sequence", observation.getObservationSequence());
        payload.put("previous_status", previousStatus);
        payload.put("current_status", observation.getStatus());
        payload.put("scheduled_at", iso(observation.getScheduledAt()));
        payload.put("started_at", iso(observation.getStartedAt()));
        payload.put("finished_at", iso(observation.getFinishedAt()));
        payload.put("duration_millis", observation.getDurationMillis());
        payload.put("compute_cost_minor", observation.getComputeCostMinor());
        payload.put("cost_currency", observation.getCostCurrency());
        payload.put("resource_millis", observation.getResourceMillis());
        payload.put("rows_read", observation.getRowsRead());
        payload.put("rows_written", observation.getRowsWritten());
        payload.put("source_checkpoint_ref", observation.getSourceCheckpointRef());
        payload.put("output_snapshot_ref", observation.getOutputSnapshotRef());
        payload.put("error_ref", observation.getErrorRef());
        append("metadata.task_run.observed", "metadata_task_run", observation.getRunId(),
                observation.getObservationSequence(), observation.getTenantId(), command, occurredAt, payload);
    }

    public void appendDqcResult(DqcResult result, MetadataCommand command, Instant occurredAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("result_id", result.getResultId());
        payload.put("dqc_rule_id", result.getDqcRuleId());
        payload.put("dqc_rule_version", result.getDqcRuleVersion());
        payload.put("dataset_id", result.getDatasetId());
        payload.put("dataset_version", result.getDatasetVersion());
        payload.put("task_run_id", result.getTaskRunId());
        payload.put("task_run_observation_sequence", result.getTaskRunObservationSequence());
        payload.put("result_status", result.getStatus());
        payload.put("expected_value", decimal(result.getExpectedValue()));
        payload.put("actual_value", decimal(result.getActualValue()));
        payload.put("evaluated_rows", result.getEvaluatedRows());
        payload.put("violation_count", result.getViolationCount());
        payload.put("evidence_ref", result.getEvidenceRef());
        payload.put("observed_at", occurredAt.toString());
        append("metadata.dqc_result.recorded", "metadata_dqc_result", result.getResultId(), 1L,
                result.getTenantId(), command, occurredAt, payload);
    }

    private void history(Long operationId, Long tenantId, String aggregateType, String aggregateId, Long version,
                         String previousStatus, String currentStatus, String reasonCode, Instant occurredAt,
                         LocalDateTime now) {
        int written = mapper.insertStatusHistory(new StatusHistory().setTenantId(tenantId)
                .setAggregateType(aggregateType).setAggregateId(aggregateId).setAggregateVersion(version)
                .setPreviousStatus(previousStatus).setCurrentStatus(currentStatus).setOperationId(operationId)
                .setReasonCode(reasonCode).setOccurredAt(LocalDateTime.ofInstant(occurredAt, ZoneOffset.UTC))
                .setCreatedAt(now));
        if (written != 1) throw new IllegalStateException("failed to append metadata status history");
    }

    private void append(String eventType, String aggregateType, String aggregateId, Long version, Long tenantId,
                        MetadataCommand command, Instant occurredAt, Map<String, Object> payload) {
        outboxAppender.append(AppendDomainEventCommand.builder().eventType(eventType).schemaVersion(1)
                .sourceSystem(SOURCE_SYSTEM).tenantId(tenantId).aggregateType(aggregateType)
                .aggregateId(aggregateId).aggregateVersion(version).eventSequence((short) 1)
                .occurredAt(occurredAt).traceId(command.getRunTraceId()).correlationId(command.getCorrelationId())
                .causationId(command.getCausationId())
                .idempotencyKey(aggregateType + ":" + aggregateId + ":event:" + version)
                .payload(payload).headers(Map.of("pii_safe", true, "raw_sql_stored", false,
                        "raw_connection_stored", false)).destination("lakehouse").build());
    }

    private static String iso(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC).toString();
    }

    private static String decimal(java.math.BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }
}
