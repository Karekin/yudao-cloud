package cn.iocoder.yudao.module.cloudmold.aioperations.temporal;

import cn.hutool.crypto.digest.DigestUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Converts immutable canonical Outbox facts into idempotent daily automation
 * candidates. Candidate insertion and later claiming are deliberately separate:
 * a worker crash can replay materialization without duplicating business work.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "cloudmold.ai-operations.temporal",
        name = "enabled", havingValue = "true")
public class TemporalAutomationCandidateMaterializer {

    private final AiOperationsTemporalMapper mapper;

    @Transactional(rollbackFor = Exception.class)
    public int materialize(TemporalDailyDispatchRequest request) {
        List<String> eventTypes =
                ManagedWorkflowOutboxRouteCatalog.eventTypes(request.getSkillId());
        if (eventTypes.isEmpty()) {
            return 0;
        }
        String consumerId = ManagedWorkflowOutboxRouteCatalog.consumerId(
                request.getSkillId(), request.getSkillVersion());
        int limit = Math.max(1,
                Math.min(request.getMaxFanOut() == null ? 100 : request.getMaxFanOut(), 500));
        List<AutomationOutboxEventRecord> events = mapper.selectUnmaterializedAutomationEvents(
                request.getTenantId(), consumerId, eventTypes, limit);
        int created = 0;
        for (AutomationOutboxEventRecord event : events) {
            ManagedWorkflowOutboxRouteCatalog.MaterializedInput input =
                    ManagedWorkflowOutboxRouteCatalog.materialize(request.getSkillId(), event);
            if (input == null) {
                mapper.insertAutomationCandidateEvent(eventLink(
                        consumerId, null, event, "REJECTED", "ROUTE_INPUT_NOT_MATERIALIZABLE"));
                continue;
            }
            String routeVersion =
                    ManagedWorkflowOutboxRouteCatalog.routeVersion(request.getSkillId());
            String executionKey = DigestUtil.sha256Hex(
                    request.getTenantId() + ":" + request.getSkillId() + ":"
                            + request.getSkillVersion() + ":" + routeVersion + ":"
                            + input.businessKey());
            String clientRequestKey = "execution:" + executionKey;
            String candidateId = "mac-" + executionKey.substring(0, 32);
            LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
            LocalDateTime dueAt = event.getRecordedAt() == null ? now : event.getRecordedAt();
            TemporalAutomationCandidateRecord record =
                    new TemporalAutomationCandidateRecord()
                            .setTenantId(request.getTenantId())
                            .setCandidateId(candidateId)
                            .setClientRequestKey(clientRequestKey)
                            .setSkillId(request.getSkillId())
                            .setSkillVersion(request.getSkillVersion())
                            .setBusinessKey(input.businessKey())
                            .setInputJson(input.inputJson())
                            .setDueAt(dueAt)
                            .setStatus("PENDING")
                            .setVersion(1L)
                            .setCreatedAt(now)
                            .setUpdatedAt(now);
            int inserted = mapper.insertAutomationCandidate(record);
            created += inserted;
            mapper.insertAutomationCandidateEvent(eventLink(
                    consumerId, candidateId, event,
                    inserted == 1 ? "MATERIALIZED" : "COALESCED", null));
        }
        return created;
    }

    private static AutomationCandidateEventRecord eventLink(
            String consumerId,
            String candidateId,
            AutomationOutboxEventRecord event,
            String disposition,
            String rejectionReason) {
        return new AutomationCandidateEventRecord()
                .setTenantId(event.getTenantId())
                .setConsumerId(consumerId)
                .setCandidateId(candidateId)
                .setEventId(event.getEventId())
                .setEventType(event.getEventType())
                .setSchemaVersion(event.getSchemaVersion())
                .setSourceSystem(event.getSourceSystem())
                .setAggregateType(event.getAggregateType())
                .setAggregateId(event.getAggregateId())
                .setAggregateVersion(event.getAggregateVersion())
                .setCorrelationId(event.getCorrelationId())
                .setCausationId(event.getCausationId())
                .setPayloadHash(event.getPayloadHash())
                .setDisposition(disposition)
                .setRejectionReason(rejectionReason)
                .setOccurredAt(event.getOccurredAt())
                .setProcessedAt(LocalDateTime.now(ZoneOffset.UTC));
    }
}
