package cn.iocoder.yudao.module.cloudmold.supplier.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.supplier.api.*;
import cn.iocoder.yudao.module.cloudmold.supplier.dal.dataobject.SupplierSourcingRecords.Operation;
import cn.iocoder.yudao.module.cloudmold.supplier.dal.dataobject.SupplierSourcingRecords.Quote;
import cn.iocoder.yudao.module.cloudmold.supplier.dal.dataobject.SupplierSourcingRecords.SampleEvaluation;
import cn.iocoder.yudao.module.cloudmold.supplier.dal.dataobject.SupplierSourcingRecords.SourcingCase;
import cn.iocoder.yudao.module.cloudmold.supplier.dal.dataobject.SupplierSourcingRecords.SupplierProfile;
import cn.iocoder.yudao.module.cloudmold.supplier.dal.mysql.SupplierSourcingMapper;
import cn.iocoder.yudao.module.cloudmold.supplier.service.actor.SupplierActorPrincipalPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class SupplierSourcingServiceImpl implements SupplierSourcingCommandApi {
    private static final int OPERATION_SUCCEEDED = 10;
    private static final String SOURCE_SYSTEM = "cloudmold-supplier";
    private static final Pattern SAFE_REF = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:/-]{0,255}");
    private static final Pattern SAFE_CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Set<String> RISK_LEVELS = Set.of("LOW", "MEDIUM", "HIGH");

    private final SupplierSourcingMapper mapper;
    private final OutboxAppender outboxAppender;
    private final SupplierActorPrincipalPort actorPrincipalPort;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SupplierSourcingResult execute(SupplierSourcingCommand command, String actorPrincipalId) {
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
        require(operationId != null && operationId > 0, "failed to resolve supplier sourcing operation");
        Operation operation = mapper.selectOperationForUpdate(operationId, tenantId);
        require(operation != null, "supplier sourcing operation disappeared");
        if (!attemptToken.equals(operation.getAttemptToken())) {
            require(Objects.equals(requestHash, operation.getRequestHash()),
                    "idempotency key conflicts with different supplier sourcing payload");
            require(operation.getStatus() == OPERATION_SUCCEEDED && operation.getResultJson() != null,
                    "existing supplier sourcing operation is incomplete");
            SupplierSourcingResult replay = JsonUtils.parseObject(
                    operation.getResultJson(), SupplierSourcingResult.class);
            replay.setDuplicate(true);
            return replay;
        }

        Outcome outcome = switch (command.getOperation()) {
            case REGISTER_SUPPLIER -> registerSupplier(tenantId, command, actorPrincipalId, now);
            case SUBMIT_ADMISSION -> submitAdmission(tenantId, command, actorPrincipalId, now);
            case APPROVE_ADMISSION -> approveAdmission(tenantId, command, actorPrincipalId, now);
            case CREATE_RFQ -> createRfq(tenantId, command, actorPrincipalId, now);
            case SUBMIT_QUOTE -> submitQuote(tenantId, command, actorPrincipalId, now);
            case RECORD_SAMPLE_EVALUATION -> recordSampleEvaluation(
                    tenantId, command, actorPrincipalId, now);
            case AWARD_SUPPLIER -> awardSupplier(tenantId, command, actorPrincipalId, now);
        };
        appendEvent(tenantId, command, outcome);
        SupplierSourcingResult result = SupplierSourcingResult.builder()
                .operationId(operationId)
                .duplicate(false)
                .aggregateType(outcome.aggregateType())
                .aggregateId(outcome.aggregateId())
                .aggregateVersion(outcome.version())
                .status(outcome.status())
                .supplierId(stringValue(outcome.payload().get("supplier_id")))
                .sourcingCaseId(stringValue(outcome.payload().get("sourcing_case_id")))
                .quoteId(stringValue(outcome.payload().get("quote_id")))
                .evaluationId(stringValue(outcome.payload().get("evaluation_id")))
                .build();
        require(mapper.markOperationSucceeded(operationId, tenantId, outcome.aggregateType(),
                outcome.aggregateId(), JsonUtils.toJsonString(result), now) == 1,
                "supplier sourcing operation completion conflict");
        return result;
    }

    private Outcome registerSupplier(Long tenantId, SupplierSourcingCommand command,
                                     String actorPrincipalId, LocalDateTime now) {
        SupplierSourcingCommand.SupplierDefinition input = nonNull(
                command.getSupplier(), "supplier is required");
        String supplierId = valueOrUuid(input.getSupplierId());
        requireRef(input.getSupplierCode(), "supplierCode", 64);
        requireText(input.getSupplierName(), "supplierName", 191);
        String countryCode = upper(input.getCountryCode());
        require(countryCode != null && countryCode.matches("[A-Z]{2}"),
                "countryCode must be ISO alpha-2");
        requireText(input.getCapabilitySummary(), "capabilitySummary", 1000);
        String riskLevel = upper(input.getRiskLevel());
        require(RISK_LEVELS.contains(riskLevel), "riskLevel must be LOW, MEDIUM, or HIGH");
        SupplierProfile row = new SupplierProfile()
                .setSupplierId(supplierId).setTenantId(tenantId)
                .setSupplierCode(input.getSupplierCode()).setSupplierName(input.getSupplierName().trim())
                .setCountryCode(countryCode).setCapabilitySummary(input.getCapabilitySummary().trim())
                .setRiskLevel(riskLevel).setStatus("CANDIDATE").setAdmissionStatus("DRAFT")
                .setCreatedByPrincipalId(actorPrincipalId)
                .setReasonCode(normalizeReasonCode(input.getReasonCode()))
                .setVersion(1L).setCreatedAt(now).setUpdatedAt(now);
        require(mapper.insertSupplier(row) == 1, "failed to persist supplier");
        return outcome("supplier.profile.registered", "supplier_profile", supplierId, 1L,
                "CANDIDATE", payload("supplier_id", supplierId, "supplier_code", row.getSupplierCode(),
                        "supplier_name", row.getSupplierName(), "risk_level", riskLevel,
                        "admission_status", "DRAFT"));
    }

    private Outcome submitAdmission(Long tenantId, SupplierSourcingCommand command,
                                    String actorPrincipalId, LocalDateTime now) {
        SupplierSourcingCommand.AdmissionDefinition input = nonNull(
                command.getAdmission(), "admission is required");
        requireRef(input.getSupplierId(), "supplierId", 128);
        requireSha256(input.getQualificationEvidenceSha256(), "qualificationEvidenceSha256");
        requireSha256(input.getRiskEvidenceSha256(), "riskEvidenceSha256");
        SupplierProfile row = nonNull(mapper.selectSupplierForUpdate(tenantId, input.getSupplierId()),
                "supplier not found");
        requireExpectedVersion(input.getExpectedVersion(), row.getVersion());
        require("CANDIDATE".equals(row.getStatus()) && "DRAFT".equals(row.getAdmissionStatus()),
                "only a draft candidate supplier can enter admission review");
        String reasonCode = normalizeReasonCode(input.getReasonCode());
        require(mapper.submitAdmission(tenantId, row.getSupplierId(), row.getVersion(), actorPrincipalId,
                input.getQualificationEvidenceSha256(), input.getRiskEvidenceSha256(), reasonCode, now) == 1,
                "supplier admission submission conflict");
        return outcome("supplier.admission.submitted", "supplier_profile", row.getSupplierId(),
                row.getVersion() + 1, "UNDER_REVIEW",
                payload("supplier_id", row.getSupplierId(), "supplier_code", row.getSupplierCode(),
                        "admission_status", "UNDER_REVIEW"));
    }

    private Outcome approveAdmission(Long tenantId, SupplierSourcingCommand command,
                                     String actorPrincipalId, LocalDateTime now) {
        SupplierSourcingCommand.AdmissionDefinition input = nonNull(
                command.getAdmission(), "admission is required");
        requireRef(input.getSupplierId(), "supplierId", 128);
        SupplierProfile row = nonNull(mapper.selectSupplierForUpdate(tenantId, input.getSupplierId()),
                "supplier not found");
        requireExpectedVersion(input.getExpectedVersion(), row.getVersion());
        require("CANDIDATE".equals(row.getStatus()) && "UNDER_REVIEW".equals(row.getAdmissionStatus()),
                "only a supplier under admission review can be admitted");
        requireSha256(row.getQualificationEvidenceSha256(), "persisted qualification evidence");
        requireSha256(row.getRiskEvidenceSha256(), "persisted risk evidence");
        String reasonCode = normalizeReasonCode(input.getReasonCode());
        require(mapper.approveAdmission(tenantId, row.getSupplierId(), row.getVersion(),
                actorPrincipalId, reasonCode, now) == 1, "supplier admission approval conflict");
        return outcome("supplier.admission.approved", "supplier_profile", row.getSupplierId(),
                row.getVersion() + 1, "ADMITTED",
                payload("supplier_id", row.getSupplierId(), "supplier_code", row.getSupplierCode(),
                        "supplier_name", row.getSupplierName(), "admission_status", "ADMITTED",
                        "supplier_status", "ACTIVE"));
    }

    private Outcome createRfq(Long tenantId, SupplierSourcingCommand command,
                              String actorPrincipalId, LocalDateTime now) {
        SupplierSourcingCommand.SourcingCaseDefinition input = nonNull(
                command.getSourcingCase(), "sourcingCase is required");
        String caseId = valueOrUuid(input.getSourcingCaseId());
        requireRef(input.getRfqCode(), "rfqCode", 64);
        requireRef(input.getRequestRef(), "requestRef", 128);
        requireRef(input.getCanonicalSkuId(), "canonicalSkuId", 128);
        requirePositive(input.getTargetQuantity(), "targetQuantity");
        requireCode(input.getUomCode(), "uomCode");
        requireCurrency(input.getCurrencyCode());
        require(input.getMaxUnitCostMinor() != null && input.getMaxUnitCostMinor() >= 0,
                "maxUnitCostMinor must not be negative");
        require(input.getRequiredDeliveryDate() != null, "requiredDeliveryDate is required");
        requireText(input.getRequirements(), "requirements", 2000);
        SourcingCase row = new SourcingCase()
                .setSourcingCaseId(caseId).setTenantId(tenantId).setRfqCode(input.getRfqCode())
                .setRequestRef(input.getRequestRef()).setCanonicalSkuId(input.getCanonicalSkuId())
                .setTargetQuantity(input.getTargetQuantity()).setUomCode(upper(input.getUomCode()))
                .setCurrencyCode(upper(input.getCurrencyCode()))
                .setMaxUnitCostMinor(input.getMaxUnitCostMinor())
                .setRequiredDeliveryDate(input.getRequiredDeliveryDate())
                .setRequirements(input.getRequirements().trim()).setStatus("OPEN")
                .setCreatedByPrincipalId(actorPrincipalId).setVersion(1L)
                .setCreatedAt(now).setUpdatedAt(now);
        require(mapper.insertSourcingCase(row) == 1, "failed to persist sourcing RFQ");
        return outcome("supplier.sourcing_case.created", "supplier_sourcing_case", caseId, 1L,
                "OPEN", payload("sourcing_case_id", caseId, "rfq_code", row.getRfqCode(),
                        "request_ref", row.getRequestRef(), "canonical_sku_id", row.getCanonicalSkuId(),
                        "target_quantity", row.getTargetQuantity(), "currency_code", row.getCurrencyCode(),
                        "max_unit_cost_minor", row.getMaxUnitCostMinor(), "current_status", "OPEN"));
    }

    private Outcome submitQuote(Long tenantId, SupplierSourcingCommand command,
                                String actorPrincipalId, LocalDateTime now) {
        SupplierSourcingCommand.QuoteDefinition input = nonNull(command.getQuote(), "quote is required");
        requireRef(input.getSourcingCaseId(), "sourcingCaseId", 128);
        requireRef(input.getSupplierId(), "supplierId", 128);
        String quoteId = valueOrUuid(input.getQuoteId());
        require(input.getQuoteVersion() != null && input.getQuoteVersion() > 0,
                "quoteVersion must be positive");
        require(input.getUnitCostMinor() != null && input.getUnitCostMinor() >= 0,
                "unitCostMinor must not be negative");
        requirePositive(input.getMoq(), "moq");
        require(input.getLeadTimeDays() != null && input.getLeadTimeDays() >= 0,
                "leadTimeDays must not be negative");
        requirePositive(input.getCapacityQuantity(), "capacityQuantity");
        require(input.getValidUntil() != null
                        && !input.getValidUntil().isBefore(command.getOccurredAt().atZone(ZoneOffset.UTC).toLocalDate()),
                "quote validUntil must not be expired");
        requireText(input.getTermsSummary(), "termsSummary", 1000);
        SupplierProfile supplier = nonNull(mapper.selectSupplierForUpdate(tenantId, input.getSupplierId()),
                "supplier not found");
        require("ACTIVE".equals(supplier.getStatus()) && "ADMITTED".equals(supplier.getAdmissionStatus()),
                "only an admitted active supplier can quote");
        SourcingCase sourcingCase = nonNull(
                mapper.selectCaseForUpdate(tenantId, input.getSourcingCaseId()),
                "supplier sourcing case not found");
        requireExpectedVersion(input.getExpectedCaseVersion(), sourcingCase.getVersion());
        require(Set.of("OPEN", "QUOTING").contains(sourcingCase.getStatus()),
                "only an open or quoting sourcing case accepts quotes");
        Quote row = new Quote()
                .setQuoteId(quoteId).setTenantId(tenantId).setSourcingCaseId(sourcingCase.getSourcingCaseId())
                .setSupplierId(supplier.getSupplierId()).setQuoteVersion(input.getQuoteVersion())
                .setUnitCostMinor(input.getUnitCostMinor()).setMoq(input.getMoq())
                .setLeadTimeDays(input.getLeadTimeDays()).setCapacityQuantity(input.getCapacityQuantity())
                .setValidUntil(input.getValidUntil()).setTermsSummary(input.getTermsSummary().trim())
                .setStatus("SUBMITTED").setSubmittedByPrincipalId(actorPrincipalId)
                .setCreatedAt(now).setUpdatedAt(now);
        require(mapper.insertQuote(row) == 1, "failed to persist supplier quote");
        require(mapper.markQuoteSubmitted(tenantId, sourcingCase.getSourcingCaseId(),
                sourcingCase.getVersion(), now) == 1, "supplier quote case version conflict");
        return outcome("supplier.quote.submitted", "supplier_sourcing_case",
                sourcingCase.getSourcingCaseId(), sourcingCase.getVersion() + 1, "QUOTING",
                payload("sourcing_case_id", sourcingCase.getSourcingCaseId(), "rfq_code",
                        sourcingCase.getRfqCode(), "supplier_id", supplier.getSupplierId(),
                        "quote_id", quoteId, "quote_version", row.getQuoteVersion(),
                        "unit_cost_minor", row.getUnitCostMinor(), "current_status", "QUOTING"));
    }

    private Outcome recordSampleEvaluation(Long tenantId, SupplierSourcingCommand command,
                                           String actorPrincipalId, LocalDateTime now) {
        SupplierSourcingCommand.SampleEvaluationDefinition input = nonNull(
                command.getSampleEvaluation(), "sampleEvaluation is required");
        requireRef(input.getSourcingCaseId(), "sourcingCaseId", 128);
        requireRef(input.getSupplierId(), "supplierId", 128);
        requireRef(input.getQuoteId(), "quoteId", 128);
        String evaluationId = valueOrUuid(input.getEvaluationId());
        requireScore(input.getQualityScore(), "qualityScore");
        requireScore(input.getFitScore(), "fitScore");
        requireScore(input.getDeliveryScore(), "deliveryScore");
        requireScore(input.getRiskScore(), "riskScore");
        String result = upper(input.getResult());
        require(Set.of("PASS", "FAIL").contains(result), "sample result must be PASS or FAIL");
        requireText(input.getNotes(), "sample notes", 1000);
        SourcingCase sourcingCase = nonNull(
                mapper.selectCaseForUpdate(tenantId, input.getSourcingCaseId()),
                "supplier sourcing case not found");
        requireExpectedVersion(input.getExpectedCaseVersion(), sourcingCase.getVersion());
        require(Set.of("QUOTING", "EVALUATING").contains(sourcingCase.getStatus()),
                "sample evaluation requires a quoting or evaluating sourcing case");
        Quote quote = nonNull(mapper.selectQuote(tenantId, input.getQuoteId()), "supplier quote not found");
        require(Objects.equals(quote.getSourcingCaseId(), sourcingCase.getSourcingCaseId())
                        && Objects.equals(quote.getSupplierId(), input.getSupplierId())
                        && "SUBMITTED".equals(quote.getStatus()),
                "sample evaluation does not match an active submitted quote");
        SampleEvaluation row = new SampleEvaluation()
                .setEvaluationId(evaluationId).setTenantId(tenantId)
                .setSourcingCaseId(sourcingCase.getSourcingCaseId()).setSupplierId(input.getSupplierId())
                .setQuoteId(input.getQuoteId()).setQualityScore(input.getQualityScore())
                .setFitScore(input.getFitScore()).setDeliveryScore(input.getDeliveryScore())
                .setRiskScore(input.getRiskScore()).setResult(result).setNotes(input.getNotes().trim())
                .setEvaluatedByPrincipalId(actorPrincipalId).setEvaluatedAt(now);
        require(mapper.insertSampleEvaluation(row) == 1, "failed to persist sample evaluation");
        require(mapper.markSampleEvaluated(tenantId, sourcingCase.getSourcingCaseId(),
                sourcingCase.getVersion(), now) == 1, "sample evaluation case version conflict");
        return outcome("supplier.sample.evaluated", "supplier_sourcing_case",
                sourcingCase.getSourcingCaseId(), sourcingCase.getVersion() + 1, "EVALUATING",
                payload("sourcing_case_id", sourcingCase.getSourcingCaseId(), "supplier_id",
                        input.getSupplierId(), "quote_id", input.getQuoteId(),
                        "evaluation_id", evaluationId, "sample_result", result,
                        "quality_score", input.getQualityScore(), "current_status", "EVALUATING"));
    }

    private Outcome awardSupplier(Long tenantId, SupplierSourcingCommand command,
                                  String actorPrincipalId, LocalDateTime now) {
        SupplierSourcingCommand.AwardDefinition input = nonNull(command.getAward(), "award is required");
        requireRef(input.getSourcingCaseId(), "sourcingCaseId", 128);
        requireRef(input.getSupplierId(), "supplierId", 128);
        requireRef(input.getQuoteId(), "quoteId", 128);
        requireText(input.getDecisionRationale(), "decisionRationale", 2000);
        SourcingCase sourcingCase = nonNull(
                mapper.selectCaseForUpdate(tenantId, input.getSourcingCaseId()),
                "supplier sourcing case not found");
        requireExpectedVersion(input.getExpectedCaseVersion(), sourcingCase.getVersion());
        require("EVALUATING".equals(sourcingCase.getStatus()),
                "supplier award requires an evaluated sourcing case");
        require(mapper.countQuotedSuppliers(tenantId, sourcingCase.getSourcingCaseId()) >= 2,
                "supplier award requires comparable quotes from at least two suppliers");
        SupplierProfile supplier = nonNull(mapper.selectSupplierForUpdate(tenantId, input.getSupplierId()),
                "awarded supplier not found");
        require("ACTIVE".equals(supplier.getStatus()) && "ADMITTED".equals(supplier.getAdmissionStatus()),
                "awarded supplier must be active and admitted");
        Quote quote = nonNull(mapper.selectQuote(tenantId, input.getQuoteId()), "awarded quote not found");
        require(Objects.equals(quote.getSourcingCaseId(), sourcingCase.getSourcingCaseId())
                        && Objects.equals(quote.getSupplierId(), supplier.getSupplierId())
                        && "SUBMITTED".equals(quote.getStatus()),
                "awarded quote does not belong to the selected supplier and sourcing case");
        require(mapper.countPassingSample(tenantId, sourcingCase.getSourcingCaseId(),
                supplier.getSupplierId(), quote.getQuoteId()) > 0,
                "awarded quote requires a passing sample evaluation");
        require(quote.getUnitCostMinor() <= sourcingCase.getMaxUnitCostMinor(),
                "awarded quote exceeds sourcing cost ceiling");
        require(quote.getCapacityQuantity().compareTo(sourcingCase.getTargetQuantity()) >= 0,
                "awarded supplier capacity is below target quantity");
        LocalDate occurredDate = command.getOccurredAt().atZone(ZoneOffset.UTC).toLocalDate();
        require(!quote.getValidUntil().isBefore(occurredDate), "awarded quote has expired");
        require(mapper.awardCase(tenantId, sourcingCase.getSourcingCaseId(), sourcingCase.getVersion(),
                supplier.getSupplierId(), quote.getQuoteId(), input.getDecisionRationale().trim(),
                actorPrincipalId, now) == 1, "supplier award case version conflict");
        require(mapper.resolveQuotes(tenantId, sourcingCase.getSourcingCaseId(), quote.getQuoteId(), now) >= 2,
                "supplier quote comparison resolution is incomplete");
        return outcome("supplier.sourcing_case.awarded", "supplier_sourcing_case",
                sourcingCase.getSourcingCaseId(), sourcingCase.getVersion() + 1, "AWARDED",
                payload("sourcing_case_id", sourcingCase.getSourcingCaseId(), "rfq_code",
                        sourcingCase.getRfqCode(), "request_ref", sourcingCase.getRequestRef(),
                        "canonical_sku_id", sourcingCase.getCanonicalSkuId(),
                        "supplier_id", supplier.getSupplierId(), "supplier_name", supplier.getSupplierName(),
                        "quote_id", quote.getQuoteId(), "unit_cost_minor", quote.getUnitCostMinor(),
                        "moq", quote.getMoq(), "lead_time_days", quote.getLeadTimeDays(),
                        "capacity_quantity", quote.getCapacityQuantity(),
                        "decision_rationale", input.getDecisionRationale().trim(),
                        "current_status", "AWARDED"));
    }

    private void appendEvent(Long tenantId, SupplierSourcingCommand command, Outcome outcome) {
        outboxAppender.append(AppendDomainEventCommand.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(outcome.eventType())
                .schemaVersion(1)
                .sourceSystem(SOURCE_SYSTEM)
                .tenantId(tenantId)
                .aggregateType(outcome.aggregateType())
                .aggregateId(outcome.aggregateId())
                .aggregateVersion(outcome.version())
                .eventSequence((short) 1)
                .occurredAt(command.getOccurredAt())
                .traceId(command.getRunId())
                .correlationId(command.getCorrelationId())
                .causationId(command.getCausationId())
                .idempotencyKey(command.getIdempotencyKey() + ":" + outcome.version())
                .payload(outcome.payload())
                .headers(payload("run_id", command.getRunId(), "status", outcome.status()))
                .destination("lakehouse")
                .build());
    }

    private static void validateEnvelope(SupplierSourcingCommand command) {
        require(command != null, "supplier sourcing command is required");
        require(command.getOperation() != null, "operation is required");
        requireUuid(command.getCorrelationId(), "correlationId");
        require(command.getOccurredAt() != null, "occurredAt is required");
        requireRef(command.getIdempotencyKey(), "idempotencyKey", 128);
        if (command.getRunId() != null) {
            requireRef(command.getRunId(), "runId", 128);
        }
        if (command.getCausationId() != null) {
            requireRef(command.getCausationId(), "causationId", 128);
        }
    }

    private static String valueOrUuid(String value) {
        return value == null || value.isBlank() ? UUID.randomUUID().toString() : value;
    }

    private static void requireExpectedVersion(Long expected, Long actual) {
        require(expected != null && Objects.equals(expected, actual),
                "expectedVersion does not match current version");
    }

    private static void requireScore(Integer value, String field) {
        require(value != null && value >= 0 && value <= 100, field + " must be between 0 and 100");
    }

    private static void requirePositive(BigDecimal value, String field) {
        require(value != null && value.signum() > 0, field + " must be positive");
    }

    private static void requireCurrency(String value) {
        require(value != null && value.matches("[A-Za-z]{3}"), "currencyCode must be ISO alpha-3");
    }

    private static void requireCode(String value, String field) {
        require(value != null && SAFE_CODE.matcher(upper(value)).matches(), field + " is invalid");
    }

    private static void requireRef(String value, String field, int maxLength) {
        require(value != null && value.length() <= maxLength && SAFE_REF.matcher(value).matches(),
                field + " is invalid");
    }

    private static void requireText(String value, String field, int maxLength) {
        require(value != null && !value.isBlank() && value.length() <= maxLength, field + " is invalid");
    }

    private static void requireSha256(String value, String field) {
        require(value != null && SHA256.matcher(value).matches(), field + " must be lowercase SHA-256");
    }

    private static void requireUuid(String value, String field) {
        try {
            UUID.fromString(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(field + " must be a UUID", exception);
        }
    }

    private static String normalizeReasonCode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = upper(value);
        requireCode(normalized, "reasonCode");
        return normalized;
    }

    private static String upper(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private static <T> T nonNull(T value, String message) {
        if (value == null) {
            throw new IllegalStateException(message);
        }
        return value;
    }

    private static Map<String, Object> payload(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            if (values[index + 1] != null) {
                result.put(values[index].toString(), values[index + 1]);
            }
        }
        return result;
    }

    private static Outcome outcome(String eventType, String aggregateType, String aggregateId,
                                   Long version, String status, Map<String, Object> payload) {
        return new Outcome(eventType, aggregateType, aggregateId, version, status, payload);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private record Outcome(String eventType, String aggregateType, String aggregateId,
                           Long version, String status, Map<String, Object> payload) {
    }
}
