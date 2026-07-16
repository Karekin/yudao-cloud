package cn.iocoder.yudao.module.cloudmold.aftersale.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.aftersale.api.*;
import cn.iocoder.yudao.module.cloudmold.aftersale.dal.dataobject.*;
import cn.iocoder.yudao.module.cloudmold.aftersale.dal.mysql.*;
import cn.iocoder.yudao.module.cloudmold.fulfillment.api.*;
import cn.iocoder.yudao.module.cloudmold.order.api.*;
import cn.iocoder.yudao.module.cloudmold.payment.api.*;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AfterSaleCommandServiceImplTest {
    private final AfterSaleOperationMapper operationMapper = mock(AfterSaleOperationMapper.class);
    private final AfterSaleCaseMapper caseMapper = mock(AfterSaleCaseMapper.class);
    private final AfterSaleItemMapper itemMapper = mock(AfterSaleItemMapper.class);
    private final AfterSaleResolutionSagaMapper sagaMapper = mock(AfterSaleResolutionSagaMapper.class);
    private final OrderAfterSaleQueryApi orderQueryApi = mock(OrderAfterSaleQueryApi.class);
    private final PaymentRefundQueryApi paymentQueryApi = mock(PaymentRefundQueryApi.class);
    private final ForwardFulfillmentAfterSaleQueryApi forwardQueryApi = mock(ForwardFulfillmentAfterSaleQueryApi.class);
    private final ReturnFulfillmentCommandApi returnCommandApi = mock(ReturnFulfillmentCommandApi.class);
    private final ReturnFulfillmentQueryApi returnQueryApi = mock(ReturnFulfillmentQueryApi.class);
    private final AfterSaleEventService eventService = mock(AfterSaleEventService.class);
    private final AfterSaleResolutionCheckpointService checkpointService = mock(AfterSaleResolutionCheckpointService.class);
    private final AfterSaleCommandServiceImpl service = new AfterSaleCommandServiceImpl(operationMapper, caseMapper,
            itemMapper, sagaMapper, orderQueryApi, paymentQueryApi, forwardQueryApi, returnCommandApi, returnQueryApi,
            eventService, checkpointService);

    private final Map<String, AfterSaleOperationDO> operations = new HashMap<>();
    private final AtomicLong operationSequence = new AtomicLong();
    private final AtomicReference<Long> lastOperationId = new AtomicReference<>();
    private AfterSaleCaseDO sale;
    private AfterSaleItemDO item;
    private String returnStatus = "CREATED";
    private long returnVersion = 1L;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        wireOperationStore();
        wirePersistence();
        when(orderQueryApi.requireEligible("order-1", "order-item-1")).thenReturn(eligibleOrder());
        when(paymentQueryApi.requireRefundable("order-1", "payment-1", 39800L, "CNY"))
                .thenReturn(refundablePayment());
        when(forwardQueryApi.requireDelivered("order-1", "fulfillment-1", "shipment-1", "order-item-1"))
                .thenReturn(deliveredForward());
        when(returnCommandApi.execute(any())).thenAnswer(invocation -> {
            ReturnFulfillmentCommand command = invocation.getArgument(0);
            returnStatus = switch (command.getOperation()) {
                case CREATE -> "CREATED";
                case HAND_OVER -> "HANDED_OVER";
                case MARK_IN_TRANSIT -> "IN_TRANSIT";
                case RECEIVE -> "RECEIVED";
                case ACCEPT_INSPECTION -> "INSPECTION_ACCEPTED";
            };
            returnVersion = command.getOperation() == ReturnFulfillmentOperation.CREATE ? 1L : returnVersion + 1;
            return returnView();
        });
        when(returnQueryApi.getForAfterSale(anyString(), anyString())).thenAnswer(ignored -> returnView());
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldRejectNonCompletedNonCapturedAndNonDeliveredBeforeCreatingCase() {
        when(orderQueryApi.requireEligible("order-1", "order-item-1"))
                .thenThrow(new IllegalStateException("canonical order must be COMPLETED"));
        assertThatThrownBy(() -> service.execute(request("after-sale-gate-order")))
                .hasMessage("canonical order must be COMPLETED");
        verifyNoInteractions(paymentQueryApi, forwardQueryApi, returnCommandApi);

        reset(orderQueryApi, paymentQueryApi, forwardQueryApi);
        when(orderQueryApi.requireEligible("order-1", "order-item-1")).thenReturn(eligibleOrder());
        when(paymentQueryApi.requireRefundable("order-1", "payment-1", 39800L, "CNY"))
                .thenThrow(new IllegalStateException("canonical payment must be CAPTURED"));
        assertThatThrownBy(() -> service.execute(request("after-sale-gate-payment")))
                .hasMessage("canonical payment must be CAPTURED");
        verifyNoInteractions(forwardQueryApi, returnCommandApi);

        reset(paymentQueryApi, forwardQueryApi);
        when(paymentQueryApi.requireRefundable("order-1", "payment-1", 39800L, "CNY"))
                .thenReturn(refundablePayment());
        when(forwardQueryApi.requireDelivered("order-1", "fulfillment-1", "shipment-1", "order-item-1"))
                .thenThrow(new IllegalStateException("forward shipment must be DELIVERED"));
        assertThatThrownBy(() -> service.execute(request("after-sale-gate-fulfillment")))
                .hasMessage("forward shipment must be DELIVERED");

        verify(caseMapper, never()).insert(any(AfterSaleCaseDO.class));
        verify(returnCommandApi, never()).execute(any());
        verify(sagaMapper, never()).insert(any(AfterSaleResolutionSagaDO.class));
    }

    @Test
    void shouldTraverseFourCaseStatesOnlyAfterQualifiedInspection() {
        AfterSaleView requested = service.execute(request("after-sale-request-001"));
        assertThat(requested.getCaseStatus()).isEqualTo("REQUESTED");
        assertThat(requested.getAggregateVersion()).isEqualTo(1L);

        AfterSaleView approved = service.execute(command(AfterSaleOperation.APPROVE, "after-sale-approve-001", 1L)
                .reviewerId("reviewer-1").build());
        assertThat(approved.getCaseStatus()).isEqualTo("APPROVED");
        assertThat(approved.getRefundStatus()).isEqualTo("REQUESTED");
        assertThat(approved.getAggregateVersion()).isEqualTo(2L);

        service.execute(command(AfterSaleOperation.HAND_OVER_RETURN, "after-sale-hand-over-001", 2L)
                .carrierCode("SF").waybillNo("SF10001").build());
        service.execute(command(AfterSaleOperation.MARK_RETURN_IN_TRANSIT, "after-sale-transit-001", 2L).build());
        service.execute(command(AfterSaleOperation.RECEIVE_RETURN, "after-sale-receive-001", 2L)
                .receiverId("receiver-1").build());
        verify(sagaMapper, never()).insert(any(AfterSaleResolutionSagaDO.class));

        AfterSaleView pending = service.execute(command(AfterSaleOperation.ACCEPT_INSPECTION,
                "after-sale-inspection-001", 2L).qualityStatus("QUALIFIED").inspectorId("inspector-1").build());
        assertThat(pending.getCaseStatus()).isEqualTo("RESOLUTION_PENDING");
        assertThat(pending.getAggregateVersion()).isEqualTo(3L);
        verify(sagaMapper).insert(argThat((AfterSaleResolutionSagaDO value) -> "REQUESTED".equals(value.getStatus())
                && "RETURN_INVENTORY".equals(value.getActiveStep())));

        sale.setStatus("COMPLETED").setRefundStatus("SUCCEEDED").setVersion(4L);
        AfterSaleView completed = service.get(sale.getAfterSaleId());
        assertThat(completed.getCaseStatus()).isEqualTo("COMPLETED");
        assertThat(completed.getAggregateVersion()).isEqualTo(4L);
    }

    @Test
    void shouldFreezeGrossBenefitAndNetButApproveOnlyNetCashRefund() {
        when(orderQueryApi.requireEligible("order-1", "order-item-1")).thenReturn(discountedOrder());
        when(paymentQueryApi.requireRefundable("order-1", "payment-1", 36000L, "CNY"))
                .thenReturn(PaymentRefundView.builder().paymentId("payment-1").orderId("order-1")
                        .status("CAPTURED").aggregateVersion(1L).capturedAmountMinor(36000L)
                        .refundedAmountMinor(0L).currencyCode("CNY").providerCode("INTERNAL_TEST")
                        .testMode(true).build());

        service.execute(request("after-sale-benefit-request"));
        service.execute(command(AfterSaleOperation.APPROVE, "after-sale-benefit-approve", 1L)
                .reviewerId("reviewer-1").build());
        service.execute(command(AfterSaleOperation.ACCEPT_INSPECTION,
                "after-sale-benefit-inspection", 2L).qualityStatus("QUALIFIED")
                .inspectorId("inspector-1").build());

        assertThat(item.getLineAmountMinor()).isEqualTo(39800L);
        assertThat(item.getDiscountAmountMinor()).isEqualTo(3800L);
        assertThat(item.getNetAmountMinor()).isEqualTo(36000L);
        assertThat(sale.getApprovedAmountMinor()).isEqualTo(36000L);
        verify(sagaMapper).insert(argThat((AfterSaleResolutionSagaDO value) ->
                value.getGrossAmountMinor() == 39800L && value.getBenefitAmountMinor() == 3800L
                        && value.getNetAmountMinor() == 36000L
                        && "PENDING".equals(value.getBenefitReversalStatus())));
    }

    @Test
    void shouldReplaySamePayloadRejectDifferentPayloadAndRejectActiveDuplicate() {
        AfterSaleCommand request = request("after-sale-request-replay");
        AfterSaleView first = service.execute(request);
        AfterSaleView replay = service.execute(request);
        assertThat(replay.getAfterSaleId()).isEqualTo(first.getAfterSaleId());
        assertThat(replay.getDuplicate()).isTrue();

        AfterSaleCommand conflict = request("after-sale-request-replay");
        conflict.setReason("different immutable payload");
        assertThatThrownBy(() -> service.execute(conflict))
                .hasMessage("idempotency key conflicts with different after-sale payload");

        when(caseMapper.selectActiveByOrderItem(1L, "order-1", "order-item-1")).thenReturn(sale);
        assertThatThrownBy(() -> service.execute(request("after-sale-active-duplicate")))
                .hasMessage("order item already owns an active after-sale case");
        verify(orderQueryApi, times(1)).requireEligible("order-1", "order-item-1");
    }

    @Test
    void shouldRejectNonQualifiedInspectionAndKeepTenantIsolation() {
        service.execute(request("after-sale-request-tenant"));
        service.execute(command(AfterSaleOperation.APPROVE, "after-sale-approve-tenant", 1L)
                .reviewerId("reviewer-1").build());
        assertThatThrownBy(() -> service.execute(command(AfterSaleOperation.ACCEPT_INSPECTION,
                "after-sale-bad-inspection", 2L).qualityStatus("DAMAGED").inspectorId("inspector-1").build()))
                .hasMessage("first slice accepts QUALIFIED only");
        verify(sagaMapper, never()).insert(any(AfterSaleResolutionSagaDO.class));

        TenantContextHolder.setTenantId(2L);
        assertThatThrownBy(() -> service.get(sale.getAfterSaleId()))
                .hasMessage("after-sale case does not exist");
        verify(caseMapper).selectTenant(2L, sale.getAfterSaleId());
    }

    private void wirePersistence() {
        when(caseMapper.insert(any(AfterSaleCaseDO.class))).thenAnswer(invocation -> {
            sale = invocation.getArgument(0);
            return 1;
        });
        when(itemMapper.insert(any(AfterSaleItemDO.class))).thenAnswer(invocation -> {
            item = invocation.getArgument(0);
            return 1;
        });
        when(caseMapper.selectForUpdate(anyLong(), anyString())).thenAnswer(invocation ->
                sale != null && Objects.equals(invocation.getArgument(0), sale.getTenantId()) ? sale : null);
        when(caseMapper.selectTenant(anyLong(), anyString())).thenAnswer(invocation ->
                sale != null && Objects.equals(invocation.getArgument(0), sale.getTenantId())
                        && Objects.equals(invocation.getArgument(1), sale.getAfterSaleId()) ? sale : null);
        when(itemMapper.selectByAfterSale(anyLong(), anyString())).thenAnswer(invocation ->
                item != null && Objects.equals(invocation.getArgument(0), item.getTenantId())
                        && Objects.equals(invocation.getArgument(1), item.getAfterSaleId()) ? List.of(item) : List.of());
        when(caseMapper.approve(anyLong(), anyString(), anyLong(), anyLong(), anyString(), anyString(), any()))
                .thenReturn(1);
        when(caseMapper.startResolution(anyLong(), anyString(), anyLong(), anyString(), anyString(), anyString(), any()))
                .thenReturn(1);
        when(sagaMapper.selectTenant(anyLong(), anyString())).thenReturn(null);
    }

    private void wireOperationStore() {
        doAnswer(invocation -> {
            Long tenantId = invocation.getArgument(0);
            String key = invocation.getArgument(1);
            String type = invocation.getArgument(2);
            String hash = invocation.getArgument(3);
            String attemptToken = invocation.getArgument(4);
            String storeKey = tenantId + ":" + key;
            AfterSaleOperationDO operation = operations.get(storeKey);
            if (operation == null) {
                operation = new AfterSaleOperationDO().setOperationId(operationSequence.incrementAndGet())
                        .setTenantId(tenantId).setIdempotencyKey(key).setCommandType(type)
                        .setRequestHash(hash).setAttemptToken(attemptToken).setStatus(0);
                operations.put(storeKey, operation);
            }
            lastOperationId.set(operation.getOperationId());
            return 1;
        }).when(operationMapper).insertOrResolve(anyLong(), anyString(), anyString(), anyString(), anyString(), any());
        when(operationMapper.lastInsertId()).thenAnswer(ignored -> lastOperationId.get());
        when(operationMapper.selectForUpdate(anyLong(), anyLong())).thenAnswer(invocation -> operations.values()
                .stream().filter(value -> Objects.equals(value.getTenantId(), invocation.getArgument(0))
                        && Objects.equals(value.getOperationId(), invocation.getArgument(1))).findFirst().orElse(null));
        when(operationMapper.markSucceeded(anyLong(), anyLong(), anyString(), anyString(), any())).thenAnswer(invocation -> {
            AfterSaleOperationDO operation = operations.values().stream()
                    .filter(value -> Objects.equals(value.getTenantId(), invocation.getArgument(0))
                            && Objects.equals(value.getOperationId(), invocation.getArgument(1))).findFirst().orElseThrow();
            operation.setStatus(10).setAfterSaleId(invocation.getArgument(2)).setResultJson(invocation.getArgument(3));
            return 1;
        });
    }

    private AfterSaleCommand request(String key) {
        return AfterSaleCommand.builder().operation(AfterSaleOperation.REQUEST).idempotencyKey(key).runId("run-1")
                .orderId("order-1").orderItemId("order-item-1").afterSaleType("RETURN_AND_REFUND")
                .reasonCode("SIZE_NOT_FIT").responsibility("BUYER").reason("size not fit")
                .correlationId("70000000-0000-4000-8000-000000000001")
                .occurredAt(Instant.parse("2026-07-15T01:00:00Z")).build();
    }

    private AfterSaleCommand.AfterSaleCommandBuilder command(AfterSaleOperation operation, String key, Long version) {
        return AfterSaleCommand.builder().operation(operation).idempotencyKey(key).runId("run-1")
                .afterSaleId(sale.getAfterSaleId()).expectedVersion(version)
                .correlationId("70000000-0000-4000-8000-000000000001")
                .occurredAt(Instant.parse("2026-07-15T01:00:01Z").plusSeconds(version));
    }

    private ReturnFulfillmentView returnView() {
        return ReturnFulfillmentView.builder().returnFulfillmentId("return-1").returnShipmentId("return-shipment-1")
                .inspectionId("inspection-1").afterSaleId(sale == null ? "after-sale-1" : sale.getAfterSaleId())
                .afterSaleItemId(item == null ? "after-sale-item-1" : item.getAfterSaleItemId())
                .currentStatus(returnStatus).aggregateVersion(returnVersion).qualityStatus(
                        "INSPECTION_ACCEPTED".equals(returnStatus) ? "QUALIFIED" : null).build();
    }

    private static OrderAfterSaleView eligibleOrder() {
        return OrderAfterSaleView.builder().orderId("order-1").orderNo("CMO1").buyerId("buyer-1")
                .status("COMPLETED").aggregateVersion(5L).payableAmountMinor(39800L).currencyCode("CNY")
                .paymentId("payment-1").fulfillmentId("fulfillment-1").shipmentId("shipment-1")
                .orderItemId("order-item-1").canonicalSkuId("sku-1").quantity(BigDecimal.ONE)
                .lineAmountMinor(39800L).discountAmountMinor(0L).netAmountMinor(39800L)
                .benefitApplications(List.of()).listingId("listing-1").listingOfferId("offer-1").build();
    }

    private static OrderAfterSaleView discountedOrder() {
        return OrderAfterSaleView.builder().orderId("order-1").orderNo("CMO1").buyerId("buyer-1")
                .status("COMPLETED").aggregateVersion(5L).payableAmountMinor(36000L).currencyCode("CNY")
                .paymentId("payment-1").fulfillmentId("fulfillment-1").shipmentId("shipment-1")
                .orderItemId("order-item-1").canonicalSkuId("sku-1").quantity(BigDecimal.ONE)
                .lineAmountMinor(39800L).discountAmountMinor(3800L).netAmountMinor(36000L)
                .benefitApplications(List.of()).listingId("listing-1").listingOfferId("offer-1").build();
    }

    private static PaymentRefundView refundablePayment() {
        return PaymentRefundView.builder().paymentId("payment-1").orderId("order-1").status("CAPTURED")
                .aggregateVersion(1L).capturedAmountMinor(39800L).refundedAmountMinor(0L).currencyCode("CNY")
                .providerCode("INTERNAL_TEST").testMode(true).build();
    }

    private static ForwardFulfillmentAfterSaleView deliveredForward() {
        return ForwardFulfillmentAfterSaleView.builder().fulfillmentId("fulfillment-1").shipmentId("shipment-1")
                .orderId("order-1").orderItemId("order-item-1").canonicalSkuId("sku-1")
                .quantity(BigDecimal.ONE).ownerId("owner-1").warehouseId("warehouse-1").uomCode("PCS")
                .status("DELIVERED").aggregateVersion(5L).build();
    }
}
