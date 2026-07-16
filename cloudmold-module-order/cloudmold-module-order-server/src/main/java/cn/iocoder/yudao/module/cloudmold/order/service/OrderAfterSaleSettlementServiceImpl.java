package cn.iocoder.yudao.module.cloudmold.order.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.order.api.*;
import cn.iocoder.yudao.module.cloudmold.order.dal.dataobject.*;
import cn.iocoder.yudao.module.cloudmold.order.dal.mysql.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Service
@RequiredArgsConstructor
public class OrderAfterSaleSettlementServiceImpl implements OrderAfterSaleSettlementApi {
    private final OrderHeaderMapper orderMapper;
    private final OrderItemMapper itemMapper;
    private final OrderReturnSettlementMapper settlementMapper;
    private final OrderItemReturnSettlementMapper itemSettlementMapper;
    private final OrderReturnEffectMapper effectMapper;
    private final OutboxAppender outboxAppender;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderAfterSaleSettlementResult record(OrderAfterSaleSettlementCommand command) {
        validate(command);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        OrderHeaderDO order = orderMapper.selectForUpdate(tenantId, command.getOrderId());
        require(order != null, "canonical order does not exist");
        OrderReturnEffectDO existing = effectMapper.selectByAfterSale(tenantId, command.getAfterSaleId());
        if (existing != null) return replay(existing, command);
        require("COMPLETED".equals(order.getStatus()),
                "partial return settlement requires COMPLETED order");

        List<OrderItemDO> items = itemMapper.selectByOrder(tenantId, order.getOrderId());
        OrderItemDO item = items.stream().filter(value -> Objects.equals(value.getOrderItemId(),
                command.getOrderItemId())).findFirst().orElseThrow(() ->
                new IllegalStateException("order item does not belong to canonical order"));
        OrderItemReturnSettlementDO itemSettlement = itemSettlementMapper.selectForUpdate(tenantId,
                item.getOrderItemId());
        BigDecimal previousQuantity = itemSettlement == null ? BigDecimal.ZERO
                : itemSettlement.getReturnedQuantity();
        BigDecimal nextQuantity = previousQuantity.add(command.getQuantity());
        require(nextQuantity.compareTo(item.getQuantity()) <= 0,
                "return settlement exceeds ordered quantity");

        long previousGross = itemSettlement == null ? 0L : itemSettlement.getReturnedGrossAmountMinor();
        long previousBenefit = itemSettlement == null ? 0L : itemSettlement.getReversedBenefitAmountMinor();
        long previousNet = itemSettlement == null ? 0L : itemSettlement.getRefundedNetAmountMinor();
        long nextGross = Math.addExact(previousGross, command.getGrossAmountMinor());
        long nextBenefit = Math.addExact(previousBenefit, command.getBenefitAmountMinor());
        long nextNet = Math.addExact(previousNet, command.getNetAmountMinor());
        require(nextGross <= item.getLineAmountMinor() && nextBenefit <= item.getDiscountAmountMinor()
                        && nextNet <= item.getNetAmountMinor(),
                "return settlement exceeds immutable order item money");
        if (nextQuantity.compareTo(item.getQuantity()) == 0) {
            require(nextGross == item.getLineAmountMinor() && nextBenefit == item.getDiscountAmountMinor()
                            && nextNet == item.getNetAmountMinor(),
                    "final item return does not absorb all quantity and money residuals");
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        long itemVersion;
        if (itemSettlement == null) {
            itemSettlement = new OrderItemReturnSettlementDO().setOrderItemId(item.getOrderItemId())
                    .setTenantId(tenantId).setOrderId(order.getOrderId()).setOrderedQuantity(item.getQuantity())
                    .setReturnedQuantity(nextQuantity).setReturnedGrossAmountMinor(nextGross)
                    .setReversedBenefitAmountMinor(nextBenefit).setRefundedNetAmountMinor(nextNet)
                    .setVersion(1L).setLastAfterSaleId(command.getAfterSaleId())
                    .setCreatedAt(now).setUpdatedAt(now);
            require(itemSettlementMapper.insert(itemSettlement) == 1,
                    "failed to create item return settlement");
            itemVersion = 1L;
        } else {
            itemVersion = itemSettlement.getVersion() + 1;
            itemSettlement.setReturnedQuantity(nextQuantity).setReturnedGrossAmountMinor(nextGross)
                    .setReversedBenefitAmountMinor(nextBenefit).setRefundedNetAmountMinor(nextNet)
                    .setVersion(itemVersion).setLastAfterSaleId(command.getAfterSaleId()).setUpdatedAt(now);
            require(itemSettlementMapper.updateById(itemSettlement) == 1,
                    "item return settlement update conflict");
        }

        List<OrderItemReturnSettlementDO> allSettlements = itemSettlementMapper.selectByOrder(tenantId,
                order.getOrderId());
        int returnedItemCount = (int) allSettlements.stream().filter(value ->
                value.getReturnedQuantity().compareTo(value.getOrderedQuantity()) == 0).count();
        BigDecimal returnedQuantity = allSettlements.stream().map(OrderItemReturnSettlementDO::getReturnedQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long refundedNet = allSettlements.stream().mapToLong(
                OrderItemReturnSettlementDO::getRefundedNetAmountMinor).sum();
        long reversedBenefit = allSettlements.stream().mapToLong(
                OrderItemReturnSettlementDO::getReversedBenefitAmountMinor).sum();
        boolean fullReturn = returnedItemCount == items.size();
        if (fullReturn) {
            require(returnedQuantity.compareTo(order.getTotalQuantity()) == 0
                            && refundedNet == order.getPayableAmountMinor()
                            && reversedBenefit == order.getDiscountAmountMinor(),
                    "full order return does not reconcile quantity, cash and benefit totals");
        }

        OrderReturnSettlementDO settlement = settlementMapper.selectForUpdate(tenantId, order.getOrderId());
        long settlementVersion;
        if (settlement == null) {
            settlementVersion = 1L;
            settlement = new OrderReturnSettlementDO().setOrderId(order.getOrderId()).setTenantId(tenantId)
                    .setStatus(fullReturn ? "FULL" : "PARTIAL").setTotalItemCount(items.size())
                    .setReturnedItemCount(returnedItemCount).setReturnedQuantity(returnedQuantity)
                    .setRefundedNetAmountMinor(refundedNet).setReversedBenefitAmountMinor(reversedBenefit)
                    .setVersion(settlementVersion).setLastAfterSaleId(command.getAfterSaleId())
                    .setCreatedAt(now).setUpdatedAt(now);
            require(settlementMapper.insert(settlement) == 1, "failed to create order return settlement");
        } else {
            settlementVersion = settlement.getVersion() + 1;
            settlement.setStatus(fullReturn ? "FULL" : "PARTIAL").setTotalItemCount(items.size())
                    .setReturnedItemCount(returnedItemCount).setReturnedQuantity(returnedQuantity)
                    .setRefundedNetAmountMinor(refundedNet).setReversedBenefitAmountMinor(reversedBenefit)
                    .setVersion(settlementVersion).setLastAfterSaleId(command.getAfterSaleId()).setUpdatedAt(now);
            require(settlementMapper.updateById(settlement) == 1, "order return settlement update conflict");
        }

        String effectId = UUID.randomUUID().toString();
        OrderReturnEffectDO effect = new OrderReturnEffectDO().setSettlementEffectId(effectId)
                .setTenantId(tenantId).setAfterSaleId(command.getAfterSaleId())
                .setAfterSaleItemId(command.getAfterSaleItemId()).setRunId(command.getRunId())
                .setOrderId(order.getOrderId()).setOrderItemId(item.getOrderItemId())
                .setQuantity(command.getQuantity()).setGrossAmountMinor(command.getGrossAmountMinor())
                .setBenefitAmountMinor(command.getBenefitAmountMinor()).setNetAmountMinor(command.getNetAmountMinor())
                .setInventoryOperationId(command.getInventoryOperationId())
                .setInventoryLedgerTransactionId(command.getInventoryLedgerTransactionId())
                .setPaymentRefundTransactionId(command.getPaymentRefundTransactionId())
                .setBenefitReversalBatchId(command.getBenefitReversalBatchId())
                .setOrderSettlementVersion(settlementVersion).setItemSettlementVersion(itemVersion)
                .setFullReturn(fullReturn).setCorrelationId(command.getCorrelationId())
                .setCausationId(command.getCausationId())
                .setOccurredAt(LocalDateTime.ofInstant(command.getOccurredAt(), ZoneOffset.UTC)).setCreatedAt(now);
        require(effectMapper.insert(effect) == 1, "failed to record immutable order return effect");
        appendEvent(tenantId, order, settlement, itemSettlement, effect, command);
        return result(effect, order.getVersion(), item.getQuantity().subtract(nextQuantity), false);
    }

    private void appendEvent(Long tenantId, OrderHeaderDO order, OrderReturnSettlementDO settlement,
                             OrderItemReturnSettlementDO item, OrderReturnEffectDO effect,
                             OrderAfterSaleSettlementCommand command) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("run_id", command.getRunId());
        payload.put("settlement_effect_id", effect.getSettlementEffectId());
        payload.put("after_sale_id", effect.getAfterSaleId());
        payload.put("after_sale_item_id", effect.getAfterSaleItemId());
        payload.put("order_id", effect.getOrderId());
        payload.put("order_item_id", effect.getOrderItemId());
        payload.put("quantity", effect.getQuantity().toPlainString());
        payload.put("gross_amount_minor", effect.getGrossAmountMinor());
        payload.put("benefit_amount_minor", effect.getBenefitAmountMinor());
        payload.put("net_amount_minor", effect.getNetAmountMinor());
        payload.put("inventory_operation_id", effect.getInventoryOperationId());
        payload.put("inventory_ledger_transaction_id", effect.getInventoryLedgerTransactionId());
        payload.put("payment_refund_transaction_id", effect.getPaymentRefundTransactionId());
        payload.put("benefit_reversal_batch_id", effect.getBenefitReversalBatchId());
        payload.put("item_returned_quantity", item.getReturnedQuantity().toPlainString());
        payload.put("item_ordered_quantity", item.getOrderedQuantity().toPlainString());
        payload.put("order_returned_quantity", settlement.getReturnedQuantity().toPlainString());
        payload.put("order_total_quantity", order.getTotalQuantity().toPlainString());
        payload.put("refunded_net_amount_minor", settlement.getRefundedNetAmountMinor());
        payload.put("reversed_benefit_amount_minor", settlement.getReversedBenefitAmountMinor());
        payload.put("settlement_status", settlement.getStatus());
        payload.put("full_return", effect.getFullReturn());
        outboxAppender.append(AppendDomainEventCommand.builder()
                .eventType("order.after_sale_settlement.recorded").schemaVersion(1)
                .sourceSystem("cloudmold-order").tenantId(tenantId).aggregateType("order_return_settlement")
                .aggregateId(order.getOrderId()).aggregateVersion(settlement.getVersion()).eventSequence((short) 1)
                .occurredAt(command.getOccurredAt()).correlationId(command.getCorrelationId())
                .causationId(command.getCausationId()).idempotencyKey("order-return:" + effect.getAfterSaleId())
                .payload(payload).headers(Map.of("operation", "RECORD_AFTER_SALE_SETTLEMENT"))
                .destination("lakehouse").build());
    }

    private static OrderAfterSaleSettlementResult replay(OrderReturnEffectDO effect,
                                                         OrderAfterSaleSettlementCommand command) {
        require(Objects.equals(effect.getAfterSaleItemId(), command.getAfterSaleItemId())
                        && Objects.equals(effect.getOrderId(), command.getOrderId())
                        && Objects.equals(effect.getOrderItemId(), command.getOrderItemId())
                        && effect.getQuantity().compareTo(command.getQuantity()) == 0
                        && Objects.equals(effect.getGrossAmountMinor(), command.getGrossAmountMinor())
                        && Objects.equals(effect.getBenefitAmountMinor(), command.getBenefitAmountMinor())
                        && Objects.equals(effect.getNetAmountMinor(), command.getNetAmountMinor())
                        && Objects.equals(effect.getInventoryOperationId(), command.getInventoryOperationId())
                        && Objects.equals(effect.getInventoryLedgerTransactionId(),
                        command.getInventoryLedgerTransactionId())
                        && Objects.equals(effect.getPaymentRefundTransactionId(),
                        command.getPaymentRefundTransactionId())
                        && Objects.equals(effect.getBenefitReversalBatchId(), command.getBenefitReversalBatchId()),
                "after-sale settlement replay conflicts with immutable first effect");
        return result(effect, null, null, true);
    }

    private static OrderAfterSaleSettlementResult result(OrderReturnEffectDO effect, Long orderVersion,
                                                         BigDecimal remainingQuantity, boolean duplicate) {
        return OrderAfterSaleSettlementResult.builder().settlementEffectId(effect.getSettlementEffectId())
                .orderId(effect.getOrderId()).orderItemId(effect.getOrderItemId())
                .returnedQuantity(effect.getQuantity()).remainingQuantity(remainingQuantity)
                .refundedNetAmountMinor(effect.getNetAmountMinor())
                .reversedBenefitAmountMinor(effect.getBenefitAmountMinor())
                .orderSettlementVersion(effect.getOrderSettlementVersion())
                .itemSettlementVersion(effect.getItemSettlementVersion()).orderVersion(orderVersion)
                .fullReturn(effect.getFullReturn()).duplicate(duplicate).build();
    }

    private static void validate(OrderAfterSaleSettlementCommand command) {
        require(command != null, "order after-sale settlement command is required");
        requireText(command.getAfterSaleId(), "afterSaleId", 36);
        requireText(command.getAfterSaleItemId(), "afterSaleItemId", 36);
        requireText(command.getRunId(), "runId", 64);
        requireText(command.getOrderId(), "orderId", 36);
        requireText(command.getOrderItemId(), "orderItemId", 36);
        require(command.getQuantity() != null && command.getQuantity().signum() > 0
                        && command.getQuantity().stripTrailingZeros().scale() <= 0,
                "settlement quantity must be positive whole pieces");
        require(command.getGrossAmountMinor() != null && command.getGrossAmountMinor() >= 0
                        && command.getBenefitAmountMinor() != null && command.getBenefitAmountMinor() >= 0
                        && command.getNetAmountMinor() != null && command.getNetAmountMinor() > 0
                        && command.getGrossAmountMinor() == Math.addExact(command.getBenefitAmountMinor(),
                        command.getNetAmountMinor()),
                "settlement money must conserve gross = benefit + positive net");
        require(command.getInventoryOperationId() != null && command.getInventoryOperationId() > 0,
                "inventoryOperationId is required");
        require(command.getInventoryLedgerTransactionId() != null
                        && command.getInventoryLedgerTransactionId() > 0,
                "inventoryLedgerTransactionId is required");
        require(command.getPaymentRefundTransactionId() != null && command.getPaymentRefundTransactionId() > 0,
                "paymentRefundTransactionId is required");
        require((command.getBenefitAmountMinor() == 0 && command.getBenefitReversalBatchId() == null)
                        || (command.getBenefitAmountMinor() > 0
                        && command.getBenefitReversalBatchId() != null
                        && !command.getBenefitReversalBatchId().isBlank()),
                "benefit reversal evidence does not match settlement money");
        requireText(command.getCorrelationId(), "correlationId", 36);
        require(command.getOccurredAt() != null, "occurredAt is required");
    }

    private static void requireText(String value, String field, int max) {
        require(value != null && !value.isBlank() && value.length() <= max, field + " is required");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
