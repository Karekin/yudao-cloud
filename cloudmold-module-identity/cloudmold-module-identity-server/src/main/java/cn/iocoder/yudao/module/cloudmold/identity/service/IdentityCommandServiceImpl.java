package cn.iocoder.yudao.module.cloudmold.identity.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.identity.api.*;
import cn.iocoder.yudao.module.cloudmold.identity.api.source.SourceAccountValidationPort;
import cn.iocoder.yudao.module.cloudmold.identity.dal.dataobject.*;
import cn.iocoder.yudao.module.cloudmold.identity.dal.mysql.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class IdentityCommandServiceImpl implements IdentityCommandApi {

    static final int OPERATION_SUCCEEDED = 10;
    private static final Set<String> PRINCIPAL_TYPES = Set.of(
            "CONSUMER", "PLATFORM_OPERATOR", "MERCHANT_OPERATOR", "SERVICE");
    private static final Pattern TYPE_CODE = Pattern.compile("^[A-Z][A-Z0-9_]*$");

    private final IdentityOperationMapper operationMapper;
    private final PrincipalMapper principalMapper;
    private final SourceIdentityMapper sourceIdentityMapper;
    private final List<SourceAccountValidationPort> sourceAccountValidators;
    private final OutboxAppender outboxAppender;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LinkSourceIdentityResult linkSource(LinkSourceIdentityCommand command) {
        validate(command);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String requestHash = fingerprint(tenantId, command);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String attemptToken = UUID.randomUUID().toString();
        operationMapper.insertOrResolve(tenantId, command.getIdempotencyKey(), "LINK_SOURCE",
                requestHash, attemptToken, now);
        Long operationId = operationMapper.selectLastInsertId();
        require(operationId != null && operationId > 0, "failed to resolve identity operation");
        IdentityOperationDO operation = operationMapper.selectForUpdate(operationId, tenantId);
        require(operation != null, "identity operation disappeared");
        if (!attemptToken.equals(operation.getAttemptToken())) {
            require(Objects.equals(operation.getRequestHash(), requestHash),
                    "idempotency key conflicts with different identity payload");
            require(operation.getStatus() == OPERATION_SUCCEEDED && operation.getResultJson() != null,
                    "existing identity operation is not complete");
            LinkSourceIdentityResult replay = JsonUtils.parseObject(
                    operation.getResultJson(), LinkSourceIdentityResult.class);
            replay.setDuplicate(true);
            return replay;
        }

        SourceAccountValidationPort validator = resolveValidator(command);
        validator.requireActive(tenantId, command.getSourceSystem(), command.getSourceType(), command.getSourceId());
        require(sourceIdentityMapper.selectActiveBySource(tenantId, command.getSourceSystem(),
                command.getSourceType(), command.getSourceId()) == null, "source identity is already linked");

        PrincipalDO principal = resolvePrincipal(tenantId, command, now);
        require(!principal.getPrincipalId().equals(command.getSourceId()),
                "principalId must not equal sourceId");
        SourceIdentityDO source = new SourceIdentityDO().setSourceIdentityId(UUID.randomUUID().toString())
                .setTenantId(tenantId).setPrincipalId(principal.getPrincipalId())
                .setSourceSystem(command.getSourceSystem()).setSourceType(command.getSourceType())
                .setSourceId(command.getSourceId()).setStatus("ACTIVE").setVersion(1L)
                .setValidFrom(LocalDateTime.ofInstant(command.getOccurredAt(), ZoneOffset.UTC))
                .setCreatedAt(now).setUpdatedAt(now);
        sourceIdentityMapper.insert(source);
        appendEvent(tenantId, principal, source, command);

        LinkSourceIdentityResult result = LinkSourceIdentityResult.builder().operationId(operationId)
                .principalId(principal.getPrincipalId()).sourceIdentityId(source.getSourceIdentityId())
                .principalStatus(principal.getStatus()).aggregateVersion(principal.getVersion())
                .duplicate(false).build();
        require(operationMapper.markSucceeded(operationId, tenantId, principal.getPrincipalId(),
                JsonUtils.toJsonString(result), now) == 1, "identity operation completion conflict");
        return result;
    }

    private PrincipalDO resolvePrincipal(Long tenantId, LinkSourceIdentityCommand command, LocalDateTime now) {
        if (command.getPrincipalId() == null) {
            String principalId = UUID.randomUUID().toString();
            PrincipalDO principal = new PrincipalDO().setPrincipalId(principalId).setTenantId(tenantId)
                    .setPrincipalType(command.getPrincipalType()).setStatus("ACTIVE").setVersion(1L)
                    .setCreatedAt(now).setUpdatedAt(now);
            principalMapper.insert(principal);
            return principal;
        }
        PrincipalDO principal = principalMapper.selectForUpdate(tenantId, command.getPrincipalId());
        require(principal != null, "canonical Principal does not exist");
        require("ACTIVE".equals(principal.getStatus()), "canonical Principal is not ACTIVE");
        require(Objects.equals(principal.getPrincipalType(), command.getPrincipalType()),
                "principalType does not match canonical Principal");
        require(Objects.equals(principal.getVersion(), command.getExpectedVersion()),
                "canonical Principal version conflict");
        require(principalMapper.advanceVersion(tenantId, principal.getPrincipalId(),
                principal.getVersion(), now) == 1, "canonical Principal version conflict");
        return principal.setVersion(principal.getVersion() + 1).setUpdatedAt(now);
    }

    private SourceAccountValidationPort resolveValidator(LinkSourceIdentityCommand command) {
        List<SourceAccountValidationPort> matches = sourceAccountValidators.stream()
                .filter(port -> port.supports(command.getSourceSystem(), command.getSourceType())).toList();
        require(matches.size() == 1, matches.isEmpty()
                ? "source account type is not supported" : "source account type has ambiguous validators");
        return matches.get(0);
    }

    private void appendEvent(Long tenantId, PrincipalDO principal, SourceIdentityDO source,
                             LinkSourceIdentityCommand command) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("run_id", command.getRunId());
        payload.put("principal_id", principal.getPrincipalId());
        payload.put("principal_type", principal.getPrincipalType());
        payload.put("principal_status", principal.getStatus());
        payload.put("source_identity_id", source.getSourceIdentityId());
        payload.put("source_system", source.getSourceSystem());
        payload.put("source_type", source.getSourceType());
        payload.put("source_id", source.getSourceId());
        payload.put("source_status", source.getStatus());
        outboxAppender.append(AppendDomainEventCommand.builder().eventType("identity.source.linked")
                .schemaVersion(1).sourceSystem("cloudmold-identity").tenantId(tenantId)
                .aggregateType("identity_principal").aggregateId(principal.getPrincipalId())
                .aggregateVersion(principal.getVersion()).eventSequence((short) 1)
                .occurredAt(command.getOccurredAt()).correlationId(command.getCorrelationId())
                .causationId(command.getCausationId()).idempotencyKey(command.getIdempotencyKey())
                .payload(payload).headers(Map.of("operation", "LINK_SOURCE"))
                .destination("lakehouse").build());
    }

    private static void validate(LinkSourceIdentityCommand command) {
        require(command != null, "identity command is required");
        requireText(command.getIdempotencyKey(), "idempotencyKey", 128);
        require(command.getIdempotencyKey().length() >= 8, "idempotencyKey is too short");
        requireText(command.getRunId(), "runId", 64);
        requireText(command.getPrincipalType(), "principalType", 32);
        require(PRINCIPAL_TYPES.contains(command.getPrincipalType()), "principalType is not supported");
        requireTypeCode(command.getSourceSystem(), "sourceSystem");
        requireTypeCode(command.getSourceType(), "sourceType");
        requireText(command.getSourceId(), "sourceId", 128);
        require(command.getOccurredAt() != null, "occurredAt is required");
        requireUuid(command.getCorrelationId(), "correlationId");
        if (command.getCausationId() != null) requireUuid(command.getCausationId(), "causationId");
        if (command.getPrincipalId() == null) {
            require(command.getExpectedVersion() == null, "new Principal does not accept expectedVersion");
        } else {
            requireUuid(command.getPrincipalId(), "principalId");
            require(command.getExpectedVersion() != null && command.getExpectedVersion() > 0,
                    "existing Principal requires positive expectedVersion");
        }
    }

    private static String fingerprint(Long tenantId, LinkSourceIdentityCommand command) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("tenant_id", tenantId);
        value.put("run_id", command.getRunId());
        value.put("principal_id", command.getPrincipalId());
        value.put("expected_version", command.getExpectedVersion());
        value.put("principal_type", command.getPrincipalType());
        value.put("source_system", command.getSourceSystem());
        value.put("source_type", command.getSourceType());
        value.put("source_id", command.getSourceId());
        value.put("occurred_at", command.getOccurredAt());
        return DigestUtil.sha256Hex(JsonUtils.toJsonString(value));
    }

    private static void requireTypeCode(String value, String field) {
        requireText(value, field, 32);
        require(TYPE_CODE.matcher(value).matches(), field + " must be an uppercase type code");
    }

    private static void requireUuid(String value, String field) {
        requireText(value, field, 36);
        try { UUID.fromString(value); } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException(field + " must be a UUID", error);
        }
    }

    private static void requireText(String value, String field, int maxLength) {
        require(value != null && !value.isBlank() && value.length() <= maxLength, field + " is required");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
