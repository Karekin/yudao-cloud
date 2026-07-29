package cn.iocoder.yudao.module.cloudmold.aftersale.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.module.cloudmold.aftersale.dal.dataobject.*;
import cn.iocoder.yudao.module.cloudmold.aftersale.dal.mysql.*;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AfterSaleEventService {
    private final AfterSaleHistoryMapper historyMapper;
    private final AfterSaleRefundHistoryMapper refundHistoryMapper;
    private final AfterSaleResolutionSagaHistoryMapper sagaHistoryMapper;
    private final OutboxAppender outboxAppender;

    public void appendDispositionAssessment(ReturnDispositionAssessmentDO assessment,
                                            AfterSaleCaseDO sale,
                                            Instant occurredAt,
                                            LocalDateTime now) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("run_id", sale.getRunId());
        payload.put("assessment_id", assessment.getAssessmentId());
        payload.put("after_sale_id", assessment.getAfterSaleId());
        payload.put("return_fulfillment_id", assessment.getReturnFulfillmentId());
        payload.put("assessor_id", assessment.getAssessorId());
        payload.put("packaging_score", assessment.getPackagingScore());
        payload.put("appearance_score", assessment.getAppearanceScore());
        payload.put("function_score", assessment.getFunctionScore());
        payload.put("safety_risk", assessment.getSafetyRisk());
        payload.put("counterfeit_risk", assessment.getCounterfeitRisk());
        payload.put("estimated_resale_value_minor", assessment.getEstimatedResaleValueMinor());
        payload.put("estimated_recovery_cost_minor", assessment.getEstimatedRecoveryCostMinor());
        payload.put("inspection_evidence_ref", assessment.getInspectionEvidenceRef());
        payload.put("recommended_disposition", assessment.getRecommendedDisposition());
        payload.put("quality_status", assessment.getQualityStatus());
        payload.put("condition_grade", assessment.getConditionGrade());
        payload.put("confidence_score", assessment.getConfidenceScore());
        payload.put("rationale_code", assessment.getRationaleCode());
        outboxAppender.append(AppendDomainEventCommand.builder()
                .eventType("after_sale.return_disposition.assessed").schemaVersion(1)
                .sourceSystem("cloudmold-aftersales").tenantId(sale.getTenantId())
                .aggregateType("return_disposition_assessment")
                .aggregateId(assessment.getAssessmentId()).aggregateVersion(1L)
                .eventSequence((short) 1).occurredAt(occurredAt)
                .correlationId(sale.getCorrelationId()).causationId(sale.getCausationId())
                .idempotencyKey("return-disposition:" + assessment.getAssessmentId() + ":event:1")
                .payload(payload).headers(Map.of("recommendation",
                        assessment.getRecommendedDisposition()))
                .destination("lakehouse").build());
    }

    public void appendCase(Long operationId, AfterSaleCaseDO sale, AfterSaleItemDO item,
                           String previous, Instant occurredAt, LocalDateTime now) {
        historyMapper.insert(new AfterSaleHistoryDO().setTenantId(sale.getTenantId())
                .setAfterSaleId(sale.getAfterSaleId()).setAggregateVersion(sale.getVersion())
                .setPreviousStatus(previous).setCurrentStatus(sale.getStatus()).setOperationId(operationId)
                .setOccurredAt(LocalDateTime.ofInstant(occurredAt, ZoneOffset.UTC)).setCreatedAt(now));
        Map<String, Object> payload = casePayload(sale, item);
        payload.put("previous_status", previous);
        payload.put("current_status", sale.getStatus());
        outboxAppender.append(AppendDomainEventCommand.builder().eventType("after_sale.status.changed")
                .schemaVersion(1).sourceSystem("cloudmold-aftersales").tenantId(sale.getTenantId())
                .aggregateType("after_sale").aggregateId(sale.getAfterSaleId())
                .aggregateVersion(sale.getVersion()).eventSequence((short) 1).occurredAt(occurredAt)
                .correlationId(sale.getCorrelationId()).causationId(sale.getCausationId())
                .idempotencyKey("after-sale:" + sale.getAfterSaleId() + ":event:" + sale.getVersion())
                .payload(payload).headers(Map.of("status", sale.getStatus())).destination("lakehouse").build());
    }

    public void appendRefund(AfterSaleCaseDO sale, AfterSaleItemDO item, String previous, String current,
                             long version, Long transactionId, Instant occurredAt, LocalDateTime now) {
        refundHistoryMapper.insert(new AfterSaleRefundHistoryDO().setTenantId(sale.getTenantId())
                .setAfterSaleId(sale.getAfterSaleId()).setAggregateVersion(version)
                .setPreviousStatus(previous).setCurrentStatus(current).setAmountMinor(sale.getApprovedAmountMinor())
                .setCurrencyCode(sale.getCurrencyCode()).setPaymentRefundTransactionId(transactionId)
                .setOccurredAt(LocalDateTime.ofInstant(occurredAt, ZoneOffset.UTC)).setCreatedAt(now));
        Map<String, Object> payload = casePayload(sale, item);
        payload.put("previous_status", previous);
        payload.put("current_status", current);
        payload.put("payment_refund_transaction_id", transactionId);
        payload.put("refund_id", sale.getAfterSaleId());
        payload.put("refunded_amount_minor", "SUCCEEDED".equals(current) ? sale.getApprovedAmountMinor() : 0L);
        payload.put("gross_amount_minor", item.getLineAmountMinor());
        payload.put("benefit_amount_minor", item.getDiscountAmountMinor());
        payload.put("net_amount_minor", item.getNetAmountMinor());
        payload.put("provider_code", "INTERNAL_TEST");
        payload.put("resolution_saga_id", sale.getResolutionSagaId());
        payload.put("step_ordinal", "SUCCEEDED".equals(current) ? 3 : 0);
        outboxAppender.append(AppendDomainEventCommand.builder().eventType("after_sale.refund.status.changed")
                .schemaVersion(2).sourceSystem("cloudmold-aftersales").tenantId(sale.getTenantId())
                .aggregateType("after_sale_refund").aggregateId(sale.getAfterSaleId())
                .aggregateVersion(version).eventSequence((short) 1).occurredAt(occurredAt)
                .correlationId(sale.getCorrelationId()).causationId(sale.getCausationId())
                .idempotencyKey("after-sale-refund:" + sale.getAfterSaleId() + ":event:" + version)
                .payload(payload).headers(Map.of("status", current)).destination("lakehouse").build());
    }

    public void appendSaga(AfterSaleResolutionSagaDO saga, String previous, LocalDateTime now) {
        sagaHistoryMapper.insert(new AfterSaleResolutionSagaHistoryDO().setTenantId(saga.getTenantId())
                .setSagaId(saga.getSagaId()).setAggregateVersion(saga.getVersion())
                .setPreviousStatus(previous).setCurrentStatus(saga.getStatus()).setActiveStep(saga.getActiveStep())
                .setAttemptCount(saga.getAttemptCount()).setErrorCode(saga.getLastErrorCode())
                .setErrorMessage(saga.getLastErrorMessage()).setNextRetryAt(saga.getNextRetryAt())
                .setOccurredAt(now).setCreatedAt(now));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("run_id", saga.getRunId());
        payload.put("saga_id", saga.getSagaId());
        payload.put("after_sale_id", saga.getAfterSaleId());
        payload.put("after_sale_item_id", saga.getAfterSaleItemId());
        payload.put("order_id", saga.getOrderId());
        payload.put("order_item_id", saga.getOrderItemId());
        payload.put("payment_id", saga.getPaymentId());
        payload.put("return_fulfillment_id", saga.getReturnFulfillmentId());
        payload.put("return_shipment_id", saga.getReturnShipmentId());
        payload.put("inspection_id", saga.getInspectionId());
        payload.put("disposition_assessment_id", saga.getDispositionAssessmentId());
        payload.put("disposition_code", saga.getDispositionCode());
        payload.put("return_stock_status", saga.getReturnStockStatus());
        payload.put("return_quality_status", saga.getReturnQualityStatus());
        payload.put("canonical_sku_id", saga.getCanonicalSkuId());
        payload.put("quantity", saga.getQuantity().toPlainString());
        payload.put("accepted_quantity", saga.getQuantity().toPlainString());
        payload.put("returned_quantity", saga.getInventoryLedgerTransactionId() == null
                ? "0" : saga.getQuantity().toPlainString());
        payload.put("uom_code", saga.getUomCode());
        payload.put("approved_amount_minor", saga.getApprovedAmountMinor());
        payload.put("gross_amount_minor", saga.getGrossAmountMinor());
        payload.put("benefit_amount_minor", saga.getBenefitAmountMinor());
        payload.put("net_amount_minor", saga.getNetAmountMinor());
        payload.put("refunded_amount_minor", saga.getPaymentRefundTransactionId() == null
                ? 0L : saga.getApprovedAmountMinor());
        payload.put("currency_code", saga.getCurrencyCode());
        payload.put("previous_status", previous);
        payload.put("current_status", saga.getStatus());
        payload.put("active_step", saga.getActiveStep());
        payload.put("step_ordinal", stepOrdinal(saga));
        payload.put("attempt", saga.getAttemptCount());
        payload.put("inventory_operation_id", saga.getInventoryOperationId());
        payload.put("inventory_ledger_transaction_id", saga.getInventoryLedgerTransactionId());
        payload.put("disposal_operation_id", saga.getDisposalOperationId());
        payload.put("disposal_ledger_transaction_id", saga.getDisposalLedgerTransactionId());
        payload.put("benefit_reversal_status", saga.getBenefitReversalStatus());
        payload.put("benefit_reversal_batch_id", saga.getBenefitReversalBatchId());
        payload.put("benefit_reversal_amount_minor", saga.getBenefitReversalAmountMinor());
        payload.put("payment_refund_transaction_id", saga.getPaymentRefundTransactionId());
        payload.put("order_settlement_effect_id", saga.getOrderSettlementEffectId());
        payload.put("order_settlement_version", saga.getOrderSettlementVersion());
        payload.put("order_return_full", saga.getOrderReturnFull());
        payload.put("order_refund_operation_id", saga.getOrderRefundOperationId());
        payload.put("order_return_operation_id", saga.getOrderReturnOperationId());
        payload.put("order_version", saga.getOrderVersion());
        Map<String, Object> checkpoints = new LinkedHashMap<>();
        checkpoints.put("inventory_returned", saga.getInventoryLedgerTransactionId() != null);
        checkpoints.put("inventory_disposed", !"SCRAP".equals(saga.getDispositionCode())
                || saga.getDisposalLedgerTransactionId() != null);
        checkpoints.put("benefit_reversed", "NOT_REQUIRED".equals(saga.getBenefitReversalStatus())
                || "RECORDED".equals(saga.getBenefitReversalStatus()));
        checkpoints.put("payment_refunded", saga.getPaymentRefundTransactionId() != null);
        checkpoints.put("order_settled", saga.getOrderSettlementEffectId() != null);
        checkpoints.put("order_refund_confirmed", saga.getOrderRefundOperationId() != null);
        checkpoints.put("order_returned", saga.getOrderReturnOperationId() != null);
        payload.put("checkpoints", checkpoints);
        payload.put("error_code", saga.getLastErrorCode());
        payload.put("error_message", saga.getLastErrorMessage());
        payload.put("next_retry_at", instant(saga.getNextRetryAt()));
        outboxAppender.append(AppendDomainEventCommand.builder()
                .eventType("after_sale.resolution_saga.status.changed").schemaVersion(3)
                .sourceSystem("cloudmold-aftersales").tenantId(saga.getTenantId())
                .aggregateType("after_sale_resolution_saga").aggregateId(saga.getSagaId())
                .aggregateVersion(saga.getVersion()).eventSequence((short) 1)
                .occurredAt(now.toInstant(ZoneOffset.UTC)).correlationId(saga.getCorrelationId())
                .causationId(saga.getCausationId())
                .idempotencyKey("after-sale-saga:" + saga.getSagaId() + ":event:" + saga.getVersion())
                .payload(payload).headers(Map.of("active_step", saga.getActiveStep()))
                .destination("lakehouse").build());
    }

    public void appendBenefitReversal(AfterSaleResolutionSagaDO saga, AfterSaleBenefitReversalDO reversal,
                                      List<AfterSaleBenefitFundingReversalDO> funding, LocalDateTime now) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("run_id", saga.getRunId());
        payload.put("reversal_batch_id", reversal.getReversalBatchId());
        payload.put("benefit_reversal_id", reversal.getBenefitReversalId());
        payload.put("after_sale_id", reversal.getAfterSaleId());
        payload.put("after_sale_item_id", reversal.getAfterSaleItemId());
        payload.put("order_id", reversal.getOrderId());
        payload.put("order_item_id", reversal.getOrderItemId());
        payload.put("benefit_application_id", reversal.getBenefitApplicationId());
        payload.put("benefit_allocation_id", reversal.getBenefitAllocationId());
        payload.put("benefit_type", reversal.getBenefitType());
        payload.put("benefit_source_type", reversal.getBenefitSourceType());
        payload.put("benefit_source_id", reversal.getBenefitSourceId());
        payload.put("benefit_source_version", reversal.getBenefitSourceVersion());
        payload.put("entitlement_id", reversal.getEntitlementId());
        payload.put("entitlement_effect_status", reversal.getEntitlementEffectStatus());
        payload.put("amount_minor", reversal.getAmountMinor());
        payload.put("currency_code", reversal.getCurrencyCode());
        payload.put("funding", funding.stream().map(row -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("funding_reversal_id", row.getFundingReversalId());
            value.put("benefit_funding_id", row.getBenefitFundingId());
            value.put("funder_type", row.getFunderType());
            value.put("funder_id", row.getFunderId());
            value.put("amount_minor", row.getAmountMinor());
            value.put("currency_code", row.getCurrencyCode());
            return value;
        }).toList());
        outboxAppender.append(AppendDomainEventCommand.builder()
                .eventType("after_sale.benefit_reversal.recorded").schemaVersion(2)
                .sourceSystem("cloudmold-aftersales").tenantId(saga.getTenantId())
                .aggregateType("after_sale_benefit_reversal").aggregateId(reversal.getBenefitReversalId())
                .aggregateVersion(1L).eventSequence((short) 1)
                .occurredAt(reversal.getOccurredAt().toInstant(ZoneOffset.UTC))
                .correlationId(saga.getCorrelationId()).causationId(saga.getCausationId())
                .idempotencyKey("after-sale-benefit-reversal:" + reversal.getBenefitReversalId())
                .payload(payload).headers(Map.of("reversal_batch_id", reversal.getReversalBatchId()))
                .destination("lakehouse").build());
    }

    private static Map<String, Object> casePayload(AfterSaleCaseDO sale, AfterSaleItemDO item) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("run_id", sale.getRunId());
        payload.put("after_sale_id", sale.getAfterSaleId());
        payload.put("after_sale_no", sale.getAfterSaleNo());
        payload.put("after_sale_item_id", item.getAfterSaleItemId());
        payload.put("after_sale_type", sale.getAfterSaleType());
        payload.put("reason_code", sale.getReasonCode());
        payload.put("responsibility", sale.getResponsibility());
        payload.put("has_reason_text", hasText(sale.getReason()));
        payload.put("reason_text_digest_sha256", digestOrNull(sale.getReason()));
        payload.put("order_id", sale.getOrderId());
        payload.put("order_item_id", item.getOrderItemId());
        payload.put("canonical_sku_id", item.getCanonicalSkuId());
        payload.put("quantity", item.getQuantity().toPlainString());
        payload.put("listing_id", item.getListingId());
        payload.put("listing_offer_id", item.getListingOfferId());
        payload.put("buyer_id", sale.getBuyerId());
        payload.put("approved_amount_minor", sale.getApprovedAmountMinor());
        payload.put("currency_code", sale.getCurrencyCode());
        payload.put("payment_id", sale.getPaymentId());
        payload.put("forward_fulfillment_id", sale.getForwardFulfillmentId());
        payload.put("forward_shipment_id", sale.getForwardShipmentId());
        payload.put("return_fulfillment_id", sale.getReturnFulfillmentId());
        payload.put("return_shipment_id", sale.getReturnShipmentId());
        payload.put("inspection_id", sale.getInspectionId());
        payload.put("resolution_saga_id", sale.getResolutionSagaId());
        payload.put("refund_status", sale.getRefundStatus());
        return payload;
    }

    private static int stepOrdinal(AfterSaleResolutionSagaDO saga) {
        if ("COMPLETED".equals(saga.getStatus())) return 8;
        return switch (saga.getActiveStep()) {
            case "RETURN_INVENTORY" -> 1;
            case "DISPOSE_INVENTORY" -> 2;
            case "REVERSE_BENEFITS" -> 3;
            case "REFUND_PAYMENT" -> 4;
            case "SETTLE_ORDER" -> 5;
            case "CONFIRM_ORDER_REFUND" -> 6;
            case "RETURN_ORDER" -> 7;
            default -> 0;
        };
    }

    private static String instant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC).toString();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String digestOrNull(String value) {
        return hasText(value) ? DigestUtil.sha256Hex(value.trim()) : null;
    }
}
