package cn.iocoder.yudao.module.cloudmold.supplier.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.supplier.api.SupplierProfileCommand;
import cn.iocoder.yudao.module.cloudmold.supplier.api.SupplierProfileCommandApi;
import cn.iocoder.yudao.module.cloudmold.supplier.api.SupplierProfileResult;
import cn.iocoder.yudao.module.cloudmold.supplier.dal.dataobject.SupplierProfileRecords.Operation;
import cn.iocoder.yudao.module.cloudmold.supplier.dal.dataobject.SupplierProfileRecords.Profile;
import cn.iocoder.yudao.module.cloudmold.supplier.dal.mysql.SupplierProfileMapper;
import cn.iocoder.yudao.module.cloudmold.supplier.service.actor.SupplierActorPrincipalPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
public class SupplierProfileServiceImpl implements SupplierProfileCommandApi {
    private static final int OPERATION_SUCCEEDED = 10;
    private static final String SOURCE_SYSTEM = "cloudmold-supplier";
    private static final Pattern SAFE_REF = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:/-]{0,255}");
    private static final Pattern SAFE_CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Set<String> RISK_LEVELS = Set.of("LOW", "MEDIUM", "HIGH");

    private final SupplierProfileMapper mapper;
    private final OutboxAppender outboxAppender;
    private final SupplierActorPrincipalPort actorPrincipalPort;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SupplierProfileResult execute(SupplierProfileCommand command, String actorPrincipalId) {
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
        require(operationId != null && operationId > 0, "failed to resolve supplier profile operation");
        Operation operation = mapper.selectOperationForUpdate(operationId, tenantId);
        require(operation != null, "supplier profile operation disappeared");
        if (!attemptToken.equals(operation.getAttemptToken())) {
            require(Objects.equals(requestHash, operation.getRequestHash()),
                    "idempotency key conflicts with a different supplier profile payload");
            require(operation.getStatus() == OPERATION_SUCCEEDED && operation.getResultJson() != null,
                    "existing supplier profile operation is incomplete");
            SupplierProfileResult replay = JsonUtils.parseObject(operation.getResultJson(), SupplierProfileResult.class);
            replay.setDuplicate(true);
            return replay;
        }

        Outcome outcome = switch (command.getOperation()) {
            case REGISTER_SUPPLIER -> registerSupplier(tenantId, command, actorPrincipalId, now);
            case SUBMIT_ADMISSION -> submitAdmission(tenantId, command, actorPrincipalId, now);
            case APPROVE_ADMISSION -> approveAdmission(tenantId, command, actorPrincipalId, now);
        };
        appendEvent(tenantId, command, outcome);
        SupplierProfileResult result = SupplierProfileResult.builder()
                .operationId(operationId.toString())
                .duplicate(false)
                .supplierId(outcome.supplierId())
                .aggregateVersion(outcome.version())
                .supplierStatus(outcome.supplierStatus())
                .admissionStatus(outcome.admissionStatus())
                .build();
        require(mapper.markOperationSucceeded(operationId, tenantId, outcome.supplierId(),
                JsonUtils.toJsonString(result), now) == 1, "supplier profile operation completion conflict");
        return result;
    }

    private Outcome registerSupplier(Long tenantId, SupplierProfileCommand command,
                                     String actorPrincipalId, LocalDateTime now) {
        SupplierProfileCommand.SupplierDefinition input = nonNull(command.getSupplier(), "supplier is required");
        String supplierId = valueOrUuid(input.getSupplierId());
        requireRef(input.getSupplierCode(), "supplierCode", 64);
        requireText(input.getSupplierName(), "supplierName", 191);
        String countryCode = upper(input.getCountryCode());
        require(countryCode != null && countryCode.matches("[A-Z]{2}"),
                "countryCode must be ISO alpha-2");
        requireText(input.getCapabilitySummary(), "capabilitySummary", 1000);
        String riskLevel = upper(input.getRiskLevel());
        require(RISK_LEVELS.contains(riskLevel), "riskLevel must be LOW, MEDIUM, or HIGH");
        Profile profile = new Profile()
                .setSupplierId(supplierId).setTenantId(tenantId)
                .setSupplierCode(input.getSupplierCode()).setSupplierName(input.getSupplierName().trim())
                .setCountryCode(countryCode).setCapabilitySummary(input.getCapabilitySummary().trim())
                .setRiskLevel(riskLevel).setStatus("CANDIDATE").setAdmissionStatus("DRAFT")
                .setCreatedByPrincipalId(actorPrincipalId).setReasonCode(normalizeReasonCode(input.getReasonCode()))
                .setVersion(1L).setCreatedAt(now).setUpdatedAt(now);
        require(mapper.insertProfile(profile) == 1, "failed to persist supplier profile");
        return new Outcome("supplier.profile.registered", supplierId, 1L, "CANDIDATE", "DRAFT",
                payload("supplier_id", supplierId, "supplier_code", profile.getSupplierCode(),
                        "supplier_name", profile.getSupplierName(), "risk_level", riskLevel,
                        "admission_status", "DRAFT"));
    }

    private Outcome submitAdmission(Long tenantId, SupplierProfileCommand command,
                                    String actorPrincipalId, LocalDateTime now) {
        SupplierProfileCommand.AdmissionDefinition input = nonNull(command.getAdmission(), "admission is required");
        requireRef(input.getSupplierId(), "supplierId", 128);
        requireSha256(input.getQualificationEvidenceSha256(), "qualificationEvidenceSha256");
        requireSha256(input.getRiskEvidenceSha256(), "riskEvidenceSha256");
        Profile profile = nonNull(mapper.selectProfileForUpdate(tenantId, input.getSupplierId()),
                "supplier not found");
        requireExpectedVersion(input.getExpectedVersion(), profile.getVersion());
        require("CANDIDATE".equals(profile.getStatus()) && "DRAFT".equals(profile.getAdmissionStatus()),
                "only a draft candidate supplier can enter admission review");
        require(mapper.submitAdmission(tenantId, profile.getSupplierId(), profile.getVersion(), actorPrincipalId,
                input.getQualificationEvidenceSha256(), input.getRiskEvidenceSha256(),
                normalizeReasonCode(input.getReasonCode()), now) == 1,
                "supplier admission submission conflict");
        return new Outcome("supplier.admission.submitted", profile.getSupplierId(), profile.getVersion() + 1,
                "CANDIDATE", "UNDER_REVIEW", payload("supplier_id", profile.getSupplierId(),
                "supplier_code", profile.getSupplierCode(), "admission_status", "UNDER_REVIEW"));
    }

    private Outcome approveAdmission(Long tenantId, SupplierProfileCommand command,
                                     String actorPrincipalId, LocalDateTime now) {
        SupplierProfileCommand.AdmissionDefinition input = nonNull(command.getAdmission(), "admission is required");
        requireRef(input.getSupplierId(), "supplierId", 128);
        Profile profile = nonNull(mapper.selectProfileForUpdate(tenantId, input.getSupplierId()),
                "supplier not found");
        requireExpectedVersion(input.getExpectedVersion(), profile.getVersion());
        require("CANDIDATE".equals(profile.getStatus()) && "UNDER_REVIEW".equals(profile.getAdmissionStatus()),
                "only a supplier under admission review can be admitted");
        requireSha256(profile.getQualificationEvidenceSha256(), "persisted qualification evidence");
        requireSha256(profile.getRiskEvidenceSha256(), "persisted risk evidence");
        require(profile.getSubmittedByPrincipalId() != null
                        && !actorPrincipalId.equals(profile.getSubmittedByPrincipalId()),
                "supplier admission approval requires an independent reviewer");
        require(mapper.approveAdmission(tenantId, profile.getSupplierId(), profile.getVersion(), actorPrincipalId,
                normalizeReasonCode(input.getReasonCode()), now) == 1,
                "supplier admission approval conflict");
        return new Outcome("supplier.admission.approved", profile.getSupplierId(), profile.getVersion() + 1,
                "ACTIVE", "ADMITTED", payload("supplier_id", profile.getSupplierId(),
                "supplier_code", profile.getSupplierCode(), "supplier_name", profile.getSupplierName(),
                "admission_status", "ADMITTED", "supplier_status", "ACTIVE"));
    }

    private void appendEvent(Long tenantId, SupplierProfileCommand command, Outcome outcome) {
        outboxAppender.append(AppendDomainEventCommand.builder()
                .eventId(UUID.randomUUID().toString()).eventType(outcome.eventType()).schemaVersion(1)
                .sourceSystem(SOURCE_SYSTEM).tenantId(tenantId).aggregateType("supplier_profile")
                .aggregateId(outcome.supplierId()).aggregateVersion(outcome.version()).eventSequence((short) 1)
                .occurredAt(command.getOccurredAt()).traceId(command.getRunId())
                .correlationId(command.getCorrelationId()).causationId(command.getCausationId())
                .idempotencyKey(command.getIdempotencyKey() + ":" + outcome.version())
                .payload(outcome.payload())
                .headers(payload("run_id", command.getRunId(), "supplier_status", outcome.supplierStatus(),
                        "admission_status", outcome.admissionStatus()))
                .destination("lakehouse").build());
    }

    private static void validateEnvelope(SupplierProfileCommand command) {
        require(command != null, "supplier profile command is required");
        require(command.getOperation() != null, "operation is required");
        requireUuid(command.getCorrelationId(), "correlationId");
        require(command.getOccurredAt() != null, "occurredAt is required");
        requireRef(command.getIdempotencyKey(), "idempotencyKey", 128);
        if (command.getRunId() != null) requireRef(command.getRunId(), "runId", 128);
        if (command.getCausationId() != null) requireRef(command.getCausationId(), "causationId", 128);
    }

    private static String valueOrUuid(String value) {
        return value == null || value.isBlank() ? UUID.randomUUID().toString() : value;
    }

    private static void requireExpectedVersion(Long expected, Long actual) {
        require(expected != null && Objects.equals(expected, actual),
                "expectedVersion does not match current version");
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
        if (value == null || value.isBlank()) return null;
        String normalized = upper(value);
        require(SAFE_CODE.matcher(normalized).matches(), "reasonCode is invalid");
        return normalized;
    }

    private static String upper(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private static <T> T nonNull(T value, String message) {
        if (value == null) throw new IllegalStateException(message);
        return value;
    }

    private static Map<String, Object> payload(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            if (values[index + 1] != null) result.put(values[index].toString(), values[index + 1]);
        }
        return result;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    private record Outcome(String eventType, String supplierId, Long version,
                           String supplierStatus, String admissionStatus, Map<String, Object> payload) {
    }
}
