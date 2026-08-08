package cn.iocoder.yudao.module.cloudmold.finance.service.receivables;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.finance.api.p2p.FinanceCommandEnvelope;
import cn.iocoder.yudao.module.cloudmold.finance.api.receivables.ReceivableSourceValidationPort;
import cn.iocoder.yudao.module.cloudmold.finance.api.receivables.ReceivableSourceView;
import cn.iocoder.yudao.module.cloudmold.finance.api.receivables.ReceivablesAutomationCommandApi;
import cn.iocoder.yudao.module.cloudmold.finance.api.receivables.ReceivablesCommandPort;
import cn.iocoder.yudao.module.cloudmold.finance.api.receivables.ReceivablesCommandResult;
import cn.iocoder.yudao.module.cloudmold.finance.api.receivables.ReceivablesCommands;
import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.receivables.ReceiptAllocationDO;
import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.receivables.ReceiptDO;
import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.receivables.ReceivablePlanDO;
import cn.iocoder.yudao.module.cloudmold.finance.dal.dataobject.receivables.ReceivablesOperationDO;
import cn.iocoder.yudao.module.cloudmold.finance.dal.mysql.receivables.ReceivablesMapper;
import cn.iocoder.yudao.module.cloudmold.finance.service.actor.FinanceActorPrincipalPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.regex.Pattern;

import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Service
@RequiredArgsConstructor
public class ReceivablesCommandServiceImpl implements ReceivablesAutomationCommandApi, ReceivablesCommandPort {
    static final int OPERATION_SUCCEEDED = 10;

    private static final String SOURCE_SYSTEM = "cloudmold-finance";
    private static final Pattern SAFE_REF = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:/-]{0,255}");
    private static final Pattern CURRENCY = Pattern.compile("[A-Z]{3}");

    private final ReceivablesMapper receivablesMapper;
    private final OutboxAppender outboxAppender;
    private final FinanceActorPrincipalPort actorPrincipalPort;
    private final ReceivableSourceValidationPort receivableSourceValidationPort;

    @Override
    public ReceivablesCommandResult registerPlan(ReceivablesCommands.RegisterPlan command) {
        return registerPlan(command, actorPrincipalPort.resolveSystemAdmin(getLoginUserId()));
    }

    @Override
    public ReceivablesCommandResult recordReceipt(ReceivablesCommands.RecordReceipt command) {
        return recordReceipt(command, actorPrincipalPort.resolveSystemAdmin(getLoginUserId()));
    }

    @Override
    public ReceivablesCommandResult allocateReceipt(ReceivablesCommands.AllocateReceipt command) {
        return allocateReceipt(command, actorPrincipalPort.resolveSystemAdmin(getLoginUserId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReceivablesCommandResult registerPlan(ReceivablesCommands.RegisterPlan command, String actorPrincipalId) {
        require(command != null, "register plan command is required");
        validateEnvelope(command.getEnvelope());
        requireActiveActor(actorPrincipalId);
        Long tenantId = tenantId();
        ReceivableSourceView source = receivableSourceValidationPort.requireActiveSalesReceivableSource(
                tenantId, command.getCustomerId(), command.getSalesContractId());
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String requestHash = fingerprintRegisterPlan(tenantId, command);
        return executeOperation(tenantId, command.getEnvelope(), "REGISTER_PLAN", requestHash, now, outcome -> {
            validateRegisterPlan(command);
            validateSourceAmountAndCurrency(source, command.getPlannedAmountMinor(), command.getCurrencyCode());
            String receivablePlanId = valueOrUuid(command.getReceivablePlanId());
            require(receivablesMapper.selectPlanForUpdate(tenantId, receivablePlanId) == null,
                    "receivable plan already exists");
            ReceivablePlanDO value = new ReceivablePlanDO();
            value.setReceivablePlanId(receivablePlanId);
            value.setTenantId(tenantId);
            value.setPlanCode(command.getPlanCode().trim());
            value.setCustomerId(command.getCustomerId().trim());
            value.setSalesContractId(command.getSalesContractId().trim());
            value.setCurrencyCode(command.getCurrencyCode().trim().toUpperCase(Locale.ROOT));
            value.setPlannedAmountMinor(command.getPlannedAmountMinor());
            value.setAllocatedAmountMinor(0L);
            value.setStatus("OPEN");
            value.setCreatedByPrincipalId(actorPrincipalId);
            value.setLastModifiedByPrincipalId(actorPrincipalId);
            value.setLatestReasonCode(normalizeReason(command.getReasonCode()));
            value.setDueDate(command.getDueDate());
            value.setVersion(1L);
            value.setCreatedAt(now);
            value.setUpdatedAt(now);
            require(receivablesMapper.insertPlan(value) == 1, "failed to create receivable plan");
            appendEvent(command.getEnvelope(), tenantId, "finance.receivable_plan.registered", "finance_receivable_plan",
                    receivablePlanId, 1L, "REGISTER_PLAN", payload(
                            "receivable_plan_id", receivablePlanId,
                            "plan_code", value.getPlanCode(),
                            "customer_id", value.getCustomerId(),
                            "sales_contract_id", value.getSalesContractId(),
                            "planned_amount_minor", value.getPlannedAmountMinor(),
                            "allocated_amount_minor", value.getAllocatedAmountMinor(),
                            "currency_code", value.getCurrencyCode(),
                            "due_date", value.getDueDate(),
                            "status", value.getStatus(),
                            "actor_principal_id", actorPrincipalId,
                            "reason_code", value.getLatestReasonCode()));
            return ReceivablesCommandResult.builder()
                    .operationId(outcome.operationId())
                    .duplicate(false)
                    .aggregateType("finance_receivable_plan")
                    .aggregateId(receivablePlanId)
                    .aggregateVersion(1L)
                    .status("OPEN")
                    .receivablePlanId(receivablePlanId)
                    .build();
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReceivablesCommandResult recordReceipt(ReceivablesCommands.RecordReceipt command, String actorPrincipalId) {
        require(command != null, "record receipt command is required");
        validateEnvelope(command.getEnvelope());
        requireActiveActor(actorPrincipalId);
        Long tenantId = tenantId();
        ReceivableSourceView source = receivableSourceValidationPort.requireActiveSalesReceivableSource(
                tenantId, command.getCustomerId(), command.getSalesContractId());
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String requestHash = fingerprintRecordReceipt(tenantId, command);
        return executeOperation(tenantId, command.getEnvelope(), "RECORD_RECEIPT", requestHash, now, outcome -> {
            validateRecordReceipt(command);
            validateSourceAmountAndCurrency(source, command.getReceiptAmountMinor(), command.getCurrencyCode());
            String receiptId = valueOrUuid(command.getReceiptId());
            require(receivablesMapper.selectReceiptForUpdate(tenantId, receiptId) == null, "receipt already exists");
            ReceiptDO value = new ReceiptDO();
            value.setReceiptId(receiptId);
            value.setTenantId(tenantId);
            value.setReceiptCode(command.getReceiptCode().trim());
            value.setCustomerId(command.getCustomerId().trim());
            value.setSalesContractId(command.getSalesContractId().trim());
            value.setCurrencyCode(command.getCurrencyCode().trim().toUpperCase(Locale.ROOT));
            value.setReceiptAmountMinor(command.getReceiptAmountMinor());
            value.setAllocatedAmountMinor(0L);
            value.setStatus("RECORDED");
            value.setExternalReference(normalizeOptionalRef(command.getExternalReference()));
            value.setRecordedByPrincipalId(actorPrincipalId);
            value.setLastModifiedByPrincipalId(actorPrincipalId);
            value.setLatestReasonCode(normalizeReason(command.getReasonCode()));
            value.setReceiptDate(command.getReceiptDate());
            value.setVersion(1L);
            value.setCreatedAt(now);
            value.setUpdatedAt(now);
            require(receivablesMapper.insertReceipt(value) == 1, "failed to create receipt");
            appendEvent(command.getEnvelope(), tenantId, "finance.receipt.recorded", "finance_receipt", receiptId, 1L,
                    "RECORD_RECEIPT", payload(
                            "receipt_id", receiptId,
                            "receipt_code", value.getReceiptCode(),
                            "customer_id", value.getCustomerId(),
                            "sales_contract_id", value.getSalesContractId(),
                            "receipt_amount_minor", value.getReceiptAmountMinor(),
                            "allocated_amount_minor", value.getAllocatedAmountMinor(),
                            "currency_code", value.getCurrencyCode(),
                            "receipt_date", value.getReceiptDate(),
                            "status", value.getStatus(),
                            "actor_principal_id", actorPrincipalId,
                            "reason_code", value.getLatestReasonCode()));
            return ReceivablesCommandResult.builder()
                    .operationId(outcome.operationId())
                    .duplicate(false)
                    .aggregateType("finance_receipt")
                    .aggregateId(receiptId)
                    .aggregateVersion(1L)
                    .status("RECORDED")
                    .receiptId(receiptId)
                    .build();
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReceivablesCommandResult allocateReceipt(ReceivablesCommands.AllocateReceipt command, String actorPrincipalId) {
        require(command != null, "allocate receipt command is required");
        validateEnvelope(command.getEnvelope());
        requireActiveActor(actorPrincipalId);
        Long tenantId = tenantId();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String requestHash = fingerprintAllocateReceipt(tenantId, command);
        return executeOperation(tenantId, command.getEnvelope(), "ALLOCATE_RECEIPT", requestHash, now, outcome -> {
            validateAllocateReceipt(command);
            ReceiptDO receipt = nonNull(receivablesMapper.selectReceiptForUpdate(tenantId, command.getReceiptId()),
                    "receipt does not exist");
            require(Objects.equals(receipt.getVersion(), command.getExpectedVersion()), "receipt version conflict");
            require(!"FULLY_ALLOCATED".equals(receipt.getStatus()), "receipt is already fully allocated");

            long allocatedAmountMinor = 0L;
            Set<String> uniquePlans = new HashSet<>();
            Set<String> uniqueAllocationIds = new HashSet<>();
            List<String> allocationIds = new ArrayList<>();
            List<String> affectedPlanIds = new ArrayList<>();
            for (ReceivablesCommands.AllocationLine line : command.getAllocations()) {
                validateAllocationLine(line, uniquePlans, uniqueAllocationIds);
                ReceivablePlanDO plan = nonNull(receivablesMapper.selectPlanForUpdate(tenantId, line.getReceivablePlanId()),
                        "receivable plan does not exist");
                require(Objects.equals(plan.getVersion(), line.getExpectedPlanVersion()), "receivable plan version conflict");
                require(Objects.equals(plan.getCustomerId(), receipt.getCustomerId()),
                        "receipt customer does not match receivable plan");
                require(Objects.equals(plan.getSalesContractId(), receipt.getSalesContractId()),
                        "receipt sales contract does not match receivable plan");
                require(Objects.equals(plan.getCurrencyCode(), receipt.getCurrencyCode()),
                        "receipt currency does not match receivable plan");
                require(line.getAmountMinor() <= remainingPlanAmount(plan), "allocation exceeds receivable plan balance");
                allocatedAmountMinor = Math.addExact(allocatedAmountMinor, line.getAmountMinor());
                require(allocatedAmountMinor <= remainingReceiptAmount(receipt), "allocation exceeds receipt balance");

                long nextPlanAllocated = Math.addExact(plan.getAllocatedAmountMinor(), line.getAmountMinor());
                String nextPlanStatus = planStatus(plan.getPlannedAmountMinor(), nextPlanAllocated);
                require(receivablesMapper.allocatePlan(tenantId, plan.getReceivablePlanId(), plan.getVersion(),
                        line.getAmountMinor(), plan.getCurrencyCode(), nextPlanStatus, actorPrincipalId,
                        normalizeReason(command.getReasonCode()), now) == 1, "receivable plan allocation conflict");

                long nextReceiptAllocated = Math.addExact(receipt.getAllocatedAmountMinor(), line.getAmountMinor());
                String nextReceiptStatus = receiptStatus(receipt.getReceiptAmountMinor(), nextReceiptAllocated);
                require(receivablesMapper.allocateReceipt(tenantId, receipt.getReceiptId(), receipt.getVersion(),
                        line.getAmountMinor(), receipt.getCurrencyCode(), nextReceiptStatus, actorPrincipalId,
                        normalizeReason(command.getReasonCode()), now) == 1, "receipt allocation conflict");
                receipt.setAllocatedAmountMinor(nextReceiptAllocated);
                receipt.setVersion(receipt.getVersion() + 1);
                receipt.setStatus(nextReceiptStatus);

                String allocationId = valueOrUuid(line.getReceiptAllocationId());
                ReceiptAllocationDO allocation = new ReceiptAllocationDO();
                allocation.setReceiptAllocationId(allocationId);
                allocation.setTenantId(tenantId);
                allocation.setReceiptId(receipt.getReceiptId());
                allocation.setReceivablePlanId(plan.getReceivablePlanId());
                allocation.setCustomerId(receipt.getCustomerId());
                allocation.setSalesContractId(receipt.getSalesContractId());
                allocation.setCurrencyCode(receipt.getCurrencyCode());
                allocation.setAmountMinor(line.getAmountMinor());
                allocation.setStatus("ACTIVE");
                allocation.setAllocatedByPrincipalId(actorPrincipalId);
                allocation.setReasonCode(normalizeReason(command.getReasonCode()));
                allocation.setReceiptVersion(receipt.getVersion());
                allocation.setReceivablePlanVersion(plan.getVersion() + 1);
                allocation.setCreatedAt(now);
                require(receivablesMapper.insertAllocation(allocation) == 1, "failed to persist receipt allocation");
                allocationIds.add(allocationId);
                affectedPlanIds.add(plan.getReceivablePlanId());
            }
            appendEvent(command.getEnvelope(), tenantId, "finance.receipt.allocated", "finance_receipt",
                    receipt.getReceiptId(), receipt.getVersion(), "ALLOCATE_RECEIPT", payload(
                            "receipt_id", receipt.getReceiptId(),
                            "customer_id", receipt.getCustomerId(),
                            "sales_contract_id", receipt.getSalesContractId(),
                            "currency_code", receipt.getCurrencyCode(),
                            "allocated_amount_minor", allocatedAmountMinor,
                            "receipt_allocated_amount_minor", receipt.getAllocatedAmountMinor(),
                            "receipt_unallocated_amount_minor", remainingReceiptAmount(receipt),
                            "allocation_ids", allocationIds,
                            "receivable_plan_ids", affectedPlanIds,
                            "actor_principal_id", actorPrincipalId,
                            "reason_code", normalizeReason(command.getReasonCode())));
            return ReceivablesCommandResult.builder()
                    .operationId(outcome.operationId())
                    .duplicate(false)
                    .aggregateType("finance_receipt")
                    .aggregateId(receipt.getReceiptId())
                    .aggregateVersion(receipt.getVersion())
                    .status(receipt.getStatus())
                    .receiptId(receipt.getReceiptId())
                    .allocatedAmountMinor(allocatedAmountMinor)
                    .allocationIds(allocationIds)
                    .affectedReceivablePlanIds(affectedPlanIds)
                    .build();
        });
    }

    private ReceivablesCommandResult executeOperation(Long tenantId, FinanceCommandEnvelope envelope, String commandType,
                                                      String requestHash, LocalDateTime now,
                                                      OperationExecutor executor) {
        String attemptToken = UUID.randomUUID().toString();
        receivablesMapper.insertOrResolveOperation(tenantId, envelope.getIdempotencyKey(), commandType, requestHash,
                attemptToken, now);
        Long operationId = receivablesMapper.selectLastInsertId();
        require(operationId != null && operationId > 0, "failed to resolve receivables operation");
        ReceivablesOperationDO operation = receivablesMapper.selectOperationForUpdate(tenantId, operationId);
        require(operation != null, "receivables operation disappeared");
        if (!attemptToken.equals(operation.getAttemptToken())) {
            require(Objects.equals(operation.getRequestHash(), requestHash),
                    "idempotency key conflicts with different receivables payload");
            require(operation.getStatus() == OPERATION_SUCCEEDED && operation.getResultJson() != null,
                    "existing receivables operation is not complete");
            ReceivablesCommandResult replay = JsonUtils.parseObject(operation.getResultJson(), ReceivablesCommandResult.class);
            replay.setDuplicate(true);
            return replay;
        }
        ReceivablesCommandResult result = executor.execute(new OperationOutcome(operationId));
        require(receivablesMapper.markOperationSucceeded(tenantId, operationId, result.getAggregateType(),
                result.getAggregateId(), JsonUtils.toJsonString(result), now) == 1,
                "receivables operation completion conflict");
        return result;
    }

    private void appendEvent(FinanceCommandEnvelope envelope, Long tenantId, String eventType, String aggregateType,
                             String aggregateId, Long aggregateVersion, String operation, Map<String, Object> payload) {
        outboxAppender.append(AppendDomainEventCommand.builder()
                .eventType(eventType)
                .schemaVersion(1)
                .sourceSystem(SOURCE_SYSTEM)
                .tenantId(tenantId)
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .aggregateVersion(aggregateVersion)
                .eventSequence((short) 1)
                .occurredAt(envelope.getOccurredAt())
                .traceId(envelope.getRunId())
                .correlationId(envelope.getCorrelationId())
                .causationId(envelope.getCausationId())
                .idempotencyKey(envelope.getIdempotencyKey())
                .payload(payload)
                .headers(Map.of("operation", operation))
                .destination("lakehouse")
                .build());
    }

    private static Map<String, Object> payload(Object... pairs) {
        Map<String, Object> payload = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            payload.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return payload;
    }

    private static void validateSourceAmountAndCurrency(ReceivableSourceView source, Long amountMinor,
                                                        String currencyCode) {
        require(source != null, "receivable source validation returned no source");
        require(currencyCode != null && currencyCode.trim().equalsIgnoreCase(source.getCurrencyCode()),
                "receivable currency does not match sales contract");
        require(source.getContractAmountMinor() != null && amountMinor != null
                        && amountMinor <= source.getContractAmountMinor(),
                "receivable amount exceeds sales contract amount");
    }

    private static long remainingPlanAmount(ReceivablePlanDO plan) {
        return Math.subtractExact(plan.getPlannedAmountMinor(), plan.getAllocatedAmountMinor());
    }

    private static long remainingReceiptAmount(ReceiptDO receipt) {
        return Math.subtractExact(receipt.getReceiptAmountMinor(), receipt.getAllocatedAmountMinor());
    }

    private static String planStatus(long plannedAmountMinor, long allocatedAmountMinor) {
        if (allocatedAmountMinor == 0L) {
            return "OPEN";
        }
        return allocatedAmountMinor == plannedAmountMinor ? "SETTLED" : "PARTIALLY_ALLOCATED";
    }

    private static String receiptStatus(long receiptAmountMinor, long allocatedAmountMinor) {
        if (allocatedAmountMinor == 0L) {
            return "RECORDED";
        }
        return allocatedAmountMinor == receiptAmountMinor ? "FULLY_ALLOCATED" : "PARTIALLY_ALLOCATED";
    }

    private static String fingerprintRegisterPlan(Long tenantId, ReceivablesCommands.RegisterPlan command) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("tenant_id", tenantId);
        value.put("command_type", "REGISTER_PLAN");
        value.put("receivable_plan_id", command.getReceivablePlanId());
        value.put("expected_version", command.getExpectedVersion());
        value.put("plan_code", command.getPlanCode());
        value.put("customer_id", command.getCustomerId());
        value.put("sales_contract_id", command.getSalesContractId());
        value.put("planned_amount_minor", command.getPlannedAmountMinor());
        value.put("currency_code", command.getCurrencyCode());
        value.put("due_date", command.getDueDate());
        value.put("reason_code", command.getReasonCode());
        value.put("occurred_at", command.getEnvelope().getOccurredAt());
        return DigestUtil.sha256Hex(JsonUtils.toJsonString(value));
    }

    private static String fingerprintRecordReceipt(Long tenantId, ReceivablesCommands.RecordReceipt command) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("tenant_id", tenantId);
        value.put("command_type", "RECORD_RECEIPT");
        value.put("receipt_id", command.getReceiptId());
        value.put("expected_version", command.getExpectedVersion());
        value.put("receipt_code", command.getReceiptCode());
        value.put("customer_id", command.getCustomerId());
        value.put("sales_contract_id", command.getSalesContractId());
        value.put("receipt_amount_minor", command.getReceiptAmountMinor());
        value.put("currency_code", command.getCurrencyCode());
        value.put("receipt_date", command.getReceiptDate());
        value.put("external_reference", command.getExternalReference());
        value.put("reason_code", command.getReasonCode());
        value.put("occurred_at", command.getEnvelope().getOccurredAt());
        return DigestUtil.sha256Hex(JsonUtils.toJsonString(value));
    }

    private static String fingerprintAllocateReceipt(Long tenantId, ReceivablesCommands.AllocateReceipt command) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("tenant_id", tenantId);
        value.put("command_type", "ALLOCATE_RECEIPT");
        value.put("receipt_id", command.getReceiptId());
        value.put("expected_version", command.getExpectedVersion());
        value.put("reason_code", command.getReasonCode());
        List<Map<String, Object>> allocations = new ArrayList<>();
        for (ReceivablesCommands.AllocationLine line : command.getAllocations()) {
            Map<String, Object> allocation = new LinkedHashMap<>();
            allocation.put("receipt_allocation_id", line.getReceiptAllocationId());
            allocation.put("receivable_plan_id", line.getReceivablePlanId());
            allocation.put("expected_plan_version", line.getExpectedPlanVersion());
            allocation.put("amount_minor", line.getAmountMinor());
            allocations.add(allocation);
        }
        value.put("allocations", allocations);
        value.put("occurred_at", command.getEnvelope().getOccurredAt());
        return DigestUtil.sha256Hex(JsonUtils.toJsonString(value));
    }

    private static void validateRegisterPlan(ReceivablesCommands.RegisterPlan command) {
        requireOptionalPositive(command.getExpectedVersion(), "expectedVersion");
        requireRef(command.getPlanCode(), "planCode");
        requireRef(command.getCustomerId(), "customerId");
        requireRef(command.getSalesContractId(), "salesContractId");
        requirePositive(command.getPlannedAmountMinor(), "plannedAmountMinor");
        requireCurrency(command.getCurrencyCode());
        require(command.getDueDate() != null, "dueDate is required");
    }

    private static void validateRecordReceipt(ReceivablesCommands.RecordReceipt command) {
        requireOptionalPositive(command.getExpectedVersion(), "expectedVersion");
        requireRef(command.getReceiptCode(), "receiptCode");
        requireRef(command.getCustomerId(), "customerId");
        requireRef(command.getSalesContractId(), "salesContractId");
        requirePositive(command.getReceiptAmountMinor(), "receiptAmountMinor");
        requireCurrency(command.getCurrencyCode());
        require(command.getReceiptDate() != null, "receiptDate is required");
        if (command.getExternalReference() != null) {
            requireSafe(command.getExternalReference(), "externalReference");
        }
    }

    private static void validateAllocateReceipt(ReceivablesCommands.AllocateReceipt command) {
        requireRef(command.getReceiptId(), "receiptId");
        requirePositive(command.getExpectedVersion(), "expectedVersion");
        require(command.getAllocations() != null && !command.getAllocations().isEmpty(), "allocations are required");
    }

    private static void validateAllocationLine(ReceivablesCommands.AllocationLine line, Set<String> uniquePlans,
                                               Set<String> uniqueAllocationIds) {
        require(line != null, "allocation line is required");
        requireRef(line.getReceivablePlanId(), "receivablePlanId");
        requirePositive(line.getExpectedPlanVersion(), "expectedPlanVersion");
        requirePositive(line.getAmountMinor(), "amountMinor");
        require(uniquePlans.add(line.getReceivablePlanId().trim()), "receivablePlanId must be unique within command");
        if (line.getReceiptAllocationId() != null && !line.getReceiptAllocationId().isBlank()) {
            requireSafe(line.getReceiptAllocationId(), "receiptAllocationId");
            require(uniqueAllocationIds.add(line.getReceiptAllocationId().trim()),
                    "receiptAllocationId must be unique within command");
        }
    }

    private static void validateEnvelope(FinanceCommandEnvelope envelope) {
        require(envelope != null, "command envelope is required");
        requireRef(envelope.getIdempotencyKey(), "idempotencyKey");
        require(envelope.getIdempotencyKey().trim().length() >= 8, "idempotencyKey is too short");
        require(envelope.getOccurredAt() != null, "occurredAt is required");
        if (envelope.getCorrelationId() != null) {
            require(!envelope.getCorrelationId().isBlank(), "correlationId must not be blank");
        }
    }

    private void requireActiveActor(String actorPrincipalId) {
        requireRef(actorPrincipalId, "actorPrincipalId");
        actorPrincipalPort.requireActive(actorPrincipalId.trim());
    }

    private static Long tenantId() {
        return TenantContextHolder.getRequiredTenantId();
    }

    private static void requireCurrency(String currencyCode) {
        require(currencyCode != null && CURRENCY.matcher(currencyCode.trim().toUpperCase(Locale.ROOT)).matches(),
                "currencyCode must be an ISO-4217 code");
    }

    private static void requireRef(String value, String field) {
        require(value != null && !value.isBlank(), field + " is required");
        requireSafe(value, field);
    }

    private static void requireSafe(String value, String field) {
        require(SAFE_REF.matcher(value.trim()).matches(), field + " is invalid");
    }

    private static void requirePositive(Long value, String field) {
        require(value != null && value > 0L, field + " must be positive");
    }

    private static void requireOptionalPositive(Long value, String field) {
        if (value != null) {
            requirePositive(value, field);
        }
    }

    private static String normalizeReason(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String normalizeOptionalRef(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String valueOrUuid(String value) {
        if (value == null || value.isBlank()) {
            return UUID.randomUUID().toString();
        }
        requireSafe(value, "aggregateId");
        return value.trim();
    }

    private static <T> T nonNull(T value, String message) {
        require(value != null, message);
        return value;
    }

    private static void require(boolean valid, String message) {
        if (!valid) {
            throw new IllegalArgumentException(message);
        }
    }

    @FunctionalInterface
    private interface OperationExecutor {
        ReceivablesCommandResult execute(OperationOutcome outcome);
    }

    private record OperationOutcome(Long operationId) {
    }
}
