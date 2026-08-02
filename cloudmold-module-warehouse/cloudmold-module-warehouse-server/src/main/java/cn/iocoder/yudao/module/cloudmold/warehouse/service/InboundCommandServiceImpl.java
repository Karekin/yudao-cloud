package cn.iocoder.yudao.module.cloudmold.warehouse.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.finance.api.p2p.FinanceCommandEnvelope;
import cn.iocoder.yudao.module.cloudmold.finance.api.p2p.P2pEvidenceCommands;
import cn.iocoder.yudao.module.cloudmold.finance.api.p2p.P2pEvidenceIngestionApi;
import cn.iocoder.yudao.module.cloudmold.finance.api.p2p.ProcureToPayResult;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryProcurementReceiptApi;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryProcurementReceiptCommand;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryProcurementReceiptDisposition;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryProcurementReceiptOperation;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryProcurementReceiptResult;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryV3Command;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryV3CommandApi;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryV3CommandResult;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryV3Operation;
import cn.iocoder.yudao.module.cloudmold.procurement.api.ProcurementOrderView;
import cn.iocoder.yudao.module.cloudmold.procurement.api.ProcurementQueryApi;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.AsnDefinition;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.AsnLineDefinition;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.InboundCommand;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.InboundCommandApi;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.InboundCommandResult;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.InboundOperation;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.PutawayDefinition;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.PutawayLineDefinition;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.ReceiptDefinition;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.ReceiptLineDefinition;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.WarehouseReferenceValidationApi;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.AsnDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.AsnLineDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.InboundOperationDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.InboundStatusHistoryDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.PutawayDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.PutawayLineDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.ReceiptDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.ReceiptLineDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject.ScheduleFulfillmentDO;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql.AsnLineMapper;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql.AsnMapper;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql.InboundOperationMapper;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql.InboundStatusHistoryMapper;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql.PutawayMapper;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql.PutawayLineMapper;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql.ReceiptLineMapper;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql.ReceiptMapper;
import cn.iocoder.yudao.module.cloudmold.warehouse.dal.mysql.ScheduleFulfillmentMapper;
import cn.iocoder.yudao.module.cloudmold.warehouse.service.actor.WarehouseActorPrincipalPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InboundCommandServiceImpl implements InboundCommandApi {

    static final int OPERATION_SUCCEEDED = 10;
    private static final BigDecimal ZERO = new BigDecimal("0.000000");

    private final InboundOperationMapper operationMapper;
    private final AsnMapper asnMapper;
    private final AsnLineMapper asnLineMapper;
    private final ReceiptMapper receiptMapper;
    private final ReceiptLineMapper receiptLineMapper;
    private final PutawayMapper putawayMapper;
    private final PutawayLineMapper putawayLineMapper;
    private final InboundStatusHistoryMapper historyMapper;
    private final ScheduleFulfillmentMapper scheduleFulfillmentMapper;
    private final ProcurementQueryApi procurementQueryApi;
    private final InventoryProcurementReceiptApi inventoryProcurementReceiptApi;
    private final InventoryV3CommandApi inventoryV3CommandApi;
    private final WarehouseReferenceValidationApi warehouseValidationApi;
    private final WarehouseActorPrincipalPort actorPrincipalPort;
    private final P2pEvidenceIngestionApi p2pEvidenceIngestionApi;
    private final OutboxAppender outboxAppender;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InboundCommandResult execute(InboundCommand command, String actorPrincipalId) {
        validate(command);
        requireText(actorPrincipalId, "actorPrincipalId", 128);
        actorPrincipalPort.requireActive(actorPrincipalId);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String requestHash = fingerprint(tenantId, command);
        String attemptToken = UUID.randomUUID().toString();
        operationMapper.insertOrResolve(tenantId, command.getIdempotencyKey(), command.getSourceEventId(),
                command.getOperation().name(), requestHash, attemptToken, now);
        Long operationId = operationMapper.selectLastInsertId();
        require(operationId != null && operationId > 0, "failed to resolve inbound operation");
        InboundOperationDO operation = operationMapper.selectForUpdate(operationId, tenantId);
        require(operation != null, "inbound operation disappeared");
        if (!attemptToken.equals(operation.getAttemptToken())) {
            require(Objects.equals(requestHash, operation.getRequestHash()),
                    "idempotency key or source event conflicts with different payload");
            require(operation.getStatus() == OPERATION_SUCCEEDED && operation.getResultJson() != null,
                    "existing inbound operation is not complete");
            InboundCommandResult replay = JsonUtils.parseObject(operation.getResultJson(), InboundCommandResult.class);
            replay.setDuplicate(true);
            return replay;
        }

        InboundCommandResult result = switch (command.getOperation()) {
            case CREATE_ASN -> createAsn(tenantId, operationId, command, now);
            case SEND_ASN -> sendAsn(tenantId, operationId, command, now);
            case CANCEL_ASN -> cancelAsn(tenantId, operationId, command, now);
            case COMPLETE_RECEIPT -> recordPartialReceipt(tenantId, operationId, command, actorPrincipalId, now);
            case COMPLETE_PUTAWAY -> completePutaway(tenantId, operationId, command, now);
        };
        result.setOperationId(operationId);
        String aggregateId = result.getReceiptId() != null ? result.getReceiptId() : result.getAsnId();
        require(operationMapper.markSucceeded(operationId, tenantId, aggregateId,
                JsonUtils.toJsonString(result), now) == 1, "inbound operation completion conflict");
        return result;
    }

    private InboundCommandResult createAsn(Long tenantId, Long operationId, InboundCommand command, LocalDateTime now) {
        AsnDefinition input = command.getAsn();
        require(asnMapper.selectByAsnNo(tenantId, input.getAsnNo()) == null,
                "asnNo already exists");
        ProcurementOrderView order = procurementQueryApi.requireCurrent(input.getProcurementOrderId());
        require(Set.of("RELEASED", "DISPATCHED", "SUPPLIER_CONFIRMED").contains(upper(order.getStatus())),
                "procurement order must be released before ASN creation");
        require(input.getSupplierId().equals(order.getSupplierId()), "ASN supplierId does not match procurement order");
        require("HALF_UP".equals(upper(order.getRoundingPolicyCode())),
                "released procurement order rounding policy must be HALF_UP");
        require(order.getReleasedVersion() != null
                        && order.getReleasedVersion().equals(input.getLines().get(0).getPoReleaseVersion()),
                "poReleaseVersion must match the authoritative procurement release version");
        List<ProcurementOrderLineRef> validatedRefs = new ArrayList<>(input.getLines().size());
        List<ScheduleFulfillmentDO> validatedFulfillments = new ArrayList<>(input.getLines().size());
        for (AsnLineDefinition line : input.getLines()) {
            ProcurementOrderLineRef ref = requireOrderLine(order, line);
            require(input.getSupplierId().equals(line.getSupplierId()), "ASN line supplierId does not match header");
            require(input.getWarehouseId().equals(line.getWarehouseId()), "ASN line warehouseId does not match header");
            validatedRefs.add(ref);
            warehouseValidationApi.requireActiveLocation(line.getWarehouseId(), line.getReceiptLocationId());
            validatedFulfillments.add(ensureScheduleFulfillment(tenantId, order, line, ref, now));
        }

        String asnId = valueOrUuid(input.getAsnId());
        AsnDO asn = new AsnDO().setAsnId(asnId).setTenantId(tenantId).setAsnNo(input.getAsnNo())
                .setProcurementOrderId(input.getProcurementOrderId()).setSupplierId(input.getSupplierId())
                .setWarehouseId(input.getWarehouseId()).setStatus("DRAFT").setVersion(1L)
                .setCreatedAt(now).setUpdatedAt(now);
        asnMapper.insert(asn);

        int autoLineNo = 1;
        Set<String> scheduleIds = new LinkedHashSet<>();
        List<Map<String, Object>> linePayloads = new ArrayList<>();
        int index = 0;
        for (AsnLineDefinition line : input.getLines()) {
            require(scheduleIds.add(line.getDeliveryScheduleId()), "duplicate deliveryScheduleId within ASN");
            ProcurementOrderLineRef ref = validatedRefs.get(index);
            AsnLineDO row = new AsnLineDO().setAsnLineId(valueOrUuid(line.getAsnLineId())).setTenantId(tenantId)
                    .setAsnId(asnId).setLineNo(line.getLineNo() == null ? autoLineNo : line.getLineNo())
                    .setProcurementOrderId(input.getProcurementOrderId())
                    .setProcurementOrderItemId(line.getProcurementOrderItemId())
                    .setDeliveryScheduleId(line.getDeliveryScheduleId()).setPoReleaseVersion(line.getPoReleaseVersion())
                    .setSupplierId(line.getSupplierId()).setWarehouseId(line.getWarehouseId())
                    .setReceiptLocationId(line.getReceiptLocationId()).setCanonicalSkuId(line.getCanonicalSkuId())
                    .setOwnerType(line.getOwnerType()).setOwnerId(line.getOwnerId())
                    .setBaseUomCode(line.getBaseUomCode()).setScheduledQuantity(ref.schedule().getScheduledQuantity())
                    .setAllowedOverReceiptQuantity(nonNegative(line.getAllowedOverReceiptQuantity(),
                            "allowedOverReceiptQuantity"))
                    .setReceivedQuantity(ZERO).setPendingQualityQuantity(ZERO)
                    .setFulfillmentVersion(validatedFulfillments.get(index).getVersion())
                    .setValuationPolicyId(ref.item().getValuationPolicyId())
                    .setValuationPolicyVersion(ref.item().getValuationPolicyVersion())
                    .setValuationPolicyHash(ref.item().getValuationPolicyHash())
                    .setUnitCostAmountMinor(frozenUnitCostMinor(ref.item().getUnitNetPriceMinor()))
                    .setCurrencyCode(requireCurrency(order.getCurrencyCode()))
                    .setRoundingPolicyCode("HALF_UP")
                    .setTolerancePolicyVersion(line.getTolerancePolicyVersion())
                    .setTolerancePolicyHash(line.getTolerancePolicyHash()).setStatus("OPEN")
                    .setCreatedAt(now).setUpdatedAt(now);
            asnLineMapper.insert(row);
            Map<String, Object> linePayload = new LinkedHashMap<>();
            linePayload.put("asn_line_id", row.getAsnLineId());
            linePayload.put("procurement_order_item_id", row.getProcurementOrderItemId());
            linePayload.put("delivery_schedule_id", row.getDeliveryScheduleId());
            linePayload.put("po_release_version", row.getPoReleaseVersion());
            linePayload.put("supplier_id", row.getSupplierId());
            linePayload.put("canonical_sku_id", row.getCanonicalSkuId());
            linePayload.put("scheduled_quantity", row.getScheduledQuantity());
            linePayload.put("valuation_policy_id", row.getValuationPolicyId());
            linePayload.put("valuation_policy_version", row.getValuationPolicyVersion());
            linePayload.put("valuation_policy_hash", row.getValuationPolicyHash());
            linePayload.put("unit_cost_amount_minor", row.getUnitCostAmountMinor());
            linePayload.put("currency_code", row.getCurrencyCode());
            linePayload.put("rounding_policy_code", row.getRoundingPolicyCode());
            linePayload.put("warehouse_id", row.getWarehouseId());
            linePayload.put("receipt_location_id", row.getReceiptLocationId());
            linePayloads.add(linePayload);
            autoLineNo++;
            index++;
        }
        insertHistory(tenantId, operationId, "ASN", asnId, "DRAFT", 1L,
                "PROCUREMENT_ASN_CREATED", "采购 ASN 已创建", input.getAsnNo(), now);
        appendEvent(tenantId, command, "inbound.procurement_asn.created", "procurement_inbound_asn",
                asnId, 1L, payload(Map.of(
                        "asn_id", asnId,
                        "asn_no", asn.getAsnNo(),
                        "procurement_order_id", asn.getProcurementOrderId(),
                        "supplier_id", asn.getSupplierId(),
                        "warehouse_id", asn.getWarehouseId(),
                        "current_status", asn.getStatus(),
                        "lines", linePayloads)));
        return InboundCommandResult.builder()
                .procurementOrderId(asn.getProcurementOrderId()).asnId(asnId).asnNo(asn.getAsnNo())
                .asnStatus(asn.getStatus()).aggregateVersion(1L).processedLineCount(linePayloads.size())
                .status("DRAFT").duplicate(false).build();
    }

    private InboundCommandResult sendAsn(Long tenantId, Long operationId, InboundCommand command, LocalDateTime now) {
        AsnDefinition input = command.getAsn();
        AsnDO asn = requireAsn(tenantId, input.getAsnId());
        requireVersion(asn.getVersion(), input.getExpectedVersion());
        require(Set.of("DRAFT", "IN_TRANSIT").contains(asn.getStatus()), "asn is not sendable");
        require(asnMapper.updateStatusCas(tenantId, asn.getAsnId(), asn.getVersion(), "IN_TRANSIT", now) == 1,
                "asn version conflict");
        insertHistory(tenantId, operationId, "ASN", asn.getAsnId(), "IN_TRANSIT", asn.getVersion() + 1,
                "ASN_IN_TRANSIT", "ASN 已发出", null, now);
        appendEvent(tenantId, command, "inbound.procurement_asn.status_changed", "procurement_inbound_asn",
                asn.getAsnId(), asn.getVersion() + 1, payload(Map.of(
                        "asn_id", asn.getAsnId(),
                        "procurement_order_id", asn.getProcurementOrderId(),
                        "supplier_id", asn.getSupplierId(),
                        "warehouse_id", asn.getWarehouseId(),
                        "previous_status", asn.getStatus(),
                        "current_status", "IN_TRANSIT")));
        return InboundCommandResult.builder()
                .procurementOrderId(asn.getProcurementOrderId()).asnId(asn.getAsnId()).asnNo(asn.getAsnNo())
                .asnStatus("IN_TRANSIT").aggregateVersion(asn.getVersion() + 1)
                .status("IN_TRANSIT").duplicate(false).build();
    }

    private InboundCommandResult cancelAsn(Long tenantId, Long operationId, InboundCommand command, LocalDateTime now) {
        AsnDefinition input = command.getAsn();
        AsnDO asn = requireAsn(tenantId, input.getAsnId());
        requireVersion(asn.getVersion(), input.getExpectedVersion());
        require(!Set.of("PARTIAL_RECEIVED", "RECEIVED_PENDING_QUALITY", "CANCELLED").contains(asn.getStatus()),
                "asn cannot be cancelled after receipt");
        require(asnMapper.updateStatusCas(tenantId, asn.getAsnId(), asn.getVersion(), "CANCELLED", now) == 1,
                "asn version conflict");
        insertHistory(tenantId, operationId, "ASN", asn.getAsnId(), "CANCELLED", asn.getVersion() + 1,
                "ASN_CANCELLED", "ASN 已取消", null, now);
        appendEvent(tenantId, command, "inbound.procurement_asn.status_changed", "procurement_inbound_asn",
                asn.getAsnId(), asn.getVersion() + 1, payload(Map.of(
                        "asn_id", asn.getAsnId(),
                        "procurement_order_id", asn.getProcurementOrderId(),
                        "supplier_id", asn.getSupplierId(),
                        "warehouse_id", asn.getWarehouseId(),
                        "previous_status", asn.getStatus(),
                        "current_status", "CANCELLED")));
        return InboundCommandResult.builder()
                .procurementOrderId(asn.getProcurementOrderId()).asnId(asn.getAsnId()).asnNo(asn.getAsnNo())
                .asnStatus("CANCELLED").aggregateVersion(asn.getVersion() + 1)
                .status("CANCELLED").duplicate(false).build();
    }

    private InboundCommandResult recordPartialReceipt(Long tenantId, Long operationId,
                                                      InboundCommand command, String actorPrincipalId,
                                                      LocalDateTime now) {
        ReceiptDefinition input = command.getReceipt();
        AsnDO asn = requireAsn(tenantId, input.getAsnId());
        require(asn.getProcurementOrderId().equals(input.getProcurementOrderId()),
                "receipt procurementOrderId does not match ASN");
        require(asn.getSupplierId().equals(input.getSupplierId()), "receipt supplierId does not match ASN");
        require(asn.getWarehouseId().equals(input.getWarehouseId()), "receipt warehouseId does not match ASN");
        require(Set.of("IN_TRANSIT", "PARTIAL_RECEIVED", "RECEIVED_PENDING_QUALITY").contains(asn.getStatus()),
                "asn is not receivable");

        String receiptId = valueOrUuid(input.getReceiptId());
        ReceiptDO receipt = new ReceiptDO().setReceiptId(receiptId).setTenantId(tenantId).setReceiptNo(input.getReceiptNo())
                .setAsnId(asn.getAsnId()).setProcurementOrderId(asn.getProcurementOrderId())
                .setSupplierId(asn.getSupplierId()).setWarehouseId(asn.getWarehouseId())
                .setStatus("PENDING_QUALITY").setVersion(1L).setRemark(input.getRemark())
                .setCreatedAt(now).setUpdatedAt(now);
        receiptMapper.insert(receipt);

        List<Map<String, Object>> linePayloads = new ArrayList<>();
        int autoLineNo = 1;
        for (ReceiptLineDefinition line : input.getLines()) {
            AsnLineDO asnLine = requireAsnLine(tenantId, line.getAsnLineId());
            require(asnLine.getAsnId().equals(asn.getAsnId()), "receipt line does not belong to ASN");
            validateReceiptLineMatches(asnLine, line);
            warehouseValidationApi.requireActiveLocation(asnLine.getWarehouseId(), line.getReceiptLocationId());
            BigDecimal receivedQuantity = positive(line.getReceivedQuantity(), "receivedQuantity");
            ScheduleFulfillmentDO fulfillment = requireScheduleFulfillment(tenantId, line.getProcurementOrderItemId(),
                    line.getDeliveryScheduleId());
            BigDecimal nextGlobalReceived = fulfillment.getReceivedQuantity().add(receivedQuantity);
            BigDecimal nextGlobalPending = fulfillment.getPendingQualityQuantity().add(receivedQuantity);
            BigDecimal maxAllowed = fulfillment.getOrderedQuantity()
                    .subtract(fulfillment.getCancelledQuantity())
                    .add(fulfillment.getAllowedOverReceiptQuantity());
            require(nextGlobalReceived.compareTo(maxAllowed) <= 0, "receipt exceeds scheduled quantity tolerance");
            BigDecimal nextReceived = asnLine.getReceivedQuantity().add(receivedQuantity);
            BigDecimal nextPendingQuality = asnLine.getPendingQualityQuantity().add(receivedQuantity);
            long movementCostAmountMinor = calculateMovementCostAmountMinor(receivedQuantity,
                    asnLine.getUnitCostAmountMinor(), asnLine.getRoundingPolicyCode());
            String nextLineStatus = nextReceived.compareTo(asnLine.getScheduledQuantity()) < 0
                    ? "PARTIAL_RECEIVED_PENDING_QUALITY" : "FULL_RECEIVED_PENDING_QUALITY";
            String receiptLineId = valueOrUuid(line.getReceiptLineId());
            String inventoryIdempotencyKey = "procurement-receipt:" + receiptId + ":" + receiptLineId;
            InventoryProcurementReceiptResult inventory = inventoryProcurementReceiptApi.execute(
                    InventoryProcurementReceiptCommand.builder()
                            .operation(InventoryProcurementReceiptOperation.RECEIVE_PENDING_QUALITY)
                            .disposition(InventoryProcurementReceiptDisposition.PENDING)
                            .idempotencyKey(inventoryIdempotencyKey)
                            .sourceEventId(eventId(command.getSourceEventId(), "pending-quality", receiptLineId))
                            .receiptId(receiptId).receiptLineId(receiptLineId)
                            .purchaseOrderId(line.getProcurementOrderId())
                            .purchaseOrderItemId(line.getProcurementOrderItemId())
                            .purchaseOrderScheduleId(line.getDeliveryScheduleId())
                            .supplierId(line.getSupplierId())
                            .ownerType(line.getOwnerType()).ownerId(line.getOwnerId())
                            .canonicalSkuId(line.getCanonicalSkuId()).warehouseId(line.getWarehouseId())
                            .locationId(line.getReceiptLocationId()).lotId(line.getLotId())
                            .baseUomCode(line.getBaseUomCode()).quantity(receivedQuantity)
                            .valuationPolicy(asnLine.getValuationPolicyId())
                            .valuationPolicyVersion(asnLine.getValuationPolicyVersion())
                            .valuationPolicyHash(asnLine.getValuationPolicyHash())
                            .unitCostAmountMinor(asnLine.getUnitCostAmountMinor())
                            .movementCostAmountMinor(movementCostAmountMinor)
                            .currencyCode(asnLine.getCurrencyCode())
                            .businessNo(receipt.getReceiptNo())
                            .correlationId(command.getCorrelationId()).causationId(command.getCausationId())
                            .occurredAt(command.getOccurredAt()).build());
            require(inventory.getOperationId() != null && inventory.getLedgerTransactionId() != null,
                    "inventory procurement receipt did not return authoritative references");

            require(scheduleFulfillmentMapper.updateReceiptCas(tenantId, fulfillment.getScheduleFulfillmentId(),
                            fulfillment.getVersion(), nextGlobalReceived, nextGlobalPending, now) == 1,
                    "global schedule fulfillment version conflict");
            require(asnLineMapper.updateFulfillmentCas(tenantId, asnLine.getAsnLineId(), line.getExpectedFulfillmentVersion(),
                    nextReceived, nextPendingQuality, nextLineStatus, now) == 1,
                    "delivery schedule fulfillment version conflict");
            ReceiptLineDO receiptLine = new ReceiptLineDO()
                    .setReceiptLineId(receiptLineId).setTenantId(tenantId)
                    .setReceiptId(receiptId).setLineNo(line.getLineNo() == null ? autoLineNo : line.getLineNo())
                    .setAsnLineId(asnLine.getAsnLineId()).setProcurementOrderId(line.getProcurementOrderId())
                    .setProcurementOrderItemId(line.getProcurementOrderItemId())
                    .setDeliveryScheduleId(line.getDeliveryScheduleId()).setPoReleaseVersion(line.getPoReleaseVersion())
                    .setFulfillmentVersionBefore(line.getExpectedFulfillmentVersion())
                    .setFulfillmentVersionAfter(line.getExpectedFulfillmentVersion() + 1)
                    .setSupplierId(line.getSupplierId()).setWarehouseId(line.getWarehouseId())
                    .setReceiptLocationId(line.getReceiptLocationId()).setCanonicalSkuId(line.getCanonicalSkuId())
                    .setOwnerType(line.getOwnerType()).setOwnerId(line.getOwnerId()).setBaseUomCode(line.getBaseUomCode())
                    .setLotId(line.getLotId()).setQualityStatus("PENDING_QUALITY")
                    .setQualityInspectionId(line.getQualityInspectionId()).setReceivedQuantity(receivedQuantity)
                    .setPendingQualityQuantity(inventory.getPendingQuantity())
                    .setAcceptedQuantity(ZERO).setRejectedQuantity(ZERO).setQuarantinedQuantity(ZERO)
                    .setCumulativePutawayQuantity(ZERO)
                    .setValuationPolicyId(asnLine.getValuationPolicyId())
                    .setValuationPolicyVersion(asnLine.getValuationPolicyVersion())
                    .setValuationPolicyHash(asnLine.getValuationPolicyHash())
                    .setUnitCostAmountMinor(asnLine.getUnitCostAmountMinor())
                    .setMovementCostAmountMinor(movementCostAmountMinor).setCurrencyCode(asnLine.getCurrencyCode())
                    .setRoundingPolicyCode(asnLine.getRoundingPolicyCode())
                    .setTolerancePolicyVersion(asnLine.getTolerancePolicyVersion())
                    .setTolerancePolicyHash(asnLine.getTolerancePolicyHash()).setInventoryIdempotencyKey(inventoryIdempotencyKey)
                    .setInventoryOperationId(inventory.getOperationId()).setInventoryLedgerTxId(inventory.getLedgerTransactionId())
                    .setInventoryBalanceId(inventory.getTargetBalanceId()).setVersion(1L)
                    .setCreatedAt(now).setUpdatedAt(now);
            receiptLineMapper.insert(receiptLine);
            String financeSourceEventId = eventId(command.getSourceEventId(), "finance-receipt-evidence", receiptLineId);
            ProcureToPayResult financeEvidence = p2pEvidenceIngestionApi.ingestReceiptLine(
                    P2pEvidenceCommands.ReceiptLine.builder()
                            .envelope(FinanceCommandEnvelope.builder()
                                    .correlationId(command.getCorrelationId())
                                    .causationId(command.getCausationId())
                                    .idempotencyKey("warehouse-receipt-evidence:" + receiptLineId)
                                    .occurredAt(command.getOccurredAt())
                                    .build())
                            .sourceEventId(financeSourceEventId)
                            .sourceVersion(receiptLine.getVersion())
                            .evidenceSha256(receiptEvidenceSha256(receiptLine, financeSourceEventId,
                                    command.getOccurredAt()))
                            .sourceOccurredAt(command.getOccurredAt())
                            .receiptId(receiptId).receiptLineId(receiptLineId)
                            .purchaseOrderId(receiptLine.getProcurementOrderId())
                            .purchaseOrderItemId(receiptLine.getProcurementOrderItemId())
                            .purchaseOrderLineVersion(receiptLine.getPoReleaseVersion())
                            .deliveryScheduleId(receiptLine.getDeliveryScheduleId())
                            .receivedQuantity(receiptLine.getReceivedQuantity())
                            .unitOfMeasure(receiptLine.getBaseUomCode())
                            .build(), actorPrincipalId);
            require(financeEvidence != null && financeEvidence.getOperationId() != null
                            && financeEvidence.getAggregateId() != null && financeEvidence.getAggregateVersion() != null
                            && "finance_receipt_line_evidence".equals(financeEvidence.getAggregateType())
                            && "RECORDED".equals(financeEvidence.getStatus()),
                    "finance did not persist authoritative receipt-line evidence");
            require(receiptLineMapper.bindFinanceEvidence(tenantId, receiptLineId, financeEvidence.getOperationId(),
                    financeEvidence.getAggregateId(), financeEvidence.getAggregateVersion(), now) == 1,
                    "failed to bind finance receipt-line evidence");
            receiptLine.setFinanceReceiptEvidenceOperationId(financeEvidence.getOperationId())
                    .setFinanceReceiptEvidenceId(financeEvidence.getAggregateId())
                    .setFinanceReceiptEvidenceVersion(financeEvidence.getAggregateVersion());
            insertHistory(tenantId, operationId, "ASN_LINE", asnLine.getAsnLineId(), nextLineStatus,
                    line.getExpectedFulfillmentVersion() + 1, "PROCUREMENT_RECEIPT_RECORDED",
                    "采购收货已登记", receiptLine.getReceiptLineId(), now);
            Map<String, Object> linePayload = new LinkedHashMap<>();
            linePayload.put("receipt_line_id", receiptLine.getReceiptLineId());
            linePayload.put("asn_line_id", receiptLine.getAsnLineId());
            linePayload.put("procurement_order_item_id", receiptLine.getProcurementOrderItemId());
            linePayload.put("delivery_schedule_id", receiptLine.getDeliveryScheduleId());
            linePayload.put("po_release_version", receiptLine.getPoReleaseVersion());
            linePayload.put("supplier_id", receiptLine.getSupplierId());
            linePayload.put("warehouse_id", receiptLine.getWarehouseId());
            linePayload.put("receipt_location_id", receiptLine.getReceiptLocationId());
            linePayload.put("canonical_sku_id", receiptLine.getCanonicalSkuId());
            linePayload.put("received_quantity", receiptLine.getReceivedQuantity());
            linePayload.put("quality_status", receiptLine.getQualityStatus());
            linePayload.put("valuation_policy_id", receiptLine.getValuationPolicyId());
            linePayload.put("valuation_policy_version", receiptLine.getValuationPolicyVersion());
            linePayload.put("valuation_policy_hash", receiptLine.getValuationPolicyHash());
            linePayload.put("unit_cost_amount_minor", receiptLine.getUnitCostAmountMinor());
            linePayload.put("movement_cost_amount_minor", receiptLine.getMovementCostAmountMinor());
            linePayload.put("currency_code", receiptLine.getCurrencyCode());
            linePayload.put("rounding_policy_code", receiptLine.getRoundingPolicyCode());
            linePayload.put("tolerance_policy_version", receiptLine.getTolerancePolicyVersion());
            linePayload.put("tolerance_policy_hash", receiptLine.getTolerancePolicyHash());
            linePayloads.add(linePayload);
            autoLineNo++;
        }

        List<AsnLineDO> lines = asnLineMapper.selectByAsn(tenantId, asn.getAsnId());
        String nextAsnStatus = lines.stream().allMatch(line -> "FULL_RECEIVED_PENDING_QUALITY".equals(line.getStatus()))
                ? "RECEIVED_PENDING_QUALITY" : "PARTIAL_RECEIVED";
        require(asnMapper.updateStatusCas(tenantId, asn.getAsnId(), asn.getVersion(), nextAsnStatus, now) == 1,
                "asn version conflict");
        insertHistory(tenantId, operationId, "RECEIPT", receiptId, "PENDING_QUALITY", 1L,
                "PROCUREMENT_RECEIPT_PENDING_QUALITY", "采购收货待质检", receipt.getRemark(), now);
        insertHistory(tenantId, operationId, "ASN", asn.getAsnId(), nextAsnStatus, asn.getVersion() + 1,
                "PENDING_QUALITY", "ASN 已收货待质检", receiptId, now);
        appendEvent(tenantId, command, "inbound.procurement_receipt.recorded", "procurement_receipt",
                receiptId, 1L, payload(Map.of(
                        "receipt_id", receiptId,
                        "receipt_no", receipt.getReceiptNo(),
                        "asn_id", asn.getAsnId(),
                        "asn_no", asn.getAsnNo(),
                        "procurement_order_id", asn.getProcurementOrderId(),
                        "supplier_id", asn.getSupplierId(),
                        "warehouse_id", asn.getWarehouseId(),
                        "receipt_status", receipt.getStatus(),
                        "asn_status", nextAsnStatus,
                        "lines", linePayloads)));
        return InboundCommandResult.builder()
                .procurementOrderId(asn.getProcurementOrderId()).asnId(asn.getAsnId()).asnNo(asn.getAsnNo())
                .asnStatus(nextAsnStatus).receiptId(receiptId).receiptNo(receipt.getReceiptNo())
                .receiptStatus(receipt.getStatus()).aggregateVersion(asn.getVersion() + 1)
                .processedLineCount(linePayloads.size()).status(receipt.getStatus()).duplicate(false).build();
    }

    private InboundCommandResult completePutaway(Long tenantId, Long operationId,
                                                 InboundCommand command, LocalDateTime now) {
        PutawayDefinition input = command.getPutaway();
        ReceiptDO receipt = requireReceipt(tenantId, input.getReceiptId());
        requireVersion(receipt.getVersion(), input.getExpectedVersion());
        require(receipt.getWarehouseId().equals(input.getWarehouseId()), "putaway warehouseId does not match receipt");
        String putawayId = valueOrUuid(input.getPutawayId());
        PutawayDO row = new PutawayDO().setPutawayId(putawayId).setTenantId(tenantId)
                .setReceiptId(receipt.getReceiptId()).setWarehouseId(receipt.getWarehouseId())
                .setStatus("COMPLETED").setVersion(1L).setCreatedAt(now).setUpdatedAt(now);
        putawayMapper.insert(row);
        List<PutawayLineDefinition> requestedLines = input.getLines().stream()
                .sorted(java.util.Comparator.comparing(PutawayLineDefinition::getReceiptLineId))
                .toList();
        Set<String> receiptLineIds = new LinkedHashSet<>();
        Set<String> putawayLineIds = new LinkedHashSet<>();
        List<ReceiptLineDO> changedLines = new ArrayList<>();
        List<Map<String, Object>> eventLines = new ArrayList<>();
        for (PutawayLineDefinition requested : requestedLines) {
            require(receiptLineIds.add(requested.getReceiptLineId()), "duplicate receiptLineId in putaway");
            String putawayLineId = valueOrUuid(requested.getPutawayLineId());
            require(putawayLineIds.add(putawayLineId), "duplicate putawayLineId in putaway");
            ReceiptLineDO line = receiptLineMapper.selectForUpdate(tenantId, requested.getReceiptLineId());
            require(line != null && line.getReceiptId().equals(receipt.getReceiptId()),
                    "putaway receipt line does not belong to receipt");
            requireVersion(line.getVersion(), requested.getExpectedReceiptLineVersion());
            require(line.getWarehouseId().equals(input.getWarehouseId()), "putaway line warehouseId mismatch");
            require(line.getReceiptLocationId().equals(requested.getSourceLocationId()),
                    "putaway sourceLocationId must match receipt location");
            require(Objects.equals(line.getLotId(), requested.getLotId()), "putaway lotId mismatch");
            warehouseValidationApi.requireActiveLocation(line.getWarehouseId(), requested.getSourceLocationId());
            warehouseValidationApi.requireActiveLocation(line.getWarehouseId(), requested.getTargetLocationId());
            require(!requested.getSourceLocationId().equals(requested.getTargetLocationId()),
                    "putaway source and target locations must differ");
            BigDecimal quantity = positive(requested.getQuantity(), "putaway quantity");
            BigDecimal currentCumulative = nonNegative(line.getCumulativePutawayQuantity(),
                    "cumulativePutawayQuantity");
            BigDecimal nextCumulative = currentCumulative.add(quantity);
            require(nextCumulative.compareTo(nonNegative(line.getAcceptedQuantity(), "acceptedQuantity")) <= 0,
                    "putaway quantity exceeds accepted not-yet-putaway quantity");
            InventoryV3CommandResult inventory = inventoryV3CommandApi.execute(InventoryV3Command.builder()
                    .operation(InventoryV3Operation.RELOCATE)
                    .idempotencyKey("procurement-putaway:" + putawayId + ":" + putawayLineId)
                    .sourceEventId(eventId(command.getSourceEventId(), "putaway", putawayLineId))
                    .ownerType(line.getOwnerType()).ownerId(line.getOwnerId())
                    .canonicalSkuId(line.getCanonicalSkuId()).warehouseId(line.getWarehouseId())
                    .locationId(requested.getSourceLocationId()).targetLocationId(requested.getTargetLocationId())
                    .lotId(line.getLotId()).stockStatus("SELLABLE").qualityStatus("QUALIFIED")
                    .baseUomCode(line.getBaseUomCode()).quantity(quantity)
                    .businessType("PROCUREMENT_PUTAWAY").businessId(putawayId)
                    .businessItemId(putawayLineId).businessNo(receipt.getReceiptNo())
                    .correlationId(command.getCorrelationId()).causationId(command.getCausationId())
                    .occurredAt(command.getOccurredAt()).build());
            require(inventory.getOperationId() != null && inventory.getLedgerTransactionId() != null
                            && inventory.getMovementGroupId() != null && inventory.getBalanceId() != null,
                    "inventory relocate did not return authoritative references");
            String lineStatus = WarehouseProcurementQualityDecisionService.deriveLineStatus(
                    line.getReceivedQuantity(), line.getPendingQualityQuantity(), line.getAcceptedQuantity(),
                    line.getRejectedQuantity(), line.getQuarantinedQuantity(), nextCumulative);
            require(receiptLineMapper.updatePutawayCas(tenantId, line.getReceiptLineId(), line.getVersion(),
                    currentCumulative, nextCumulative, lineStatus, now) == 1,
                    "procurement receipt line putaway version conflict");
            PutawayLineDO putawayLine = new PutawayLineDO().setPutawayLineId(putawayLineId).setTenantId(tenantId)
                    .setPutawayId(putawayId).setReceiptId(receipt.getReceiptId())
                    .setReceiptLineId(line.getReceiptLineId()).setWarehouseId(line.getWarehouseId())
                    .setSourceLocationId(requested.getSourceLocationId())
                    .setTargetLocationId(requested.getTargetLocationId()).setCanonicalSkuId(line.getCanonicalSkuId())
                    .setOwnerType(line.getOwnerType()).setOwnerId(line.getOwnerId()).setLotId(line.getLotId())
                    .setBaseUomCode(line.getBaseUomCode()).setPutawayQuantity(quantity)
                    .setCumulativePutawayQuantity(nextCumulative).setStatus("COMPLETED").setVersion(1L)
                    .setInventoryOperationId(inventory.getOperationId())
                    .setInventoryLedgerTxId(inventory.getLedgerTransactionId())
                    .setInventoryMovementGroupId(inventory.getMovementGroupId())
                    .setInventoryTargetBalanceId(inventory.getBalanceId()).setCreatedAt(now).setUpdatedAt(now);
            putawayLineMapper.insert(putawayLine);
            line.setCumulativePutawayQuantity(nextCumulative).setQualityStatus(lineStatus)
                    .setVersion(line.getVersion() + 1);
            changedLines.add(line);
            insertHistory(tenantId, operationId, "PUTAWAY_LINE", putawayLineId, "COMPLETED", 1L,
                    "PROCUREMENT_PUTAWAY_LINE_COMPLETED", "合格收货行已分批上架", line.getReceiptLineId(), now);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("putaway_line_id", putawayLineId);
            payload.put("receipt_line_id", line.getReceiptLineId());
            payload.put("source_location_id", requested.getSourceLocationId());
            payload.put("target_location_id", requested.getTargetLocationId());
            payload.put("lot_id", line.getLotId());
            payload.put("putaway_quantity", quantity);
            payload.put("cumulative_putaway_quantity", nextCumulative);
            payload.put("inventory_operation_id", inventory.getOperationId());
            payload.put("inventory_ledger_transaction_id", inventory.getLedgerTransactionId());
            payload.put("inventory_movement_group_id", inventory.getMovementGroupId());
            payload.put("inventory_target_balance_id", inventory.getBalanceId());
            eventLines.add(payload);
        }
        List<ReceiptLineDO> allLines = receiptLineMapper.selectByReceipt(tenantId, receipt.getReceiptId());
        for (ReceiptLineDO changed : changedLines) {
            allLines = allLines.stream().map(existing -> existing.getReceiptLineId().equals(changed.getReceiptLineId())
                    ? changed : existing).toList();
        }
        String receiptStatus = WarehouseProcurementQualityDecisionService.deriveReceiptStatus(allLines);
        require(receiptMapper.updateStatusCas(tenantId, receipt.getReceiptId(), receipt.getVersion(),
                receiptStatus, now) == 1, "receipt version conflict");
        insertHistory(tenantId, operationId, "PUTAWAY", putawayId, "COMPLETED", 1L,
                "PROCUREMENT_PUTAWAY_COMPLETED", "采购入库上架完成", receipt.getReceiptId(), now);
        insertHistory(tenantId, operationId, "RECEIPT", receipt.getReceiptId(), receiptStatus,
                receipt.getVersion() + 1, "PROCUREMENT_PUTAWAY_AGGREGATED", "收货上架状态已聚合", putawayId, now);
        appendEvent(tenantId, command, "inbound.procurement_putaway.completed", "procurement_putaway",
                putawayId, 1L, payload(Map.of(
                        "putaway_id", putawayId,
                        "receipt_id", receipt.getReceiptId(),
                        "receipt_no", receipt.getReceiptNo(),
                        "procurement_order_id", receipt.getProcurementOrderId(),
                        "warehouse_id", receipt.getWarehouseId(),
                        "receipt_status", receiptStatus,
                        "current_status", row.getStatus(),
                        "lines", eventLines)));
        return InboundCommandResult.builder()
                .procurementOrderId(receipt.getProcurementOrderId()).receiptId(receipt.getReceiptId())
                .receiptNo(receipt.getReceiptNo()).receiptStatus(receiptStatus)
                .putawayId(putawayId).aggregateVersion(1L).processedLineCount(requestedLines.size())
                .status("COMPLETED").duplicate(false).build();
    }

    private ProcurementOrderLineRef requireOrderLine(ProcurementOrderView order, AsnLineDefinition line) {
        require(order.getReleasedVersion() != null && order.getReleasedVersion().equals(line.getPoReleaseVersion()),
                "poReleaseVersion does not match the authoritative procurement release version");
        require(order.getSupplierId().equals(line.getSupplierId()), "supplierId does not match procurement order");
        ProcurementOrderView.PurchaseOrderItemView item = order.getItems().stream()
                .filter(candidate -> candidate.getItemId().equals(line.getProcurementOrderItemId()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("procurement order item not found"));
        require(item.getCanonicalSkuId().equals(line.getCanonicalSkuId()), "canonicalSkuId does not match procurement order item");
        require(item.getUomCode().equals(line.getBaseUomCode()), "baseUomCode does not match procurement order item");
        requireText(item.getValuationPolicyId(), "procurement valuationPolicyId", 128);
        requireText(item.getValuationPolicyVersion(), "procurement valuationPolicyVersion", 64);
        requireSha256(item.getValuationPolicyHash(), "procurement valuationPolicyHash");
        require(item.getUnitNetPriceMinor() != null && item.getUnitNetPriceMinor().signum() >= 0,
                "procurement unitNetPriceMinor is required");
        ProcurementOrderView.PurchaseOrderDeliveryScheduleView schedule = item.getSchedules().stream()
                .filter(candidate -> candidate.getScheduleId().equals(line.getDeliveryScheduleId()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("procurement delivery schedule not found"));
        require(schedule.getCanonicalWarehouseId().equals(line.getWarehouseId()),
                "warehouseId does not match procurement delivery schedule");
        require(schedule.getScheduledQuantity().compareTo(line.getScheduledQuantity()) == 0,
                "scheduledQuantity must match procurement delivery schedule");
        return new ProcurementOrderLineRef(item, schedule);
    }

    private ScheduleFulfillmentDO ensureScheduleFulfillment(Long tenantId, ProcurementOrderView order,
                                                            AsnLineDefinition line,
                                                            ProcurementOrderLineRef ref, LocalDateTime now) {
        scheduleFulfillmentMapper.insertIgnore(new ScheduleFulfillmentDO()
                .setScheduleFulfillmentId(UUID.randomUUID().toString()).setTenantId(tenantId)
                .setProcurementOrderId(order.getOrderId()).setProcurementOrderItemId(line.getProcurementOrderItemId())
                .setDeliveryScheduleId(line.getDeliveryScheduleId()).setOrderedQuantity(ref.schedule().getScheduledQuantity())
                .setCancelledQuantity(ZERO).setAllowedOverReceiptQuantity(nonNegative(line.getAllowedOverReceiptQuantity(),
                        "allowedOverReceiptQuantity"))
                .setReceivedQuantity(ZERO).setPendingQualityQuantity(ZERO).setAcceptedQuantity(ZERO)
                .setRejectedQuantity(ZERO).setQuarantinedQuantity(ZERO).setReturnedQuantity(ZERO)
                .setTolerancePolicyVersion(line.getTolerancePolicyVersion())
                .setTolerancePolicyHash(line.getTolerancePolicyHash()).setVersion(1L)
                .setCreatedAt(now).setUpdatedAt(now));
        ScheduleFulfillmentDO current = requireScheduleFulfillment(tenantId, line.getProcurementOrderItemId(),
                line.getDeliveryScheduleId());
        require(current.getProcurementOrderId().equals(order.getOrderId()),
                "schedule fulfillment conflicts with different procurement order");
        require(current.getOrderedQuantity().compareTo(ref.schedule().getScheduledQuantity()) == 0,
                "schedule fulfillment ordered quantity mismatch");
        require(current.getAllowedOverReceiptQuantity().compareTo(line.getAllowedOverReceiptQuantity()) == 0,
                "schedule fulfillment tolerance mismatch");
        require(current.getTolerancePolicyVersion().equals(line.getTolerancePolicyVersion())
                        && current.getTolerancePolicyHash().equals(line.getTolerancePolicyHash()),
                "schedule fulfillment policy mismatch");
        return current;
    }

    private ScheduleFulfillmentDO requireScheduleFulfillment(Long tenantId, String procurementOrderItemId,
                                                             String deliveryScheduleId) {
        ScheduleFulfillmentDO row = scheduleFulfillmentMapper.selectForUpdate(tenantId, procurementOrderItemId, deliveryScheduleId);
        require(row != null, "procurement schedule fulfillment not found");
        return row;
    }

    private void validateReceiptLineMatches(AsnLineDO asnLine, ReceiptLineDefinition line) {
        require(asnLine.getProcurementOrderId().equals(line.getProcurementOrderId()),
                "receipt procurementOrderId does not match ASN line");
        require(asnLine.getProcurementOrderItemId().equals(line.getProcurementOrderItemId()),
                "receipt procurementOrderItemId does not match ASN line");
        require(asnLine.getDeliveryScheduleId().equals(line.getDeliveryScheduleId()),
                "receipt deliveryScheduleId does not match ASN line");
        require(asnLine.getPoReleaseVersion().equals(line.getPoReleaseVersion()),
                "receipt poReleaseVersion does not match ASN line");
        require(asnLine.getSupplierId().equals(line.getSupplierId()), "receipt supplierId does not match ASN line");
        require(asnLine.getWarehouseId().equals(line.getWarehouseId()), "receipt warehouseId does not match ASN line");
        require(asnLine.getReceiptLocationId().equals(line.getReceiptLocationId()),
                "receipt location does not match ASN line");
        require(asnLine.getCanonicalSkuId().equals(line.getCanonicalSkuId()),
                "receipt canonicalSkuId does not match ASN line");
        require(asnLine.getOwnerType().equals(line.getOwnerType()), "receipt ownerType does not match ASN line");
        require(asnLine.getOwnerId().equals(line.getOwnerId()), "receipt ownerId does not match ASN line");
        require(asnLine.getBaseUomCode().equals(line.getBaseUomCode()), "receipt baseUomCode does not match ASN line");
        require("PENDING_QUALITY".equals(upper(line.getQualityStatus())), "qualityStatus must be PENDING_QUALITY");
    }

    private AsnDO requireAsn(Long tenantId, String asnId) {
        AsnDO row = asnMapper.selectForUpdate(tenantId, asnId);
        require(row != null, "asn does not exist");
        return row;
    }

    private AsnLineDO requireAsnLine(Long tenantId, String asnLineId) {
        AsnLineDO row = asnLineMapper.selectForUpdate(tenantId, asnLineId);
        require(row != null, "asn line does not exist");
        return row;
    }

    private ReceiptDO requireReceipt(Long tenantId, String receiptId) {
        ReceiptDO row = receiptMapper.selectForUpdate(tenantId, receiptId);
        require(row != null, "receipt does not exist");
        return row;
    }

    private void appendEvent(Long tenantId, InboundCommand command, String eventType,
                             String aggregateType, String aggregateId, Long aggregateVersion,
                             Map<String, Object> payload) {
        outboxAppender.append(AppendDomainEventCommand.builder()
                .eventId(UUID.randomUUID().toString()).eventType(eventType).schemaVersion(1)
                .sourceSystem("cloudmold-warehouse").tenantId(tenantId).aggregateType(aggregateType)
                .aggregateId(aggregateId).aggregateVersion(aggregateVersion).eventSequence((short) 1)
                .occurredAt(command.getOccurredAt()).correlationId(command.getCorrelationId())
                .causationId(command.getCausationId())
                .idempotencyKey(command.getIdempotencyKey() + ":event")
                .payload(payload).headers(Map.of("operation", command.getOperation().name()))
                .destination("lakehouse").build());
    }

    private void insertHistory(Long tenantId, Long operationId, String objectType, String objectId,
                               String status, Long version, String stageCode, String stageLabel,
                               String remark, LocalDateTime now) {
        historyMapper.insert(new InboundStatusHistoryDO().setHistoryId(UUID.randomUUID().toString())
                .setTenantId(tenantId).setOperationId(operationId).setBusinessObjectType(objectType)
                .setBusinessObjectId(objectId).setStatus(status).setStatusVersion(version)
                .setStageCode(stageCode).setStageLabel(stageLabel).setRemark(remark)
                .setChangedAt(now).setCreatedAt(now));
    }

    static String fingerprint(Long tenantId, InboundCommand command) {
        return DigestUtil.sha256Hex(tenantId + "\u001f" + JsonUtils.toJsonString(command));
    }

    static String receiptEvidenceSha256(ReceiptLineDO line, String sourceEventId, java.time.Instant sourceOccurredAt) {
        String canonical = String.join("\u001f",
                sourceEventId,
                line.getVersion().toString(),
                sourceOccurredAt.toString(),
                line.getReceiptId(),
                line.getReceiptLineId(),
                line.getProcurementOrderId(),
                line.getProcurementOrderItemId(),
                line.getDeliveryScheduleId() == null ? "" : line.getDeliveryScheduleId(),
                line.getReceivedQuantity().toPlainString(),
                upper(line.getBaseUomCode()));
        return DigestUtil.sha256Hex(canonical);
    }

    private static void validate(InboundCommand command) {
        require(command != null && command.getOperation() != null, "operation is required");
        requireText(command.getIdempotencyKey(), "idempotencyKey", 128);
        requireUuid(command.getCorrelationId(), "correlationId");
        if (command.getCausationId() != null) {
            requireUuid(command.getCausationId(), "causationId");
        }
        require(command.getOccurredAt() != null, "occurredAt is required");
        switch (command.getOperation()) {
            case CREATE_ASN -> validateCreateAsn(command.getAsn());
            case SEND_ASN, CANCEL_ASN -> validateAsnStatus(command.getAsn());
            case COMPLETE_RECEIPT -> validateReceipt(command.getReceipt());
            case COMPLETE_PUTAWAY -> validatePutaway(command.getPutaway());
        }
    }

    private static void validateCreateAsn(AsnDefinition asn) {
        require(asn != null, "asn is required");
        requireText(asn.getAsnNo(), "asnNo", 64);
        requireText(asn.getProcurementOrderId(), "procurementOrderId", 128);
        requireText(asn.getSupplierId(), "supplierId", 128);
        requireText(asn.getWarehouseId(), "warehouseId", 128);
        require(asn.getLines() != null && !asn.getLines().isEmpty(), "asn lines are required");
        for (AsnLineDefinition line : asn.getLines()) {
            requireText(line.getProcurementOrderId(), "procurementOrderId", 128);
            requireText(line.getProcurementOrderItemId(), "procurementOrderItemId", 128);
            requireText(line.getDeliveryScheduleId(), "deliveryScheduleId", 128);
            require(line.getPoReleaseVersion() != null && line.getPoReleaseVersion() > 0, "poReleaseVersion is required");
            requireText(line.getSupplierId(), "supplierId", 128);
            requireText(line.getWarehouseId(), "warehouseId", 128);
            requireText(line.getReceiptLocationId(), "receiptLocationId", 128);
            requireText(line.getCanonicalSkuId(), "canonicalSkuId", 128);
            requireText(line.getOwnerType(), "ownerType", 64);
            requireText(line.getOwnerId(), "ownerId", 128);
            requireText(line.getBaseUomCode(), "baseUomCode", 64);
            positive(line.getScheduledQuantity(), "scheduledQuantity");
            nonNegative(line.getAllowedOverReceiptQuantity(), "allowedOverReceiptQuantity");
        }
    }

    private static void validateAsnStatus(AsnDefinition asn) {
        require(asn != null, "asn is required");
        requireText(asn.getAsnId(), "asnId", 128);
        require(asn.getExpectedVersion() != null && asn.getExpectedVersion() > 0, "expectedVersion is required");
    }

    private static void validateReceipt(ReceiptDefinition receipt) {
        require(receipt != null, "receipt is required");
        requireText(receipt.getReceiptNo(), "receiptNo", 64);
        requireText(receipt.getAsnId(), "asnId", 128);
        requireText(receipt.getProcurementOrderId(), "procurementOrderId", 128);
        requireText(receipt.getSupplierId(), "supplierId", 128);
        requireText(receipt.getWarehouseId(), "warehouseId", 128);
        require(receipt.getLines() != null && !receipt.getLines().isEmpty(), "receipt lines are required");
        for (ReceiptLineDefinition line : receipt.getLines()) {
            requireText(line.getAsnLineId(), "asnLineId", 128);
            requireText(line.getProcurementOrderId(), "procurementOrderId", 128);
            requireText(line.getProcurementOrderItemId(), "procurementOrderItemId", 128);
            requireText(line.getDeliveryScheduleId(), "deliveryScheduleId", 128);
            require(line.getPoReleaseVersion() != null && line.getPoReleaseVersion() > 0, "poReleaseVersion is required");
            require(line.getExpectedFulfillmentVersion() != null && line.getExpectedFulfillmentVersion() > 0,
                    "expectedFulfillmentVersion is required");
            requireText(line.getSupplierId(), "supplierId", 128);
            requireText(line.getWarehouseId(), "warehouseId", 128);
            requireText(line.getReceiptLocationId(), "receiptLocationId", 128);
            requireText(line.getCanonicalSkuId(), "canonicalSkuId", 128);
            requireText(line.getOwnerType(), "ownerType", 64);
            requireText(line.getOwnerId(), "ownerId", 128);
            requireText(line.getBaseUomCode(), "baseUomCode", 64);
            positive(line.getReceivedQuantity(), "receivedQuantity");
            requireText(line.getQualityStatus(), "qualityStatus", 64);
        }
    }

    private static void validatePutaway(PutawayDefinition putaway) {
        require(putaway != null, "putaway is required");
        requireText(putaway.getReceiptId(), "receiptId", 128);
        requireText(putaway.getWarehouseId(), "warehouseId", 128);
        require(putaway.getExpectedVersion() != null && putaway.getExpectedVersion() > 0,
                "expectedVersion is required");
        require(putaway.getLines() != null && !putaway.getLines().isEmpty(), "putaway lines are required");
        for (PutawayLineDefinition line : putaway.getLines()) {
            requireText(line.getReceiptLineId(), "receiptLineId", 128);
            requireText(line.getSourceLocationId(), "sourceLocationId", 128);
            requireText(line.getTargetLocationId(), "targetLocationId", 128);
            positive(line.getQuantity(), "putaway quantity");
            require(line.getExpectedReceiptLineVersion() != null && line.getExpectedReceiptLineVersion() > 0,
                    "expectedReceiptLineVersion is required");
        }
    }

    private static BigDecimal positive(BigDecimal value, String field) {
        require(value != null && value.signum() > 0 && value.scale() <= 6 && value.precision() <= 24,
                field + " must be a positive DECIMAL(24,6)");
        return value.setScale(6);
    }

    private static BigDecimal nonNegative(BigDecimal value, String field) {
        require(value != null && value.signum() >= 0 && value.scale() <= 6 && value.precision() <= 24,
                field + " must be a non-negative DECIMAL(24,6)");
        return value.setScale(6);
    }

    private static String upper(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private static long frozenUnitCostMinor(BigDecimal unitNetPriceMinor) {
        require(unitNetPriceMinor != null && unitNetPriceMinor.signum() >= 0,
                "procurement unitNetPriceMinor must be non-negative");
        try {
            return unitNetPriceMinor.longValueExact();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "procurement unitNetPriceMinor must be an exact integer minor-unit amount", exception);
        }
    }

    private static long calculateMovementCostAmountMinor(BigDecimal quantity, Long unitCostAmountMinor,
                                                         String roundingPolicyCode) {
        require(unitCostAmountMinor != null && unitCostAmountMinor >= 0, "frozen unit cost is invalid");
        require("HALF_UP".equals(roundingPolicyCode), "frozen rounding policy must be HALF_UP");
        return quantity.multiply(BigDecimal.valueOf(unitCostAmountMinor))
                .setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private static String requireCurrency(String value) {
        require(value != null && value.matches("[A-Za-z]{3}"), "procurement currencyCode must be ISO alpha-3");
        return upper(value);
    }

    private static String valueOrUuid(String value) {
        return value == null || value.isBlank() ? UUID.randomUUID().toString() : value;
    }

    private static Map<String, Object> payload(Map<String, Object> value) {
        return new LinkedHashMap<>(value);
    }

    private static String eventId(String sourceEventId, String phase, String receiptLineId) {
        String source = sourceEventId == null || sourceEventId.isBlank()
                ? "warehouse-procurement-inbound" : sourceEventId;
        return UUID.nameUUIDFromBytes((source + "\u001f" + phase + "\u001f" + receiptLineId)
                .getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static void requireVersion(Long currentVersion, Long expectedVersion) {
        require(currentVersion.equals(expectedVersion), "optimistic lock conflict");
    }

    private static void requireText(String value, String field, int max) {
        require(value != null && !value.isBlank() && value.length() <= max, field + " is required");
    }

    private static void requireSha256(String value, String field) {
        require(value != null && value.matches("[0-9a-f]{64}"), field + " must be a lowercase SHA-256");
    }

    private static void requireUuid(String value, String field) {
        try {
            UUID.fromString(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(field + " must be a UUID", exception);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    private record ProcurementOrderLineRef(ProcurementOrderView.PurchaseOrderItemView item,
                                           ProcurementOrderView.PurchaseOrderDeliveryScheduleView schedule) {
    }
}
