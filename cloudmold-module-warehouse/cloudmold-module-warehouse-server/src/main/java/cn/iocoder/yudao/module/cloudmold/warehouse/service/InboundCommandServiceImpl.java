package cn.iocoder.yudao.module.cloudmold.warehouse.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryV3Command;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryV3CommandApi;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryV3CommandResult;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryV3Operation;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.*;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.*;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 入库权威聚合（ASN/收货/上架）命令服务，骨架与 {@link WarehouseNetworkCommandServiceImpl} 同形：
 * 幂等信封（ON DUPLICATE KEY 回填 operation_id）→ 重放检测 → switch 业务分支 → Outbox 事件 → CAS 收尾。
 * 关键差异：COMPLETE_RECEIPT 每行同事务调库存 RECEIVE（落 NON_SELLABLE/PENDING_QC 暂存余额），打通补货闭环下游 SoR。
 */
@Service
@RequiredArgsConstructor
public class InboundCommandServiceImpl implements InboundCommandApi {

    static final int OPERATION_PROCESSING = 0;
    static final int OPERATION_SUCCEEDED = 10;

    private final InboundOperationMapper operationMapper;
    private final AsnMapper asnMapper;
    private final AsnLineMapper asnLineMapper;
    private final ReceiptMapper receiptMapper;
    private final ReceiptLineMapper receiptLineMapper;
    private final PutawayMapper putawayMapper;
    private final InventoryV3CommandApi inventoryV3CommandApi;
    private final WarehouseReferenceValidationApi warehouseValidationApi;
    private final OutboxAppender outboxAppender;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InboundCommandResult execute(InboundCommand command) {
        validate(command);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String hash = fingerprint(tenantId, command);
        String attemptToken = UUID.randomUUID().toString();
        operationMapper.insertOrResolve(tenantId, command.getIdempotencyKey(), command.getSourceEventId(),
                command.getOperation().name(), hash, attemptToken, now);
        Long operationId = operationMapper.selectLastInsertId();
        require(operationId != null && operationId > 0, "failed to resolve inbound operation");
        InboundOperationDO operation = operationMapper.selectForUpdate(operationId, tenantId);
        require(operation != null, "inbound operation disappeared");
        if (!attemptToken.equals(operation.getAttemptToken())) {
            require(Objects.equals(hash, operation.getRequestHash()),
                    "idempotency key or source event conflicts with different payload");
            require(operation.getStatus() == OPERATION_SUCCEEDED && operation.getResultJson() != null,
                    "existing inbound operation is not complete");
            InboundCommandResult replay = JsonUtils.parseObject(operation.getResultJson(), InboundCommandResult.class);
            replay.setDuplicate(true);
            return replay;
        }

        Outcome outcome = switch (command.getOperation()) {
            case CREATE_ASN -> createAsn(tenantId, command, now);
            case SEND_ASN -> sendAsn(tenantId, command, now);
            case CANCEL_ASN -> cancelAsn(tenantId, command, now);
            case COMPLETE_RECEIPT -> completeReceipt(tenantId, command, now);
            case COMPLETE_PUTAWAY -> completePutaway(tenantId, command, now);
        };
        appendEvent(tenantId, command, outcome);
        InboundCommandResult result = outcome.result();
        result.setOperationId(operationId);
        require(operationMapper.markSucceeded(operationId, tenantId, outcome.aggregateId(),
                JsonUtils.toJsonString(result), now) == 1, "inbound operation completion conflict");
        return result;
    }

    private Outcome createAsn(Long tenantId, InboundCommand command, LocalDateTime now) {
        AsnDefinition input = command.getAsn();
        require(asnMapper.selectBySourceRef(tenantId, input.getSourceBusinessType(), input.getSourceBusinessRef()) == null,
                "asn already exists for this business reference");
        require(input.getLines() != null && !input.getLines().isEmpty(), "asn must have at least one line");
        String asnId = valueOrUuid(input.getAsnId());
        AsnDO asn = new AsnDO().setAsnId(asnId).setTenantId(tenantId).setAsnNo(input.getAsnNo())
                .setSourceBusinessType(input.getSourceBusinessType()).setSourceBusinessRef(input.getSourceBusinessRef())
                .setSupplierRef(input.getSupplierRef()).setWarehouseId(input.getWarehouseId())
                .setStatus("DRAFT").setVersion(1L).setCreatedAt(now).setUpdatedAt(now);
        asnMapper.insert(asn);
        int autoLineNo = 1;
        for (AsnLineDefinition line : input.getLines()) {
            warehouseValidationApi.requireActiveLocation(input.getWarehouseId(), line.getStagingLocationId());
            AsnLineDO row = new AsnLineDO().setAsnLineId(valueOrUuid(line.getAsnLineId())).setTenantId(tenantId)
                    .setAsnId(asnId).setLineNo(line.getLineNo() == null ? autoLineNo : line.getLineNo())
                    .setCanonicalSkuId(line.getCanonicalSkuId()).setOwnerType(line.getOwnerType())
                    .setOwnerId(line.getOwnerId()).setBaseUomCode(line.getBaseUomCode())
                    .setExpectedQuantity(line.getExpectedQuantity()).setUnitCostAmountMinor(line.getUnitCostAmountMinor())
                    .setCurrencyCode(line.getCurrencyCode()).setStagingLocationId(line.getStagingLocationId())
                    .setCreatedAt(now);
            asnLineMapper.insert(row);
            autoLineNo++;
        }
        Map<String, Object> payload = payload("asn_id", asnId);
        payload.put("asn_no", asn.getAsnNo());
        payload.put("source_business_type", asn.getSourceBusinessType());
        payload.put("source_business_ref", asn.getSourceBusinessRef());
        payload.put("warehouse_id", asn.getWarehouseId());
        payload.put("current_status", asn.getStatus());
        return outcome("inbound.asn.created", "inbound_asn", asnId, 1L, payload,
                InboundCommandResult.builder().asnId(asnId).aggregateVersion(1L).status("DRAFT").duplicate(false).build());
    }

    private Outcome sendAsn(Long tenantId, InboundCommand command, LocalDateTime now) {
        AsnDefinition input = command.getAsn();
        AsnDO asn = requireAsn(tenantId, input.getAsnId());
        requireVersion(asn.getVersion(), input.getExpectedVersion());
        require("DRAFT".equals(asn.getStatus()) || "ANNOUNCED".equals(asn.getStatus()),
                "asn is not in a sendable status");
        require(asnMapper.updateStatusCas(tenantId, asn.getAsnId(), asn.getVersion(), "IN_TRANSIT", now) == 1,
                "asn version conflict");
        Map<String, Object> payload = statusPayload(asn, "IN_TRANSIT");
        return outcome("inbound.asn.status_changed", "inbound_asn", asn.getAsnId(), asn.getVersion() + 1, payload,
                InboundCommandResult.builder().asnId(asn.getAsnId()).aggregateVersion(asn.getVersion() + 1)
                        .status("IN_TRANSIT").duplicate(false).build());
    }

    private Outcome cancelAsn(Long tenantId, InboundCommand command, LocalDateTime now) {
        AsnDefinition input = command.getAsn();
        AsnDO asn = requireAsn(tenantId, input.getAsnId());
        requireVersion(asn.getVersion(), input.getExpectedVersion());
        require(!"RECEIVED".equals(asn.getStatus()) && !"CANCELLED".equals(asn.getStatus()),
                "asn cannot be cancelled after receipt");
        require(asnMapper.updateStatusCas(tenantId, asn.getAsnId(), asn.getVersion(), "CANCELLED", now) == 1,
                "asn version conflict");
        Map<String, Object> payload = statusPayload(asn, "CANCELLED");
        return outcome("inbound.asn.status_changed", "inbound_asn", asn.getAsnId(), asn.getVersion() + 1, payload,
                InboundCommandResult.builder().asnId(asn.getAsnId()).aggregateVersion(asn.getVersion() + 1)
                        .status("CANCELLED").duplicate(false).build());
    }

    private Outcome completeReceipt(Long tenantId, InboundCommand command, LocalDateTime now) {
        AsnDefinition input = command.getAsn();
        AsnDO asn = requireAsn(tenantId, input.getAsnId());
        requireVersion(asn.getVersion(), input.getExpectedVersion());
        require("IN_TRANSIT".equals(asn.getStatus()) || "ANNOUNCED".equals(asn.getStatus()),
                "asn is not in a receivable status");
        require(command.getReceiptLines() != null && !command.getReceiptLines().isEmpty(),
                "receipt must have at least one line");
        // 同事务建 receipt 头（RECEIVING）→ 每行落库存 RECEIVE（NON_SELLABLE/PENDING_QC）→ 头收口 COMPLETED
        String receiptId = UUID.randomUUID().toString();
        String receiptNo = "RCV-" + asn.getAsnNo();
        ReceiptDO receipt = new ReceiptDO().setReceiptId(receiptId).setTenantId(tenantId).setReceiptNo(receiptNo)
                .setAsnId(asn.getAsnId()).setWarehouseId(asn.getWarehouseId())
                .setStatus("RECEIVING").setVersion(1L).setCreatedAt(now).setUpdatedAt(now);
        receiptMapper.insert(receipt);

        List<AsnLineDO> asnLines = asnLineMapper.selectByAsn(tenantId, asn.getAsnId());
        Map<String, AsnLineDO> asnLineByLineId = new HashMap<>();
        for (AsnLineDO al : asnLines) {
            asnLineByLineId.put(al.getAsnLineId(), al);
        }
        for (ReceiptLineDefinition line : command.getReceiptLines()) {
            AsnLineDO asnLine = asnLineByLineId.get(line.getAsnLineId());
            require(asnLine != null, "receipt line references unknown asn line: " + line.getAsnLineId());
            BigDecimal received = line.getReceivedQuantity();
            BigDecimal expected = asnLine.getExpectedQuantity();
            BigDecimal shortQuantity = expected.subtract(received).max(BigDecimal.ZERO);
            BigDecimal overQuantity = received.subtract(expected).max(BigDecimal.ZERO);
            // 调库存 RECEIVE：落 NON_SELLABLE/PENDING_QC 暂存余额（质检放行切片 D 的上游）；幂等键绑定 receipt+asnLine
            String inventoryIdempotencyKey = "inbound-receipt:" + receiptId + ":" + line.getAsnLineId();
            InventoryV3CommandResult invResult = inventoryV3CommandApi.execute(InventoryV3Command.builder()
                    .operation(InventoryV3Operation.RECEIVE).idempotencyKey(inventoryIdempotencyKey)
                    .ownerType(line.getOwnerType()).ownerId(line.getOwnerId()).canonicalSkuId(line.getCanonicalSkuId())
                    .warehouseId(asn.getWarehouseId()).locationId(line.getStagingLocationId())
                    .stockStatus("NON_SELLABLE").qualityStatus("PENDING_QC").baseUomCode(line.getBaseUomCode())
                    .quantity(received).businessType("INBOUND_RECEIPT").businessId(receiptId)
                    .businessItemId(line.getAsnLineId()).businessNo(receiptNo).correlationId(command.getCorrelationId())
                    .causationId(command.getCausationId()).occurredAt(command.getOccurredAt()).build());
            ReceiptLineDO rl = new ReceiptLineDO().setReceiptLineId(UUID.randomUUID().toString()).setTenantId(tenantId)
                    .setReceiptId(receiptId).setAsnLineId(line.getAsnLineId()).setCanonicalSkuId(line.getCanonicalSkuId())
                    .setOwnerType(line.getOwnerType()).setOwnerId(line.getOwnerId()).setBaseUomCode(line.getBaseUomCode())
                    .setExpectedQuantity(expected).setReceivedQuantity(received).setShortQuantity(shortQuantity)
                    .setOverQuantity(overQuantity).setUnitCostAmountMinor(asnLine.getUnitCostAmountMinor())
                    .setCurrencyCode(asnLine.getCurrencyCode()).setStagingLocationId(line.getStagingLocationId())
                    .setQcRequired(Boolean.TRUE.equals(line.getQcRequired()) ? 1 : 0).setQualityTaskId(line.getQualityTaskId())
                    .setInventoryIdempotencyKey(inventoryIdempotencyKey).setInventoryOperationId(invResult.getOperationId())
                    .setInventoryLedgerTxId(invResult.getLedgerTransactionId())
                    .setInventoryBalanceId(invResult.getBalanceId()).setCreatedAt(now);
            receiptLineMapper.insert(rl);
        }
        // 收货完成：receipt → COMPLETED、asn → RECEIVED（CAS）
        require(receiptMapper.updateStatusCas(tenantId, receiptId, 1L, "COMPLETED", now) == 1,
                "receipt version conflict");
        require(asnMapper.updateStatusCas(tenantId, asn.getAsnId(), asn.getVersion(), "RECEIVED", now) == 1,
                "asn version conflict");
        Map<String, Object> payload = payload("receipt_id", receiptId);
        payload.put("receipt_no", receiptNo);
        payload.put("asn_id", asn.getAsnId());
        payload.put("warehouse_id", asn.getWarehouseId());
        payload.put("source_business_ref", asn.getSourceBusinessRef());
        payload.put("receipt_status", "COMPLETED");
        payload.put("asn_status", "RECEIVED");
        return outcome("inbound.receipt.completed", "inbound_receipt", receiptId, 2L, payload,
                InboundCommandResult.builder().asnId(asn.getAsnId()).receiptId(receiptId).aggregateVersion(2L)
                        .status("COMPLETED").duplicate(false).build());
    }

    private Outcome completePutaway(Long tenantId, InboundCommand command, LocalDateTime now) {
        PutawayDefinition input = command.getPutaway();
        ReceiptDO receipt = receiptMapper.selectForUpdate(tenantId, input.getReceiptId());
        require(receipt != null, "receipt does not exist");
        require("COMPLETED".equals(receipt.getStatus()), "receipt is not COMPLETED");
        require(putawayMapper.selectByReceipt(tenantId, receipt.getReceiptId()) == null,
                "putaway already exists for this receipt");
        warehouseValidationApi.requireActiveLocation(receipt.getWarehouseId(), input.getTargetLocationId());
        String putawayId = valueOrUuid(input.getPutawayId());
        List<ReceiptLineDO> receiptLines = receiptLineMapper.selectByReceipt(tenantId, receipt.getReceiptId());
        require(receiptLines != null && !receiptLines.isEmpty(), "receipt has no lines to put away");
        int releasedLineCount = 0;
        int relocatedLineCount = 0;
        for (ReceiptLineDO line : receiptLines) {
            boolean requiresQc = Objects.equals(line.getQcRequired(), 1);
            InventoryV3Operation operation = requiresQc
                    ? InventoryV3Operation.RELOCATE : InventoryV3Operation.QUALITY_RELEASE;
            InventoryV3Command.InventoryV3CommandBuilder inventory = InventoryV3Command.builder()
                    .operation(operation)
                    .idempotencyKey("inbound-putaway:" + putawayId + ":" + line.getReceiptLineId())
                    .ownerType(line.getOwnerType()).ownerId(line.getOwnerId())
                    .canonicalSkuId(line.getCanonicalSkuId()).warehouseId(receipt.getWarehouseId())
                    .locationId(line.getStagingLocationId()).targetLocationId(input.getTargetLocationId())
                    .stockStatus(requiresQc ? "SELLABLE" : "NON_SELLABLE")
                    .qualityStatus(requiresQc ? "QUALIFIED" : "PENDING_QC")
                    .baseUomCode(line.getBaseUomCode()).quantity(line.getReceivedQuantity())
                    .businessType("INBOUND_PUTAWAY").businessId(putawayId)
                    .businessItemId(line.getReceiptLineId()).businessNo(receipt.getReceiptNo())
                    .correlationId(command.getCorrelationId()).causationId(command.getCausationId())
                    .occurredAt(command.getOccurredAt());
            if (requiresQc) {
                require(line.getQualityTaskId() != null && !line.getQualityTaskId().isBlank(),
                        "QC-required receipt line must reference a completed quality task before putaway");
                relocatedLineCount++;
            } else {
                inventory.targetStockStatus("SELLABLE").targetQualityStatus("QUALIFIED");
                releasedLineCount++;
            }
            inventoryV3CommandApi.execute(inventory.build());
        }
        PutawayDO row = new PutawayDO().setPutawayId(putawayId).setTenantId(tenantId)
                .setReceiptId(receipt.getReceiptId()).setWarehouseId(receipt.getWarehouseId())
                .setTargetLocationId(input.getTargetLocationId())
                .setStatus("COMPLETED").setVersion(1L).setCreatedAt(now).setUpdatedAt(now);
        putawayMapper.insert(row);
        Map<String, Object> payload = payload("putaway_id", putawayId);
        payload.put("receipt_id", receipt.getReceiptId());
        payload.put("warehouse_id", receipt.getWarehouseId());
        payload.put("target_location_id", row.getTargetLocationId());
        payload.put("current_status", row.getStatus());
        payload.put("quality_released_line_count", releasedLineCount);
        payload.put("relocated_line_count", relocatedLineCount);
        payload.put("inventory_terminal", true);
        return outcome("inbound.putaway.completed", "inbound_putaway", putawayId, 1L, payload,
                InboundCommandResult.builder().putawayId(putawayId).receiptId(receipt.getReceiptId())
                        .aggregateVersion(1L).status("COMPLETED").duplicate(false).build());
    }

    private AsnDO requireAsn(Long tenantId, String asnId) {
        AsnDO row = asnMapper.selectForUpdate(tenantId, asnId);
        require(row != null, "asn does not exist");
        return row;
    }

    private void appendEvent(Long tenantId, InboundCommand command, Outcome outcome) {
        outboxAppender.append(AppendDomainEventCommand.builder().eventType(outcome.eventType()).schemaVersion(1)
                .sourceSystem("cloudmold-warehouse").tenantId(tenantId).aggregateType(outcome.aggregateType())
                .aggregateId(outcome.aggregateId()).aggregateVersion(outcome.aggregateVersion()).eventSequence((short) 1)
                .occurredAt(command.getOccurredAt()).correlationId(command.getCorrelationId())
                .causationId(command.getCausationId()).idempotencyKey(command.getIdempotencyKey() + ":event")
                .payload(outcome.payload()).headers(Map.of("operation", command.getOperation().name()))
                .destination("lakehouse").build());
    }

    static String fingerprint(Long tenantId, InboundCommand command) {
        return DigestUtil.sha256Hex(tenantId + "" + JsonUtils.toJsonString(command));
    }

    private static void validate(InboundCommand command) {
        require(command != null && command.getOperation() != null, "operation is required");
        requireText(command.getIdempotencyKey(), "idempotencyKey", 128);
        requireUuid(command.getCorrelationId(), "correlationId");
        if (command.getCausationId() != null) requireUuid(command.getCausationId(), "causationId");
        require(command.getOccurredAt() != null, "occurredAt is required");
        switch (command.getOperation()) {
            case CREATE_ASN -> {
                AsnDefinition asn = command.getAsn();
                require(asn != null, "asn is required");
                requireText(asn.getSourceBusinessType(), "sourceBusinessType", 32);
                requireText(asn.getSourceBusinessRef(), "sourceBusinessRef", 128);
                requireText(asn.getWarehouseId(), "warehouseId", 36);
                require(asn.getLines() != null && !asn.getLines().isEmpty(), "asn lines are required");
                for (AsnLineDefinition line : asn.getLines()) {
                    requireText(line.getCanonicalSkuId(), "canonicalSkuId", 36);
                    requireText(line.getOwnerType(), "ownerType", 32);
                    requireText(line.getOwnerId(), "ownerId", 36);
                    requireText(line.getBaseUomCode(), "baseUomCode", 32);
                    requireText(line.getStagingLocationId(), "stagingLocationId", 36);
                    require(line.getExpectedQuantity() != null && line.getExpectedQuantity().signum() > 0,
                            "expectedQuantity must be positive");
                }
            }
            case SEND_ASN, CANCEL_ASN -> {
                AsnDefinition asn = command.getAsn();
                require(asn != null, "asn is required");
                requireText(asn.getAsnId(), "asnId", 36);
                require(asn.getExpectedVersion() != null, "expectedVersion is required");
            }
            case COMPLETE_RECEIPT -> {
                AsnDefinition asn = command.getAsn();
                require(asn != null, "asn is required");
                requireText(asn.getAsnId(), "asnId", 36);
                require(asn.getExpectedVersion() != null, "expectedVersion is required");
                require(command.getReceiptLines() != null && !command.getReceiptLines().isEmpty(),
                        "receiptLines are required");
                for (ReceiptLineDefinition line : command.getReceiptLines()) {
                    requireText(line.getAsnLineId(), "asnLineId", 36);
                    requireText(line.getCanonicalSkuId(), "canonicalSkuId", 36);
                    requireText(line.getStagingLocationId(), "stagingLocationId", 36);
                    require(line.getReceivedQuantity() != null && line.getReceivedQuantity().signum() > 0,
                            "receivedQuantity must be positive");
                }
            }
            case COMPLETE_PUTAWAY -> {
                PutawayDefinition pa = command.getPutaway();
                require(pa != null, "putaway is required");
                requireText(pa.getReceiptId(), "receiptId", 36);
                requireText(pa.getTargetLocationId(), "targetLocationId", 36);
            }
        }
    }

    private static Map<String, Object> statusPayload(AsnDO asn, String currentStatus) {
        Map<String, Object> payload = payload("asn_id", asn.getAsnId());
        payload.put("previous_status", asn.getStatus());
        payload.put("current_status", currentStatus);
        payload.put("source_business_ref", asn.getSourceBusinessRef());
        return payload;
    }

    private static Outcome outcome(String event, String type, String id, Long version, Map<String, Object> payload,
                                   InboundCommandResult result) {
        return new Outcome(event, type, id, version, payload, result);
    }

    private static Map<String, Object> payload(String idName, String id) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(idName, id);
        return result;
    }

    private static void requireVersion(Long actual, Long expected) {
        require(expected != null && Objects.equals(actual, expected), "expectedVersion does not match aggregate version");
    }

    private static String valueOrUuid(String value) {
        return value == null || value.isBlank() ? UUID.randomUUID().toString() : value;
    }

    private static void requireUuid(String value, String field) {
        requireText(value, field, 36);
        try {
            UUID.fromString(value);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException(field + " must be a UUID", error);
        }
    }

    private static void requireText(String value, String field, int max) {
        require(value != null && !value.isBlank() && value.length() <= max, field + " is required and too long");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    private record Outcome(String eventType, String aggregateType, String aggregateId, Long aggregateVersion,
                           Map<String, Object> payload, InboundCommandResult result) {
    }
}
