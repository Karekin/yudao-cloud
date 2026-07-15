package cn.iocoder.yudao.module.cloudmold.tokenplatform.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.tokenplatform.api.*;
import cn.iocoder.yudao.module.cloudmold.tokenplatform.dal.dataobject.*;
import cn.iocoder.yudao.module.cloudmold.tokenplatform.dal.mysql.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class TokenPlatformServiceImpl implements TokenPlatformCommandApi, TokenPlatformQueryApi {

    static final int OPERATION_SUCCEEDED = 10;
    private static final BigInteger TOKEN_PRICE_DENOMINATOR = BigInteger.valueOf(1_000_000L);

    private final TokenPlatformOperationMapper operationMapper;
    private final ModelOfferingMapper offeringMapper;
    private final PricingVersionMapper pricingMapper;
    private final AccessCredentialMapper credentialMapper;
    private final QuotaAccountMapper accountMapper;
    private final QuotaLedgerMapper ledgerMapper;
    private final InvocationUsageMapper usageMapper;
    private final OutboxAppender outboxAppender;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TokenPlatformCommandResult execute(TokenPlatformCommand command) {
        validateEnvelope(command);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String requestHash = fingerprint(tenantId, command);
        String attemptToken = UUID.randomUUID().toString();
        operationMapper.insertOrResolve(tenantId, command.getIdempotencyKey(), command.getOperation().name(),
                requestHash, attemptToken, now);
        Long operationId = operationMapper.selectLastInsertId();
        require(operationId != null && operationId > 0, "failed to resolve token platform operation");
        TokenPlatformOperationDO operation = operationMapper.selectForUpdate(operationId, tenantId);
        require(operation != null, "token platform operation disappeared");
        if (!attemptToken.equals(operation.getAttemptToken())) {
            require(Objects.equals(requestHash, operation.getRequestHash()),
                    "idempotency key conflicts with different payload");
            require(operation.getStatus() == OPERATION_SUCCEEDED && operation.getResultJson() != null,
                    "existing token platform operation is not complete");
            TokenPlatformCommandResult replay = JsonUtils.parseObject(operation.getResultJson(),
                    TokenPlatformCommandResult.class);
            replay.setDuplicate(true);
            return replay;
        }

        Outcome outcome = switch (command.getOperation()) {
            case CREATE_MODEL_OFFERING -> createOffering(tenantId, command);
            case ADD_PRICING_VERSION -> addPricingVersion(tenantId, command, now);
            case ACTIVATE_MODEL_OFFERING, SUSPEND_MODEL_OFFERING, RETIRE_MODEL_OFFERING ->
                    changeOffering(tenantId, command, now);
            case CREATE_ACCESS_CREDENTIAL -> createCredential(tenantId, command);
            case ACTIVATE_ACCESS_CREDENTIAL, REVOKE_ACCESS_CREDENTIAL ->
                    changeCredential(tenantId, command, now);
            case OPEN_QUOTA_ACCOUNT -> openQuotaAccount(tenantId, operationId, command);
            case POST_QUOTA_LEDGER -> postQuotaLedger(tenantId, operationId, command, now);
            case RECORD_INVOCATION_USAGE -> recordInvocationUsage(tenantId, operationId, command, now);
        };
        appendEvents(tenantId, command, outcome.events());
        TokenPlatformCommandResult result = outcome.result();
        result.setOperationId(operationId);
        require(operationMapper.markSucceeded(operationId, tenantId, result.getAggregateType(),
                result.getAggregateId(), JsonUtils.toJsonString(result), now) == 1,
                "token platform operation completion conflict");
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public TokenPlatformAggregateView get(String aggregateType, String aggregateId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        requireText(aggregateId, "aggregateId", 64);
        return switch (normalized(aggregateType)) {
            case "TOKEN_PLATFORM_MODEL_OFFERING" -> offeringView(requireNonNull(
                    offeringMapper.selectOneById(tenantId, aggregateId), "model offering not found"));
            case "TOKEN_PLATFORM_ACCESS_CREDENTIAL" -> credentialView(requireNonNull(
                    credentialMapper.selectOneById(tenantId, aggregateId), "access credential not found"));
            case "TOKEN_PLATFORM_QUOTA_ACCOUNT" -> accountView(requireNonNull(
                    accountMapper.selectOneById(tenantId, aggregateId), "quota account not found"));
            case "TOKEN_PLATFORM_INVOCATION_USAGE" -> usageView(requireNonNull(
                    usageMapper.selectOneById(tenantId, aggregateId), "invocation usage not found"));
            default -> throw new IllegalArgumentException("unsupported aggregateType");
        };
    }

    private Outcome createOffering(Long tenantId, TokenPlatformCommand command) {
        TokenPlatformCommand.ModelOfferingDefinition input = requireNonNull(command.getModelOffering(),
                "modelOffering is required");
        requireText(input.getOfferingCode(), "offeringCode", 64);
        requireText(input.getProviderCode(), "providerCode", 64);
        requireText(input.getModelCode(), "modelCode", 128);
        require(offeringMapper.selectByCode(tenantId, input.getOfferingCode()) == null,
                "offeringCode already exists");
        TokenPlatformCommand.PricingVersionDefinition pricing = validatePricing(input.getPricing());
        String offeringId = valueOrUuid(input.getOfferingId());
        String pricingVersionId = valueOrUuid(pricing.getPricingVersionId());
        LocalDateTime occurredAt = at(command.getOccurredAt());
        ModelOfferingDO row = new ModelOfferingDO().setOfferingId(offeringId).setTenantId(tenantId)
                .setOfferingCode(input.getOfferingCode().trim()).setProviderCode(normalized(input.getProviderCode()))
                .setModelCode(input.getModelCode().trim()).setCurrentPricingVersionId(pricingVersionId)
                .setStatus("DRAFT").setVersion(1L).setCreatedAt(occurredAt).setUpdatedAt(occurredAt);
        offeringMapper.insert(row);
        pricingMapper.insert(pricingRow(tenantId, offeringId, pricingVersionId, 1L, pricing, occurredAt));
        EventFact event = offeringEvent(row, null, "DRAFT", command.getOperation(), 1L);
        return outcome(event, "token_platform_model_offering", offeringId, 1L, "DRAFT");
    }

    private Outcome addPricingVersion(Long tenantId, TokenPlatformCommand command, LocalDateTime now) {
        TokenPlatformCommand.ModelOfferingDefinition input = requireNonNull(command.getModelOffering(),
                "modelOffering is required");
        requireText(input.getOfferingId(), "offeringId", 64);
        ModelOfferingDO row = requireNonNull(offeringMapper.selectForUpdate(tenantId, input.getOfferingId()),
                "model offering not found");
        requireVersion(row.getVersion(), input.getExpectedVersion());
        require(!"RETIRED".equals(row.getStatus()), "retired model offering cannot receive pricing");
        TokenPlatformCommand.PricingVersionDefinition pricing = validatePricing(input.getPricing());
        String pricingVersionId = valueOrUuid(pricing.getPricingVersionId());
        require(pricingMapper.selectOneById(tenantId, pricingVersionId) == null,
                "pricingVersionId already exists");
        pricingMapper.insert(pricingRow(tenantId, row.getOfferingId(), pricingVersionId, row.getVersion() + 1,
                pricing, at(command.getOccurredAt())));
        require(offeringMapper.updatePricingCas(tenantId, row.getOfferingId(), row.getVersion(), pricingVersionId,
                now) == 1, "model offering version conflict");
        row.setCurrentPricingVersionId(pricingVersionId);
        EventFact event = offeringEvent(row, row.getStatus(), row.getStatus(), command.getOperation(),
                row.getVersion() + 1);
        return outcome(event, "token_platform_model_offering", row.getOfferingId(), row.getVersion() + 1,
                row.getStatus());
    }

    private Outcome changeOffering(Long tenantId, TokenPlatformCommand command, LocalDateTime now) {
        TokenPlatformCommand.ModelOfferingDefinition input = requireNonNull(command.getModelOffering(),
                "modelOffering is required");
        requireText(input.getOfferingId(), "offeringId", 64);
        ModelOfferingDO row = requireNonNull(offeringMapper.selectForUpdate(tenantId, input.getOfferingId()),
                "model offering not found");
        requireVersion(row.getVersion(), input.getExpectedVersion());
        String next = offeringTransition(row.getStatus(), command.getOperation());
        require(offeringMapper.updateStatusCas(tenantId, row.getOfferingId(), row.getVersion(), next, now) == 1,
                "model offering version conflict");
        EventFact event = offeringEvent(row, row.getStatus(), next, command.getOperation(), row.getVersion() + 1);
        return outcome(event, "token_platform_model_offering", row.getOfferingId(), row.getVersion() + 1, next);
    }

    private Outcome createCredential(Long tenantId, TokenPlatformCommand command) {
        TokenPlatformCommand.AccessCredentialDefinition input = requireNonNull(command.getAccessCredential(),
                "accessCredential is required");
        validateCredentialDefinition(input);
        ModelOfferingDO offering = requireNonNull(offeringMapper.selectForUpdate(tenantId, input.getOfferingId()),
                "model offering not found");
        require("ACTIVE".equals(offering.getStatus()), "model offering must be ACTIVE");
        String credentialId = valueOrUuid(input.getCredentialId());
        LocalDateTime occurredAt = at(command.getOccurredAt());
        AccessCredentialDO row = new AccessCredentialDO().setCredentialId(credentialId).setTenantId(tenantId)
                .setPrincipalId(input.getPrincipalId().trim()).setOfferingId(input.getOfferingId().trim())
                .setCredentialFingerprint(input.getCredentialFingerprint().trim().toLowerCase(Locale.ROOT))
                .setSecretRef(input.getSecretRef().trim()).setLast4(input.getLast4()).setKeyVersion(input.getKeyVersion())
                .setStatus("INACTIVE").setExpiresAt(at(input.getExpiresAt())).setVersion(1L)
                .setCreatedAt(occurredAt).setUpdatedAt(occurredAt);
        credentialMapper.insert(row);
        EventFact event = credentialEvent(row, null, "INACTIVE", command.getOperation(), 1L);
        return outcome(event, "token_platform_access_credential", credentialId, 1L, "INACTIVE");
    }

    private Outcome changeCredential(Long tenantId, TokenPlatformCommand command, LocalDateTime now) {
        TokenPlatformCommand.AccessCredentialDefinition input = requireNonNull(command.getAccessCredential(),
                "accessCredential is required");
        requireText(input.getCredentialId(), "credentialId", 64);
        AccessCredentialDO row = requireNonNull(credentialMapper.selectForUpdate(tenantId, input.getCredentialId()),
                "access credential not found");
        requireVersion(row.getVersion(), input.getExpectedVersion());
        String next = credentialTransition(row.getStatus(), command.getOperation());
        if ("ACTIVE".equals(next)) {
            ModelOfferingDO offering = requireNonNull(offeringMapper.selectForUpdate(tenantId, row.getOfferingId()),
                    "model offering not found");
            require("ACTIVE".equals(offering.getStatus()), "model offering must be ACTIVE");
            require(row.getExpiresAt() == null || at(command.getOccurredAt()).isBefore(row.getExpiresAt()),
                    "access credential is expired");
        }
        require(credentialMapper.updateStatusCas(tenantId, row.getCredentialId(), row.getVersion(), next, now) == 1,
                "access credential version conflict");
        EventFact event = credentialEvent(row, row.getStatus(), next, command.getOperation(), row.getVersion() + 1);
        return outcome(event, "token_platform_access_credential", row.getCredentialId(), row.getVersion() + 1,
                next);
    }

    private Outcome openQuotaAccount(Long tenantId, Long operationId, TokenPlatformCommand command) {
        TokenPlatformCommand.QuotaAccountDefinition input = requireNonNull(command.getQuotaAccount(),
                "quotaAccount is required");
        requireText(input.getPrincipalId(), "principalId", 64);
        require(accountMapper.selectByPrincipal(tenantId, input.getPrincipalId()) == null,
                "principal already has a quota account");
        String accountId = valueOrUuid(input.getAccountId());
        String ledgerEntryId = UUID.randomUUID().toString();
        LocalDateTime occurredAt = at(command.getOccurredAt());
        QuotaAccountDO account = new QuotaAccountDO().setAccountId(accountId).setTenantId(tenantId)
                .setPrincipalId(input.getPrincipalId().trim()).setBalanceMicrounits(0L).setStatus("ACTIVE")
                .setVersion(1L).setCreatedAt(occurredAt).setUpdatedAt(occurredAt);
        accountMapper.insert(account);
        QuotaLedgerDO ledger = ledgerRow(tenantId, operationId, account, ledgerEntryId, "OPENING", 0L, 0L, 1L,
                "QUOTA_ACCOUNT", accountId, occurredAt);
        ledgerMapper.insert(ledger);
        EventFact event = quotaEvent(ledger);
        return outcome(event, "token_platform_quota_account", accountId, 1L, "ACTIVE");
    }

    private Outcome postQuotaLedger(Long tenantId, Long operationId, TokenPlatformCommand command,
                                    LocalDateTime now) {
        TokenPlatformCommand.QuotaLedgerDefinition input = requireNonNull(command.getQuotaLedger(),
                "quotaLedger is required");
        requireText(input.getAccountId(), "accountId", 64);
        requireVersionNonNull(input.getExpectedVersion(), "expectedVersion");
        String entryType = normalized(input.getEntryType());
        require(Set.of("ALLOCATION", "ADJUSTMENT", "REVERSAL", "EXPIRATION").contains(entryType),
                "invalid quota ledger entryType");
        require(input.getSignedDeltaMicrounits() != null && input.getSignedDeltaMicrounits() != 0,
                "signedDeltaMicrounits must be non-zero");
        requireText(input.getReferenceType(), "referenceType", 64);
        requireText(input.getReferenceId(), "referenceId", 128);
        QuotaAccountDO account = requireNonNull(accountMapper.selectForUpdate(tenantId, input.getAccountId()),
                "quota account not found");
        require("ACTIVE".equals(account.getStatus()), "quota account must be ACTIVE");
        requireVersion(account.getVersion(), input.getExpectedVersion());
        long balanceAfter = addExact(account.getBalanceMicrounits(), input.getSignedDeltaMicrounits(),
                "quota balance overflow");
        require(balanceAfter >= 0, "quota balance cannot become negative");
        require(accountMapper.updateBalanceCas(tenantId, account.getAccountId(), account.getVersion(), balanceAfter,
                now) == 1, "quota account version conflict");
        String ledgerEntryId = valueOrUuid(input.getLedgerEntryId());
        QuotaLedgerDO ledger = ledgerRow(tenantId, operationId, account, ledgerEntryId, entryType,
                input.getSignedDeltaMicrounits(), balanceAfter, account.getVersion() + 1,
                input.getReferenceType().trim(), input.getReferenceId().trim(), at(command.getOccurredAt()));
        ledgerMapper.insert(ledger);
        EventFact event = quotaEvent(ledger);
        return outcome(event, "token_platform_quota_account", account.getAccountId(), account.getVersion() + 1,
                account.getStatus());
    }

    private Outcome recordInvocationUsage(Long tenantId, Long operationId, TokenPlatformCommand command,
                                          LocalDateTime now) {
        TokenPlatformCommand.InvocationUsageDefinition input = requireNonNull(command.getInvocationUsage(),
                "invocationUsage is required");
        validateInvocation(input);
        require(usageMapper.selectByRequestId(tenantId, input.getRequestId()) == null,
                "requestId already exists");
        QuotaAccountDO account = requireNonNull(accountMapper.selectForUpdate(tenantId, input.getAccountId()),
                "quota account not found");
        require("ACTIVE".equals(account.getStatus()), "quota account must be ACTIVE");
        requireVersion(account.getVersion(), input.getExpectedAccountVersion());
        require(Objects.equals(account.getPrincipalId(), input.getPrincipalId()),
                "quota account principal mismatch");
        AccessCredentialDO credential = requireNonNull(
                credentialMapper.selectForUpdate(tenantId, input.getCredentialId()), "access credential not found");
        require("ACTIVE".equals(credential.getStatus()), "access credential must be ACTIVE");
        require(Objects.equals(credential.getPrincipalId(), input.getPrincipalId()),
                "access credential principal mismatch");
        require(Objects.equals(credential.getOfferingId(), input.getOfferingId()),
                "access credential offering mismatch");
        LocalDateTime occurredAt = at(command.getOccurredAt());
        require(credential.getExpiresAt() == null || occurredAt.isBefore(credential.getExpiresAt()),
                "access credential is expired");
        ModelOfferingDO offering = requireNonNull(offeringMapper.selectForUpdate(tenantId, input.getOfferingId()),
                "model offering not found");
        require("ACTIVE".equals(offering.getStatus()), "model offering must be ACTIVE");
        PricingVersionDO pricing = requireNonNull(pricingMapper.selectOneById(tenantId, input.getPricingVersionId()),
                "pricing version not found");
        require(Objects.equals(pricing.getOfferingId(), offering.getOfferingId()),
                "pricing version offering mismatch");
        require(!occurredAt.isBefore(pricing.getEffectiveAt()), "pricing version was not effective at occurredAt");
        long computedCost = computeCostMicrounits(input.getInputTokens(), input.getCachedInputTokens(),
                input.getOutputTokens(), pricing);
        require(Objects.equals(computedCost, input.getQuotaCostMicrounits()),
                "quotaCostMicrounits does not match pricing snapshot");
        long balanceAfter = addExact(account.getBalanceMicrounits(), -computedCost, "quota balance overflow");
        require(balanceAfter >= 0, "insufficient quota balance");
        require(accountMapper.updateBalanceCas(tenantId, account.getAccountId(), account.getVersion(), balanceAfter,
                now) == 1, "quota account version conflict");

        String usageId = valueOrUuid(input.getUsageId());
        String ledgerEntryId = UUID.randomUUID().toString();
        QuotaLedgerDO ledger = ledgerRow(tenantId, operationId, account, ledgerEntryId, "INVOCATION_USAGE",
                -computedCost, balanceAfter, account.getVersion() + 1, "INVOCATION_USAGE", usageId, occurredAt);
        ledgerMapper.insert(ledger);
        InvocationUsageDO usage = new InvocationUsageDO().setUsageId(usageId).setTenantId(tenantId)
                .setRequestId(input.getRequestId().trim()).setPrincipalId(input.getPrincipalId().trim())
                .setAccountId(account.getAccountId()).setCredentialId(credential.getCredentialId())
                .setOfferingId(offering.getOfferingId()).setProviderCode(offering.getProviderCode())
                .setModelCode(offering.getModelCode()).setResultStatus(normalized(input.getResultStatus()))
                .setInputTokens(input.getInputTokens()).setCachedInputTokens(input.getCachedInputTokens())
                .setOutputTokens(input.getOutputTokens()).setTotalTokens(input.getTotalTokens())
                .setDurationMillis(input.getDurationMillis()).setQuotaCostMicrounits(computedCost)
                .setPricingVersionId(pricing.getPricingVersionId()).setLedgerEntryId(ledgerEntryId)
                .setErrorCode(emptyToNull(input.getErrorCode())).setOccurredAt(occurredAt).setCreatedAt(occurredAt);
        usageMapper.insert(usage);

        EventFact quotaEvent = quotaEvent(ledger);
        EventFact usageEvent = usageEvent(usage);
        TokenPlatformCommandResult result = result("token_platform_invocation_usage", usageId, 1L,
                usage.getResultStatus());
        return new Outcome(List.of(quotaEvent, usageEvent), result);
    }

    private void appendEvents(Long tenantId, TokenPlatformCommand command, List<EventFact> events) {
        for (EventFact event : events) {
            String eventIdempotencyKey = DigestUtil.sha256Hex(tenantId + "\u001f" + command.getIdempotencyKey()
                    + "\u001f" + event.eventType() + "\u001f" + event.aggregateId() + "\u001f"
                    + event.aggregateVersion());
            outboxAppender.append(AppendDomainEventCommand.builder().eventType(event.eventType()).schemaVersion(1)
                    .sourceSystem("cloudmold-token-platform").tenantId(tenantId)
                    .aggregateType(event.aggregateType()).aggregateId(event.aggregateId())
                    .aggregateVersion(event.aggregateVersion()).eventSequence((short) 1)
                    .occurredAt(command.getOccurredAt()).correlationId(command.getCorrelationId())
                    .causationId(command.getCausationId()).idempotencyKey(eventIdempotencyKey)
                    .payload(event.payload()).headers(Map.of("operation", command.getOperation().name()))
                    .destination("lakehouse").build());
        }
    }

    private static EventFact offeringEvent(ModelOfferingDO row, String previous, String current,
                                            TokenPlatformOperation operation, Long version) {
        Map<String, Object> payload = new LinkedHashMap<>();
        put(payload, "offering_id", row.getOfferingId());
        put(payload, "offering_code", row.getOfferingCode());
        put(payload, "provider_code", row.getProviderCode());
        put(payload, "model_code", row.getModelCode());
        payload.put("previous_status", previous);
        put(payload, "current_status", current);
        put(payload, "operation", operation.name());
        return new EventFact("token_platform.model_offering.status_changed",
                "token_platform_model_offering", row.getOfferingId(), version, payload);
    }

    private static EventFact credentialEvent(AccessCredentialDO row, String previous, String current,
                                              TokenPlatformOperation operation, Long version) {
        Map<String, Object> payload = new LinkedHashMap<>();
        put(payload, "credential_id", row.getCredentialId());
        put(payload, "principal_id", row.getPrincipalId());
        put(payload, "offering_id", row.getOfferingId());
        put(payload, "credential_fingerprint", row.getCredentialFingerprint());
        put(payload, "key_version", row.getKeyVersion());
        payload.put("previous_status", previous);
        put(payload, "current_status", current);
        put(payload, "expires_at", instant(row.getExpiresAt()));
        put(payload, "operation", operation.name());
        return new EventFact("token_platform.access_credential.status_changed",
                "token_platform_access_credential", row.getCredentialId(), version, payload);
    }

    private static EventFact quotaEvent(QuotaLedgerDO row) {
        Map<String, Object> payload = new LinkedHashMap<>();
        put(payload, "account_id", row.getAccountId());
        put(payload, "ledger_entry_id", row.getLedgerEntryId());
        put(payload, "principal_id", row.getPrincipalId());
        put(payload, "entry_type", row.getEntryType());
        put(payload, "signed_delta_microunits", row.getSignedDeltaMicrounits());
        put(payload, "balance_after_microunits", row.getBalanceAfterMicrounits());
        put(payload, "reference_type", row.getReferenceType());
        put(payload, "reference_id", row.getReferenceId());
        put(payload, "occurred_at", instant(row.getOccurredAt()));
        return new EventFact("token_platform.quota.ledger_posted", "token_platform_quota_account",
                row.getAccountId(), row.getAccountVersion(), payload);
    }

    private static EventFact usageEvent(InvocationUsageDO row) {
        Map<String, Object> payload = new LinkedHashMap<>();
        put(payload, "usage_id", row.getUsageId());
        put(payload, "request_id", row.getRequestId());
        put(payload, "principal_id", row.getPrincipalId());
        put(payload, "credential_id", row.getCredentialId());
        put(payload, "offering_id", row.getOfferingId());
        put(payload, "provider_code", row.getProviderCode());
        put(payload, "model_code", row.getModelCode());
        put(payload, "result_status", row.getResultStatus());
        put(payload, "input_tokens", row.getInputTokens());
        put(payload, "cached_input_tokens", row.getCachedInputTokens());
        put(payload, "output_tokens", row.getOutputTokens());
        put(payload, "total_tokens", row.getTotalTokens());
        put(payload, "duration_millis", row.getDurationMillis());
        put(payload, "quota_cost_microunits", row.getQuotaCostMicrounits());
        put(payload, "pricing_version_id", row.getPricingVersionId());
        put(payload, "ledger_entry_id", row.getLedgerEntryId());
        put(payload, "error_code", row.getErrorCode());
        put(payload, "occurred_at", instant(row.getOccurredAt()));
        return new EventFact("token_platform.invocation.usage_recorded", "token_platform_invocation_usage",
                row.getUsageId(), 1L, payload);
    }

    private static PricingVersionDO pricingRow(Long tenantId, String offeringId, String pricingVersionId,
                                               Long versionNo,
                                               TokenPlatformCommand.PricingVersionDefinition input,
                                               LocalDateTime occurredAt) {
        return new PricingVersionDO().setPricingVersionId(pricingVersionId).setTenantId(tenantId)
                .setOfferingId(offeringId).setPricingVersionNo(versionNo)
                .setInputPriceMicrounitsPerMillionTokens(input.getInputPriceMicrounitsPerMillionTokens())
                .setCachedInputPriceMicrounitsPerMillionTokens(
                        input.getCachedInputPriceMicrounitsPerMillionTokens())
                .setOutputPriceMicrounitsPerMillionTokens(input.getOutputPriceMicrounitsPerMillionTokens())
                .setEffectiveAt(occurredAt).setCreatedAt(occurredAt);
    }

    private static QuotaLedgerDO ledgerRow(Long tenantId, Long operationId, QuotaAccountDO account,
                                           String ledgerEntryId, String entryType, Long delta, Long balanceAfter,
                                           Long accountVersion, String referenceType, String referenceId,
                                           LocalDateTime occurredAt) {
        return new QuotaLedgerDO().setLedgerEntryId(ledgerEntryId).setTenantId(tenantId)
                .setAccountId(account.getAccountId()).setPrincipalId(account.getPrincipalId())
                .setOperationId(operationId).setEntryType(entryType).setSignedDeltaMicrounits(delta)
                .setBalanceAfterMicrounits(balanceAfter).setAccountVersion(accountVersion)
                .setReferenceType(referenceType).setReferenceId(referenceId).setOccurredAt(occurredAt)
                .setCreatedAt(occurredAt);
    }

    static long computeCostMicrounits(long inputTokens, long cachedInputTokens, long outputTokens,
                                      PricingVersionDO pricing) {
        BigInteger uncached = BigInteger.valueOf(inputTokens - cachedInputTokens)
                .multiply(BigInteger.valueOf(pricing.getInputPriceMicrounitsPerMillionTokens()));
        BigInteger cached = BigInteger.valueOf(cachedInputTokens)
                .multiply(BigInteger.valueOf(pricing.getCachedInputPriceMicrounitsPerMillionTokens()));
        BigInteger output = BigInteger.valueOf(outputTokens)
                .multiply(BigInteger.valueOf(pricing.getOutputPriceMicrounitsPerMillionTokens()));
        BigInteger[] quotient = uncached.add(cached).add(output).divideAndRemainder(TOKEN_PRICE_DENOMINATOR);
        BigInteger rounded = quotient[1].signum() == 0 ? quotient[0] : quotient[0].add(BigInteger.ONE);
        try {
            return rounded.longValueExact();
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("quota cost overflow", ex);
        }
    }

    private static TokenPlatformCommand.PricingVersionDefinition validatePricing(
            TokenPlatformCommand.PricingVersionDefinition input) {
        require(input != null, "pricing is required");
        requireNonNegative(input.getInputPriceMicrounitsPerMillionTokens(), "input price");
        requireNonNegative(input.getCachedInputPriceMicrounitsPerMillionTokens(), "cached input price");
        requireNonNegative(input.getOutputPriceMicrounitsPerMillionTokens(), "output price");
        return input;
    }

    private static void validateCredentialDefinition(TokenPlatformCommand.AccessCredentialDefinition input) {
        requireText(input.getPrincipalId(), "principalId", 64);
        requireText(input.getOfferingId(), "offeringId", 64);
        require(input.getCredentialFingerprint() != null
                        && input.getCredentialFingerprint().matches("(?i)[0-9a-f]{64}"),
                "credentialFingerprint must be a SHA-256 hex digest");
        requireText(input.getSecretRef(), "secretRef", 256);
        String secretRef = input.getSecretRef().trim();
        require(secretRef.matches("(?i)(vault|secret|kms)://[^\\s]+"),
                "secretRef must be a vault, secret, or kms locator");
        require(!secretRef.matches("(?i).*([?&](api_?key|token|secret)=|/sk-[A-Za-z0-9]).*"),
                "secretRef must not contain a credential value");
        require(input.getLast4() != null && input.getLast4().matches("[A-Za-z0-9_-]{4}"),
                "last4 must contain exactly four safe characters");
        require(input.getKeyVersion() != null && input.getKeyVersion() > 0, "keyVersion must be positive");
    }

    private static void validateInvocation(TokenPlatformCommand.InvocationUsageDefinition input) {
        requireText(input.getRequestId(), "requestId", 128);
        requireText(input.getAccountId(), "accountId", 64);
        requireText(input.getPrincipalId(), "principalId", 64);
        requireText(input.getCredentialId(), "credentialId", 64);
        requireText(input.getOfferingId(), "offeringId", 64);
        requireText(input.getPricingVersionId(), "pricingVersionId", 64);
        String status = normalized(input.getResultStatus());
        require(Set.of("SUCCEEDED", "FAILED").contains(status), "invalid resultStatus");
        requireNonNegative(input.getInputTokens(), "inputTokens");
        requireNonNegative(input.getCachedInputTokens(), "cachedInputTokens");
        requireNonNegative(input.getOutputTokens(), "outputTokens");
        require(input.getCachedInputTokens() <= input.getInputTokens(),
                "cachedInputTokens must not exceed inputTokens");
        long total = addExact(input.getInputTokens(), input.getOutputTokens(), "totalTokens overflow");
        require(Objects.equals(total, input.getTotalTokens()), "totalTokens must equal inputTokens + outputTokens");
        requireNonNegative(input.getDurationMillis(), "durationMillis");
        requireNonNegative(input.getQuotaCostMicrounits(), "quotaCostMicrounits");
        requireVersionNonNull(input.getExpectedAccountVersion(), "expectedAccountVersion");
        if ("FAILED".equals(status)) requireText(input.getErrorCode(), "errorCode", 64);
        if ("SUCCEEDED".equals(status)) require(input.getErrorCode() == null || input.getErrorCode().isBlank(),
                "successful invocation must not have errorCode");
    }

    private static String offeringTransition(String status, TokenPlatformOperation operation) {
        return switch (operation) {
            case ACTIVATE_MODEL_OFFERING -> transition(status, Set.of("DRAFT", "SUSPENDED"), "ACTIVE",
                    "model offering");
            case SUSPEND_MODEL_OFFERING -> transition(status, Set.of("ACTIVE"), "SUSPENDED", "model offering");
            case RETIRE_MODEL_OFFERING -> transition(status, Set.of("DRAFT", "ACTIVE", "SUSPENDED"), "RETIRED",
                    "model offering");
            default -> throw new IllegalArgumentException("invalid model offering operation");
        };
    }

    private static String credentialTransition(String status, TokenPlatformOperation operation) {
        return switch (operation) {
            case ACTIVATE_ACCESS_CREDENTIAL -> transition(status, Set.of("INACTIVE"), "ACTIVE",
                    "access credential");
            case REVOKE_ACCESS_CREDENTIAL -> transition(status, Set.of("INACTIVE", "ACTIVE"), "REVOKED",
                    "access credential");
            default -> throw new IllegalArgumentException("invalid access credential operation");
        };
    }

    private static String transition(String current, Set<String> allowed, String next, String aggregate) {
        require(allowed.contains(current), "illegal " + aggregate + " transition from " + current + " to " + next);
        return next;
    }

    private static TokenPlatformAggregateView offeringView(ModelOfferingDO row) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        put(attributes, "provider_code", row.getProviderCode());
        put(attributes, "model_code", row.getModelCode());
        put(attributes, "current_pricing_version_id", row.getCurrentPricingVersionId());
        return view("token_platform_model_offering", row.getOfferingId(), row.getOfferingCode(), row.getStatus(),
                row.getVersion(), attributes);
    }

    private static TokenPlatformAggregateView credentialView(AccessCredentialDO row) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        put(attributes, "principal_id", row.getPrincipalId());
        put(attributes, "offering_id", row.getOfferingId());
        put(attributes, "credential_fingerprint", row.getCredentialFingerprint());
        put(attributes, "last4", row.getLast4());
        put(attributes, "key_version", row.getKeyVersion());
        put(attributes, "expires_at", instant(row.getExpiresAt()));
        return view("token_platform_access_credential", row.getCredentialId(), row.getLast4(), row.getStatus(),
                row.getVersion(), attributes);
    }

    private static TokenPlatformAggregateView accountView(QuotaAccountDO row) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        put(attributes, "principal_id", row.getPrincipalId());
        put(attributes, "balance_microunits", row.getBalanceMicrounits());
        return view("token_platform_quota_account", row.getAccountId(), row.getPrincipalId(), row.getStatus(),
                row.getVersion(), attributes);
    }

    private static TokenPlatformAggregateView usageView(InvocationUsageDO row) {
        Map<String, Object> attributes = usageEvent(row).payload();
        attributes = new LinkedHashMap<>(attributes);
        attributes.remove("usage_id");
        return view("token_platform_invocation_usage", row.getUsageId(), row.getRequestId(), row.getResultStatus(),
                1L, attributes);
    }

    private static TokenPlatformAggregateView view(String type, String id, String code, String status, Long version,
                                                   Map<String, Object> attributes) {
        return TokenPlatformAggregateView.builder().aggregateType(type).aggregateId(id).businessCode(code)
                .status(status).version(version).attributes(attributes).build();
    }

    private static Outcome outcome(EventFact event, String aggregateType, String aggregateId, Long version,
                                   String status) {
        return new Outcome(List.of(event), result(aggregateType, aggregateId, version, status));
    }

    private static TokenPlatformCommandResult result(String aggregateType, String aggregateId, Long version,
                                                     String status) {
        return TokenPlatformCommandResult.builder().aggregateType(aggregateType).aggregateId(aggregateId)
                .aggregateVersion(version).status(status).duplicate(false).build();
    }

    static String fingerprint(Long tenantId, TokenPlatformCommand command) {
        return DigestUtil.sha256Hex(tenantId + "\u001f" + JsonUtils.toJsonString(command));
    }

    private static void validateEnvelope(TokenPlatformCommand command) {
        require(command != null && command.getOperation() != null, "operation is required");
        requireText(command.getIdempotencyKey(), "idempotencyKey", 128);
        requireUuid(command.getCorrelationId(), "correlationId");
        if (command.getCausationId() != null) requireUuid(command.getCausationId(), "causationId");
        require(command.getOccurredAt() != null, "occurredAt is required");
    }

    private static void requireVersion(Long actual, Long expected) {
        requireVersionNonNull(expected, "expectedVersion");
        require(Objects.equals(actual, expected), "aggregate version conflict");
    }

    private static void requireVersionNonNull(Long value, String name) {
        require(value != null && value > 0, name + " must be positive");
    }

    private static void requireNonNegative(Long value, String name) {
        require(value != null && value >= 0, name + " must be non-negative");
    }

    private static long addExact(long left, long right, String message) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException(message, ex);
        }
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String valueOrUuid(String value) {
        return value == null || value.isBlank() ? UUID.randomUUID().toString() : value.trim();
    }

    private static LocalDateTime at(Instant value) {
        return value == null ? null : LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static Instant instant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private static void put(Map<String, Object> target, String key, Object value) {
        if (value != null) target.put(key, value);
    }

    private static void requireText(String value, String name, int maxLength) {
        require(value != null && !value.isBlank(), name + " is required");
        require(value.trim().length() <= maxLength, name + " is too long");
    }

    private static void requireUuid(String value, String name) {
        requireText(value, name, 36);
        try {
            UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(name + " must be a UUID");
        }
    }

    private static <T> T requireNonNull(T value, String message) {
        require(value != null, message);
        return value;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    private record EventFact(String eventType, String aggregateType, String aggregateId, Long aggregateVersion,
                             Map<String, Object> payload) {
    }

    private record Outcome(List<EventFact> events, TokenPlatformCommandResult result) {
    }
}
