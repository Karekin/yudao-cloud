package cn.iocoder.yudao.module.cloudmold.procurement.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.finance.api.p2p.P2pEvidenceIngestionApi;
import cn.iocoder.yudao.module.cloudmold.finance.api.p2p.P2pEvidenceCommands;
import cn.iocoder.yudao.module.cloudmold.procurement.api.*;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementRecords.Operation;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementRecords.OrderStatusHistory;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementRecords.ProcurementOrder;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementRecords.PurchaseOrderDeliverySchedule;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.dataobject.ProcurementRecords.PurchaseOrderItem;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.mysql.ProcurementMapper;
import cn.iocoder.yudao.module.cloudmold.procurement.dal.mysql.ProcurementSourcingMapper;
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
    private final ProcurementSourcingMapper sourcingMapper = mock(ProcurementSourcingMapper.class);
    private final OutboxAppender outbox = mock(OutboxAppender.class);
    private final ProcurementActorPrincipalPort actorPrincipalPort = mock(ProcurementActorPrincipalPort.class);
    private final ProcurementReferenceValidationPort referenceValidationPort = mock(ProcurementReferenceValidationPort.class);
    private final P2pEvidenceIngestionApi p2pEvidence = mock(P2pEvidenceIngestionApi.class);
    private final ProcurementServiceImpl service = new ProcurementServiceImpl(
            mapper, sourcingMapper, outbox, actorPrincipalPort, referenceValidationPort, p2pEvidence);
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
        when(mapper.insertStatusHistory(any(OrderStatusHistory.class))).thenReturn(1);
        when(mapper.markOperationSucceeded(eq(301L), eq(31L), anyString(), anyString(), anyString(), any()))
                .thenReturn(1);
        when(mapper.selectCurrentHeader(31L, "order-01")).thenReturn(baseOrder("DRAFT", 1L));
        when(mapper.selectItems(31L, "order-01")).thenReturn(List.of(baseItem()));
        when(mapper.selectSchedules(31L, "order-01")).thenReturn(List.of(baseSchedule()));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void dispatchesReleasedAwardBackedOrder() {
        when(mapper.selectOrderForUpdate(31L, "order-01")).thenReturn(baseOrder("RELEASED", 4L));
        when(mapper.dispatchOrder(eq(31L), eq("order-01"), eq(4L), eq(ACTOR), eq("RELEASED_TO_SUPPLIER"), any()))
                .thenReturn(1);
        when(mapper.selectCurrentHeader(31L, "order-01")).thenReturn(baseOrder("DISPATCHED", 5L));

        ProcurementResult result = execute(base(ProcurementOperation.DISPATCH_PURCHASE_ORDER)
                .purchaseOrder(ProcurementCommand.PurchaseOrderDefinition.builder()
                        .orderId("order-01").expectedVersion(4L).reasonCode("released_to_supplier").build())
                .build());

        assertThat(result.getStatus()).isEqualTo("DISPATCHED");
        verify(outbox).append(argThat(event -> event.getEventType().equals("procurement.order.dispatched")));
    }

    @Test
    void advancesOrderThroughExplicitSubmitApproveReleaseStates() {
        when(mapper.selectOrderForUpdate(31L, "order-01"))
                .thenReturn(baseOrder("DRAFT", 1L), baseOrder("SUBMITTED", 2L), baseOrder("APPROVED", 3L));
        when(mapper.submitOrder(eq(31L), eq("order-01"), eq(1L), eq(ACTOR), isNull(), any())).thenReturn(1);
        when(mapper.approveOrder(eq(31L), eq("order-01"), eq(2L), eq(ACTOR), isNull(), any())).thenReturn(1);
        when(mapper.releaseOrder(eq(31L), eq("order-01"), eq(3L), eq(ACTOR), isNull(), any())).thenReturn(1);
        when(mapper.selectCurrentHeader(31L, "order-01"))
                .thenReturn(baseOrder("SUBMITTED", 2L), baseOrder("APPROVED", 3L), baseOrder("RELEASED", 4L));

        ProcurementResult submitted = execute(transitionCommand(ProcurementOperation.SUBMIT_PURCHASE_ORDER, 1L));
        ProcurementResult approved = execute(transitionCommand(ProcurementOperation.APPROVE_PURCHASE_ORDER, 2L));
        ProcurementResult released = execute(transitionCommand(ProcurementOperation.RELEASE_PURCHASE_ORDER, 3L));

        assertThat(List.of(submitted.getStatus(), approved.getStatus(), released.getStatus()))
                .containsExactly("SUBMITTED", "APPROVED", "RELEASED");
        verify(outbox).append(argThat(event -> event.getEventType().equals("procurement.order.submitted")));
        verify(outbox).append(argThat(event -> event.getEventType().equals("procurement.order.approved")));
        verify(outbox).append(argThat(event -> event.getEventType().equals("procurement.order.released")
                && event.getSchemaVersion() == 4
                && ((List<?>) event.getPayload().get("items")).toString().contains("valuation_policy_id=valuation-policy-01")
                && ((List<?>) event.getPayload().get("items")).toString().contains("valuation_policy_version=v1")
                && ((List<?>) event.getPayload().get("items")).toString().contains("valuation_policy_hash=" + "a".repeat(64))));
        verify(p2pEvidence).ingestPurchaseOrderLine(argThat(evidence ->
                evidence.getSourceEventId().matches("po-release:[0-9a-f]{64}")
                        && evidence.getSourceVersion().equals(4L)
                        && evidence.getEvidenceSha256().matches("[0-9a-f]{64}")
                        && evidence.getLegalEntityId().equals("legal-entity-01")
                        && evidence.getOrderedQuantity().compareTo(new BigDecimal("120")) == 0
                        && evidence.getNetAmountMinor().equals(155880L)
                        && evidence.getGrossAmountMinor().equals(155880L)), eq(ACTOR));
    }

    @Test
    void financeEvidenceFailureAbortsReleaseBeforeOutboxAndCompletion() {
        when(mapper.selectOrderForUpdate(31L,"order-01")).thenReturn(baseOrder("APPROVED",3L));
        when(mapper.releaseOrder(eq(31L),eq("order-01"),eq(3L),eq(ACTOR),isNull(),any())).thenReturn(1);
        when(mapper.selectCurrentHeader(31L,"order-01")).thenReturn(baseOrder("RELEASED",4L));
        doThrow(new IllegalStateException("finance unavailable")).when(p2pEvidence).ingestPurchaseOrderLine(any(P2pEvidenceCommands.PurchaseOrderLine.class),eq(ACTOR));

        assertThatThrownBy(()->execute(transitionCommand(ProcurementOperation.RELEASE_PURCHASE_ORDER,3L)))
                .isInstanceOf(IllegalStateException.class).hasMessage("finance unavailable");
        verify(mapper).releaseOrder(eq(31L),eq("order-01"),eq(3L),eq(ACTOR),isNull(),any());
        verifyNoInteractions(outbox);
        verify(mapper,never()).markOperationSucceeded(anyLong(),anyLong(),anyString(),anyString(),anyString(),any());
    }

    @Test
    void rejectsReleaseWhenPersistedValuationPolicySnapshotIsNotCanonical() {
        PurchaseOrderItem invalid = baseItem().setValuationPolicyHash("A".repeat(64));
        when(mapper.selectOrderForUpdate(31L,"order-01")).thenReturn(baseOrder("APPROVED",3L));
        when(mapper.releaseOrder(eq(31L),eq("order-01"),eq(3L),eq(ACTOR),isNull(),any())).thenReturn(1);
        when(mapper.selectCurrentHeader(31L,"order-01")).thenReturn(baseOrder("RELEASED",4L));
        when(mapper.selectItems(31L,"order-01")).thenReturn(List.of(invalid));

        assertThatThrownBy(()->execute(transitionCommand(ProcurementOperation.RELEASE_PURCHASE_ORDER,3L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("valuationPolicyHash must be a lowercase SHA-256");
        verifyNoInteractions(p2pEvidence);
        verifyNoInteractions(outbox);
        verify(mapper,never()).markOperationSucceeded(anyLong(),anyLong(),anyString(),anyString(),anyString(),any());
    }

    @Test
    void duplicateReleaseDoesNotRepublishFinanceEvidence() {
        when(mapper.selectOperationForUpdate(301L,31L)).thenReturn(new Operation().setOperationId(301L).setTenantId(31L)
                .setRequestHash("ignored").setAttemptToken("another-attempt").setStatus(10)
                .setResultJson("{\"operationId\":301,\"duplicate\":false,\"aggregateType\":\"procurement_order\",\"aggregateId\":\"order-01\",\"aggregateVersion\":4,\"status\":\"RELEASED\"}"));
        ProcurementCommand command=transitionCommand(ProcurementOperation.RELEASE_PURCHASE_ORDER,3L);
        // Capture the hash generated for this exact command while preserving a different attempt token.
        when(mapper.selectOperationForUpdate(301L,31L)).thenAnswer(i->new Operation().setOperationId(301L).setTenantId(31L)
                .setRequestHash(requestHash.get()).setAttemptToken("another-attempt").setStatus(10)
                .setResultJson("{\"operationId\":301,\"duplicate\":false,\"aggregateType\":\"procurement_order\",\"aggregateId\":\"order-01\",\"aggregateVersion\":4,\"status\":\"RELEASED\"}"));
        ProcurementResult replay=execute(command);
        assertThat(replay.isDuplicate()).isTrue();
        verifyNoInteractions(p2pEvidence); verify(mapper,never()).releaseOrder(anyLong(),anyString(),anyLong(),anyString(),any(),any());
    }

    @Test
    void rejectsDirectDispatchFromDraft() {
        when(mapper.selectOrderForUpdate(31L, "order-01")).thenReturn(baseOrder("DRAFT", 1L));

        assertThatThrownBy(() -> execute(transitionCommand(ProcurementOperation.DISPATCH_PURCHASE_ORDER, 1L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("only an award-backed released purchase order can be dispatched");
        verify(mapper, never()).dispatchOrder(anyLong(), anyString(), anyLong(), anyString(), any(), any());
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
                base(ProcurementOperation.SUBMIT_PURCHASE_ORDER).build(), null))
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
                .setSourceBusinessType("SOURCING_AWARD")
                .setSourceBusinessRef("award-01")
                .setAwardId("award-01").setAwardVersion(1L)
                .setLegalEntityId("legal-entity-01")
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
                .setAwardLineId("award-line-01")
                .setCanonicalSkuId("sku-01")
                .setOrderedQuantity(new BigDecimal("120"))
                .setUomCode("EA")
                .setTaxCode("VAT13")
                .setTaxRateBps(0)
                .setUnitNetPriceMinor(new BigDecimal("1299.000000"))
                .setValuationPolicyId("valuation-policy-01")
                .setValuationPolicyVersion("v1")
                .setValuationPolicyHash("a".repeat(64))
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

    private static ProcurementCommand transitionCommand(ProcurementOperation operation, long version) {
        return base(operation)
                .purchaseOrder(ProcurementCommand.PurchaseOrderDefinition.builder()
                        .orderId("order-01").expectedVersion(version).build())
                .build();
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
