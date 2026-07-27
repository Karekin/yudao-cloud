package cn.iocoder.yudao.module.cloudmold.aftersale.service.workflow;

import cn.iocoder.yudao.module.cloudmold.aftersale.api.AfterSaleQueryApi;
import cn.iocoder.yudao.module.cloudmold.aftersale.api.AfterSaleView;
import cn.iocoder.yudao.module.cloudmold.fulfillment.api.AppFulfillmentQueryApi;
import cn.iocoder.yudao.module.cloudmold.fulfillment.api.AppFulfillmentView;
import cn.iocoder.yudao.module.cloudmold.fulfillment.api.exception.*;
import cn.iocoder.yudao.module.cloudmold.order.api.cancellation.OrderCancellationSagaQueryApi;
import cn.iocoder.yudao.module.cloudmold.order.api.cancellation.OrderCancellationSagaView;
import cn.iocoder.yudao.module.cloudmold.order.api.workflow.*;
import cn.iocoder.yudao.module.cloudmold.payment.api.PaymentWorkflowFactsApi;
import cn.iocoder.yudao.module.cloudmold.payment.api.PaymentWorkflowFactsView;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommerceWorkflowQueryServiceImplTest {

    private final OrderWorkflowFactsApi orderQuery = mock(OrderWorkflowFactsApi.class);
    private final PaymentWorkflowFactsApi paymentQuery = mock(PaymentWorkflowFactsApi.class);
    private final AppFulfillmentQueryApi fulfillmentQuery = mock(AppFulfillmentQueryApi.class);
    private final FulfillmentExceptionQueryApi fulfillmentExceptionQuery =
            mock(FulfillmentExceptionQueryApi.class);
    private final OrderCancellationSagaQueryApi cancellationQuery = mock(OrderCancellationSagaQueryApi.class);
    private final AfterSaleQueryApi afterSaleQuery = mock(AfterSaleQueryApi.class);
    private final CommerceWorkflowQueryServiceImpl service = new CommerceWorkflowQueryServiceImpl(
            orderQuery, paymentQuery, fulfillmentQuery, fulfillmentExceptionQuery, cancellationQuery,
            afterSaleQuery);

    @Test
    void shouldExposeCompletedOrderToCashWithRealDomainReferences() {
        when(orderQuery.get("order-1")).thenReturn(OrderWorkflowFactsView.builder()
                .orderId("order-1").orderNo("CM-1").status("COMPLETED").aggregateVersion(5L)
                .payableAmountMinor(39800L).currencyCode("CNY").paymentId("payment-1")
                .fulfillmentId("fulfillment-1").shipmentId("shipment-1").build());
        when(paymentQuery.get("order-1", "payment-1")).thenReturn(PaymentWorkflowFactsView.builder()
                .paymentId("payment-1").orderId("order-1").status("CAPTURED").aggregateVersion(1L)
                .capturedAmountMinor(39800L).refundedAmountMinor(0L).currencyCode("CNY").build());
        when(fulfillmentQuery.getByOrder("order-1")).thenReturn(AppFulfillmentView.builder()
                .fulfillmentId("fulfillment-1").fulfillmentNo("F-1").orderId("order-1")
                .status("DELIVERED").aggregateVersion(4L).shipmentId("shipment-1")
                .carrierCode("SF").waybillNo("SF-1").items(List.of()).trackingEvents(List.of()).build());

        CommerceWorkflowResult result = service.inspectOrderToCash("order-1");

        assertThat(result.getStatus()).isEqualTo(CommerceWorkflowStatus.SUCCEEDED);
        assertThat(result.getPhase()).isEqualTo("CASH_REALIZED");
        assertThat(result.getTerminal()).isTrue();
        assertThat(result.getWorkflowInstanceKey()).isEqualTo("OrderToCash:order-1");
        assertThat(result.getArtifacts()).extracting(CommerceWorkflowArtifact::getType)
                .containsExactly("ORDER", "PAYMENT", "FULFILLMENT", "SHIPMENT", "WAYBILL");
    }

    @Test
    void shouldExposeCancellationManualReviewWithoutClaimingSuccess() {
        when(cancellationQuery.get("saga-1")).thenReturn(OrderCancellationSagaView.builder()
                .sagaId("saga-1").orderId("order-1").orderNo("CM-1")
                .orderStatusAtRequest("PAYMENT_CONFIRMED").orderVersionAtRequest(3L)
                .status("MANUAL_REVIEW").activeStep("REFUND_PAYMENT").aggregateVersion(7L)
                .expectedReservationCount(1).releasedReservationCount(0)
                .expectedFulfillmentCount(1).cancelledFulfillmentCount(1)
                .paymentId("payment-1").paymentStatus("CAPTURED")
                .fulfillmentId("fulfillment-1").fulfillmentStatus("CANCELLED")
                .lastErrorCode("REFUND_UNCERTAIN").build());

        CommerceWorkflowResult result = service.inspectOrderCancellation("saga-1");

        assertThat(result.getStatus()).isEqualTo(CommerceWorkflowStatus.MANUAL_REVIEW);
        assertThat(result.getTerminal()).isFalse();
        assertThat(result.getActionRequired()).isTrue();
        assertThat(result.getBlockers()).containsExactly(
                "CANCELLATION_MANUAL_REVIEW:REFUND_UNCERTAIN");
    }

    @Test
    void shouldExposeRealFulfillmentExceptionAwaitingBpmApproval() {
        when(fulfillmentQuery.getByOrder("order-1")).thenReturn(AppFulfillmentView.builder()
                .fulfillmentId("fulfillment-1").fulfillmentNo("F-1").orderId("order-1")
                .status("IN_TRANSIT").aggregateVersion(3L).shipmentId("shipment-1")
                .carrierCode("SF").waybillNo("SF-1").items(List.of()).trackingEvents(List.of()).build());
        when(fulfillmentExceptionQuery.getLatestByOrder("order-1")).thenReturn(
                FulfillmentExceptionView.builder()
                        .exceptionId("exception-1").exceptionNo("CMX-1").orderId("order-1")
                        .fulfillmentId("fulfillment-1").exceptionType(FulfillmentExceptionType.DELAY)
                        .status(FulfillmentExceptionStatus.WAITING_APPROVAL)
                        .action(FulfillmentExceptionAction.CONTACT_CARRIER)
                        .planEvidenceRef("evidence://plan-1").approvalRef("bpm://approval-1")
                        .aggregateVersion(3L).build());

        CommerceWorkflowResult result = service.inspectFulfillmentException("order-1");

        assertThat(result.getStatus()).isEqualTo(CommerceWorkflowStatus.WAITING);
        assertThat(result.getPhase()).isEqualTo("WAITING_BPM_APPROVAL");
        assertThat(result.getBlockers()).containsExactly("BPM_APPROVAL_REQUIRED:bpm://approval-1");
        assertThat(result.getSummary()).contains("未获批前不会执行");
        assertThat(result.getArtifacts()).extracting(CommerceWorkflowArtifact::getType)
                .contains("FULFILLMENT", "SHIPMENT", "WAYBILL", "FULFILLMENT_EXCEPTION",
                        "DISPOSITION_PLAN_EVIDENCE", "BPM_APPROVAL_EVIDENCE");
    }

    @Test
    void shouldRequestExceptionRegistrationWhenNoCaseExists() {
        when(fulfillmentQuery.getByOrder("order-1")).thenReturn(AppFulfillmentView.builder()
                .fulfillmentId("fulfillment-1").fulfillmentNo("F-1").orderId("order-1")
                .status("IN_TRANSIT").aggregateVersion(3L).shipmentId("shipment-1")
                .items(List.of()).trackingEvents(List.of()).build());

        CommerceWorkflowResult result = service.inspectFulfillmentException("order-1");

        assertThat(result.getStatus()).isEqualTo(CommerceWorkflowStatus.PREPARE);
        assertThat(result.getPhase()).isEqualTo("NO_EXCEPTION_RECORDED");
        assertThat(result.getBlockers()).containsExactly("FULFILLMENT_EXCEPTION_NOT_RECORDED");
    }

    @Test
    void shouldExposeCompletedReturnRefundWithTerminalBusinessArtifacts() {
        when(afterSaleQuery.get("after-sale-1")).thenReturn(AfterSaleView.builder()
                .afterSaleId("after-sale-1").afterSaleNo("AS-1").orderId("order-1")
                .caseStatus("COMPLETED").aggregateVersion(4L)
                .returnFulfillmentId("return-1").returnFulfillmentStatus("INSPECTION_ACCEPTED")
                .returnShipmentId("return-shipment-1").inspectionId("inspection-1")
                .refundStatus("SUCCEEDED").paymentRefundTransactionId(202L)
                .inventoryLedgerTransactionId(102L).orderSettlementVersion(1L)
                .netAmountMinor(39800L).currencyCode("CNY").build());

        CommerceWorkflowResult result = service.inspectReturnRefund("after-sale-1");

        assertThat(result.getStatus()).isEqualTo(CommerceWorkflowStatus.SUCCEEDED);
        assertThat(result.getPhase()).isEqualTo("RETURN_AND_REFUND_COMPLETED");
        assertThat(result.getTerminal()).isTrue();
        assertThat(result.getArtifacts()).extracting(CommerceWorkflowArtifact::getType)
                .contains("AFTER_SALE", "ORDER", "RETURN_FULFILLMENT", "RETURN_SHIPMENT",
                        "INSPECTION", "PAYMENT_REFUND_TRANSACTION", "INVENTORY_RETURN");
    }
}
