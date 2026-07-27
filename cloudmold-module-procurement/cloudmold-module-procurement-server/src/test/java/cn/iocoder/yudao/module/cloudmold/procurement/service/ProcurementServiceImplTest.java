package cn.iocoder.yudao.module.cloudmold.procurement.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.procurement.api.*;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementRecords.Operation;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementRecords.ProcurementOrder;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.mysql.ProcurementMapper;
import cn.iocoder.yudao.module.cloudmold.procurement.service.actor.ProcurementActorPrincipalPort;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProcurementServiceImplTest {
    private static final String ACTOR = "principal-buyer-01";
    private final ProcurementMapper mapper = mock(ProcurementMapper.class);
    private final OutboxAppender outbox = mock(OutboxAppender.class);
    private final ProcurementActorPrincipalPort actorPrincipalPort = mock(ProcurementActorPrincipalPort.class);
    private final ProcurementServiceImpl service = new ProcurementServiceImpl(mapper, outbox, actorPrincipalPort);
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
        when(mapper.markOperationSucceeded(eq(301L), eq(31L), anyString(), anyString(), anyString(), any()))
                .thenReturn(1);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void createsCanonicalOrderWhileKeepingErpPrepareAsProjectionOnly() {
        ProcurementResult result = execute(base(ProcurementOperation.CREATE_PURCHASE_ORDER)
                .purchaseOrder(ProcurementCommand.PurchaseOrderDefinition.builder()
                        .orderCode("PO-CM-001")
                        .sourceBusinessType("REPLENISHMENT")
                        .sourceBusinessRef("recommendation-01")
                        .supplierRef("supplier/dewu-alpha")
                        .canonicalSkuId("sku-01")
                        .canonicalWarehouseId("warehouse-01")
                        .orderedQuantity(new BigDecimal("120"))
                        .uomCode("EA")
                        .unitCostMinor(1299L)
                        .totalAmountMinor(155880L)
                        .currencyCode("CNY")
                        .leadTimeDays(7)
                        .requiredDeliveryDate(LocalDate.of(2026, 8, 3))
                        .projection(ProcurementCommand.ProjectionDefinition.builder()
                                .sourceSystem("YUDAO_ERP")
                                .documentType("PURCHASE_ORDER")
                                .externalDocumentId("781")
                                .externalDocumentNo("PO-ERP-781")
                                .documentStatus("PREPARE")
                                .evidenceSha256("a".repeat(64))
                                .build())
                        .build())
                .build());

        assertThat(result.getStatus()).isEqualTo("CREATED");
        assertThat(result.getProjectionDocumentStatus()).isEqualTo("PREPARE");
        verify(mapper).insertOrder(argThat(row ->
                row.getStatus().equals("CREATED")
                        && row.getProjectionSourceSystem().equals("YUDAO_ERP")
                        && row.getProjectionDocumentType().equals("PURCHASE_ORDER")
                        && row.getProjectionDocumentStatus().equals("PREPARE")));
        verify(outbox).append(argThat(event ->
                event.getEventType().equals("procurement.order.created")
                        && event.getAggregateType().equals("procurement_order")));
    }

    @Test
    void dispatchesCreatedOrder() {
        when(mapper.selectOrderForUpdate(31L, "order-01")).thenReturn(baseOrder("CREATED", 1L));
        when(mapper.dispatchOrder(eq(31L), eq("order-01"), eq(1L), eq(ACTOR), eq("RELEASED_TO_SUPPLIER"), any()))
                .thenReturn(1);

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

        ProcurementResult result = execute(base(ProcurementOperation.CLOSE_PURCHASE_ORDER)
                .purchaseOrder(ProcurementCommand.PurchaseOrderDefinition.builder()
                        .orderId("order-01").expectedVersion(3L).reasonCode("inbound_completed").build())
                .build());

        assertThat(result.getStatus()).isEqualTo("CLOSED");
        verify(outbox).append(argThat(event -> event.getEventType().equals("procurement.order.closed")));
    }

    @Test
    void rejectsCommandsWithoutAttestedActor() {
        assertThatThrownBy(() -> service.execute(base(ProcurementOperation.CREATE_PURCHASE_ORDER).build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("attested actor Principal is required");
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
                .setSupplierRef("supplier/dewu-alpha")
                .setCanonicalSkuId("sku-01")
                .setCanonicalWarehouseId("warehouse-01")
                .setOrderedQuantity(new BigDecimal("120"))
                .setUomCode("EA")
                .setUnitCostMinor(1299L)
                .setTotalAmountMinor(155880L)
                .setCurrencyCode("CNY")
                .setLeadTimeDays(7)
                .setRequiredDeliveryDate(LocalDate.of(2026, 8, 3))
                .setProjectionSourceSystem("YUDAO_ERP")
                .setProjectionDocumentType("PURCHASE_ORDER")
                .setProjectionExternalDocumentId("781")
                .setProjectionExternalDocumentNo("PO-ERP-781")
                .setProjectionDocumentStatus("PREPARE")
                .setStatus(status)
                .setVersion(version);
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
