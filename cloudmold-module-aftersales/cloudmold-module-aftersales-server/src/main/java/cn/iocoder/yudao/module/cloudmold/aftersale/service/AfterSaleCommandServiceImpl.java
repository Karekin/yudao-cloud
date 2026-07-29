package cn.iocoder.yudao.module.cloudmold.aftersale.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.aftersale.api.*;
import cn.iocoder.yudao.module.cloudmold.aftersale.dal.dataobject.*;
import cn.iocoder.yudao.module.cloudmold.aftersale.dal.mysql.*;
import cn.iocoder.yudao.module.cloudmold.fulfillment.api.*;
import cn.iocoder.yudao.module.cloudmold.order.api.*;
import cn.iocoder.yudao.module.cloudmold.payment.api.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AfterSaleCommandServiceImpl implements AfterSaleCommandApi, AfterSaleQueryApi {
    private static final int OPERATION_SUCCEEDED = 10;

    private final AfterSaleOperationMapper operationMapper;
    private final AfterSaleCaseMapper caseMapper;
    private final AfterSaleItemMapper itemMapper;
    private final AfterSaleResolutionSagaMapper sagaMapper;
    private final ReturnDispositionAssessmentMapper dispositionAssessmentMapper;
    private final OrderAfterSaleQueryApi orderQueryApi;
    private final PaymentRefundQueryApi paymentQueryApi;
    private final ForwardFulfillmentAfterSaleQueryApi forwardFulfillmentQueryApi;
    private final ReturnFulfillmentCommandApi returnCommandApi;
    private final ReturnFulfillmentQueryApi returnQueryApi;
    private final AfterSaleEventService eventService;
    private final AfterSaleResolutionCheckpointService checkpointService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AfterSaleView execute(AfterSaleCommand command) {
        validateCommon(command);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String hash = DigestUtil.sha256Hex(JsonUtils.toJsonString(List.of(tenantId, command)));
        String token = UUID.randomUUID().toString();
        operationMapper.insertOrResolve(tenantId, command.getIdempotencyKey(), command.getOperation().name(),
                hash, token, now);
        Long operationId = operationMapper.lastInsertId();
        require(operationId != null, "failed to resolve after-sale operation");
        AfterSaleOperationDO operation = operationMapper.selectForUpdate(tenantId, operationId);
        require(operation != null, "after-sale operation disappeared");
        if (!token.equals(operation.getAttemptToken())) {
            require(Objects.equals(hash, operation.getRequestHash()),
                    "idempotency key conflicts with different after-sale payload");
            require(operation.getStatus() == OPERATION_SUCCEEDED && operation.getResultJson() != null,
                    "existing after-sale operation is incomplete");
            AfterSaleView replay = JsonUtils.parseObject(operation.getResultJson(), AfterSaleView.class);
            replay.setDuplicate(true);
            return replay;
        }
        AfterSaleView result = switch (command.getOperation()) {
            case REQUEST -> request(tenantId, operationId, command, now);
            case APPROVE -> approve(tenantId, operationId, command, now);
            case HAND_OVER_RETURN, MARK_RETURN_IN_TRANSIT, RECEIVE_RETURN ->
                    advanceReturn(tenantId, operationId, command, now);
            case ASSESS_DISPOSITION -> assessDisposition(tenantId, operationId, command, now);
            case ACCEPT_INSPECTION -> acceptInspection(tenantId, operationId, command, now);
            case RETRY_RESOLUTION -> retryResolution(tenantId, command, now);
        };
        require(operationMapper.markSucceeded(tenantId, operationId, result.getAfterSaleId(),
                JsonUtils.toJsonString(result), now) == 1, "after-sale operation completion conflict");
        return result;
    }

    @Override
    public AfterSaleView get(String afterSaleId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        requireText(afterSaleId, "afterSaleId", 36);
        AfterSaleCaseDO sale = caseMapper.selectTenant(tenantId, afterSaleId);
        require(sale != null, "after-sale case does not exist");
        return view(null, sale, requireSingleItem(tenantId, afterSaleId), false);
    }

    @Override
    public AfterSaleView getByOrderItem(String orderId, String orderItemId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        requireText(orderId, "orderId", 36);
        requireText(orderItemId, "orderItemId", 36);
        AfterSaleCaseDO sale = caseMapper.selectByOrderItem(tenantId, orderId, orderItemId);
        require(sale != null, "after-sale case does not exist for order item");
        return view(null, sale, requireSingleItem(tenantId, sale.getAfterSaleId()), false);
    }

    @Override
    public AppAfterSalePageView listOwned(String buyerPrincipalId, int pageNo, int pageSize) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        requireText(buyerPrincipalId, "buyerPrincipalId", 128);
        require(pageNo >= 1, "pageNo must be at least 1");
        require(pageSize >= 1 && pageSize <= 50, "pageSize must be between 1 and 50");
        long total = caseMapper.countByBuyer(tenantId, buyerPrincipalId);
        List<AfterSaleView> list = total == 0 ? List.of()
                : caseMapper.selectByBuyer(tenantId, buyerPrincipalId, (long) (pageNo - 1) * pageSize, pageSize)
                .stream()
                .map(sale -> view(null, sale, requireSingleItem(tenantId, sale.getAfterSaleId()), false))
                .toList();
        return AppAfterSalePageView.builder().list(list).total(total).pageNo(pageNo).pageSize(pageSize).build();
    }

    private AfterSaleView request(Long tenantId, Long operationId, AfterSaleCommand command, LocalDateTime now) {
        require(command.getAfterSaleId() == null && command.getExpectedVersion() == null,
                "REQUEST does not accept aggregate identity or version");
        requireText(command.getOrderId(), "orderId", 36);
        requireText(command.getOrderItemId(), "orderItemId", 36);
        require("RETURN_AND_REFUND".equals(command.getAfterSaleType()),
                "first slice supports RETURN_AND_REFUND only");
        require("SIZE_NOT_FIT".equals(command.getReasonCode()),
                "first slice supports SIZE_NOT_FIT reason only");
        require("BUYER".equals(command.getResponsibility()),
                "first slice supports BUYER responsibility only");
        requireText(command.getReason(), "reason", 256);
        require(caseMapper.selectActiveByOrderItem(tenantId, command.getOrderId(), command.getOrderItemId()) == null,
                "order item already owns an active after-sale case");
        OrderAfterSaleView order = orderQueryApi.requireEligible(command.getOrderId(), command.getOrderItemId(),
                command.getRequestedQuantity());
        PaymentRefundView payment = paymentQueryApi.requireRefundable(order.getOrderId(), order.getPaymentId(),
                order.getNetAmountMinor(), order.getCurrencyCode());
        ForwardFulfillmentAfterSaleView forward = forwardFulfillmentQueryApi.requireDelivered(order.getOrderId(),
                order.getFulfillmentId(), order.getShipmentId(), order.getOrderItemId());
        require(Objects.equals(order.getCanonicalSkuId(), forward.getCanonicalSkuId())
                        && order.getOrderedQuantity().compareTo(forward.getQuantity()) == 0
                        && order.getQuantity().compareTo(forward.getQuantity()) <= 0,
                "forward fulfillment line does not reconcile with order item");
        String id = UUID.randomUUID().toString();
        AfterSaleCaseDO sale = new AfterSaleCaseDO().setAfterSaleId(id).setTenantId(tenantId)
                .setAfterSaleNo("CMAS" + compact(id)).setRunId(command.getRunId())
                .setOrderId(order.getOrderId()).setOrderNo(order.getOrderNo()).setBuyerId(order.getBuyerId())
                .setOrderVersionAtRequest(order.getAggregateVersion()).setPaymentId(payment.getPaymentId())
                .setPaymentVersionAtRequest(payment.getAggregateVersion())
                .setForwardFulfillmentId(forward.getFulfillmentId()).setForwardShipmentId(forward.getShipmentId())
                .setOwnerId(forward.getOwnerId()).setWarehouseId(forward.getWarehouseId())
                .setUomCode(forward.getUomCode()).setStatus("REQUESTED").setRefundStatus("NOT_REQUESTED")
                .setCurrencyCode(order.getCurrencyCode()).setAfterSaleType(command.getAfterSaleType())
                .setReasonCode(command.getReasonCode()).setResponsibility(command.getResponsibility())
                .setReason(command.getReason()).setVersion(1L).setCorrelationId(command.getCorrelationId())
                .setCausationId(command.getCausationId()).setCreatedAt(now).setUpdatedAt(now);
        AfterSaleItemDO item = new AfterSaleItemDO().setAfterSaleItemId(UUID.randomUUID().toString())
                .setTenantId(tenantId).setAfterSaleId(id).setOrderItemId(order.getOrderItemId())
                .setCanonicalSkuId(order.getCanonicalSkuId()).setQuantity(order.getQuantity())
                .setLineAmountMinor(order.getLineAmountMinor())
                .setDiscountAmountMinor(order.getDiscountAmountMinor()).setNetAmountMinor(order.getNetAmountMinor())
                .setListingId(order.getListingId())
                .setListingOfferId(order.getListingOfferId()).setActiveGuard(1).setCreatedAt(now);
        caseMapper.insert(sale);
        itemMapper.insert(item);
        eventService.appendCase(operationId, sale, item, null, command.getOccurredAt(), now);
        return view(operationId, sale, item, false);
    }

    private AfterSaleView approve(Long tenantId, Long operationId, AfterSaleCommand command, LocalDateTime now) {
        AfterSaleCaseDO sale = requireCase(tenantId, command, "REQUESTED");
        requireText(command.getReviewerId(), "reviewerId", 128);
        AfterSaleItemDO item = requireSingleItem(tenantId, sale.getAfterSaleId());
        OrderAfterSaleView order = orderQueryApi.requireEligible(sale.getOrderId(), item.getOrderItemId(),
                item.getQuantity());
        require(Objects.equals(order.getLineAmountMinor(), item.getLineAmountMinor())
                        && Objects.equals(order.getDiscountAmountMinor(), item.getDiscountAmountMinor())
                        && Objects.equals(order.getNetAmountMinor(), item.getNetAmountMinor()),
                "after-sale item money changed between request and approval");
        paymentQueryApi.requireRefundable(order.getOrderId(), sale.getPaymentId(), order.getNetAmountMinor(),
                sale.getCurrencyCode());
        ForwardFulfillmentAfterSaleView forward = forwardFulfillmentQueryApi.requireDelivered(sale.getOrderId(),
                sale.getForwardFulfillmentId(), sale.getForwardShipmentId(), item.getOrderItemId());
        ReturnFulfillmentView returned = returnCommandApi.execute(ReturnFulfillmentCommand.builder()
                .operation(ReturnFulfillmentOperation.CREATE)
                .idempotencyKey("after-sale:" + sale.getAfterSaleId() + ":return-create")
                .runId(sale.getRunId()).afterSaleId(sale.getAfterSaleId())
                .afterSaleItemId(item.getAfterSaleItemId()).orderId(sale.getOrderId())
                .orderItemId(item.getOrderItemId()).canonicalSkuId(item.getCanonicalSkuId())
                .quantity(item.getQuantity()).ownerId(forward.getOwnerId()).warehouseId(forward.getWarehouseId())
                .uomCode(forward.getUomCode()).correlationId(sale.getCorrelationId())
                .causationId(sale.getCausationId()).occurredAt(command.getOccurredAt()).build());
        require(caseMapper.approve(tenantId, sale.getAfterSaleId(), sale.getVersion(), order.getNetAmountMinor(),
                command.getReviewerId(), returned.getReturnFulfillmentId(), now) == 1,
                "after-sale approval conflict");
        String previous = sale.getStatus();
        sale.setStatus("APPROVED").setRefundStatus("REQUESTED")
                .setApprovedAmountMinor(order.getNetAmountMinor()).setReviewerId(command.getReviewerId())
                .setReturnFulfillmentId(returned.getReturnFulfillmentId())
                .setVersion(sale.getVersion() + 1).setUpdatedAt(now);
        eventService.appendRefund(sale, item, "NOT_REQUESTED", "REQUESTED", 1L, null,
                command.getOccurredAt(), now);
        eventService.appendCase(operationId, sale, item, previous, command.getOccurredAt(), now);
        return view(operationId, sale, item, false);
    }

    private AfterSaleView advanceReturn(Long tenantId, Long operationId, AfterSaleCommand command,
                                        LocalDateTime now) {
        AfterSaleCaseDO sale = requireCase(tenantId, command, "APPROVED");
        ReturnFulfillmentView current = returnQueryApi.getForAfterSale(sale.getAfterSaleId(),
                sale.getReturnFulfillmentId());
        ReturnFulfillmentOperation operation = switch (command.getOperation()) {
            case HAND_OVER_RETURN -> ReturnFulfillmentOperation.HAND_OVER;
            case MARK_RETURN_IN_TRANSIT -> ReturnFulfillmentOperation.MARK_IN_TRANSIT;
            case RECEIVE_RETURN -> ReturnFulfillmentOperation.RECEIVE;
            default -> throw new IllegalArgumentException("not a return logistics command");
        };
        String operator = command.getOperation() == AfterSaleOperation.RECEIVE_RETURN
                ? command.getReceiverId() : null;
        ReturnFulfillmentView result = returnCommandApi.execute(ReturnFulfillmentCommand.builder()
                .operation(operation).idempotencyKey("after-sale:" + sale.getAfterSaleId() + ":return:"
                        + operation.name().toLowerCase(Locale.ROOT))
                .runId(sale.getRunId()).returnFulfillmentId(sale.getReturnFulfillmentId())
                .expectedVersion(current.getAggregateVersion()).carrierCode(command.getCarrierCode())
                .waybillNo(command.getWaybillNo()).operatorId(operator).correlationId(sale.getCorrelationId())
                .causationId(sale.getCausationId()).occurredAt(command.getOccurredAt()).build());
        return view(operationId, sale, requireSingleItem(tenantId, sale.getAfterSaleId()), result, null, false);
    }

    private AfterSaleView acceptInspection(Long tenantId, Long operationId, AfterSaleCommand command,
                                           LocalDateTime now) {
        AfterSaleCaseDO sale = requireCase(tenantId, command, "APPROVED");
        ReturnDispositionAssessmentDO assessment =
                dispositionAssessmentMapper.selectByAfterSale(tenantId, sale.getAfterSaleId());
        require(assessment != null, "return disposition assessment is required before inspection");
        require(Objects.equals(command.getDispositionAssessmentId(), assessment.getAssessmentId()),
                "dispositionAssessmentId does not match the governed assessment");
        require(!"MANUAL_REVIEW".equals(assessment.getRecommendedDisposition()),
                "ambiguous return disposition requires manual review");
        require(Objects.equals(command.getDispositionCode(), assessment.getRecommendedDisposition()),
                "inspection disposition does not match the governed assessment");
        require(Objects.equals(command.getQualityStatus(), assessment.getQualityStatus()),
                "inspection qualityStatus does not match the governed assessment");
        requireText(command.getInspectorId(), "inspectorId", 128);
        require(!Objects.equals(command.getInspectorId(), assessment.getAssessorId()),
                "disposition assessor and inspection confirmer must be different");
        ReturnFulfillmentView current = returnQueryApi.getForAfterSale(sale.getAfterSaleId(),
                sale.getReturnFulfillmentId());
        ReturnFulfillmentView returned = returnCommandApi.execute(ReturnFulfillmentCommand.builder()
                .operation(ReturnFulfillmentOperation.ACCEPT_INSPECTION)
                .idempotencyKey("after-sale:" + sale.getAfterSaleId() + ":return:accept-inspection")
                .runId(sale.getRunId()).returnFulfillmentId(sale.getReturnFulfillmentId())
                .expectedVersion(current.getAggregateVersion()).operatorId(command.getInspectorId())
                .qualityStatus(command.getQualityStatus())
                .dispositionAssessmentId(assessment.getAssessmentId())
                .dispositionCode(assessment.getRecommendedDisposition())
                .conditionGrade(assessment.getConditionGrade())
                .inspectionEvidenceRef(assessment.getInspectionEvidenceRef())
                .correlationId(sale.getCorrelationId())
                .causationId(sale.getCausationId()).occurredAt(command.getOccurredAt()).build());
        require("INSPECTION_ACCEPTED".equals(returned.getCurrentStatus()), "return inspection is not accepted");
        AfterSaleItemDO item = requireSingleItem(tenantId, sale.getAfterSaleId());
        long downstreamOffset = "SCRAP".equals(assessment.getRecommendedDisposition()) ? 1 : 0;
        String sagaId = UUID.randomUUID().toString();
        AfterSaleResolutionSagaDO saga = new AfterSaleResolutionSagaDO().setSagaId(sagaId).setTenantId(tenantId)
                .setAfterSaleId(sale.getAfterSaleId()).setAfterSaleItemId(item.getAfterSaleItemId())
                .setRunId(sale.getRunId()).setOrderId(sale.getOrderId()).setOrderNo(sale.getOrderNo())
                .setOrderItemId(item.getOrderItemId()).setOrderVersionAtRequest(sale.getOrderVersionAtRequest())
                .setOrderVersion(sale.getOrderVersionAtRequest())
                .setPaymentId(sale.getPaymentId()).setPaymentVersionAtRequest(sale.getPaymentVersionAtRequest())
                .setReturnFulfillmentId(returned.getReturnFulfillmentId())
                .setReturnShipmentId(returned.getReturnShipmentId()).setInspectionId(returned.getInspectionId())
                .setDispositionAssessmentId(assessment.getAssessmentId())
                .setDispositionCode(assessment.getRecommendedDisposition())
                .setReturnStockStatus("RESTOCK".equals(assessment.getRecommendedDisposition())
                        ? "SELLABLE" : "NON_SELLABLE")
                .setReturnQualityStatus(assessment.getQualityStatus())
                .setCanonicalSkuId(item.getCanonicalSkuId()).setQuantity(item.getQuantity())
                .setOwnerId(sale.getOwnerId()).setWarehouseId(sale.getWarehouseId()).setUomCode(sale.getUomCode())
                .setApprovedAmountMinor(sale.getApprovedAmountMinor())
                .setGrossAmountMinor(item.getLineAmountMinor()).setBenefitAmountMinor(item.getDiscountAmountMinor())
                .setNetAmountMinor(item.getNetAmountMinor())
                .setBenefitReversalStatus(item.getDiscountAmountMinor() == 0 ? "NOT_REQUIRED" : "PENDING")
                .setBenefitReversalAmountMinor(0L).setCurrencyCode(sale.getCurrencyCode())
                .setReason(sale.getReason())
                .setStatus("REQUESTED").setActiveStep("RETURN_INVENTORY").setAttemptCount(0).setMaxAttempts(8)
                .setVersion(1L).setCorrelationId(sale.getCorrelationId()).setCausationId(sale.getCausationId())
                .setInventoryOccurredAt(at(command.getOccurredAt(), 1))
                .setBenefitReversalOccurredAt(item.getDiscountAmountMinor() == 0 ? null
                        : at(command.getOccurredAt(), 2 + downstreamOffset))
                .setPaymentOccurredAt(at(command.getOccurredAt(), 3 + downstreamOffset))
                .setOrderRefundOccurredAt(at(command.getOccurredAt(), 4 + downstreamOffset))
                .setOrderReturnOccurredAt(at(command.getOccurredAt(), 5 + downstreamOffset))
                .setCreatedAt(now).setUpdatedAt(now);
        sagaMapper.insert(saga);
        require(caseMapper.startResolution(tenantId, sale.getAfterSaleId(), sale.getVersion(),
                returned.getReturnShipmentId(), returned.getInspectionId(), sagaId, now) == 1,
                "after-sale resolution start conflict");
        String previous = sale.getStatus();
        sale.setStatus("RESOLUTION_PENDING").setReturnShipmentId(returned.getReturnShipmentId())
                .setInspectionId(returned.getInspectionId()).setResolutionSagaId(sagaId)
                .setVersion(sale.getVersion() + 1).setUpdatedAt(now);
        eventService.appendCase(operationId, sale, item, previous, command.getOccurredAt(), now);
        eventService.appendSaga(saga, null, now);
        return view(operationId, sale, item, returned, saga, false);
    }

    private AfterSaleView assessDisposition(Long tenantId, Long operationId, AfterSaleCommand command,
                                            LocalDateTime now) {
        AfterSaleCaseDO sale = requireCase(tenantId, command, "APPROVED");
        ReturnFulfillmentView returned = returnQueryApi.getForAfterSale(sale.getAfterSaleId(),
                sale.getReturnFulfillmentId());
        require("RECEIVED".equals(returned.getCurrentStatus()),
                "return disposition assessment requires warehouse receipt");
        require(dispositionAssessmentMapper.selectByAfterSale(tenantId, sale.getAfterSaleId()) == null,
                "return disposition assessment already exists");
        requireText(command.getAssessorId(), "assessorId", 128);
        int packaging = score(command.getPackagingScore(), "packagingScore");
        int appearance = score(command.getAppearanceScore(), "appearanceScore");
        int function = score(command.getFunctionScore(), "functionScore");
        require(command.getSafetyRisk() != null, "safetyRisk is required");
        require(command.getCounterfeitRisk() != null, "counterfeitRisk is required");
        require(command.getEstimatedResaleValueMinor() != null
                        && command.getEstimatedResaleValueMinor() > 0,
                "estimatedResaleValueMinor must be positive");
        require(command.getEstimatedRecoveryCostMinor() != null
                        && command.getEstimatedRecoveryCostMinor() >= 0,
                "estimatedRecoveryCostMinor must be nonnegative");
        requireText(command.getInspectionEvidenceRef(), "inspectionEvidenceRef", 256);

        boolean hardScrap = Boolean.TRUE.equals(command.getSafetyRisk())
                || Boolean.TRUE.equals(command.getCounterfeitRisk())
                || function < 50
                || command.getEstimatedRecoveryCostMinor() >= command.getEstimatedResaleValueMinor();
        boolean directRestock = packaging >= 80 && appearance >= 85 && function >= 90
                && command.getEstimatedRecoveryCostMinor()
                <= command.getEstimatedResaleValueMinor() / 20;
        String recommendation = hardScrap ? "SCRAP" : directRestock ? "RESTOCK" : "MANUAL_REVIEW";
        String qualityStatus = "RESTOCK".equals(recommendation) ? "QUALIFIED" : "DAMAGED";
        String grade = "RESTOCK".equals(recommendation) ? "A"
                : "SCRAP".equals(recommendation) ? "D" : "C";
        int confidence = hardScrap ? 96 : directRestock ? 92 : 60;
        String rationale = Boolean.TRUE.equals(command.getSafetyRisk()) ? "SAFETY_RISK"
                : Boolean.TRUE.equals(command.getCounterfeitRisk()) ? "COUNTERFEIT_RISK"
                : function < 50 ? "FUNCTION_FAILURE"
                : command.getEstimatedRecoveryCostMinor() >= command.getEstimatedResaleValueMinor()
                ? "RECOVERY_COST_EXCEEDS_VALUE"
                : directRestock ? "QUALIFIED_LOW_RECOVERY_COST" : "AMBIGUOUS_CONDITION";
        ReturnDispositionAssessmentDO assessment = new ReturnDispositionAssessmentDO()
                .setAssessmentId(UUID.randomUUID().toString()).setTenantId(tenantId)
                .setAfterSaleId(sale.getAfterSaleId())
                .setReturnFulfillmentId(sale.getReturnFulfillmentId())
                .setAssessorId(command.getAssessorId()).setPackagingScore(packaging)
                .setAppearanceScore(appearance).setFunctionScore(function)
                .setSafetyRisk(command.getSafetyRisk()).setCounterfeitRisk(command.getCounterfeitRisk())
                .setEstimatedResaleValueMinor(command.getEstimatedResaleValueMinor())
                .setEstimatedRecoveryCostMinor(command.getEstimatedRecoveryCostMinor())
                .setInspectionEvidenceRef(command.getInspectionEvidenceRef())
                .setRecommendedDisposition(recommendation).setQualityStatus(qualityStatus)
                .setConditionGrade(grade).setConfidenceScore(confidence)
                .setRationaleCode(rationale).setCreatedAt(now);
        dispositionAssessmentMapper.insert(assessment);
        eventService.appendDispositionAssessment(assessment, sale, command.getOccurredAt(), now);
        return view(operationId, sale, requireSingleItem(tenantId, sale.getAfterSaleId()),
                returned, null, assessment, false);
    }

    private AfterSaleView retryResolution(Long tenantId, AfterSaleCommand command, LocalDateTime now) {
        AfterSaleCaseDO sale = requireCase(tenantId, command, "RESOLUTION_PENDING");
        require(command.getResolutionSagaExpectedVersion() != null,
                "resolutionSagaExpectedVersion is required");
        AfterSaleResolutionSagaDO saga = checkpointService.retryManually(tenantId, sale.getResolutionSagaId(),
                command.getResolutionSagaExpectedVersion(), now);
        return view(null, sale, requireSingleItem(tenantId, sale.getAfterSaleId()), null, saga, false);
    }

    private AfterSaleCaseDO requireCase(Long tenantId, AfterSaleCommand command, String status) {
        requireText(command.getAfterSaleId(), "afterSaleId", 36);
        require(command.getExpectedVersion() != null && command.getExpectedVersion() > 0,
                "expectedVersion is required");
        AfterSaleCaseDO sale = caseMapper.selectForUpdate(tenantId, command.getAfterSaleId());
        require(sale != null, "after-sale case does not exist");
        require(Objects.equals(sale.getVersion(), command.getExpectedVersion()), "after-sale version conflict");
        require(status.equals(sale.getStatus()), command.getOperation() + " requires " + status);
        return sale;
    }

    private AfterSaleView view(Long operationId, AfterSaleCaseDO sale, AfterSaleItemDO item, boolean duplicate) {
        ReturnFulfillmentView returned = sale.getReturnFulfillmentId() == null ? null
                : returnQueryApi.getForAfterSale(sale.getAfterSaleId(), sale.getReturnFulfillmentId());
        AfterSaleResolutionSagaDO saga = sale.getResolutionSagaId() == null ? null
                : sagaMapper.selectTenant(sale.getTenantId(), sale.getResolutionSagaId());
        ReturnDispositionAssessmentDO assessment =
                dispositionAssessmentMapper.selectByAfterSale(sale.getTenantId(), sale.getAfterSaleId());
        return view(operationId, sale, item, returned, saga, assessment, duplicate);
    }

    private static AfterSaleView view(Long operationId, AfterSaleCaseDO sale, AfterSaleItemDO item,
                                      ReturnFulfillmentView returned, AfterSaleResolutionSagaDO saga,
                                      boolean duplicate) {
        return view(operationId, sale, item, returned, saga, null, duplicate);
    }

    private static AfterSaleView view(Long operationId, AfterSaleCaseDO sale, AfterSaleItemDO item,
                                      ReturnFulfillmentView returned, AfterSaleResolutionSagaDO saga,
                                      ReturnDispositionAssessmentDO assessment, boolean duplicate) {
        return AfterSaleView.builder().operationId(operationId).afterSaleId(sale.getAfterSaleId())
                .afterSaleNo(sale.getAfterSaleNo()).runId(sale.getRunId()).orderId(sale.getOrderId())
                .afterSaleItemId(item.getAfterSaleItemId()).orderItemId(item.getOrderItemId())
                .canonicalSkuId(item.getCanonicalSkuId()).quantity(item.getQuantity())
                .afterSaleType(sale.getAfterSaleType()).reasonCode(sale.getReasonCode())
                .responsibility(sale.getResponsibility()).caseStatus(sale.getStatus())
                .aggregateVersion(sale.getVersion()).refundStatus(sale.getRefundStatus())
                .approvedAmountMinor(sale.getApprovedAmountMinor()).grossAmountMinor(item.getLineAmountMinor())
                .benefitAmountMinor(item.getDiscountAmountMinor()).netAmountMinor(item.getNetAmountMinor())
                .currencyCode(sale.getCurrencyCode())
                .returnFulfillmentId(sale.getReturnFulfillmentId())
                .returnFulfillmentStatus(returned == null ? null : returned.getCurrentStatus())
                .returnShipmentId(returned == null ? sale.getReturnShipmentId() : returned.getReturnShipmentId())
                .inspectionId(returned == null ? sale.getInspectionId() : returned.getInspectionId())
                .dispositionAssessmentId(assessment == null
                        ? (saga == null ? null : saga.getDispositionAssessmentId()) : assessment.getAssessmentId())
                .recommendedDisposition(assessment == null
                        ? (saga == null ? null : saga.getDispositionCode())
                        : assessment.getRecommendedDisposition())
                .recommendedQualityStatus(assessment == null
                        ? (saga == null ? null : saga.getReturnQualityStatus())
                        : assessment.getQualityStatus())
                .dispositionCode(returned == null
                        ? (saga == null ? null : saga.getDispositionCode()) : returned.getDispositionCode())
                .conditionGrade(assessment == null
                        ? (returned == null ? null : returned.getConditionGrade()) : assessment.getConditionGrade())
                .dispositionConfidence(assessment == null ? null : assessment.getConfidenceScore())
                .dispositionRationaleCode(assessment == null ? null : assessment.getRationaleCode())
                .resolutionSagaId(saga == null ? sale.getResolutionSagaId() : saga.getSagaId())
                .resolutionSagaStatus(saga == null ? null : saga.getStatus())
                .resolutionSagaVersion(saga == null ? null : saga.getVersion())
                .paymentRefundTransactionId(saga == null ? null : saga.getPaymentRefundTransactionId())
                .inventoryOperationId(saga == null ? null : saga.getInventoryOperationId())
                .inventoryLedgerTransactionId(saga == null ? null : saga.getInventoryLedgerTransactionId())
                .disposalOperationId(saga == null ? null : saga.getDisposalOperationId())
                .disposalLedgerTransactionId(saga == null ? null : saga.getDisposalLedgerTransactionId())
                .orderSettlementEffectId(saga == null ? null : saga.getOrderSettlementEffectId())
                .orderSettlementVersion(saga == null ? null : saga.getOrderSettlementVersion())
                .orderReturnFull(saga == null ? null : saga.getOrderReturnFull())
                .benefitReversalStatus(saga == null
                        ? (item.getDiscountAmountMinor() == 0 ? "NOT_REQUIRED" : "PENDING")
                        : saga.getBenefitReversalStatus())
                .benefitReversalBatchId(saga == null ? null : saga.getBenefitReversalBatchId())
                .benefitReversalAmountMinor(saga == null ? 0L : saga.getBenefitReversalAmountMinor())
                .duplicate(duplicate).build();
    }

    private AfterSaleItemDO requireSingleItem(Long tenantId, String afterSaleId) {
        List<AfterSaleItemDO> items = itemMapper.selectByAfterSale(tenantId, afterSaleId);
        require(items.size() == 1, "first slice requires exactly one after-sale item");
        return items.get(0);
    }

    private static LocalDateTime at(Instant instant, long seconds) {
        return LocalDateTime.ofInstant(instant.plusSeconds(seconds), ZoneOffset.UTC);
    }

    private static int score(Integer value, String field) {
        require(value != null && value >= 0 && value <= 100,
                field + " must be between 0 and 100");
        return value;
    }

    private static String compact(String uuid) {
        return uuid.replace("-", "").substring(0, 20).toUpperCase(Locale.ROOT);
    }

    private static void validateCommon(AfterSaleCommand command) {
        require(command != null && command.getOperation() != null, "after-sale operation is required");
        requireText(command.getIdempotencyKey(), "idempotencyKey", 128);
        require(command.getIdempotencyKey().length() >= 8, "idempotencyKey is too short");
        requireText(command.getRunId(), "runId", 64);
        requireUuid(command.getCorrelationId(), "correlationId");
        if (command.getCausationId() != null) requireUuid(command.getCausationId(), "causationId");
        require(command.getOccurredAt() != null, "occurredAt is required");
    }

    private static void requireUuid(String value, String field) {
        requireText(value, field, 36);
        try { UUID.fromString(value); } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(field + " must be a UUID", e);
        }
    }

    private static void requireText(String value, String field, int max) {
        require(value != null && !value.isBlank() && value.length() <= max, field + " is required");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
