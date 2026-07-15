package cn.iocoder.yudao.module.cloudmold.merchant.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.merchant.api.*;
import cn.iocoder.yudao.module.cloudmold.merchant.api.deposit.*;
import cn.iocoder.yudao.module.cloudmold.merchant.dal.dataobject.*;
import cn.iocoder.yudao.module.cloudmold.merchant.dal.mysql.MerchantDepositStoreMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MerchantDepositCommandServiceImpl implements MerchantDepositCommandApi, MerchantDepositQueryApi {

    static final String DEPOSIT_EVENT = "merchant.deposit.ledger_posted";
    private static final int OPERATION_SUCCEEDED = 10;
    private static final String SHADOW = "SHADOW";
    private static final String ENFORCED = "ENFORCED";

    private final MerchantDepositStoreMapper mapper;
    private final MerchantCommandApi merchantCommandApi;
    private final OutboxAppender outboxAppender;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MerchantDepositResult execute(MerchantDepositCommand command) {
        validateCommon(command);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String currency = normalizeCurrency(command.getCurrency());
        String requestHash = DigestUtil.sha256Hex(tenantId + "|" + JsonUtils.toJsonString(command));
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String attemptToken = UUID.randomUUID().toString();
        mapper.insertOrResolveOperation(tenantId, command.getIdempotencyKey(), command.getOperation().name(),
                requestHash, attemptToken, now);
        Long operationId = mapper.selectLastInsertId();
        require(operationId != null && operationId > 0, "failed to resolve Merchant deposit operation");
        MerchantDepositOperationDO operation = mapper.selectOperationForUpdate(operationId, tenantId);
        require(operation != null, "Merchant deposit operation disappeared");
        if (!attemptToken.equals(operation.getAttemptToken())) {
            require(Objects.equals(operation.getRequestHash(), requestHash),
                    "idempotency key conflicts with different Merchant deposit payload");
            require(operation.getStatus() == OPERATION_SUCCEEDED && operation.getResultJson() != null,
                    "existing Merchant deposit operation is not complete");
            MerchantDepositResult replay = JsonUtils.parseObject(operation.getResultJson(), MerchantDepositResult.class);
            replay.setDuplicate(true);
            return replay;
        }

        MerchantDepositResult result = apply(tenantId, operationId, command, currency, now);
        require(mapper.markOperationSucceeded(operationId, tenantId, result.getAccountId(),
                JsonUtils.toJsonString(result), now) == 1, "Merchant deposit operation completion conflict");
        return result;
    }

    @Override
    public MerchantDepositView requireCurrent(String merchantId, String currency) {
        requireText(merchantId, "merchantId", 64);
        MerchantDepositView view = mapper.selectCurrent(TenantContextHolder.getRequiredTenantId(), merchantId,
                normalizeCurrency(currency));
        require(view != null, "Merchant deposit account does not exist");
        return view;
    }

    private MerchantDepositResult apply(Long tenantId, Long operationId, MerchantDepositCommand command,
                                        String currency, LocalDateTime now) {
        MerchantAccountDO merchant = mapper.selectMerchantForUpdate(tenantId, command.getMerchantId());
        require(merchant != null, "merchant does not exist");
        MerchantDepositAccountDO account = mapper.selectAccountForUpdate(tenantId, command.getMerchantId(), currency);
        if (command.getOperation() == MerchantDepositOperation.ASSESS_REQUIRED) {
            require(account == null, "Merchant deposit account already exists");
            require(command.getExpectedAccountVersion() == null,
                    "expectedAccountVersion must be null when assessing a new account");
            long required = requirePositiveAmount(command);
            account = new MerchantDepositAccountDO().setAccountId(UUID.randomUUID().toString())
                    .setTenantId(tenantId).setMerchantId(command.getMerchantId()).setCurrency(currency)
                    .setRequiredAmountMinor(required).setHeldAmountMinor(0L).setFrozenAmountMinor(0L)
                    .setPaidAmountMinor(0L).setDeductedAmountMinor(0L).setCoverageStatus("SALES_BLOCKED")
                    .setEnforcementStatus(SHADOW).setPolicyVersion(command.getPolicyVersion()).setVersion(1L)
                    .setCreatedAt(now).setUpdatedAt(now);
            require(mapper.insertAccount(account) == 1, "failed to create Merchant deposit account");
            return appendLedgerAndEnforce(tenantId, operationId, command, merchant, account, null,
                    "ASSESSED", required, 0L, 0L, 0L, 0L, 0L, required, null, SHADOW, now);
        }

        require(account != null, "Merchant deposit account does not exist");
        require(command.getExpectedAccountVersion() != null && command.getExpectedAccountVersion() > 0,
                "expectedAccountVersion must be positive");
        require(Objects.equals(account.getVersion(), command.getExpectedAccountVersion()),
                "Merchant deposit account version conflict");
        long amount = command.getOperation() == MerchantDepositOperation.ACTIVATE_ENFORCEMENT
                ? requireZeroAmount(command) : requirePositiveAmount(command);
        long heldBefore = account.getHeldAmountMinor();
        long frozenBefore = account.getFrozenAmountMinor();
        long requiredBefore = account.getRequiredAmountMinor();
        long paid = account.getPaidAmountMinor();
        long deducted = account.getDeductedAmountMinor();
        long heldAfter = heldBefore;
        long frozenAfter = frozenBefore;
        long heldDelta = 0L;
        long frozenDelta = 0L;
        String entryType;
        String enforcementAfter = account.getEnforcementStatus();
        switch (command.getOperation()) {
            case PAY -> {
                entryType = "PAID";
                heldAfter = Math.addExact(heldBefore, amount);
                paid = Math.addExact(paid, amount);
                heldDelta = amount;
            }
            case FREEZE -> {
                entryType = "FROZEN";
                require(heldBefore - frozenBefore >= amount, "insufficient available Merchant deposit to freeze");
                frozenAfter = Math.addExact(frozenBefore, amount);
                frozenDelta = amount;
            }
            case UNFREEZE -> {
                entryType = "UNFROZEN";
                require(frozenBefore >= amount, "insufficient frozen Merchant deposit to unfreeze");
                frozenAfter = frozenBefore - amount;
                frozenDelta = -amount;
            }
            case DEDUCT -> {
                entryType = "DEDUCTED";
                require(heldBefore - frozenBefore >= amount,
                        "insufficient available Merchant deposit; unfreeze an evidenced amount before deduction");
                heldAfter = heldBefore - amount;
                deducted = Math.addExact(deducted, amount);
                heldDelta = -amount;
            }
            case ACTIVATE_ENFORCEMENT -> {
                entryType = "ENFORCEMENT_ACTIVATED";
                require(SHADOW.equals(account.getEnforcementStatus()), "Merchant deposit account is already enforced");
                require("SUFFICIENT".equals(account.getCoverageStatus()),
                        "Merchant deposit must be sufficient before enforcement is activated");
                enforcementAfter = ENFORCED;
            }
            default -> throw new IllegalArgumentException("unsupported Merchant deposit operation");
        }
        String currentCoverage = coverage(heldAfter, requiredBefore);
        long expectedVersion = account.getVersion();
        require(mapper.updateAccount(tenantId, account.getAccountId(), expectedVersion, requiredBefore, heldAfter,
                frozenAfter, paid, deducted, currentCoverage, enforcementAfter, command.getPolicyVersion(), now) == 1,
                "Merchant deposit account update conflict");
        account.setHeldAmountMinor(heldAfter).setFrozenAmountMinor(frozenAfter).setPaidAmountMinor(paid)
                .setDeductedAmountMinor(deducted).setCoverageStatus(currentCoverage)
                .setEnforcementStatus(enforcementAfter).setPolicyVersion(command.getPolicyVersion())
                .setVersion(expectedVersion + 1).setUpdatedAt(now);
        return appendLedgerAndEnforce(tenantId, operationId, command, merchant, account,
                command.getOperation() == MerchantDepositOperation.ACTIVATE_ENFORCEMENT ? account.getCoverageStatus()
                        : coverage(heldBefore, requiredBefore),
                entryType, amount, heldDelta, frozenDelta, heldBefore, frozenBefore, requiredBefore, requiredBefore,
                command.getOperation() == MerchantDepositOperation.ACTIVATE_ENFORCEMENT ? SHADOW
                        : account.getEnforcementStatus(),
                enforcementAfter, now);
    }

    private MerchantDepositResult appendLedgerAndEnforce(Long tenantId, Long operationId,
                                                          MerchantDepositCommand command,
                                                          MerchantAccountDO merchant,
                                                          MerchantDepositAccountDO account,
                                                          String previousCoverage, String entryType, long amount,
                                                          long heldDelta, long frozenDelta, long heldBefore,
                                                          long frozenBefore, long requiredBefore, long requiredAfter,
                                                          String previousEnforcement, String currentEnforcement,
                                                          LocalDateTime now) {
        MerchantDepositLedgerEntryDO ledger = new MerchantDepositLedgerEntryDO()
                .setLedgerEntryId(UUID.randomUUID().toString()).setTenantId(tenantId)
                .setAccountId(account.getAccountId()).setMerchantId(account.getMerchantId())
                .setCurrency(account.getCurrency()).setAccountVersion(account.getVersion()).setEntryType(entryType)
                .setAmountMinor(amount).setHeldDeltaMinor(heldDelta).setFrozenDeltaMinor(frozenDelta)
                .setHeldBeforeMinor(heldBefore).setHeldAfterMinor(account.getHeldAmountMinor())
                .setFrozenBeforeMinor(frozenBefore).setFrozenAfterMinor(account.getFrozenAmountMinor())
                .setRequiredBeforeMinor(requiredBefore).setRequiredAfterMinor(requiredAfter)
                .setPaidAfterMinor(account.getPaidAmountMinor()).setDeductedAfterMinor(account.getDeductedAmountMinor())
                .setPreviousCoverageStatus(previousCoverage).setCurrentCoverageStatus(account.getCoverageStatus())
                .setPreviousEnforcementStatus(previousEnforcement).setCurrentEnforcementStatus(currentEnforcement)
                .setPolicyVersion(account.getPolicyVersion()).setBusinessReference(command.getBusinessReference())
                .setReasonCode(command.getReasonCode()).setEvidenceRef(command.getEvidenceRef())
                .setOccurredAt(toUtc(command.getOccurredAt())).setCreatedAt(now);
        require(mapper.insertLedgerEntry(ledger) == 1, "failed to append Merchant deposit ledger entry");
        String eventId = appendEvent(tenantId, command, account, ledger);

        MerchantCommandResult statusResult = null;
        boolean crossedSalesBlock = !"SALES_BLOCKED".equals(previousCoverage)
                && "SALES_BLOCKED".equals(account.getCoverageStatus());
        if (ENFORCED.equals(account.getEnforcementStatus()) && crossedSalesBlock
                && "ACTIVE".equals(merchant.getStatus())) {
            statusResult = merchantCommandApi.execute(new MerchantCommand()
                    .setOperation(MerchantOperation.SUSPEND_MERCHANT)
                    .setIdempotencyKey("deposit-sales-block:" + eventId).setRunId(command.getRunId())
                    .setMerchantId(merchant.getMerchantId()).setExpectedVersion(merchant.getVersion())
                    .setReason("MERCHANT_DEPOSIT_SALES_BLOCKED:" + command.getReasonCode())
                    .setSourceSystem("cloudmold-merchant-deposit").setTraceId(command.getTraceId())
                    .setCorrelationId(resolvedCorrelationId(tenantId, command)).setCausationId(eventId)
                    .setOccurredAt(command.getOccurredAt()));
        }
        String merchantStatus = statusResult == null ? merchant.getStatus() : statusResult.getMerchantStatus();
        Long merchantVersion = statusResult == null ? merchant.getVersion() : statusResult.getMerchantVersion();
        return new MerchantDepositResult().setOperationId(operationId).setAccountId(account.getAccountId())
                .setLedgerEntryId(ledger.getLedgerEntryId()).setDepositEventId(eventId)
                .setMerchantId(account.getMerchantId()).setCurrency(account.getCurrency())
                .setAccountVersion(account.getVersion()).setRequiredAmountMinor(account.getRequiredAmountMinor())
                .setHeldAmountMinor(account.getHeldAmountMinor()).setFrozenAmountMinor(account.getFrozenAmountMinor())
                .setAvailableAmountMinor(account.getHeldAmountMinor() - account.getFrozenAmountMinor())
                .setPaidAmountMinor(account.getPaidAmountMinor()).setDeductedAmountMinor(account.getDeductedAmountMinor())
                .setPreviousCoverageStatus(previousCoverage).setCoverageStatus(account.getCoverageStatus())
                .setEnforcementStatus(account.getEnforcementStatus()).setMerchantStatus(merchantStatus)
                .setMerchantVersion(merchantVersion)
                .setListingUnpublishSagaId(statusResult == null ? null : statusResult.getListingUnpublishSagaId())
                .setAffectedListingCount(statusResult == null ? null : statusResult.getAffectedListingCount());
    }

    private String appendEvent(Long tenantId, MerchantDepositCommand command, MerchantDepositAccountDO account,
                               MerchantDepositLedgerEntryDO ledger) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("run_id", command.getRunId());
        payload.put("merchant_id", account.getMerchantId());
        payload.put("account_id", account.getAccountId());
        payload.put("ledger_entry_id", ledger.getLedgerEntryId());
        payload.put("entry_type", ledger.getEntryType());
        payload.put("amount_minor", ledger.getAmountMinor());
        payload.put("currency", ledger.getCurrency());
        payload.put("held_delta_minor", ledger.getHeldDeltaMinor());
        payload.put("frozen_delta_minor", ledger.getFrozenDeltaMinor());
        payload.put("held_before_minor", ledger.getHeldBeforeMinor());
        payload.put("held_after_minor", ledger.getHeldAfterMinor());
        payload.put("frozen_before_minor", ledger.getFrozenBeforeMinor());
        payload.put("frozen_after_minor", ledger.getFrozenAfterMinor());
        payload.put("required_before_minor", ledger.getRequiredBeforeMinor());
        payload.put("required_after_minor", ledger.getRequiredAfterMinor());
        payload.put("paid_after_minor", ledger.getPaidAfterMinor());
        payload.put("deducted_after_minor", ledger.getDeductedAfterMinor());
        payload.put("previous_coverage_status", ledger.getPreviousCoverageStatus());
        payload.put("current_coverage_status", ledger.getCurrentCoverageStatus());
        payload.put("previous_enforcement_status", ledger.getPreviousEnforcementStatus());
        payload.put("current_enforcement_status", ledger.getCurrentEnforcementStatus());
        payload.put("policy_version", ledger.getPolicyVersion());
        payload.put("business_reference", ledger.getBusinessReference());
        payload.put("reason_code", ledger.getReasonCode());
        payload.put("evidence_ref", ledger.getEvidenceRef());
        String idempotency = command.getIdempotencyKey() + ":MERCHANT_DEPOSIT_ACCOUNT:" + account.getVersion();
        String eventId = UUID.nameUUIDFromBytes((tenantId + "|" + idempotency)
                .getBytes(StandardCharsets.UTF_8)).toString();
        outboxAppender.append(AppendDomainEventCommand.builder().eventId(eventId).eventType(DEPOSIT_EVENT)
                .schemaVersion(1).sourceSystem("cloudmold-merchant").tenantId(tenantId)
                .aggregateType("MERCHANT_DEPOSIT_ACCOUNT").aggregateId(account.getAccountId())
                .aggregateVersion(account.getVersion()).eventSequence((short) 1).occurredAt(command.getOccurredAt())
                .traceId(command.getTraceId()).correlationId(resolvedCorrelationId(tenantId, command))
                .causationId(command.getCausationId()).idempotencyKey(idempotency).payload(payload)
                .headers(Map.of("run_id", command.getRunId())).destination("lakehouse").build());
        return eventId;
    }

    static String coverage(long heldAmountMinor, long requiredAmountMinor) {
        require(requiredAmountMinor > 0, "required Merchant deposit must be positive");
        BigInteger held = BigInteger.valueOf(heldAmountMinor);
        BigInteger required = BigInteger.valueOf(requiredAmountMinor);
        if (held.multiply(BigInteger.valueOf(5)).compareTo(required.multiply(BigInteger.valueOf(3))) >= 0) {
            return "SUFFICIENT";
        }
        return held.multiply(BigInteger.valueOf(5)).compareTo(required) >= 0
                ? "BID_RESTRICTED" : "SALES_BLOCKED";
    }

    private static long requirePositiveAmount(MerchantDepositCommand command) {
        require(command.getAmountMinor() != null && command.getAmountMinor() > 0,
                "amountMinor must be positive");
        return command.getAmountMinor();
    }

    private static long requireZeroAmount(MerchantDepositCommand command) {
        require(command.getAmountMinor() == null || command.getAmountMinor() == 0,
                "amountMinor must be zero for ACTIVATE_ENFORCEMENT");
        return 0L;
    }

    private static String normalizeCurrency(String currency) {
        require(currency != null && currency.matches("[A-Za-z]{3}"), "currency must be a three-letter code");
        return currency.toUpperCase(Locale.ROOT);
    }

    private static void validateCommon(MerchantDepositCommand command) {
        require(command != null, "Merchant deposit command is required");
        require(command.getOperation() != null, "Merchant deposit operation is required");
        requireText(command.getIdempotencyKey(), "idempotencyKey", 128);
        requireText(command.getRunId(), "runId", 128);
        requireText(command.getMerchantId(), "merchantId", 64);
        requireText(command.getPolicyVersion(), "policyVersion", 32);
        requireText(command.getBusinessReference(), "businessReference", 128);
        requireText(command.getReasonCode(), "reasonCode", 64);
        require(command.getReasonCode().matches("[A-Z0-9_]+"), "reasonCode must be normalized uppercase code");
        requireText(command.getEvidenceRef(), "evidenceRef", 256);
        require(command.getEvidenceRef().matches(
                        "(?i)(sha256|sha512|ticket|run|evidence|vault|kms|token):[a-z0-9._/-]+"),
                "evidenceRef must be an opaque evidence reference");
        requireText(command.getSourceSystem(), "sourceSystem", 64);
        requireText(command.getTraceId(), "traceId", 128);
        require(command.getOccurredAt() != null, "occurredAt is required");
        if (command.getCorrelationId() != null) requireUuid(command.getCorrelationId(), "correlationId");
        if (command.getCausationId() != null) requireUuid(command.getCausationId(), "causationId");
    }

    private static String resolvedCorrelationId(Long tenantId, MerchantDepositCommand command) {
        return command.getCorrelationId() == null
                ? UUID.nameUUIDFromBytes((tenantId + "|correlation|" + command.getRunId())
                        .getBytes(StandardCharsets.UTF_8)).toString()
                : command.getCorrelationId();
    }

    private static LocalDateTime toUtc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static void requireUuid(String value, String field) {
        try {
            UUID.fromString(value);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException(field + " must be a UUID", error);
        }
    }

    private static void requireText(String value, String field, int max) {
        require(value != null && !value.isBlank(), field + " is required");
        require(value.length() <= max, field + " exceeds " + max + " characters");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
