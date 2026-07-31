package cn.iocoder.yudao.module.cloudmold.supplier.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.supplier.api.SupplierPerformanceCommand;
import cn.iocoder.yudao.module.cloudmold.supplier.api.SupplierPerformanceCommandApi;
import cn.iocoder.yudao.module.cloudmold.supplier.api.SupplierPerformanceOperation;
import cn.iocoder.yudao.module.cloudmold.supplier.api.SupplierPerformanceResult;
import cn.iocoder.yudao.module.cloudmold.supplier.dal.dataobject.SupplierPerformanceRecords.MetricEvidence;
import cn.iocoder.yudao.module.cloudmold.supplier.dal.dataobject.SupplierPerformanceRecords.Operation;
import cn.iocoder.yudao.module.cloudmold.supplier.dal.dataobject.SupplierPerformanceRecords.Scorecard;
import cn.iocoder.yudao.module.cloudmold.supplier.dal.mysql.SupplierPerformanceMapper;
import cn.iocoder.yudao.module.cloudmold.supplier.service.actor.SupplierActorPrincipalPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Supplier performance is an evidence authority, not a free-form AI score.
 * Restriction and requalification remain separately governed decisions.
 */
@Service
@RequiredArgsConstructor
public class SupplierPerformanceServiceImpl implements SupplierPerformanceCommandApi {
    private static final int OPERATION_SUCCEEDED = 10;
    private static final String SOURCE_SYSTEM = "cloudmold-supplier";
    private static final Pattern SAFE_REF = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:/-]{0,255}");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Set<String> SOURCE_SYSTEMS = Set.of(
            "CLOUDMOLD_PROCUREMENT", "CLOUDMOLD_WAREHOUSE", "CLOUDMOLD_QUALITY", "CLOUDMOLD_MES",
            "EXTERNAL_FACTORY", "EXTERNAL_INSPECTION");
    private static final Set<String> METRIC_CODES = Set.of(
            "OTIF", "QUALITY_PASS_RATE", "CAPACITY_ATTAINMENT", "CAPA_EFFECTIVENESS");
    private static final List<String> REQUIRED_METRIC_CODES = List.of(
            "OTIF", "QUALITY_PASS_RATE", "CAPACITY_ATTAINMENT", "CAPA_EFFECTIVENESS");

    private final SupplierPerformanceMapper mapper;
    private final OutboxAppender outboxAppender;
    private final SupplierActorPrincipalPort actorPrincipalPort;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SupplierPerformanceResult execute(SupplierPerformanceCommand command, String actorPrincipalId) {
        validateEnvelope(command);
        requireRef(actorPrincipalId, "actorPrincipalId", 128);
        actorPrincipalPort.requireActive(actorPrincipalId);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDateTime now = LocalDateTime.ofInstant(command.getOccurredAt(), ZoneOffset.UTC);
        String requestHash = DigestUtil.sha256Hex(actorPrincipalId + "\n" + JsonUtils.toJsonString(command));
        String attemptToken = UUID.randomUUID().toString();
        mapper.insertOrResolveOperation(tenantId, command.getIdempotencyKey(), command.getOperation().name(),
                requestHash, attemptToken, now);
        Long operationId = mapper.selectLastInsertId();
        require(operationId != null && operationId > 0, "failed to resolve supplier performance operation");
        Operation operation = mapper.selectOperationForUpdate(operationId, tenantId);
        require(operation != null, "supplier performance operation disappeared");
        if (!attemptToken.equals(operation.getAttemptToken())) {
            require(Objects.equals(requestHash, operation.getRequestHash()),
                    "idempotency key conflicts with different supplier performance payload");
            require(operation.getStatus() == OPERATION_SUCCEEDED && operation.getResultJson() != null,
                    "existing supplier performance operation is incomplete");
            SupplierPerformanceResult replay = JsonUtils.parseObject(
                    operation.getResultJson(), SupplierPerformanceResult.class);
            replay.setDuplicate(true);
            return replay;
        }

        Outcome outcome = switch (command.getOperation()) {
            case RECORD_METRIC_EVIDENCE -> recordMetricEvidence(tenantId, command, actorPrincipalId, now);
            case GENERATE_SCORECARD -> generateScorecard(tenantId, command, actorPrincipalId, now);
        };
        if (outcome.eventType() != null) {
            appendEvent(tenantId, command, outcome);
        }
        SupplierPerformanceResult result = SupplierPerformanceResult.builder()
                .operationId(operationId).duplicate(false).aggregateType(outcome.aggregateType())
                .aggregateId(outcome.aggregateId()).aggregateVersion(outcome.version()).status(outcome.status())
                .supplierId(outcome.supplierId()).metricEvidenceId(outcome.metricEvidenceId())
                .scorecardId(outcome.scorecardId()).missingMetricCodes(outcome.missingMetricCodes()).build();
        require(mapper.markOperationSucceeded(operationId, tenantId, outcome.aggregateType(), outcome.aggregateId(),
                JsonUtils.toJsonString(result), now) == 1, "supplier performance operation completion conflict");
        return result;
    }

    private Outcome recordMetricEvidence(Long tenantId, SupplierPerformanceCommand command,
                                         String actorPrincipalId, LocalDateTime now) {
        SupplierPerformanceCommand.MetricEvidenceDefinition input = requireNonNull(
                command.getMetricEvidence(), "metricEvidence is required");
        String metricEvidenceId = valueOrUuid(input.getMetricEvidenceId());
        validatePeriod(input.getPeriodStart(), input.getPeriodEnd());
        requireActiveAdmittedSupplier(tenantId, input.getSupplierId());
        String sourceSystem = upper(input.getSourceSystem());
        String metricCode = upper(input.getMetricCode());
        require(SOURCE_SYSTEMS.contains(sourceSystem), "unsupported supplier performance sourceSystem");
        require(METRIC_CODES.contains(metricCode), "unsupported supplier performance metricCode");
        requireRef(input.getSourceRecordId(), "sourceRecordId", 128);
        requirePositive(input.getDenominator(), "denominator");
        require(input.getNumerator() != null && input.getNumerator().compareTo(BigDecimal.ZERO) >= 0
                        && input.getNumerator().compareTo(input.getDenominator()) <= 0,
                "numerator must be between zero and denominator");
        requireSha256(input.getEvidenceSha256(), "evidenceSha256");
        require(input.getObservedAt() != null, "observedAt is required");
        LocalDateTime observedAt = LocalDateTime.ofInstant(input.getObservedAt(), ZoneOffset.UTC);
        MetricEvidence row = new MetricEvidence().setMetricEvidenceId(metricEvidenceId).setTenantId(tenantId)
                .setSupplierId(input.getSupplierId()).setPeriodStart(input.getPeriodStart()).setPeriodEnd(input.getPeriodEnd())
                .setSourceSystem(sourceSystem).setSourceRecordId(input.getSourceRecordId()).setMetricCode(metricCode)
                .setNumerator(input.getNumerator()).setDenominator(input.getDenominator())
                .setEvidenceSha256(input.getEvidenceSha256()).setObservedAt(observedAt)
                .setRecordedByPrincipalId(actorPrincipalId).setCreatedAt(now);
        require(mapper.insertMetricEvidence(row) == 1, "failed to persist supplier metric evidence");
        return new Outcome("supplier.performance.metric_evidence.recorded", "supplier_metric_evidence", metricEvidenceId,
                1L, "RECORDED", input.getSupplierId(), metricEvidenceId, null, List.of(), payload(
                "supplier_id", input.getSupplierId(), "metric_evidence_id", metricEvidenceId,
                "metric_code", metricCode, "source_system", sourceSystem,
                "source_record_id", input.getSourceRecordId(), "period_start", input.getPeriodStart().toString(),
                "period_end", input.getPeriodEnd().toString(), "numerator", input.getNumerator(),
                "denominator", input.getDenominator(), "evidence_sha256", input.getEvidenceSha256()));
    }

    private Outcome generateScorecard(Long tenantId, SupplierPerformanceCommand command,
                                      String actorPrincipalId, LocalDateTime now) {
        SupplierPerformanceCommand.ScorecardDefinition input = requireNonNull(command.getScorecard(),
                "scorecard is required");
        validatePeriod(input.getPeriodStart(), input.getPeriodEnd());
        requireActiveAdmittedSupplier(tenantId, input.getSupplierId());
        List<MetricEvidence> evidence = mapper.selectMetricEvidence(tenantId, input.getSupplierId(),
                input.getPeriodStart(), input.getPeriodEnd());
        Map<String, MetricTotals> totals = totals(evidence);
        List<String> missing = REQUIRED_METRIC_CODES.stream().filter(code -> !totals.containsKey(code)).toList();
        if (!missing.isEmpty()) {
            return new Outcome(null, "supplier_performance_scorecard", null, null, "NO_ACTION_DUE",
                    input.getSupplierId(), null, null, missing, Map.of());
        }
        String snapshot = snapshotHash(evidence);
        int otif = toBps(totals.get("OTIF"));
        int quality = toBps(totals.get("QUALITY_PASS_RATE"));
        int capacity = toBps(totals.get("CAPACITY_ATTAINMENT"));
        int capa = toBps(totals.get("CAPA_EFFECTIVENESS"));
        int overall = weighted(otif, quality, capacity, capa);
        int version = mapper.selectMaxScorecardVersion(tenantId, input.getSupplierId(), input.getPeriodStart(),
                input.getPeriodEnd()) + 1;
        String scorecardId = valueOrUuid(input.getScorecardId());
        String assessment = assessment(overall, otif, quality, capacity, capa);
        Scorecard row = new Scorecard().setScorecardId(scorecardId).setTenantId(tenantId)
                .setSupplierId(input.getSupplierId()).setPeriodStart(input.getPeriodStart()).setPeriodEnd(input.getPeriodEnd())
                .setScorecardVersion(version).setOtifBps(otif).setQualityBps(quality).setCapacityBps(capacity)
                .setCapaBps(capa).setOverallBps(overall).setAssessment(assessment).setEvidenceSnapshotSha256(snapshot)
                .setGeneratedByPrincipalId(actorPrincipalId).setGeneratedAt(now);
        require(mapper.insertScorecard(row) == 1, "failed to persist supplier performance scorecard");
        return new Outcome("supplier.performance.scorecard.generated", "supplier_performance_scorecard", scorecardId,
                (long) version, "READY", input.getSupplierId(), null, scorecardId, List.of(), payload(
                "supplier_id", input.getSupplierId(), "scorecard_id", scorecardId, "scorecard_version", version,
                "period_start", input.getPeriodStart().toString(), "period_end", input.getPeriodEnd().toString(),
                "otif_bps", otif, "quality_bps", quality, "capacity_bps", capacity, "capa_bps", capa,
                "overall_bps", overall, "assessment", assessment, "evidence_snapshot_sha256", snapshot));
    }

    private void appendEvent(Long tenantId, SupplierPerformanceCommand command, Outcome outcome) {
        outboxAppender.append(AppendDomainEventCommand.builder().eventId(UUID.randomUUID().toString())
                .eventType(outcome.eventType()).schemaVersion(1).sourceSystem(SOURCE_SYSTEM).tenantId(tenantId)
                .aggregateType(outcome.aggregateType()).aggregateId(outcome.aggregateId())
                .aggregateVersion(outcome.version()).eventSequence((short) 1).occurredAt(command.getOccurredAt())
                .traceId(command.getRunId())
                .correlationId(command.getCorrelationId()).causationId(command.getCausationId())
                .idempotencyKey(command.getIdempotencyKey() + ":" + outcome.version()).payload(outcome.payload())
                .headers(payload("run_id", command.getRunId(), "status", outcome.status()))
                .destination("lakehouse").build());
    }

    private static Map<String, MetricTotals> totals(List<MetricEvidence> evidence) {
        Map<String, MetricTotals> totals = new LinkedHashMap<>();
        for (MetricEvidence item : evidence) {
            totals.computeIfAbsent(item.getMetricCode(), ignored -> new MetricTotals())
                    .add(item.getNumerator(), item.getDenominator());
        }
        return totals;
    }

    private static int toBps(MetricTotals value) {
        return value.numerator.multiply(BigDecimal.valueOf(10_000)).divide(value.denominator, 0, RoundingMode.DOWN)
                .intValueExact();
    }

    private static int weighted(int otif, int quality, int capacity, int capa) {
        return (otif * 40 + quality * 30 + capacity * 20 + capa * 10) / 100;
    }

    private static String assessment(int overall, int otif, int quality, int capacity, int capa) {
        if (overall >= 9000 && otif >= 9000 && quality >= 9000 && capacity >= 9000 && capa >= 9000) return "HEALTHY";
        if (overall >= 8000) return "WATCH";
        return "AT_RISK";
    }

    private static String snapshotHash(List<MetricEvidence> evidence) {
        List<String> values = new ArrayList<>();
        evidence.forEach(item -> values.add(item.getMetricCode() + "|" + item.getSourceSystem() + "|"
                + item.getSourceRecordId() + "|" + item.getNumerator().toPlainString() + "|"
                + item.getDenominator().toPlainString() + "|" + item.getEvidenceSha256()));
        values.sort(String::compareTo);
        return DigestUtil.sha256Hex(String.join("\n", values));
    }

    private void requireActiveAdmittedSupplier(Long tenantId, String supplierId) {
        requireRef(supplierId, "supplierId", 128);
        require(mapper.countActiveAdmittedSupplier(tenantId, supplierId) == 1,
                "supplier must be active and admitted before performance evidence is recorded");
    }

    private static void validateEnvelope(SupplierPerformanceCommand command) {
        require(command != null && command.getOperation() != null, "supplier performance operation is required");
        requireRef(command.getIdempotencyKey(), "idempotencyKey", 128);
        requireRef(command.getRunId(), "runId", 128);
        requireRef(command.getCorrelationId(), "correlationId", 128);
        require(command.getOccurredAt() != null, "occurredAt is required");
    }

    private static void validatePeriod(LocalDate start, LocalDate end) {
        require(start != null && end != null && !end.isBefore(start), "performance period is invalid");
    }

    private static void requirePositive(BigDecimal value, String label) {
        require(value != null && value.compareTo(BigDecimal.ZERO) > 0, label + " must be positive");
    }

    private static void requireSha256(String value, String label) {
        require(value != null && SHA256.matcher(value).matches(), label + " must be SHA-256 lowercase hex");
    }

    private static void requireRef(String value, String label, int maxLength) {
        require(value != null && value.length() <= maxLength && SAFE_REF.matcher(value).matches(),
                label + " is invalid");
    }

    private static String valueOrUuid(String value) {
        return value == null || value.isBlank() ? UUID.randomUUID().toString() : value;
    }

    private static String upper(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private static <T> T requireNonNull(T value, String message) {
        require(value != null, message);
        return value;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static Map<String, Object> payload(Object... entries) {
        Map<String, Object> value = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            value.put((String) entries[index], entries[index + 1]);
        }
        return value;
    }

    private record Outcome(String eventType, String aggregateType, String aggregateId, Long version, String status,
                           String supplierId, String metricEvidenceId, String scorecardId,
                           List<String> missingMetricCodes, Map<String, Object> payload) {
    }

    private static final class MetricTotals {
        private BigDecimal numerator = BigDecimal.ZERO;
        private BigDecimal denominator = BigDecimal.ZERO;

        private void add(BigDecimal nextNumerator, BigDecimal nextDenominator) {
            numerator = numerator.add(nextNumerator);
            denominator = denominator.add(nextDenominator);
        }
    }
}
