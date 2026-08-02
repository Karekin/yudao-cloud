package cn.iocoder.yudao.module.cloudmold.procurement.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.procurement.api.AwardReleaseCommand;
import cn.iocoder.yudao.module.cloudmold.procurement.api.AwardReleaseCommandApi;
import cn.iocoder.yudao.module.cloudmold.procurement.api.AwardReleaseResult;
import cn.iocoder.yudao.module.cloudmold.procurement.api.ProcurementOrderView;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementRecords.Operation;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementRecords.OrderStatusHistory;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementRecords.ProcurementOrder;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementRecords.PurchaseOrderAwardSource;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementRecords.PurchaseOrderDeliverySchedule;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementRecords.PurchaseOrderItem;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementSourcingRecords.Award;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementSourcingRecords.AwardSnapshot;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementSourcingRecords.AwardSnapshotLine;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.mysql.ProcurementMapper;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.mysql.ProcurementSourcingMapper;
import cn.iocoder.yudao.module.cloudmold.procurement.service.actor.ProcurementActorPrincipalPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
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
public class AwardReleaseService implements AwardReleaseCommandApi {
    private static final int OPERATION_SUCCEEDED = 10;
    private static final String COMMAND_TYPE = "RELEASE_APPROVED_AWARD_TO_PURCHASE_ORDERS";
    private static final String SOURCE_SYSTEM = "cloudmold-procurement";
    private static final String SOURCE_BUSINESS_TYPE = "SOURCING_AWARD";
    private static final String ORDER_REASON_CODE = "AWARD_RELEASED";
    private static final String SUPPORTED_TAX_POLICY = "STANDARD_V1";
    private static final String SUPPORTED_ROUNDING_POLICY = "HALF_UP";
    private static final Pattern SAFE_REF = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:/-]{0,255}");
    private static final Pattern SAFE_CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    private final ProcurementMapper procurementMapper;
    private final ProcurementSourcingMapper sourcingMapper;
    private final OutboxAppender outboxAppender;
    private final ProcurementActorPrincipalPort actorPrincipalPort;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AwardReleaseResult releaseApprovedAward(AwardReleaseCommand command, String actorPrincipalId) {
        validate(command, actorPrincipalId);
        actorPrincipalPort.requireActive(actorPrincipalId);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDateTime now = LocalDateTime.ofInstant(command.getOccurredAt(), ZoneOffset.UTC);
        String requestHash = DigestUtil.sha256Hex(actorPrincipalId + "\n" + JsonUtils.toJsonString(command));
        String attemptToken = UUID.randomUUID().toString();
        procurementMapper.insertOrResolveOperation(tenantId, command.getIdempotencyKey(), COMMAND_TYPE,
                requestHash, attemptToken, now);
        Long operationId = procurementMapper.selectLastInsertId();
        require(operationId != null && operationId > 0, "failed to resolve award release operation");
        Operation operation = procurementMapper.selectOperationForUpdate(operationId, tenantId);
        require(operation != null, "award release operation disappeared");
        if (!attemptToken.equals(operation.getAttemptToken())) {
            require(Objects.equals(requestHash, operation.getRequestHash()),
                    "idempotency key conflicts with a different award release payload");
            require(operation.getStatus() == OPERATION_SUCCEEDED && operation.getResultJson() != null,
                    "existing award release operation is incomplete");
            AwardReleaseResult replay = JsonUtils.parseObject(operation.getResultJson(), AwardReleaseResult.class);
            replay.setDuplicate(true);
            return replay;
        }

        Award award = nonNull(sourcingMapper.selectAwardForUpdate(tenantId, command.getAwardId()), "award not found");
        require("APPROVED".equals(award.getStatus()), "award must be APPROVED");
        require(Objects.equals(command.getExpectedAwardVersion(), award.getVersion()), "award version conflict");
        AwardSnapshot snapshot = nonNull(sourcingMapper.selectAwardSnapshotForUpdate(
                tenantId, award.getAwardId(), award.getVersion()), "approved award snapshot not found");
        require("APPROVED".equals(snapshot.getStatus()), "award snapshot must be APPROVED");
        require(procurementMapper.countAwardLineConsumptions(tenantId, award.getAwardId(), award.getVersion()) == 0,
                "approved award snapshot has already been released");
        List<AwardSnapshotLine> snapshotLines = sourcingMapper.selectAwardSnapshotLines(tenantId, snapshot.getSnapshotId());
        require(!snapshotLines.isEmpty(), "approved award snapshot has no lines");

        SnapshotHeader normalizedHeader = normalizeHeader(snapshot);
        List<SnapshotLine> normalizedLines = normalizeLines(snapshotLines, snapshot);
        Map<GroupKey, List<SnapshotLine>> grouped = groupLines(normalizedLines);
        List<ProcurementOrderView> purchaseOrders = new ArrayList<>(grouped.size());
        int orderIndex = 1;
        for (Map.Entry<GroupKey, List<SnapshotLine>> entry : grouped.entrySet()) {
            OrderAggregate aggregate = createDraftOrder(
                    tenantId, operationId, award, snapshot, normalizedHeader, entry.getKey(), entry.getValue(),
                    actorPrincipalId, now, orderIndex++);
            appendCreatedEvent(command, tenantId, aggregate);
            purchaseOrders.add(toView(aggregate));
        }

        AwardReleaseResult result = AwardReleaseResult.builder()
                .operationId(operationId)
                .duplicate(false)
                .aggregateType("procurement_award")
                .awardId(award.getAwardId())
                .awardVersion(award.getVersion())
                .purchaseOrders(purchaseOrders)
                .build();
        require(procurementMapper.markOperationSucceeded(operationId, tenantId, "procurement_award",
                        award.getAwardId(), JsonUtils.toJsonString(result), now) == 1,
                "award release operation completion conflict");
        return result;
    }

    private SnapshotHeader normalizeHeader(AwardSnapshot snapshot) {
        requireRef(snapshot.getSnapshotId(), "snapshotId", 160);
        requireRef(snapshot.getAwardId(), "awardId", 128);
        require(snapshot.getAwardVersion() != null && snapshot.getAwardVersion() > 0, "awardVersion must be positive");
        requireRef(snapshot.getRequisitionId(), "requisitionId", 128);
        require(snapshot.getRequisitionVersion() != null && snapshot.getRequisitionVersion() > 0,
                "requisitionVersion must be positive");
        requireRef(snapshot.getLegalEntityId(), "legalEntityId", 128);
        String taxPolicy = requireSupportedPolicy(snapshot.getTaxCalculationPolicyCode(), SUPPORTED_TAX_POLICY,
                "taxCalculationPolicyCode");
        String roundingPolicy = requireSupportedPolicy(snapshot.getRoundingPolicyCode(), SUPPORTED_ROUNDING_POLICY,
                "roundingPolicyCode");
        require(snapshot.getApprovedAt() != null, "approvedAt is required");
        return new SnapshotHeader(snapshot.getSnapshotId(), snapshot.getAwardId(), snapshot.getAwardVersion(),
                snapshot.getRequisitionId(), snapshot.getRequisitionVersion(), snapshot.getLegalEntityId(),
                taxPolicy, roundingPolicy, snapshot.getApprovedAt().toLocalDate());
    }

    private List<SnapshotLine> normalizeLines(List<AwardSnapshotLine> sourceLines, AwardSnapshot snapshot) {
        Set<String> awardLineIds = new LinkedHashSet<>();
        Set<Integer> lineNumbers = new LinkedHashSet<>();
        List<SnapshotLine> normalized = new ArrayList<>(sourceLines.size());
        for (AwardSnapshotLine line : sourceLines) {
            require(Objects.equals(snapshot.getSnapshotId(), line.getSnapshotId()), "award snapshot line is not attached to the locked snapshot");
            requireRef(line.getAwardLineId(), "awardLineId", 128);
            require(awardLineIds.add(line.getAwardLineId()), "award snapshot line must be unique");
            require(line.getLineNumber() != null && line.getLineNumber() > 0 && lineNumbers.add(line.getLineNumber()),
                    "award snapshot lineNumber must be positive and unique");
            requireRef(line.getSupplierId(), "supplierId", 128);
            requireCurrency(line.getCurrencyCode());
            requireRef(line.getCanonicalSkuId(), "canonicalSkuId", 128);
            requireRef(line.getCanonicalWarehouseId(), "canonicalWarehouseId", 128);
            requirePositive(line.getAwardedQuantity(), "awardedQuantity");
            requireCode(line.getUomCode(), "uomCode");
            requireCode(line.getTaxCode(), "taxCode");
            require(line.getTaxRateBps() != null && line.getTaxRateBps() >= 0 && line.getTaxRateBps() <= 10_000,
                    "taxRateBps must be between 0 and 10000");
            require(line.getPromisedDeliveryDate() != null, "promisedDeliveryDate is required");
            require(!line.getPromisedDeliveryDate().isBefore(snapshot.getApprovedAt().toLocalDate()),
                    "promisedDeliveryDate must not be earlier than approvedAt");
            BigDecimal unitNetPriceMinor = nonNegativePrice(line.getUnitNetPriceMinor(), "unitNetPriceMinor");
            long lineNetAmountMinor = nonNegativeAmount(line.getLineNetAmountMinor(), "lineNetAmountMinor");
            long lineTaxAmountMinor = nonNegativeAmount(line.getLineTaxAmountMinor(), "lineTaxAmountMinor");
            long lineGrossAmountMinor = nonNegativeAmount(line.getLineGrossAmountMinor(), "lineGrossAmountMinor");
            require(calculateLineNetAmountMinor(line.getAwardedQuantity(), unitNetPriceMinor) == lineNetAmountMinor,
                    "award snapshot lineNetAmountMinor must equal awardedQuantity * unitNetPriceMinor using HALF_UP");
            require(calculateTaxAmountMinor(lineNetAmountMinor, line.getTaxRateBps()) == lineTaxAmountMinor,
                    "award snapshot lineTaxAmountMinor must equal HALF_UP(lineNetAmountMinor * taxRateBps / 10000)");
            require(Math.addExact(lineNetAmountMinor, lineTaxAmountMinor) == lineGrossAmountMinor,
                    "award snapshot line gross amount must equal net plus tax");
            requireRef(line.getRequisitionLineId(), "requisitionLineId", 128);
            requireRef(line.getRequisitionScheduleId(), "requisitionScheduleId", 128);
            requireRef(line.getValuationPolicyId(), "valuationPolicyId", 128);
            requireRef(line.getValuationPolicyVersion(), "valuationPolicyVersion", 64);
            requireSha(line.getValuationPolicyHash(), "valuationPolicyHash");
            normalized.add(new SnapshotLine(line));
        }
        return normalized;
    }

    private Map<GroupKey, List<SnapshotLine>> groupLines(List<SnapshotLine> lines) {
        Map<GroupKey, List<SnapshotLine>> grouped = new LinkedHashMap<>();
        lines.stream()
                .sorted((left, right) -> {
                    int supplier = left.line().getSupplierId().compareTo(right.line().getSupplierId());
                    if (supplier != 0) {
                        return supplier;
                    }
                    int currency = upper(left.line().getCurrencyCode()).compareTo(upper(right.line().getCurrencyCode()));
                    if (currency != 0) {
                        return currency;
                    }
                    return Integer.compare(left.line().getLineNumber(), right.line().getLineNumber());
                })
                .forEach(line -> grouped.computeIfAbsent(
                        new GroupKey(line.line().getSupplierId(), upper(line.line().getCurrencyCode())),
                        ignored -> new ArrayList<>()).add(line));
        return grouped;
    }

    private OrderAggregate createDraftOrder(Long tenantId, Long operationId, Award award, AwardSnapshot snapshot,
                                            SnapshotHeader header, GroupKey groupKey, List<SnapshotLine> lines,
                                            String actorPrincipalId, LocalDateTime now, int orderIndex) {
        LocalDate earliestPromisedDate = lines.stream()
                .map(line -> line.line().getPromisedDeliveryDate())
                .min(LocalDate::compareTo)
                .orElseThrow(() -> new IllegalArgumentException("award release group must contain at least one line"));
        int leadTimeDays = Math.toIntExact(ChronoUnit.DAYS.between(header.approvedDate(), earliestPromisedDate));
        require(leadTimeDays >= 0, "leadTimeDays must not be negative");

        String groupSeed = award.getAwardId() + "\n" + award.getVersion() + "\n" + groupKey.supplierId() + "\n" + groupKey.currencyCode();
        String orderId = "po:award-release:" + shortHash(groupSeed, 32);
        String orderCode = "PO-CM-AR-" + shortHash(groupSeed, 20).toUpperCase(Locale.ROOT);
        long headerNetAmountMinor = lines.stream().mapToLong(line -> line.line().getLineNetAmountMinor()).reduce(0L, Math::addExact);
        long headerTaxAmountMinor = lines.stream().mapToLong(line -> line.line().getLineTaxAmountMinor()).reduce(0L, Math::addExact);
        long headerGrossAmountMinor = lines.stream().mapToLong(line -> line.line().getLineGrossAmountMinor()).reduce(0L, Math::addExact);

        ProcurementOrder order = new ProcurementOrder()
                .setOrderId(orderId)
                .setTenantId(tenantId)
                .setOrderCode(orderCode)
                .setSourceBusinessType(SOURCE_BUSINESS_TYPE)
                .setSourceBusinessRef(award.getAwardId())
                .setAwardId(award.getAwardId())
                .setAwardVersion(award.getVersion())
                .setLegalEntityId(header.legalEntityId())
                .setSupplierId(groupKey.supplierId())
                .setCurrencyCode(groupKey.currencyCode())
                .setLeadTimeDays(leadTimeDays)
                .setHeaderNetAmountMinor(headerNetAmountMinor)
                .setHeaderTaxAmountMinor(headerTaxAmountMinor)
                .setHeaderGrossAmountMinor(headerGrossAmountMinor)
                .setTaxCalculationPolicyCode(header.taxCalculationPolicyCode())
                .setRoundingPolicyCode(header.roundingPolicyCode())
                .setStatus("DRAFT")
                .setCreatedByPrincipalId(actorPrincipalId)
                .setReasonCode(ORDER_REASON_CODE)
                .setRemark(null)
                .setVersion(1L)
                .setCreatedAt(now)
                .setUpdatedAt(now);
        require(procurementMapper.insertOrder(order) == 1, "failed to persist released procurement order");

        List<PurchaseOrderItem> items = new ArrayList<>(lines.size());
        List<PurchaseOrderDeliverySchedule> schedules = new ArrayList<>(lines.size());
        List<PurchaseOrderAwardSource> awardSources = new ArrayList<>(lines.size());
        for (SnapshotLine line : lines) {
            String lineSeed = orderId + "\n" + line.line().getAwardLineId();
            String itemId = "poi:" + shortHash(lineSeed, 32);
            PurchaseOrderItem item = new PurchaseOrderItem()
                    .setItemId(itemId)
                    .setTenantId(tenantId)
                    .setOrderId(orderId)
                    .setLineNumber(line.line().getLineNumber())
                    .setAwardLineId(line.line().getAwardLineId())
                    .setCanonicalSkuId(line.line().getCanonicalSkuId())
                    .setOrderedQuantity(line.line().getAwardedQuantity())
                    .setUomCode(upper(line.line().getUomCode()))
                    .setTaxCode(upper(line.line().getTaxCode()))
                    .setTaxRateBps(line.line().getTaxRateBps())
                    .setUnitNetPriceMinor(line.line().getUnitNetPriceMinor())
                    .setValuationPolicyId(line.line().getValuationPolicyId())
                    .setValuationPolicyVersion(line.line().getValuationPolicyVersion())
                    .setValuationPolicyHash(line.line().getValuationPolicyHash())
                    .setLineNetAmountMinor(line.line().getLineNetAmountMinor())
                    .setLineTaxAmountMinor(line.line().getLineTaxAmountMinor())
                    .setLineGrossAmountMinor(line.line().getLineGrossAmountMinor())
                    .setCreatedAt(now)
                    .setUpdatedAt(now);
            items.add(item);
            String scheduleId = "pos:" + shortHash(itemId + "\n" + line.line().getRequisitionScheduleId(), 32);
            schedules.add(new PurchaseOrderDeliverySchedule()
                    .setScheduleId(scheduleId)
                    .setTenantId(tenantId)
                    .setOrderId(orderId)
                    .setItemId(itemId)
                    .setScheduleNumber(1)
                    .setRequiredDeliveryDate(line.line().getPromisedDeliveryDate())
                    .setCanonicalWarehouseId(line.line().getCanonicalWarehouseId())
                    .setScheduledQuantity(line.line().getAwardedQuantity())
                    .setCreatedAt(now)
                    .setUpdatedAt(now));
            awardSources.add(new PurchaseOrderAwardSource()
                    .setTenantId(tenantId)
                    .setOrderId(orderId)
                    .setAwardId(award.getAwardId())
                    .setAwardVersion(award.getVersion())
                    .setAwardLineId(line.line().getAwardLineId())
                    .setItemId(itemId)
                    .setSourceSnapshotId(snapshot.getSnapshotId())
                    .setCreatedAt(now));
        }
        require(procurementMapper.insertItems(items) == items.size(), "failed to persist released procurement order items");
        require(procurementMapper.insertSchedules(schedules) == schedules.size(), "failed to persist released procurement schedules");
        require(procurementMapper.insertAwardSources(awardSources) == awardSources.size(),
                "failed to persist released procurement award sources");
        require(procurementMapper.insertStatusHistory(new OrderStatusHistory()
                        .setTenantId(tenantId)
                        .setOrderId(orderId)
                        .setOperationId(operationId)
                        .setAggregateVersion(1L)
                        .setStatus("DRAFT")
                        .setActorPrincipalId(actorPrincipalId)
                        .setReasonCode(ORDER_REASON_CODE)
                        .setOccurredAt(now)
                        .setCreatedAt(now)) == 1,
                "failed to persist released procurement order history");
        return new OrderAggregate(order, items, schedules, orderIndex);
    }

    private void appendCreatedEvent(AwardReleaseCommand command, Long tenantId, OrderAggregate aggregate) {
        ProcurementOrder order = aggregate.order();
        outboxAppender.append(AppendDomainEventCommand.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("procurement.order.created")
                .schemaVersion(2)
                .sourceSystem(SOURCE_SYSTEM)
                .tenantId(tenantId)
                .aggregateType("procurement_order")
                .aggregateId(order.getOrderId())
                .aggregateVersion(order.getVersion())
                .eventSequence((short) 1)
                .occurredAt(command.getOccurredAt())
                .traceId(command.getRunId())
                .correlationId(command.getCorrelationId())
                .causationId(command.getCausationId())
                .idempotencyKey(command.getIdempotencyKey() + ":" + aggregate.orderIndex())
                .payload(toPayload(aggregate))
                .headers(Map.of("run_id", command.getRunId(), "status", order.getStatus()))
                .destination("lakehouse")
                .build());
    }

    private ProcurementOrderView toView(OrderAggregate aggregate) {
        Map<String, List<ProcurementOrderView.PurchaseOrderDeliveryScheduleView>> schedulesByItem = new LinkedHashMap<>();
        for (PurchaseOrderDeliverySchedule schedule : aggregate.schedules()) {
            schedulesByItem.computeIfAbsent(schedule.getItemId(), ignored -> new ArrayList<>())
                    .add(ProcurementOrderView.PurchaseOrderDeliveryScheduleView.builder()
                            .scheduleId(schedule.getScheduleId())
                            .scheduleNumber(schedule.getScheduleNumber())
                            .requiredDeliveryDate(schedule.getRequiredDeliveryDate())
                            .canonicalWarehouseId(schedule.getCanonicalWarehouseId())
                            .scheduledQuantity(schedule.getScheduledQuantity())
                            .build());
        }
        List<ProcurementOrderView.PurchaseOrderItemView> itemViews = new ArrayList<>(aggregate.items().size());
        for (PurchaseOrderItem item : aggregate.items()) {
            itemViews.add(ProcurementOrderView.PurchaseOrderItemView.builder()
                    .itemId(item.getItemId())
                    .lineNumber(item.getLineNumber())
                    .canonicalSkuId(item.getCanonicalSkuId())
                    .orderedQuantity(item.getOrderedQuantity())
                    .uomCode(item.getUomCode())
                    .taxCode(item.getTaxCode())
                    .taxRateBps(item.getTaxRateBps())
                    .unitNetPriceMinor(item.getUnitNetPriceMinor())
                    .valuationPolicyId(item.getValuationPolicyId())
                    .valuationPolicyVersion(item.getValuationPolicyVersion())
                    .valuationPolicyHash(item.getValuationPolicyHash())
                    .lineNetAmountMinor(item.getLineNetAmountMinor())
                    .lineTaxAmountMinor(item.getLineTaxAmountMinor())
                    .lineGrossAmountMinor(item.getLineGrossAmountMinor())
                    .schedules(schedulesByItem.getOrDefault(item.getItemId(), List.of()))
                    .build());
        }
        return ProcurementOrderView.builder()
                .orderId(aggregate.order().getOrderId())
                .orderCode(aggregate.order().getOrderCode())
                .sourceBusinessType(aggregate.order().getSourceBusinessType())
                .sourceBusinessRef(aggregate.order().getSourceBusinessRef())
                .supplierId(aggregate.order().getSupplierId())
                .currencyCode(aggregate.order().getCurrencyCode())
                .leadTimeDays(aggregate.order().getLeadTimeDays())
                .headerNetAmountMinor(aggregate.order().getHeaderNetAmountMinor())
                .headerTaxAmountMinor(aggregate.order().getHeaderTaxAmountMinor())
                .headerGrossAmountMinor(aggregate.order().getHeaderGrossAmountMinor())
                .taxCalculationPolicyCode(aggregate.order().getTaxCalculationPolicyCode())
                .roundingPolicyCode(aggregate.order().getRoundingPolicyCode())
                .status(aggregate.order().getStatus())
                .version(aggregate.order().getVersion())
                .createdByPrincipalId(aggregate.order().getCreatedByPrincipalId())
                .reasonCode(aggregate.order().getReasonCode())
                .createdAt(aggregate.order().getCreatedAt())
                .updatedAt(aggregate.order().getUpdatedAt())
                .items(itemViews)
                .build();
    }

    private Map<String, Object> toPayload(OrderAggregate aggregate) {
        List<Map<String, Object>> items = new ArrayList<>(aggregate.items().size());
        Map<String, List<PurchaseOrderDeliverySchedule>> schedulesByItem = new LinkedHashMap<>();
        for (PurchaseOrderDeliverySchedule schedule : aggregate.schedules()) {
            schedulesByItem.computeIfAbsent(schedule.getItemId(), ignored -> new ArrayList<>()).add(schedule);
        }
        for (PurchaseOrderItem item : aggregate.items()) {
            List<Map<String, Object>> schedulePayloads = new ArrayList<>();
            for (PurchaseOrderDeliverySchedule schedule : schedulesByItem.getOrDefault(item.getItemId(), List.of())) {
                schedulePayloads.add(payload(
                        "schedule_id", schedule.getScheduleId(),
                        "schedule_number", schedule.getScheduleNumber(),
                        "required_delivery_date", schedule.getRequiredDeliveryDate().toString(),
                        "canonical_warehouse_id", schedule.getCanonicalWarehouseId(),
                        "scheduled_quantity", schedule.getScheduledQuantity()));
            }
            items.add(payload(
                    "item_id", item.getItemId(),
                    "line_number", item.getLineNumber(),
                    "award_line_id", item.getAwardLineId(),
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
                    "schedules", schedulePayloads));
        }
        return payload(
                "order_id", aggregate.order().getOrderId(),
                "order_code", aggregate.order().getOrderCode(),
                "source_business_type", aggregate.order().getSourceBusinessType(),
                "source_business_ref", aggregate.order().getSourceBusinessRef(),
                "award_id", aggregate.order().getAwardId(),
                "award_version", aggregate.order().getAwardVersion(),
                "legal_entity_id", aggregate.order().getLegalEntityId(),
                "supplier_id", aggregate.order().getSupplierId(),
                "currency_code", aggregate.order().getCurrencyCode(),
                "lead_time_days", aggregate.order().getLeadTimeDays(),
                "header_net_amount_minor", aggregate.order().getHeaderNetAmountMinor(),
                "header_tax_amount_minor", aggregate.order().getHeaderTaxAmountMinor(),
                "header_gross_amount_minor", aggregate.order().getHeaderGrossAmountMinor(),
                "tax_calculation_policy_code", aggregate.order().getTaxCalculationPolicyCode(),
                "rounding_policy_code", aggregate.order().getRoundingPolicyCode(),
                "current_status", aggregate.order().getStatus(),
                "items", items);
    }

    private static void validate(AwardReleaseCommand command, String actorPrincipalId) {
        require(command != null, "award release command is required");
        requireRef(actorPrincipalId, "actorPrincipalId", 128);
        requireRef(command.getIdempotencyKey(), "idempotencyKey", 128);
        requireRef(command.getRunId(), "runId", 128);
        requireUuid(command.getCorrelationId(), "correlationId");
        require(command.getOccurredAt() != null, "occurredAt is required");
        requireRef(command.getAwardId(), "awardId", 128);
        require(command.getExpectedAwardVersion() != null && command.getExpectedAwardVersion() > 0,
                "expectedAwardVersion must be positive");
        if (command.getCausationId() != null) {
            requireRef(command.getCausationId(), "causationId", 128);
        }
    }

    private static String requireSupportedPolicy(String value, String expected, String field) {
        requireCode(value, field);
        require(expected.equals(upper(value)), "unsupported " + field);
        return upper(value);
    }

    private static String shortHash(String seed, int length) {
        return DigestUtil.sha256Hex(seed).substring(0, length);
    }

    private static BigDecimal nonNegativePrice(BigDecimal value, String field) {
        require(value != null && value.compareTo(BigDecimal.ZERO) >= 0, field + " must not be negative");
        require(value.scale() <= 6, field + " must have scale <= 6");
        return value.stripTrailingZeros().scale() < 0 ? value.setScale(0) : value;
    }

    private static long nonNegativeAmount(Long value, String field) {
        require(value != null && value >= 0, field + " must not be negative");
        return value;
    }

    private static long calculateLineNetAmountMinor(BigDecimal quantity, BigDecimal unitNetPriceMinor) {
        return quantity.multiply(unitNetPriceMinor).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact();
    }

    private static long calculateTaxAmountMinor(long lineNetAmountMinor, int taxRateBps) {
        return BigDecimal.valueOf(lineNetAmountMinor)
                .multiply(BigDecimal.valueOf(taxRateBps))
                .divide(BigDecimal.valueOf(10_000), 0, java.math.RoundingMode.HALF_UP)
                .longValueExact();
    }

    private static void requireUuid(String value, String field) {
        try {
            UUID.fromString(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(field + " must be a UUID", exception);
        }
    }

    private static void requireCurrency(String value) {
        require(value != null && upper(value).matches("[A-Z]{3}"), "currencyCode must be ISO-4217");
    }

    private static void requirePositive(BigDecimal value, String field) {
        require(value != null && value.signum() > 0 && value.scale() <= 6 && value.precision() <= 24,
                field + " must be a positive DECIMAL(24,6)");
    }

    private static void requireRef(String value, String field, int maxLength) {
        require(value != null && value.length() <= maxLength && SAFE_REF.matcher(value).matches(),
                field + " must be a safe opaque reference");
    }

    private static void requireCode(String value, String field) {
        String normalized = upper(value);
        require(normalized != null && SAFE_CODE.matcher(normalized).matches(),
                field + " must be an uppercase code");
    }

    private static void requireSha(String value, String field) {
        require(value != null && SHA_256.matcher(value).matches(),
                field + " must be a lowercase SHA-256");
    }

    private static String upper(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private static Map<String, Object> payload(Object... values) {
        Map<String, Object> payload = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            payload.put((String) values[i], values[i + 1]);
        }
        return payload;
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

    private record SnapshotHeader(String snapshotId, String awardId, Long awardVersion, String requisitionId,
                                  Long requisitionVersion, String legalEntityId, String taxCalculationPolicyCode,
                                  String roundingPolicyCode, LocalDate approvedDate) {
    }

    private record SnapshotLine(AwardSnapshotLine line) {
    }

    private record GroupKey(String supplierId, String currencyCode) {
    }

    private record OrderAggregate(ProcurementOrder order, List<PurchaseOrderItem> items,
                                  List<PurchaseOrderDeliverySchedule> schedules, int orderIndex) {
    }
}
