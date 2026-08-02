package cn.iocoder.yudao.module.cloudmold.procurement.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.procurement.api.*;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementRecords.Operation;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementRecords.OrderStatusHistory;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementRecords.ProcurementOrder;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementRecords.PurchaseOrderDeliverySchedule;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementRecords.PurchaseOrderItem;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.mysql.ProcurementMapper;
import cn.iocoder.yudao.module.cloudmold.procurement.service.actor.ProcurementActorPrincipalPort;
import cn.iocoder.yudao.module.cloudmold.procurement.service.reference.ProcurementReferenceValidationPort;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProcurementServiceImplTest {
    private static final String ACTOR = "principal-buyer-01";
    private final ProcurementMapper mapper = mock(ProcurementMapper.class);
    private final OutboxAppender outbox = mock(OutboxAppender.class);
    private final ProcurementActorPrincipalPort actorPrincipalPort = mock(ProcurementActorPrincipalPort.class);
    private final ProcurementReferenceValidationPort referenceValidationPort = mock(ProcurementReferenceValidationPort.class);
    private final ProcurementServiceImpl service = new ProcurementServiceImpl(
            mapper, outbox, actorPrincipalPort, referenceValidationPort);
    private final AtomicReference<String> requestHash = new AtomicReference<>();
    private final AtomicReference<String> attemptToken = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(31L);
        when(mapper.insertOrResolveOperation(eq(31L), anyString(), anyString(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    requestHash.set(invocation.getArgument(3));
                    attemptToken.set(invocation.getArgument(4));
                    return 1;
                });
        when(mapper.selectLastInsertId()).thenReturn(301L);
        when(mapper.selectOperationForUpdate(301L, 31L)).thenAnswer(invocation ->
                new Operation().setOperationId(301L).setTenantId(31L)
                        .setRequestHash(requestHash.get()).setAttemptToken(attemptToken.get()).setStatus(0));
        when(mapper.insertOrder(any())).thenReturn(1);
        when(mapper.insertItems(anyList())).thenAnswer(invocation -> ((List<?>) invocation.getArgument(0)).size());
        when(mapper.insertSchedules(anyList())).thenAnswer(invocation -> ((List<?>) invocation.getArgument(0)).size());
        when(mapper.insertStatusHistory(any(OrderStatusHistory.class))).thenReturn(1);
        when(mapper.markOperationSucceeded(eq(301L), eq(31L), anyString(), anyString(), anyString(), any()))
                .thenReturn(1);
        when(mapper.selectCurrentHeader(31L, "order-01")).thenReturn(baseOrder("CREATED", 1L));
        when(mapper.selectItems(31L, "order-01")).thenReturn(List.of(baseItem()));
        when(mapper.selectSchedules(31L, "order-01")).thenReturn(List.of(baseSchedule()));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void createsCanonicalOrderWithoutLegacyProjectionArtifacts() {
        ProcurementResult result = execute(base(ProcurementOperation.CREATE_PURCHASE_ORDER)
                .purchaseOrder(ProcurementCommand.PurchaseOrderDefinition.builder()
                        .orderCode("PO-CM-001")
                        .sourceBusinessType("REPLENISHMENT")
                        .sourceBusinessRef("recommendation-01")
                        .supplierId("supplier/dewu-alpha")
                        .currencyCode("CNY")
                        .leadTimeDays(7)
                        .headerNetAmountMinor(155880L)
                        .headerTaxAmountMinor(0L)
                        .headerGrossAmountMinor(155880L)
                        .lines(List.of(ProcurementCommand.PurchaseOrderLineDefinition.builder()
                                .lineNumber(10)
                                .canonicalSkuId("sku-01")
                                .orderedQuantity(new BigDecimal("120"))
                                .uomCode("EA")
                                .taxCode("VAT13")
                                .taxRateBps(0)
                                .unitNetPriceMinor(new BigDecimal("1299.000000"))
                                .lineNetAmountMinor(155880L)
                                .lineTaxAmountMinor(0L)
                                .lineGrossAmountMinor(155880L)
                                .schedules(List.of(ProcurementCommand.PurchaseOrderDeliveryScheduleDefinition.builder()
                                        .scheduleNumber(1)
                                        .requiredDeliveryDate(LocalDate.of(2026, 8, 3))
                                        .canonicalWarehouseId("warehouse-01")
                                        .scheduledQuantity(new BigDecimal("120"))
                                        .build()))
                                .build()))
                        .build())
                .build());

        assertThat(result.getStatus()).isEqualTo("CREATED");
        verify(mapper).insertOrder(argThat(row ->
                row.getStatus().equals("CREATED")
                        && row.getSupplierId().equals("supplier/dewu-alpha")
                        && row.getHeaderGrossAmountMinor().equals(155880L)));
        verify(outbox).append(argThat(event ->
                event.getEventType().equals("procurement.order.created")
                        && event.getAggregateType().equals("procurement_order")));
    }

    @Test
    void dispatchesCreatedOrder() {
        when(mapper.selectOrderForUpdate(31L, "order-01")).thenReturn(baseOrder("CREATED", 1L));
        when(mapper.dispatchOrder(eq(31L), eq("order-01"), eq(1L), eq(ACTOR), eq("RELEASED_TO_SUPPLIER"), any()))
                .thenReturn(1);
        when(mapper.selectCurrentHeader(31L, "order-01")).thenReturn(baseOrder("DISPATCHED", 2L));

        ProcurementResult result = execute(base(ProcurementOperation.DISPATCH_PURCHASE_ORDER)
                .purchaseOrder(ProcurementCommand.PurchaseOrderDefinition.builder()
                        .orderId("order-01").expectedVersion(1L).reasonCode("released_to_supplier").build())
                .build());

        assertThat(result.getStatus()).isEqualTo("DISPATCHED");
        verify(outbox).append(argThat(event -> event.getEventType().equals("procurement.order.dispatched")));
    }

    @Test
    void supplierConfirmsDispatchedOrder() {
        when(mapper.selectOrderForUpdate(31L, "order-01")).thenReturn(baseOrder("DISPATCHED", 2L));
        when(mapper.confirmSupplier(eq(31L), eq("order-01"), eq(2L), eq(ACTOR), eq("SUPPLIER_ACCEPTED"), any()))
                .thenReturn(1);
        when(mapper.selectCurrentHeader(31L, "order-01")).thenReturn(baseOrder("SUPPLIER_CONFIRMED", 3L));

        ProcurementResult result = execute(base(ProcurementOperation.SUPPLIER_CONFIRM_PURCHASE_ORDER)
                .purchaseOrder(ProcurementCommand.PurchaseOrderDefinition.builder()
                        .orderId("order-01").expectedVersion(2L).reasonCode("supplier_accepted").build())
                .build());

        assertThat(result.getStatus()).isEqualTo("SUPPLIER_CONFIRMED");
        verify(outbox).append(argThat(event -> event.getEventType().equals("procurement.order.supplier_confirmed")));
    }

    @Test
    void cancelsNonTerminalOrder() {
        when(mapper.selectOrderForUpdate(31L, "order-01")).thenReturn(baseOrder("DISPATCHED", 2L));
        when(mapper.cancelOrder(eq(31L), eq("order-01"), eq(2L), eq(ACTOR), eq("BUYER_WITHDRAWN"), any()))
                .thenReturn(1);
        when(mapper.selectCurrentHeader(31L, "order-01")).thenReturn(baseOrder("CANCELLED", 3L));

        ProcurementResult result = execute(base(ProcurementOperation.CANCEL_PURCHASE_ORDER)
                .purchaseOrder(ProcurementCommand.PurchaseOrderDefinition.builder()
                        .orderId("order-01").expectedVersion(2L).reasonCode("buyer_withdrawn").build())
                .build());

        assertThat(result.getStatus()).isEqualTo("CANCELLED");
        verify(outbox).append(argThat(event -> event.getEventType().equals("procurement.order.cancelled")));
    }

    @Test
    void closesOnlyAfterSupplierConfirmation() {
        when(mapper.selectOrderForUpdate(31L, "order-01")).thenReturn(baseOrder("SUPPLIER_CONFIRMED", 3L));
        when(mapper.closeOrder(eq(31L), eq("order-01"), eq(3L), eq(ACTOR), eq("INBOUND_COMPLETED"), any()))
                .thenReturn(1);
        when(mapper.selectCurrentHeader(31L, "order-01")).thenReturn(baseOrder("CLOSED", 4L));

        ProcurementResult result = execute(base(ProcurementOperation.CLOSE_PURCHASE_ORDER)
                .purchaseOrder(ProcurementCommand.PurchaseOrderDefinition.builder()
                        .orderId("order-01").expectedVersion(3L).reasonCode("inbound_completed").build())
                .build());

        assertThat(result.getStatus()).isEqualTo("CLOSED");
        verify(outbox).append(argThat(event -> event.getEventType().equals("procurement.order.closed")));
    }

    @Test
    void rejectsCommandsWithoutAttestedActor() {
        assertThatThrownBy(() -> service.execute(
                base(ProcurementOperation.CREATE_PURCHASE_ORDER).build(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("actorPrincipalId");
        verifyNoInteractions(actorPrincipalPort);
    }

    private ProcurementResult execute(ProcurementCommand command) {
        return service.execute(command, ACTOR);
    }

    private static ProcurementOrder baseOrder(String status, Long version) {
        return new ProcurementOrder()
                .setOrderId("order-01")
                .setTenantId(31L)
                .setOrderCode("PO-CM-001")
                .setSourceBusinessType("REPLENISHMENT")
                .setSourceBusinessRef("recommendation-01")
                .setSupplierId("supplier/dewu-alpha")
                .setCurrencyCode("CNY")
                .setLeadTimeDays(7)
                .setHeaderNetAmountMinor(155880L)
                .setHeaderTaxAmountMinor(0L)
                .setHeaderGrossAmountMinor(155880L)
                .setTaxCalculationPolicyCode("STANDARD_V1")
                .setRoundingPolicyCode("HALF_UP")
                .setStatus(status)
                .setVersion(version);
    }

    private static PurchaseOrderItem baseItem() {
        return new PurchaseOrderItem()
                .setItemId("item-01")
                .setTenantId(31L)
                .setOrderId("order-01")
                .setLineNumber(10)
                .setCanonicalSkuId("sku-01")
                .setOrderedQuantity(new BigDecimal("120"))
                .setUomCode("EA")
                .setTaxCode("VAT13")
                .setTaxRateBps(0)
                .setUnitNetPriceMinor(new BigDecimal("1299.000000"))
                .setLineNetAmountMinor(155880L)
                .setLineTaxAmountMinor(0L)
                .setLineGrossAmountMinor(155880L);
    }

    private static PurchaseOrderDeliverySchedule baseSchedule() {
        return new PurchaseOrderDeliverySchedule()
                .setScheduleId("schedule-01")
                .setTenantId(31L)
                .setOrderId("order-01")
                .setItemId("item-01")
                .setScheduleNumber(1)
                .setRequiredDeliveryDate(LocalDate.of(2026, 8, 3))
                .setCanonicalWarehouseId("warehouse-01")
                .setScheduledQuantity(new BigDecimal("120"));
    }
    private static ProcurementCommand.ProcurementCommandBuilder base(ProcurementOperation operation) {
        return ProcurementCommand.builder()
                .operation(operation)
                .idempotencyKey("procurement-idempotency-" + operation)
                .runId("run-001")
                .correlationId("11111111-1111-4111-8111-111111111111")
                .occurredAt(Instant.parse("2026-07-27T00:00:00Z"));
    }
}
