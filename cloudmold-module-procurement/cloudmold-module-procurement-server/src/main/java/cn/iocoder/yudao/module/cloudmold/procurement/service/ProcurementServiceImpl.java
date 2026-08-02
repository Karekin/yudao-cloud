package cn.iocoder.yudao.module.cloudmold.procurement.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.finance.api.p2p.FinanceCommandEnvelope;
import cn.iocoder.yudao.module.cloudmold.finance.api.p2p.P2pEvidenceCommands;
import cn.iocoder.yudao.module.cloudmold.finance.api.p2p.P2pEvidenceIngestionApi;
import cn.iocoder.yudao.module.cloudmold.procurement.api.ProcurementCommand;
import cn.iocoder.yudao.module.cloudmold.procurement.api.ProcurementCommandApi;
import cn.iocoder.yudao.module.cloudmold.procurement.api.ProcurementOrderView;
import cn.iocoder.yudao.module.cloudmold.procurement.api.ProcurementOperation;
import cn.iocoder.yudao.module.cloudmold.procurement.api.ProcurementResult;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementRecords.Operation;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementRecords.OrderStatusHistory;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementRecords.ProcurementOrder;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementRecords.PurchaseOrderDeliverySchedule;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementRecords.PurchaseOrderAwardSource;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementRecords.PurchaseOrderItem;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.mysql.ProcurementMapper;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.mysql.ProcurementSourcingMapper;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementSourcingRecords.Award;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementSourcingRecords.AwardLine;
import cn.iocoder.yudao.module.cloudmold.procurement.service.actor.ProcurementActorPrincipalPort;
import cn.iocoder.yudao.module.cloudmold.procurement.service.reference.ProcurementReferenceValidationPort;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ProcurementServiceImpl implements ProcurementCommandApi {
    private static final int OPERATION_SUCCEEDED = 10;
    private static final int EVENT_SCHEMA_VERSION = 2;
    private static final int RELEASE_EVENT_SCHEMA_VERSION = 4;
    private static final String SOURCE_SYSTEM = "cloudmold-procurement";
    private static final String DEFAULT_TAX_POLICY = "STANDARD_V1";
    private static final String DEFAULT_ROUNDING_POLICY = "HALF_UP";
    private static final Pattern SAFE_REF = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:/-]{0,255}");
    private static final Pattern SAFE_CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");
    private static final BigDecimal BPS_DENOMINATOR = BigDecimal.valueOf(10_000);

    private final ProcurementMapper mapper;
    private final ProcurementSourcingMapper sourcingMapper;
    private final OutboxAppender outboxAppender;
    private final ProcurementActorPrincipalPort actorPrincipalPort;
    private final ProcurementReferenceValidationPort referenceValidationPort;
    private final P2pEvidenceIngestionApi p2pEvidenceIngestionApi;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProcurementResult execute(ProcurementCommand command, String actorPrincipalId) {
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
        require(operationId != null && operationId > 0, "failed to resolve procurement operation");
        Operation operation = mapper.selectOperationForUpdate(operationId, tenantId);
        require(operation != null, "procurement operation disappeared");
        if (!attemptToken.equals(operation.getAttemptToken())) {
            require(Objects.equals(requestHash, operation.getRequestHash()),
                    "idempotency key conflicts with different procurement payload");
            require(operation.getStatus() == OPERATION_SUCCEEDED && operation.getResultJson() != null,
                    "existing procurement operation is incomplete");
            ProcurementResult replay = JsonUtils.parseObject(operation.getResultJson(), ProcurementResult.class);
            replay.setDuplicate(true);
            return replay;
        }

        Outcome outcome = switch (command.getOperation()) {
            case CREATE_PURCHASE_ORDER -> createOrder(tenantId, operationId, command, actorPrincipalId, now);
            case SUBMIT_PURCHASE_ORDER -> submitOrder(tenantId, operationId, command, actorPrincipalId, now);
            case APPROVE_PURCHASE_ORDER -> approveOrder(tenantId, operationId, command, actorPrincipalId, now);
            case RELEASE_PURCHASE_ORDER -> releaseOrder(tenantId, operationId, command, actorPrincipalId, now);
            case DISPATCH_PURCHASE_ORDER -> dispatchOrder(tenantId, operationId, command, actorPrincipalId, now);
            case SUPPLIER_CONFIRM_PURCHASE_ORDER -> confirmOrder(tenantId, operationId, command, actorPrincipalId, now);
            case CANCEL_PURCHASE_ORDER -> cancelOrder(tenantId, operationId, command, actorPrincipalId, now);
            case CLOSE_PURCHASE_ORDER -> closeOrder(tenantId, operationId, command, actorPrincipalId, now);
        };
        appendEvent(tenantId, command, outcome);
        ProcurementResult result = toResult(operationId, outcome.snapshot());
        require(mapper.markOperationSucceeded(operationId, tenantId, outcome.aggregateType(),
                outcome.aggregateId(), JsonUtils.toJsonString(result), now) == 1,
                "procurement operation completion conflict");
        return result;
    }

    private Outcome createOrder(Long tenantId, Long operationId, ProcurementCommand command,
                                String actorPrincipalId, LocalDateTime now) {
        NormalizedCreateOrder normalized = normalizeCreateOrder(
                nonNull(command.getPurchaseOrder(), "purchaseOrder is required"));
        ProcurementCommand.PurchaseOrderDefinition input = command.getPurchaseOrder();
        requireRef(input.getAwardId(), "awardId", 128);
        require(input.getAwardVersion() != null && input.getAwardVersion() > 0,
                "awardVersion must identify the approved immutable award snapshot");
        require("SOURCING_AWARD".equals(normalized.sourceBusinessType())
                        && input.getAwardId().equals(normalized.sourceBusinessRef()),
                "award-backed purchase order source must be SOURCING_AWARD and reference the exact award");
        Award award = nonNull(sourcingMapper.selectApprovedAward(tenantId, input.getAwardId()), "approved award not found");
        require("APPROVED".equals(award.getStatus()) && Objects.equals(input.getAwardVersion(), award.getVersion()),
                "purchase order requires the exact approved award version");
        Map<String, AwardLine> awardLines = new LinkedHashMap<>();
        for (AwardLine line : sourcingMapper.selectAwardLines(tenantId, award.getAwardId())) awardLines.put(line.getAwardLineId(), line);
        require(!awardLines.isEmpty(), "approved award has no lines");
        Set<String> consumedAwardLines = new LinkedHashSet<>();
        for (NormalizedLine line : normalized.lines()) {
            require(consumedAwardLines.add(line.awardLineId()), "purchase order cannot consume an award line more than once");
            AwardLine source = nonNull(awardLines.get(line.awardLineId()), "purchase order line requires an award line from the approved award");
            require(line.schedules().size() == 1, "award-backed purchase order line must have exactly one frozen delivery schedule");
            NormalizedSchedule schedule = line.schedules().get(0);
            require(source.getSupplierId().equals(normalized.supplierId())
                    && source.getCanonicalSkuId().equals(line.canonicalSkuId())
                    && source.getAwardedQuantity().compareTo(line.orderedQuantity()) == 0
                    && source.getUomCode().equals(line.uomCode())
                    && source.getCurrencyCode().equals(normalized.currencyCode())
                    && source.getUnitNetPriceMinor().compareTo(line.unitNetPriceMinor()) == 0
                    && source.getTaxCode().equals(line.taxCode())
                    && source.getTaxRateBps().equals(line.taxRateBps())
                    && source.getLineNetAmountMinor().equals(line.lineNetAmountMinor())
                    && source.getLineTaxAmountMinor().equals(line.lineTaxAmountMinor())
                    && source.getLineGrossAmountMinor().equals(line.lineGrossAmountMinor())
                    && source.getCanonicalWarehouseId().equals(schedule.canonicalWarehouseId())
                    && source.getPromisedDeliveryDate().equals(schedule.requiredDeliveryDate())
                    && source.getAwardedQuantity().compareTo(schedule.scheduledQuantity()) == 0,
                    "purchase order line must exactly freeze its award line");
        }
        ProcurementOrder row = new ProcurementOrder()
                .setOrderId(normalized.orderId())
                .setTenantId(tenantId)
                .setOrderCode(normalized.orderCode())
                .setSourceBusinessType(normalized.sourceBusinessType())
                .setSourceBusinessRef(normalized.sourceBusinessRef())
                .setAwardId(award.getAwardId()).setAwardVersion(award.getVersion())
                .setLegalEntityId(normalized.legalEntityId())
                .setSupplierId(normalized.supplierId())
                .setCurrencyCode(normalized.currencyCode())
                .setLeadTimeDays(normalized.leadTimeDays())
                .setHeaderNetAmountMinor(normalized.headerNetAmountMinor())
                .setHeaderTaxAmountMinor(normalized.headerTaxAmountMinor())
                .setHeaderGrossAmountMinor(normalized.headerGrossAmountMinor())
                .setTaxCalculationPolicyCode(normalized.taxCalculationPolicyCode())
                .setRoundingPolicyCode(normalized.roundingPolicyCode())
                .setStatus("DRAFT")
                .setCreatedByPrincipalId(actorPrincipalId)
                .setReasonCode(normalized.reasonCode())
                .setRemark(normalized.remark())
                .setVersion(1L)
                .setCreatedAt(now)
                .setUpdatedAt(now);
        require(mapper.insertOrder(row) == 1, "failed to persist procurement order");

        List<PurchaseOrderItem> items = buildItemRows(tenantId, row.getOrderId(), now, normalized.lines());
        require(mapper.insertItems(items) == items.size(), "failed to persist procurement order items");
        List<PurchaseOrderAwardSource> awardSources = buildAwardSourceRows(tenantId, row, items, now);
        require(mapper.insertAwardSources(awardSources) == awardSources.size(),
                "failed to persist immutable purchase order award sources");
        List<PurchaseOrderDeliverySchedule> schedules = buildScheduleRows(tenantId, row.getOrderId(), now, normalized.lines());
        require(mapper.insertSchedules(schedules) == schedules.size(), "failed to persist procurement delivery schedules");
        insertStatusHistory(tenantId, operationId, row.getOrderId(), row.getVersion(), row.getStatus(),
                actorPrincipalId, row.getReasonCode(), now);
        return outcome("procurement.order.created", snapshot(row, items, schedules));
    }

    private Outcome submitOrder(Long tenantId, Long operationId, ProcurementCommand command, String actor, LocalDateTime now) {
        return transition(tenantId, operationId, command, actor, now, "DRAFT", "SUBMITTED",
                mapper::submitOrder, "procurement.order.submitted");
    }

    private Outcome approveOrder(Long tenantId, Long operationId, ProcurementCommand command, String actor, LocalDateTime now) {
        return transition(tenantId, operationId, command, actor, now, "SUBMITTED", "APPROVED",
                mapper::approveOrder, "procurement.order.approved");
    }

    private Outcome releaseOrder(Long tenantId, Long operationId, ProcurementCommand command, String actor, LocalDateTime now) {
        Outcome released = transition(tenantId, operationId, command, actor, now, "APPROVED", "RELEASED",
                mapper::releaseOrder, "procurement.order.released");
        validateReleasedValuationPolicySnapshot(released.snapshot().items());
        ingestReleasedOrderEvidence(command, actor, released.snapshot());
        return released;
    }

    private static void validateReleasedValuationPolicySnapshot(List<PurchaseOrderItem> items) {
        require(!items.isEmpty(), "released purchase order requires at least one immutable item");
        for (PurchaseOrderItem item : items) {
            requireRef(item.getValuationPolicyId(), "valuationPolicyId", 128);
            requireRef(item.getValuationPolicyVersion(), "valuationPolicyVersion", 64);
            requireSha256(item.getValuationPolicyHash(), "valuationPolicyHash");
        }
    }

    private void ingestReleasedOrderEvidence(ProcurementCommand command, String actor, OrderSnapshot snapshot) {
        ProcurementOrder order = snapshot.order();
        Map<String, List<PurchaseOrderDeliverySchedule>> schedulesByItem = new LinkedHashMap<>();
        for (PurchaseOrderDeliverySchedule schedule : snapshot.schedules()) {
            schedulesByItem.computeIfAbsent(schedule.getItemId(), ignored -> new ArrayList<>()).add(schedule);
        }
        for (PurchaseOrderItem item : snapshot.items()) {
            List<PurchaseOrderDeliverySchedule> schedules = schedulesByItem.getOrDefault(item.getItemId(), List.of());
            require(!schedules.isEmpty(), "released purchase order item requires delivery schedule evidence");
            long allocatedNet=0,allocatedTax=0,allocatedGross=0;
            for(int i=0;i<schedules.size();i++) {
                PurchaseOrderDeliverySchedule schedule=schedules.get(i); boolean last=i==schedules.size()-1;
                long net=last?item.getLineNetAmountMinor()-allocatedNet:allocate(item.getLineNetAmountMinor(),schedule.getScheduledQuantity(),item.getOrderedQuantity());
                long tax=last?item.getLineTaxAmountMinor()-allocatedTax:allocate(item.getLineTaxAmountMinor(),schedule.getScheduledQuantity(),item.getOrderedQuantity());
                long gross=last?item.getLineGrossAmountMinor()-allocatedGross:Math.addExact(net,tax);
                allocatedNet+=net;allocatedTax+=tax;allocatedGross+=gross;
                String sourceEventId="po-release:"+DigestUtil.sha256Hex(order.getOrderId()+"\n"+order.getVersion()+"\n"+item.getItemId()+"\n"+schedule.getScheduleId());
                Map<String,Object> evidence=payload("source_event_id",sourceEventId,"source_version",order.getVersion(),"purchase_order_id",order.getOrderId(),
                        "purchase_order_item_id",item.getItemId(),"delivery_schedule_id",schedule.getScheduleId(),"legal_entity_id",order.getLegalEntityId(),
                        "supplier_id",order.getSupplierId(),"currency_code",order.getCurrencyCode(),"ordered_quantity",schedule.getScheduledQuantity().toPlainString(),
                        "unit_of_measure",item.getUomCode(),"unit_net_price",item.getUnitNetPriceMinor().toPlainString(),
                        "valuation_policy_id",item.getValuationPolicyId(),"valuation_policy_version",item.getValuationPolicyVersion(),
                        "valuation_policy_hash",item.getValuationPolicyHash(),"net_amount_minor",net,"tax_amount_minor",tax,"gross_amount_minor",gross);
                p2pEvidenceIngestionApi.ingestPurchaseOrderLine(P2pEvidenceCommands.PurchaseOrderLine.builder()
                        .envelope(FinanceCommandEnvelope.builder().correlationId(command.getCorrelationId()).causationId(command.getCausationId())
                                .runId(command.getRunId()).idempotencyKey("p2p-po:"+DigestUtil.sha256Hex(command.getIdempotencyKey()+"\n"+item.getItemId()+"\n"+schedule.getScheduleId())).occurredAt(command.getOccurredAt()).build())
                        .sourceEventId(sourceEventId).sourceVersion(order.getVersion()).evidenceSha256(DigestUtil.sha256Hex(JsonUtils.toJsonString(evidence)))
                        .sourceOccurredAt(command.getOccurredAt()).purchaseOrderId(order.getOrderId()).purchaseOrderItemId(item.getItemId()).deliveryScheduleId(schedule.getScheduleId())
                        .legalEntityId(order.getLegalEntityId()).supplierId(order.getSupplierId()).currencyCode(order.getCurrencyCode()).orderedQuantity(schedule.getScheduledQuantity())
                        .unitOfMeasure(item.getUomCode()).unitNetPrice(item.getUnitNetPriceMinor()).netAmountMinor(net).taxAmountMinor(tax).grossAmountMinor(gross).build(),actor);
            }
            require(allocatedNet==item.getLineNetAmountMinor()&&allocatedTax==item.getLineTaxAmountMinor()&&allocatedGross==item.getLineGrossAmountMinor(),
                    "finance evidence allocation must exactly preserve line totals");
        }
    }

    private Outcome transition(Long tenantId, Long operationId, ProcurementCommand command, String actor,
                               LocalDateTime now, String from, String to, OrderTransition updater, String eventType) {
        ProcurementCommand.PurchaseOrderDefinition input = nonNull(command.getPurchaseOrder(), "purchaseOrder is required");
        ProcurementOrder row = nonNull(mapper.selectOrderForUpdate(tenantId, input.getOrderId()), "procurement order not found");
        requireExpectedVersion(input.getExpectedVersion(), row.getVersion()); require(from.equals(row.getStatus()), "purchase order must be " + from);
        String reason = normalizeReasonCode(input.getReasonCode()); require(updater.update(tenantId,row.getOrderId(),row.getVersion(),actor,reason,now)==1,"purchase order transition conflict");
        row.setStatus(to).setVersion(row.getVersion()+1).setReasonCode(reason).setUpdatedAt(now);
        insertStatusHistory(tenantId,operationId,row.getOrderId(),row.getVersion(),to,actor,reason,now);
        return outcome(eventType,loadSnapshot(tenantId,row.getOrderId()));
    }

    private Outcome dispatchOrder(Long tenantId, Long operationId, ProcurementCommand command,
                                  String actorPrincipalId, LocalDateTime now) {
        ProcurementCommand.PurchaseOrderDefinition input = nonNull(command.getPurchaseOrder(), "purchaseOrder is required");
        requireRef(input.getOrderId(), "orderId", 128);
        ProcurementOrder row = nonNull(mapper.selectOrderForUpdate(tenantId, input.getOrderId()), "procurement order not found");
        requireExpectedVersion(input.getExpectedVersion(), row.getVersion());
        require("RELEASED".equals(row.getStatus()) && row.getAwardId() != null,
                "only an award-backed released purchase order can be dispatched");
        String reasonCode = normalizeReasonCode(input.getReasonCode());
        require(mapper.dispatchOrder(tenantId, row.getOrderId(), row.getVersion(), actorPrincipalId, reasonCode, now) == 1,
                "procurement order dispatch conflict");
        row.setStatus("DISPATCHED").setVersion(row.getVersion() + 1).setDispatchedByPrincipalId(actorPrincipalId)
                .setDispatchedAt(now).setReasonCode(reasonCode).setUpdatedAt(now);
        insertStatusHistory(tenantId, operationId, row.getOrderId(), row.getVersion(), row.getStatus(),
                actorPrincipalId, row.getReasonCode(), now);
        return outcome("procurement.order.dispatched", loadSnapshot(tenantId, row.getOrderId()));
    }

    private Outcome confirmOrder(Long tenantId, Long operationId, ProcurementCommand command,
                                 String actorPrincipalId, LocalDateTime now) {
        ProcurementCommand.PurchaseOrderDefinition input = nonNull(command.getPurchaseOrder(), "purchaseOrder is required");
        requireRef(input.getOrderId(), "orderId", 128);
        ProcurementOrder row = nonNull(mapper.selectOrderForUpdate(tenantId, input.getOrderId()), "procurement order not found");
        requireExpectedVersion(input.getExpectedVersion(), row.getVersion());
        require("DISPATCHED".equals(row.getStatus()), "only a dispatched procurement order can be supplier-confirmed");
        String reasonCode = normalizeReasonCode(input.getReasonCode());
        require(mapper.confirmSupplier(tenantId, row.getOrderId(), row.getVersion(), actorPrincipalId, reasonCode, now) == 1,
                "procurement order supplier confirmation conflict");
        row.setStatus("SUPPLIER_CONFIRMED").setVersion(row.getVersion() + 1)
                .setSupplierConfirmedByPrincipalId(actorPrincipalId).setSupplierConfirmedAt(now)
                .setReasonCode(reasonCode).setUpdatedAt(now);
        insertStatusHistory(tenantId, operationId, row.getOrderId(), row.getVersion(), row.getStatus(),
                actorPrincipalId, row.getReasonCode(), now);
        return outcome("procurement.order.supplier_confirmed", loadSnapshot(tenantId, row.getOrderId()));
    }

    private Outcome cancelOrder(Long tenantId, Long operationId, ProcurementCommand command,
                                String actorPrincipalId, LocalDateTime now) {
        ProcurementCommand.PurchaseOrderDefinition input = nonNull(command.getPurchaseOrder(), "purchaseOrder is required");
        requireRef(input.getOrderId(), "orderId", 128);
        ProcurementOrder row = nonNull(mapper.selectOrderForUpdate(tenantId, input.getOrderId()), "procurement order not found");
        requireExpectedVersion(input.getExpectedVersion(), row.getVersion());
        require(!"CANCELLED".equals(row.getStatus()) && !"CLOSED".equals(row.getStatus()),
                "completed procurement order cannot be cancelled");
        String reasonCode = normalizeReasonCode(input.getReasonCode());
        require(mapper.cancelOrder(tenantId, row.getOrderId(), row.getVersion(), actorPrincipalId, reasonCode, now) == 1,
                "procurement order cancellation conflict");
        row.setStatus("CANCELLED").setVersion(row.getVersion() + 1).setCancelledByPrincipalId(actorPrincipalId)
                .setCancelledAt(now).setReasonCode(reasonCode).setUpdatedAt(now);
        insertStatusHistory(tenantId, operationId, row.getOrderId(), row.getVersion(), row.getStatus(),
                actorPrincipalId, row.getReasonCode(), now);
        return outcome("procurement.order.cancelled", loadSnapshot(tenantId, row.getOrderId()));
    }

    private Outcome closeOrder(Long tenantId, Long operationId, ProcurementCommand command,
                               String actorPrincipalId, LocalDateTime now) {
        ProcurementCommand.PurchaseOrderDefinition input = nonNull(command.getPurchaseOrder(), "purchaseOrder is required");
        requireRef(input.getOrderId(), "orderId", 128);
        ProcurementOrder row = nonNull(mapper.selectOrderForUpdate(tenantId, input.getOrderId()), "procurement order not found");
        requireExpectedVersion(input.getExpectedVersion(), row.getVersion());
        require("SUPPLIER_CONFIRMED".equals(row.getStatus()), "only a supplier-confirmed procurement order can be closed");
        String reasonCode = normalizeReasonCode(input.getReasonCode());
        require(mapper.closeOrder(tenantId, row.getOrderId(), row.getVersion(), actorPrincipalId, reasonCode, now) == 1,
                "procurement order close conflict");
        row.setStatus("CLOSED").setVersion(row.getVersion() + 1).setClosedByPrincipalId(actorPrincipalId)
                .setClosedAt(now).setReasonCode(reasonCode).setUpdatedAt(now);
        insertStatusHistory(tenantId, operationId, row.getOrderId(), row.getVersion(), row.getStatus(),
                actorPrincipalId, row.getReasonCode(), now);
        return outcome("procurement.order.closed", loadSnapshot(tenantId, row.getOrderId()));
    }

    private ProcurementResult toResult(Long operationId, OrderSnapshot snapshot) {
        return ProcurementResult.builder()
                .operationId(operationId)
                .duplicate(false)
                .aggregateType("procurement_order")
                .aggregateId(snapshot.order().getOrderId())
                .aggregateVersion(snapshot.order().getVersion())
                .status(snapshot.order().getStatus())
                .orderCode(snapshot.order().getOrderCode())
                .sourceBusinessRef(snapshot.order().getSourceBusinessRef())
                .build();
    }

    private void appendEvent(Long tenantId, ProcurementCommand command, Outcome outcome) {
        outboxAppender.append(AppendDomainEventCommand.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(outcome.eventType())
                .schemaVersion("procurement.order.released".equals(outcome.eventType())
                        ? RELEASE_EVENT_SCHEMA_VERSION : EVENT_SCHEMA_VERSION)
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
                .headers(Map.of("run_id", command.getRunId(), "status", outcome.status()))
                .destination("lakehouse")
                .build());
    }

    private Outcome outcome(String eventType, OrderSnapshot snapshot) {
        return new Outcome(eventType, "procurement_order", snapshot.order().getOrderId(), snapshot.order().getVersion(),
                snapshot.order().getStatus(), payload(snapshot), snapshot);
    }

    private OrderSnapshot loadSnapshot(Long tenantId, String orderId) {
        ProcurementOrder order = nonNull(mapper.selectCurrentHeader(tenantId, orderId), "procurement order not found");
        return new OrderSnapshot(order, mapper.selectItems(tenantId, orderId), mapper.selectSchedules(tenantId, orderId));
    }

    private OrderSnapshot snapshot(ProcurementOrder order, List<PurchaseOrderItem> items,
                                   List<PurchaseOrderDeliverySchedule> schedules) {
        return new OrderSnapshot(order, items, schedules);
    }

    private Map<String, Object> payload(OrderSnapshot snapshot) {
        ProcurementOrder row = snapshot.order();
        Map<String, Object> payload = payload(
                "order_id", row.getOrderId(),
                "order_code", row.getOrderCode(),
                "source_business_type", row.getSourceBusinessType(),
                "source_business_ref", row.getSourceBusinessRef(),
                "supplier_id", row.getSupplierId(),
                "currency_code", row.getCurrencyCode(),
                "lead_time_days", row.getLeadTimeDays(),
                "header_net_amount_minor", row.getHeaderNetAmountMinor(),
                "header_tax_amount_minor", row.getHeaderTaxAmountMinor(),
                "header_gross_amount_minor", row.getHeaderGrossAmountMinor(),
                "tax_calculation_policy_code", row.getTaxCalculationPolicyCode(),
                "rounding_policy_code", row.getRoundingPolicyCode(),
                "current_status", row.getStatus());
        List<Map<String, Object>> items = new ArrayList<>();
        for (PurchaseOrderItem item : snapshot.items()) {
            items.add(payload(
                    "item_id", item.getItemId(),
                    "line_number", item.getLineNumber(),
                    "canonical_sku_id", item.getCanonicalSkuId(),
                    "ordered_quantity", item.getOrderedQuantity(),
                    "uom_code", item.getUomCode(),
                    "tax_code", item.getTaxCode(),
                    "tax_rate_bps", item.getTaxRateBps(),
                    "unit_net_price_minor", item.getUnitNetPriceMinor(),
                    "valuation_policy_id", item.getValuationPolicyId(),
                    "valuation_policy_version", item.getValuationPolicyVersion(),
                    "valuation_policy_hash", item.getValuationPolicyHash(),
                    "line_net_amount_minor", item.getLineNetAmountMinor(),
                    "line_tax_amount_minor", item.getLineTaxAmountMinor(),
                    "line_gross_amount_minor", item.getLineGrossAmountMinor(),
                    "schedules", schedulePayloads(snapshot.schedules(), item.getItemId())));
        }
        payload.put("items", items);
        return payload;
    }

    private List<Map<String, Object>> schedulePayloads(List<PurchaseOrderDeliverySchedule> schedules, String itemId) {
        List<Map<String, Object>> values = new ArrayList<>();
        for (PurchaseOrderDeliverySchedule schedule : schedules) {
            if (!Objects.equals(itemId, schedule.getItemId())) {
                continue;
            }
            values.add(payload(
                    "schedule_id", schedule.getScheduleId(),
                    "schedule_number", schedule.getScheduleNumber(),
                    "required_delivery_date", schedule.getRequiredDeliveryDate().toString(),
                    "canonical_warehouse_id", schedule.getCanonicalWarehouseId(),
                    "scheduled_quantity", schedule.getScheduledQuantity()));
        }
        return values;
    }

    private List<PurchaseOrderItem> buildItemRows(Long tenantId, String orderId, LocalDateTime now,
                                                  List<NormalizedLine> lines) {
        List<PurchaseOrderItem> rows = new ArrayList<>(lines.size());
        for (NormalizedLine line : lines) {
            rows.add(new PurchaseOrderItem()
                    .setItemId(line.itemId())
                    .setTenantId(tenantId)
                    .setOrderId(orderId)
                    .setLineNumber(line.lineNumber())
                    .setAwardLineId(line.awardLineId())
                    .setCanonicalSkuId(line.canonicalSkuId())
                    .setOrderedQuantity(line.orderedQuantity())
                    .setUomCode(line.uomCode())
                    .setTaxCode(line.taxCode())
                    .setTaxRateBps(line.taxRateBps())
                    .setUnitNetPriceMinor(line.unitNetPriceMinor())
                    .setValuationPolicyId(line.valuationPolicyId())
                    .setValuationPolicyVersion(line.valuationPolicyVersion())
                    .setValuationPolicyHash(line.valuationPolicyHash())
                    .setLineNetAmountMinor(line.lineNetAmountMinor())
                    .setLineTaxAmountMinor(line.lineTaxAmountMinor())
                    .setLineGrossAmountMinor(line.lineGrossAmountMinor())
                    .setCreatedAt(now)
                    .setUpdatedAt(now));
        }
        return rows;
    }

    private List<PurchaseOrderDeliverySchedule> buildScheduleRows(Long tenantId, String orderId, LocalDateTime now,
                                                                  List<NormalizedLine> lines) {
        List<PurchaseOrderDeliverySchedule> rows = new ArrayList<>();
        for (NormalizedLine line : lines) {
            for (NormalizedSchedule schedule : line.schedules()) {
                rows.add(new PurchaseOrderDeliverySchedule()
                        .setScheduleId(schedule.scheduleId())
                        .setTenantId(tenantId)
                        .setOrderId(orderId)
                        .setItemId(line.itemId())
                        .setScheduleNumber(schedule.scheduleNumber())
                        .setRequiredDeliveryDate(schedule.requiredDeliveryDate())
                        .setCanonicalWarehouseId(schedule.canonicalWarehouseId())
                        .setScheduledQuantity(schedule.scheduledQuantity())
                        .setCreatedAt(now)
                        .setUpdatedAt(now));
            }
        }
        return rows;
    }

    private List<PurchaseOrderAwardSource> buildAwardSourceRows(Long tenantId, ProcurementOrder order,
                                                                 List<PurchaseOrderItem> items, LocalDateTime now) {
        String snapshotId = order.getAwardId() + ":v" + order.getAwardVersion();
        List<PurchaseOrderAwardSource> rows = new ArrayList<>(items.size());
        for (PurchaseOrderItem item : items) {
            rows.add(new PurchaseOrderAwardSource()
                    .setTenantId(tenantId)
                    .setOrderId(order.getOrderId())
                    .setAwardId(order.getAwardId())
                    .setAwardVersion(order.getAwardVersion())
                    .setAwardLineId(item.getAwardLineId())
                    .setItemId(item.getItemId())
                    .setSourceSnapshotId(snapshotId)
                    .setCreatedAt(now));
        }
        return rows;
    }

    private void insertStatusHistory(Long tenantId, Long operationId, String orderId, Long version,
                                     String status, String actorPrincipalId, String reasonCode, LocalDateTime now) {
        require(mapper.insertStatusHistory(new OrderStatusHistory()
                .setTenantId(tenantId)
                .setOrderId(orderId)
                .setOperationId(operationId)
                .setAggregateVersion(version)
                .setStatus(status)
                .setActorPrincipalId(actorPrincipalId)
                .setReasonCode(reasonCode)
                .setOccurredAt(now)
                .setCreatedAt(now)) == 1, "failed to persist procurement status history");
    }

    private NormalizedCreateOrder normalizeCreateOrder(ProcurementCommand.PurchaseOrderDefinition input) {
        String orderId = valueOrUuid(input.getOrderId(), "orderId");
        requireRef(input.getOrderCode(), "orderCode", 64);
        requireCode(input.getSourceBusinessType(), "sourceBusinessType");
        requireRef(input.getSourceBusinessRef(), "sourceBusinessRef", 128);
        requireRef(input.getLegalEntityId(), "legalEntityId", 128);
        requireRef(input.getSupplierId(), "supplierId", 128);
        String supplierId = input.getSupplierId();
        requireCurrency(input.getCurrencyCode());
        require(input.getLeadTimeDays() != null && input.getLeadTimeDays() >= 0, "leadTimeDays must not be negative");
        String taxPolicy = normalizeOrDefaultCode(input.getTaxCalculationPolicyCode(), DEFAULT_TAX_POLICY);
        String roundingPolicy = normalizeOrDefaultCode(input.getRoundingPolicyCode(), DEFAULT_ROUNDING_POLICY);
        require(DEFAULT_TAX_POLICY.equals(taxPolicy), "unsupported taxCalculationPolicyCode");
        require(DEFAULT_ROUNDING_POLICY.equals(roundingPolicy), "unsupported roundingPolicyCode");
        List<NormalizedLine> lines = normalizeLines(input);
        validateReferences(supplierId, lines);
        long headerNet = total(lines, line -> line.lineNetAmountMinor());
        long headerTax = total(lines, line -> line.lineTaxAmountMinor());
        long headerGross = total(lines, line -> line.lineGrossAmountMinor());
        long requestedHeaderNet = coalesce(input.getHeaderNetAmountMinor(), headerNet);
        long requestedHeaderTax = coalesce(input.getHeaderTaxAmountMinor(), headerTax);
        long requestedHeaderGross = coalesce(input.getHeaderGrossAmountMinor(), headerGross);
        require(addExact(requestedHeaderNet, requestedHeaderTax) == requestedHeaderGross,
                "purchase order header gross amount must equal net plus tax");
        require(requestedHeaderNet == headerNet && requestedHeaderTax == headerTax && requestedHeaderGross == headerGross,
                "purchase order header totals must equal the sum of line totals");
        return new NormalizedCreateOrder(orderId, input.getOrderCode(), upper(input.getSourceBusinessType()),
                input.getSourceBusinessRef(), input.getLegalEntityId(), supplierId, upper(input.getCurrencyCode()), input.getLeadTimeDays(),
                requestedHeaderNet, requestedHeaderTax, requestedHeaderGross, taxPolicy, roundingPolicy,
                input.getRemark(), normalizeReasonCode(input.getReasonCode()), lines);
    }

    private List<NormalizedLine> normalizeLines(ProcurementCommand.PurchaseOrderDefinition input) {
        List<ProcurementCommand.PurchaseOrderLineDefinition> sourceLines = input.getLines();
        require(!sourceLines.isEmpty(), "purchase order must contain at least one line");
        Set<Integer> lineNumbers = new LinkedHashSet<>();
        Set<String> itemIds = new LinkedHashSet<>();
        List<NormalizedLine> lines = new ArrayList<>(sourceLines.size());
        int generatedLineNumber = 10;
        for (ProcurementCommand.PurchaseOrderLineDefinition line : sourceLines) {
            int lineNumber = line.getLineNumber() == null ? generatedLineNumber : line.getLineNumber();
            generatedLineNumber += 10;
            require(lineNumber > 0, "purchase order lineNumber must be positive");
            require(lineNumbers.add(lineNumber), "purchase order lineNumber must be unique");
            String itemId = valueOrUuid(line.getItemId(), "itemId");
            requireRef(line.getAwardLineId(), "awardLineId", 128);
            require(itemIds.add(itemId), "purchase order itemId must be unique");
            requireRef(line.getCanonicalSkuId(), "canonicalSkuId", 128);
            requirePositive(line.getOrderedQuantity(), "orderedQuantity");
            requireCode(line.getUomCode(), "uomCode");
            requireCode(line.getTaxCode(), "taxCode");
            String taxCode = upper(line.getTaxCode());
            int taxRateBps = line.getTaxRateBps() == null ? 0 : line.getTaxRateBps();
            require(taxRateBps >= 0 && taxRateBps <= 10000, "taxRateBps must be between 0 and 10000");
            BigDecimal unitNetPriceMinor = nonNegativePrice(line.getUnitNetPriceMinor(), "unitNetPriceMinor");
            requireRef(line.getValuationPolicyId(), "valuationPolicyId", 128);
            requireRef(line.getValuationPolicyVersion(), "valuationPolicyVersion", 64);
            requireSha256(line.getValuationPolicyHash(), "valuationPolicyHash");
            long lineNetAmountMinor = nonNegativeAmount(line.getLineNetAmountMinor(), "lineNetAmountMinor");
            long lineTaxAmountMinor = nonNegativeAmount(line.getLineTaxAmountMinor(), "lineTaxAmountMinor");
            long lineGrossAmountMinor = nonNegativeAmount(line.getLineGrossAmountMinor(), "lineGrossAmountMinor");
            long expectedLineNetAmountMinor = calculateLineNetAmountMinor(line.getOrderedQuantity(), unitNetPriceMinor);
            require(expectedLineNetAmountMinor == lineNetAmountMinor,
                    "purchase order lineNetAmountMinor must equal orderedQuantity * unitNetPriceMinor using HALF_UP");
            long expectedLineTaxAmountMinor = calculateTaxAmountMinor(lineNetAmountMinor, taxRateBps);
            require(expectedLineTaxAmountMinor == lineTaxAmountMinor,
                    "purchase order lineTaxAmountMinor must equal HALF_UP(lineNetAmountMinor * taxRateBps / 10000)");
            require(addExact(lineNetAmountMinor, lineTaxAmountMinor) == lineGrossAmountMinor,
                    "purchase order line gross amount must equal net plus tax");
            List<NormalizedSchedule> schedules = normalizeSchedules(line);
            require(sumQuantities(schedules).compareTo(line.getOrderedQuantity()) == 0,
                    "purchase order schedule quantities must equal the line ordered quantity");
            lines.add(new NormalizedLine(itemId, lineNumber, line.getAwardLineId(), line.getCanonicalSkuId(), line.getOrderedQuantity(),
                    upper(line.getUomCode()), taxCode, taxRateBps, unitNetPriceMinor,
                    line.getValuationPolicyId(), line.getValuationPolicyVersion(), line.getValuationPolicyHash(), lineNetAmountMinor,
                    lineTaxAmountMinor, lineGrossAmountMinor, schedules));
        }
        return lines;
    }

    private List<NormalizedSchedule> normalizeSchedules(ProcurementCommand.PurchaseOrderLineDefinition line) {
        List<ProcurementCommand.PurchaseOrderDeliveryScheduleDefinition> sourceSchedules = line.getSchedules();
        require(sourceSchedules != null && !sourceSchedules.isEmpty(),
                "purchase order line schedules must contain at least one row");
        Set<Integer> scheduleNumbers = new LinkedHashSet<>();
        Set<String> scheduleIds = new LinkedHashSet<>();
        List<NormalizedSchedule> schedules = new ArrayList<>(sourceSchedules.size());
        int generatedScheduleNumber = 1;
        for (ProcurementCommand.PurchaseOrderDeliveryScheduleDefinition schedule : sourceSchedules) {
            int scheduleNumber = schedule.getScheduleNumber() == null ? generatedScheduleNumber : schedule.getScheduleNumber();
            generatedScheduleNumber += 1;
            require(scheduleNumber > 0, "purchase order scheduleNumber must be positive");
            require(scheduleNumbers.add(scheduleNumber), "purchase order scheduleNumber must be unique within a line");
            String scheduleId = valueOrUuid(schedule.getScheduleId(), "scheduleId");
            require(scheduleIds.add(scheduleId), "purchase order scheduleId must be unique within a line");
            require(schedule.getRequiredDeliveryDate() != null, "purchase order schedule requiredDeliveryDate is required");
            requireRef(schedule.getCanonicalWarehouseId(), "canonicalWarehouseId", 128);
            requirePositive(schedule.getScheduledQuantity(), "scheduledQuantity");
            schedules.add(new NormalizedSchedule(scheduleId, scheduleNumber, schedule.getRequiredDeliveryDate(),
                    schedule.getCanonicalWarehouseId(), schedule.getScheduledQuantity()));
        }
        return schedules;
    }

    private void validateReferences(String supplierId, List<NormalizedLine> lines) {
        referenceValidationPort.requireActiveSupplier(supplierId);
        for (NormalizedLine line : lines) {
            referenceValidationPort.requireActiveSku(line.canonicalSkuId());
            for (NormalizedSchedule schedule : line.schedules()) {
                referenceValidationPort.requireActiveWarehouse(schedule.canonicalWarehouseId());
            }
        }
    }

    private static void validateEnvelope(ProcurementCommand command) {
        require(command != null, "procurement command is required");
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

    private static String valueOrUuid(String value, String field) {
        if (value == null) {
            return UUID.randomUUID().toString();
        }
        requireRef(value, field, 128);
        return value;
    }

    private static String normalizeReasonCode(String value) {
        return value == null ? null : upper(value);
    }

    private static String normalizeOrDefaultCode(String value, String defaultValue) {
        String normalized = value == null ? defaultValue : upper(value);
        requireCode(normalized, "code");
        return normalized;
    }

    private static void requireExpectedVersion(Long expected, Long actual) {
        require(expected != null && expected.equals(actual), "aggregate version conflict");
    }

    private static void requireCurrency(String value) {
        require(value != null && upper(value).matches("[A-Z]{3}"), "currencyCode must be ISO-4217");
    }

    private static void requireUuid(String value, String field) {
        try {
            UUID.fromString(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(field + " must be a UUID", exception);
        }
    }

    private static void requireRef(String value, String field, int maxLength) {
        require(value != null && value.length() <= maxLength && SAFE_REF.matcher(value).matches(),
                field + " must be a safe opaque reference");
    }

    private static void requireSha256(String value, String field) {
        require(value != null && value.matches("[0-9a-f]{64}"),
                field + " must be a lowercase SHA-256");
    }

    private static void requireCode(String value, String field) {
        String normalized = upper(value);
        require(normalized != null && SAFE_CODE.matcher(normalized).matches(),
                field + " must be an uppercase code");
    }

    private static void requirePositive(BigDecimal value, String field) {
        require(value != null && value.compareTo(BigDecimal.ZERO) > 0, field + " must be positive");
    }

    private static BigDecimal nonNegativePrice(BigDecimal value, String field) {
        require(value != null && value.compareTo(BigDecimal.ZERO) >= 0, field + " must not be negative");
        require(value.scale() <= 6, field + " must have scale <= 6");
        return value.stripTrailingZeros().scale() < 0 ? value.setScale(0) : value;
    }

    private static String upper(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private static long nonNegativeAmount(Long value, String field) {
        require(value != null && value >= 0, field + " must not be negative");
        return value;
    }

    private static long coalesce(Long value, long fallback) {
        return value == null ? fallback : value;
    }

    private static long calculateLineNetAmountMinor(BigDecimal orderedQuantity, BigDecimal unitNetPriceMinor) {
        return orderedQuantity.multiply(unitNetPriceMinor)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    private static long calculateTaxAmountMinor(long lineNetAmountMinor, int taxRateBps) {
        return BigDecimal.valueOf(lineNetAmountMinor)
                .multiply(BigDecimal.valueOf(taxRateBps))
                .divide(BPS_DENOMINATOR, 0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    private static long allocate(long amount, BigDecimal part, BigDecimal total) {
        return BigDecimal.valueOf(amount).multiply(part).divide(total, 0, RoundingMode.HALF_UP).longValueExact();
    }

    private static long addExact(long left, long right) {
        return Math.addExact(left, right);
    }

    private static BigDecimal sumQuantities(List<NormalizedSchedule> schedules) {
        BigDecimal total = BigDecimal.ZERO;
        for (NormalizedSchedule schedule : schedules) {
            total = total.add(schedule.scheduledQuantity());
        }
        return total;
    }

    private static long total(List<NormalizedLine> lines, LongExtractor extractor) {
        long total = 0L;
        for (NormalizedLine line : lines) {
            total = addExact(total, extractor.extract(line));
        }
        return total;
    }

    private static <T> T nonNull(T value, String message) {
        require(value != null, message);
        return value;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    private static Map<String, Object> payload(Object... values) {
        Map<String, Object> payload = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            payload.put((String) values[i], values[i + 1]);
        }
        return payload;
    }

    @FunctionalInterface
    private interface LongExtractor {
        long extract(NormalizedLine line);
    }

    private record NormalizedCreateOrder(String orderId, String orderCode, String sourceBusinessType,
                                         String sourceBusinessRef, String legalEntityId, String supplierId, String currencyCode,
                                         Integer leadTimeDays, long headerNetAmountMinor,
                                         long headerTaxAmountMinor, long headerGrossAmountMinor,
                                         String taxCalculationPolicyCode, String roundingPolicyCode,
                                         String remark, String reasonCode, List<NormalizedLine> lines) {
    }

    private record NormalizedLine(String itemId, Integer lineNumber, String awardLineId, String canonicalSkuId,
                                  BigDecimal orderedQuantity, String uomCode, String taxCode, Integer taxRateBps,
                                  BigDecimal unitNetPriceMinor, String valuationPolicyId,
                                  String valuationPolicyVersion, String valuationPolicyHash,
                                  Long lineNetAmountMinor, Long lineTaxAmountMinor,
                                  Long lineGrossAmountMinor, List<NormalizedSchedule> schedules) {
    }

    private record NormalizedSchedule(String scheduleId, Integer scheduleNumber, LocalDate requiredDeliveryDate,
                                      String canonicalWarehouseId, BigDecimal scheduledQuantity) {
    }

    private record OrderSnapshot(ProcurementOrder order, List<PurchaseOrderItem> items,
                                 List<PurchaseOrderDeliverySchedule> schedules) {
    }

    private record Outcome(String eventType, String aggregateType, String aggregateId,
                           Long version, String status, Map<String, Object> payload,
                           OrderSnapshot snapshot) {
    }

    @FunctionalInterface
    private interface OrderTransition {
        int update(Long tenantId,String orderId,Long version,String actor,String reason,LocalDateTime now);
    }
}
