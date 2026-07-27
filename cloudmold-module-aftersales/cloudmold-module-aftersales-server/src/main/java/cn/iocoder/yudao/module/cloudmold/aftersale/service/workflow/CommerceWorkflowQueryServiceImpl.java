package cn.iocoder.yudao.module.cloudmold.aftersale.service.workflow;

import cn.iocoder.yudao.module.cloudmold.aftersale.api.AfterSaleQueryApi;
import cn.iocoder.yudao.module.cloudmold.aftersale.api.AfterSaleView;
import cn.iocoder.yudao.module.cloudmold.fulfillment.api.AppFulfillmentQueryApi;
import cn.iocoder.yudao.module.cloudmold.fulfillment.api.AppFulfillmentView;
import cn.iocoder.yudao.module.cloudmold.fulfillment.api.exception.FulfillmentExceptionQueryApi;
import cn.iocoder.yudao.module.cloudmold.fulfillment.api.exception.FulfillmentExceptionView;
import cn.iocoder.yudao.module.cloudmold.order.api.cancellation.OrderCancellationSagaQueryApi;
import cn.iocoder.yudao.module.cloudmold.order.api.cancellation.OrderCancellationSagaView;
import cn.iocoder.yudao.module.cloudmold.order.api.workflow.*;
import cn.iocoder.yudao.module.cloudmold.payment.api.PaymentWorkflowFactsApi;
import cn.iocoder.yudao.module.cloudmold.payment.api.PaymentWorkflowFactsView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Read-only composition over canonical domain authorities. Temporal owns durable
 * orchestration; existing domain command/Saga APIs remain the only write surface.
 */
@Service
@RequiredArgsConstructor
public class CommerceWorkflowQueryServiceImpl implements CommerceWorkflowQueryPort {

    private static final String ORDER_TO_CASH = "OrderToCash";
    private static final String ORDER_CANCELLATION = "OrderCancellation";
    private static final String FULFILLMENT_EXCEPTION = "FulfillmentException";
    private static final String RETURN_REFUND = "ReturnRefund";

    private final OrderWorkflowFactsApi orderQuery;
    private final PaymentWorkflowFactsApi paymentQuery;
    private final AppFulfillmentQueryApi fulfillmentQuery;
    private final FulfillmentExceptionQueryApi fulfillmentExceptionQuery;
    private final OrderCancellationSagaQueryApi cancellationQuery;
    private final AfterSaleQueryApi afterSaleQuery;

    @Override
    public CommerceWorkflowResult inspectOrderToCash(String orderId) {
        OrderWorkflowFactsView order = orderQuery.get(required(orderId, "orderId"));
        List<CommerceWorkflowArtifact> artifacts = new ArrayList<>();
        artifacts.add(artifact("ORDER", order.getOrderId(), order.getStatus(),
                order.getAggregateVersion(), "订单 " + order.getOrderNo()));

        PaymentWorkflowFactsView payment = null;
        if (StringUtils.hasText(order.getPaymentId())) {
            payment = paymentQuery.get(order.getOrderId(), order.getPaymentId());
            artifacts.add(artifact("PAYMENT", payment.getPaymentId(), payment.getStatus(),
                    payment.getAggregateVersion(), "支付 " + payment.getCapturedAmountMinor() + " "
                            + payment.getCurrencyCode() + " 分"));
        }
        AppFulfillmentView fulfillment = fulfillmentQuery.getByOrder(order.getOrderId());
        if (fulfillment != null) {
            require(Objects.equals(order.getFulfillmentId(), fulfillment.getFulfillmentId()),
                    "Order and Fulfillment references do not reconcile");
            artifacts.add(artifact("FULFILLMENT", fulfillment.getFulfillmentId(), fulfillment.getStatus(),
                    fulfillment.getAggregateVersion(), "履约单 " + fulfillment.getFulfillmentNo()));
            addReference(artifacts, "SHIPMENT", fulfillment.getShipmentId(), fulfillment.getStatus(), null,
                    "正向包裹");
            addReference(artifacts, "WAYBILL", fulfillment.getWaybillNo(), fulfillment.getStatus(), null,
                    fulfillment.getCarrierCode());
        } else {
            require(!StringUtils.hasText(order.getFulfillmentId()),
                    "Order references a missing canonical Fulfillment");
        }

        CommerceWorkflowStatus status;
        String phase;
        boolean terminal;
        boolean actionRequired;
        List<String> blockers = List.of();
        String summary;
        switch (order.getStatus()) {
            case "PLACED" -> {
                status = CommerceWorkflowStatus.WAITING;
                phase = "WAITING_INVENTORY_RESERVATION";
                terminal = false;
                actionRequired = true;
                summary = "订单已创建，等待规范库存预占。";
            }
            case "INVENTORY_RESERVED" -> {
                status = CommerceWorkflowStatus.WAITING;
                phase = "WAITING_PAYMENT_CAPTURE";
                terminal = false;
                actionRequired = true;
                summary = "库存已预占，等待支付确认。";
            }
            case "PAYMENT_CONFIRMED" -> {
                status = CommerceWorkflowStatus.RUNNING;
                phase = "WAITING_FULFILLMENT";
                terminal = false;
                actionRequired = true;
                summary = "支付已确认，等待履约发货。";
            }
            case "SHIPPED" -> {
                status = CommerceWorkflowStatus.RUNNING;
                phase = "WAITING_DELIVERY";
                terminal = false;
                actionRequired = false;
                summary = "订单已发货，等待签收。";
            }
            case "COMPLETED" -> {
                status = CommerceWorkflowStatus.SUCCEEDED;
                phase = "CASH_REALIZED";
                terminal = true;
                actionRequired = false;
                summary = "订单已完成，支付与履约产物已形成。";
            }
            case "CANCELLATION_PENDING" -> {
                status = CommerceWorkflowStatus.RUNNING;
                phase = "CANCELLATION_IN_PROGRESS";
                terminal = false;
                actionRequired = false;
                summary = "订单已进入持久化取消补偿，不再继续正向履约。";
            }
            case "CANCELLED", "REFUNDED", "RETURNED" -> {
                status = CommerceWorkflowStatus.COMPENSATED;
                phase = "COMMERCIAL_EFFECT_REVERSED";
                terminal = true;
                actionRequired = false;
                summary = "订单的正向交易效果已取消或冲回。";
            }
            default -> {
                status = CommerceWorkflowStatus.PREPARE;
                phase = "UNSUPPORTED_ORDER_STATE";
                terminal = false;
                actionRequired = true;
                blockers = List.of("ORDER_STATE_NOT_MAPPED:" + order.getStatus());
                summary = "订单状态尚未纳入自动编排，保持人工接管。";
            }
        }
        if (payment != null && "PARTIALLY_REFUNDED".equals(payment.getStatus())) {
            status = CommerceWorkflowStatus.RUNNING;
            phase = "PARTIAL_REFUND_IN_PROGRESS";
            terminal = false;
            actionRequired = false;
            summary = "订单已发生部分退款，等待售后结算继续推进。";
        }
        return result(ORDER_TO_CASH, order.getOrderId(), status, phase, terminal, actionRequired,
                summary, order.getAggregateVersion(), blockers, artifacts);
    }

    @Override
    public CommerceWorkflowResult inspectOrderCancellation(String cancellationSagaId) {
        OrderCancellationSagaView saga = cancellationQuery.get(required(cancellationSagaId,
                "cancellationSagaId"));
        List<CommerceWorkflowArtifact> artifacts = new ArrayList<>();
        artifacts.add(artifact("CANCELLATION_SAGA", saga.getSagaId(), saga.getStatus(),
                saga.getAggregateVersion(), "订单取消补偿"));
        artifacts.add(artifact("ORDER", saga.getOrderId(), saga.getOrderStatusAtRequest(),
                saga.getOrderVersionAtRequest(), "订单 " + saga.getOrderNo()));
        addReference(artifacts, "PAYMENT", saga.getPaymentId(), saga.getPaymentStatus(), null, "退款支付");
        addReference(artifacts, "PAYMENT_REFUND_TRANSACTION",
                saga.getPaymentRefundTransactionId() == null ? null
                        : saga.getPaymentRefundTransactionId().toString(),
                saga.getPaymentStatus(), null, "退款交易");
        addReference(artifacts, "FULFILLMENT", saga.getFulfillmentId(), saga.getFulfillmentStatus(),
                null, "已取消履约");

        CommerceWorkflowStatus status;
        boolean terminal;
        boolean actionRequired;
        if ("COMPLETED".equals(saga.getStatus())) {
            status = CommerceWorkflowStatus.SUCCEEDED;
            terminal = true;
            actionRequired = false;
        } else if ("MANUAL_REVIEW".equals(saga.getStatus())) {
            status = CommerceWorkflowStatus.MANUAL_REVIEW;
            terminal = false;
            actionRequired = true;
        } else if ("RETRY_SCHEDULED".equals(saga.getStatus())) {
            status = CommerceWorkflowStatus.WAITING;
            terminal = false;
            actionRequired = false;
        } else {
            status = CommerceWorkflowStatus.RUNNING;
            terminal = false;
            actionRequired = false;
        }
        List<String> blockers = "MANUAL_REVIEW".equals(saga.getStatus())
                ? List.of("CANCELLATION_MANUAL_REVIEW:" + Objects.toString(saga.getLastErrorCode(), "UNKNOWN"))
                : List.of();
        return result(ORDER_CANCELLATION, saga.getSagaId(), status,
                Objects.toString(saga.getActiveStep(), saga.getStatus()), terminal, actionRequired,
                cancellationSummary(saga), saga.getAggregateVersion(), blockers, artifacts);
    }

    @Override
    public CommerceWorkflowResult inspectFulfillmentException(String orderId) {
        String key = required(orderId, "orderId");
        AppFulfillmentView fulfillment = fulfillmentQuery.getByOrder(key);
        if (fulfillment == null) {
            return result(FULFILLMENT_EXCEPTION, key, CommerceWorkflowStatus.PREPARE,
                    "WAITING_FULFILLMENT_FACT", false, true,
                    "尚未形成规范履约单，不能启动异常处置。",
                    null, List.of("FULFILLMENT_NOT_CREATED"), List.of());
        }
        List<CommerceWorkflowArtifact> artifacts = new ArrayList<>();
        artifacts.add(artifact("FULFILLMENT", fulfillment.getFulfillmentId(), fulfillment.getStatus(),
                fulfillment.getAggregateVersion(), "履约单 " + fulfillment.getFulfillmentNo()));
        addReference(artifacts, "SHIPMENT", fulfillment.getShipmentId(), fulfillment.getStatus(), null,
                "正向包裹");
        addReference(artifacts, "WAYBILL", fulfillment.getWaybillNo(), fulfillment.getStatus(), null,
                fulfillment.getCarrierCode());
        FulfillmentExceptionView exception = fulfillmentExceptionQuery.getLatestByOrder(key);
        if (exception == null && "DELIVERED".equals(fulfillment.getStatus())) {
            return result(FULFILLMENT_EXCEPTION, key, CommerceWorkflowStatus.SUCCEEDED,
                    "NO_ACTIVE_EXCEPTION", true, false,
                    "履约已完成，当前无需异常处置。", fulfillment.getAggregateVersion(),
                    List.of(), artifacts);
        }
        if (exception == null && "CANCELLED".equals(fulfillment.getStatus())) {
            return result(FULFILLMENT_EXCEPTION, key, CommerceWorkflowStatus.COMPENSATED,
                    "FULFILLMENT_CANCELLED", true, false,
                    "履约已由订单取消流程补偿关闭。", fulfillment.getAggregateVersion(),
                    List.of(), artifacts);
        }
        if (exception == null) {
            return result(FULFILLMENT_EXCEPTION, key, CommerceWorkflowStatus.PREPARE,
                    "NO_EXCEPTION_RECORDED", false, true,
                    "履约状态已读取，尚未建立异常工单。",
                    fulfillment.getAggregateVersion(), List.of("FULFILLMENT_EXCEPTION_NOT_RECORDED"), artifacts);
        }
        require(Objects.equals(exception.getOrderId(), key)
                        && Objects.equals(exception.getFulfillmentId(), fulfillment.getFulfillmentId()),
                "Fulfillment exception references do not reconcile");
        artifacts.add(artifact("FULFILLMENT_EXCEPTION", exception.getExceptionId(),
                exception.getStatus().name(), exception.getAggregateVersion(),
                exception.getExceptionType().name() + " / "
                        + Objects.toString(exception.getAction(), "待制定处置动作")));
        addReference(artifacts, "DISPOSITION_PLAN_EVIDENCE", exception.getPlanEvidenceRef(),
                exception.getStatus().name(), null, "异常处置计划");
        addReference(artifacts, "BPM_APPROVAL_EVIDENCE", exception.getApprovalRef(),
                exception.getStatus().name(), null, "高风险动作审批");
        addReference(artifacts, "RESOLUTION_EVIDENCE", exception.getResolutionEvidenceRef(),
                exception.getStatus().name(), null, "异常处置结果");

        CommerceWorkflowStatus workflowStatus;
        String phase;
        boolean terminal;
        boolean actionRequired;
        String summary;
        List<String> blockers = List.of();
        switch (exception.getStatus()) {
            case OPEN -> {
                workflowStatus = CommerceWorkflowStatus.WAITING;
                phase = "WAITING_DISPOSITION_PLAN";
                terminal = false;
                actionRequired = true;
                summary = "履约异常已登记，等待生成处置计划与证据。";
            }
            case PLANNED -> {
                workflowStatus = CommerceWorkflowStatus.WAITING;
                phase = "WAITING_APPROVAL_REQUEST";
                terminal = false;
                actionRequired = true;
                summary = "异常处置计划已形成，等待发起 BPM 审批。";
            }
            case WAITING_APPROVAL -> {
                workflowStatus = CommerceWorkflowStatus.WAITING;
                phase = "WAITING_BPM_APPROVAL";
                terminal = false;
                actionRequired = true;
                blockers = List.of("BPM_APPROVAL_REQUIRED:" + exception.getApprovalRef());
                summary = "高风险处置动作正在等待 BPM 审批，未获批前不会执行。";
            }
            case EXECUTING -> {
                workflowStatus = CommerceWorkflowStatus.RUNNING;
                phase = "EXECUTING_" + exception.getAction().name();
                terminal = false;
                actionRequired = false;
                summary = "经审批的履约异常处置动作正在执行。";
            }
            case RESOLVED -> {
                workflowStatus = CommerceWorkflowStatus.SUCCEEDED;
                phase = "EXCEPTION_RESOLVED";
                terminal = true;
                actionRequired = false;
                summary = "履约异常已经处置并形成结果证据。";
            }
            case CLOSED -> {
                workflowStatus = CommerceWorkflowStatus.SUCCEEDED;
                phase = "EXCEPTION_CLOSED";
                terminal = true;
                actionRequired = false;
                summary = "履约异常工单已核验关闭。";
            }
            default -> throw new IllegalArgumentException("unsupported fulfillment exception status");
        }
        return result(FULFILLMENT_EXCEPTION, key, workflowStatus, phase, terminal, actionRequired,
                summary, exception.getAggregateVersion(), blockers, artifacts);
    }

    @Override
    public CommerceWorkflowResult inspectReturnRefund(String afterSaleId) {
        AfterSaleView afterSale = afterSaleQuery.get(required(afterSaleId, "afterSaleId"));
        require(afterSale != null, "canonical after-sale case does not exist");
        List<CommerceWorkflowArtifact> artifacts = new ArrayList<>();
        artifacts.add(artifact("AFTER_SALE", afterSale.getAfterSaleId(), afterSale.getCaseStatus(),
                afterSale.getAggregateVersion(), "售后单 " + afterSale.getAfterSaleNo()));
        addReference(artifacts, "ORDER", afterSale.getOrderId(), null,
                afterSale.getOrderSettlementVersion(), "原订单");
        addReference(artifacts, "RETURN_FULFILLMENT", afterSale.getReturnFulfillmentId(),
                afterSale.getReturnFulfillmentStatus(), null, "退货履约");
        addReference(artifacts, "RETURN_SHIPMENT", afterSale.getReturnShipmentId(),
                afterSale.getReturnFulfillmentStatus(), null, "退货包裹");
        addReference(artifacts, "INSPECTION", afterSale.getInspectionId(),
                afterSale.getReturnFulfillmentStatus(), null, "退货质检");
        addReference(artifacts, "PAYMENT_REFUND_TRANSACTION",
                afterSale.getPaymentRefundTransactionId() == null ? null
                        : afterSale.getPaymentRefundTransactionId().toString(),
                afterSale.getRefundStatus(), null,
                afterSale.getNetAmountMinor() + " " + afterSale.getCurrencyCode() + " 分");
        addReference(artifacts, "INVENTORY_RETURN",
                afterSale.getInventoryLedgerTransactionId() == null ? null
                        : afterSale.getInventoryLedgerTransactionId().toString(),
                afterSale.getCaseStatus(), null, "退货入账");

        CommerceWorkflowStatus status;
        String phase;
        boolean terminal;
        boolean actionRequired;
        List<String> blockers = List.of();
        if ("COMPLETED".equals(afterSale.getCaseStatus())) {
            status = CommerceWorkflowStatus.SUCCEEDED;
            phase = "RETURN_AND_REFUND_COMPLETED";
            terminal = true;
            actionRequired = false;
        } else if ("MANUAL_REVIEW".equals(afterSale.getResolutionSagaStatus())) {
            status = CommerceWorkflowStatus.MANUAL_REVIEW;
            phase = "RESOLUTION_MANUAL_REVIEW";
            terminal = false;
            actionRequired = true;
            blockers = List.of("AFTER_SALE_RESOLUTION_MANUAL_REVIEW");
        } else if ("REQUESTED".equals(afterSale.getCaseStatus())) {
            status = CommerceWorkflowStatus.WAITING;
            phase = "WAITING_REVIEW";
            terminal = false;
            actionRequired = true;
        } else if ("APPROVED".equals(afterSale.getCaseStatus())) {
            status = CommerceWorkflowStatus.WAITING;
            phase = Objects.toString(afterSale.getReturnFulfillmentStatus(), "WAITING_RETURN_HANDOVER");
            terminal = false;
            actionRequired = true;
        } else {
            status = CommerceWorkflowStatus.RUNNING;
            phase = Objects.toString(afterSale.getResolutionSagaStatus(), afterSale.getCaseStatus());
            terminal = false;
            actionRequired = false;
        }
        String summary = status == CommerceWorkflowStatus.SUCCEEDED
                ? "退货已质检入账，退款交易与订单结算产物均已形成。"
                : "售后当前处于 " + phase + "，仅由售后持久化 Saga 继续推进。";
        return result(RETURN_REFUND, afterSale.getAfterSaleId(), status, phase, terminal, actionRequired,
                summary, afterSale.getAggregateVersion(), blockers, artifacts);
    }

    private static CommerceWorkflowResult result(String workflowType, String businessKey,
                                                 CommerceWorkflowStatus status, String phase,
                                                 boolean terminal, boolean actionRequired,
                                                 String summary, Long aggregateVersion,
                                                 List<String> blockers,
                                                 List<CommerceWorkflowArtifact> artifacts) {
        return CommerceWorkflowResult.builder()
                .workflowType(workflowType)
                .workflowInstanceKey(workflowType + ":" + businessKey)
                .businessKey(businessKey)
                .status(status)
                .phase(phase)
                .terminal(terminal)
                .actionRequired(actionRequired)
                .summary(summary)
                .aggregateVersion(aggregateVersion)
                .blockers(List.copyOf(blockers))
                .artifacts(List.copyOf(artifacts))
                .build();
    }

    private static CommerceWorkflowArtifact artifact(String type, String id, String status,
                                                     Long version, String label) {
        return CommerceWorkflowArtifact.builder().type(type).id(id).status(status)
                .version(version).label(label).build();
    }

    private static void addReference(List<CommerceWorkflowArtifact> artifacts, String type, String id,
                                     String status, Long version, String label) {
        if (StringUtils.hasText(id)) {
            artifacts.add(artifact(type, id, status, version, label));
        }
    }

    private static String cancellationSummary(OrderCancellationSagaView saga) {
        if ("COMPLETED".equals(saga.getStatus())) {
            return "订单取消补偿已完成：" + saga.getReleasedReservationCount() + "/"
                    + saga.getExpectedReservationCount() + " 个库存预占已释放，"
                    + saga.getCancelledFulfillmentCount() + "/"
                    + saga.getExpectedFulfillmentCount() + " 个履约单已关闭。";
        }
        if ("MANUAL_REVIEW".equals(saga.getStatus())) {
            return "订单取消停在人工复核，已保留当前补偿检查点和业务产物。";
        }
        return "订单取消正在执行 " + Objects.toString(saga.getActiveStep(), saga.getStatus()) + "。";
    }

    private static String required(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
