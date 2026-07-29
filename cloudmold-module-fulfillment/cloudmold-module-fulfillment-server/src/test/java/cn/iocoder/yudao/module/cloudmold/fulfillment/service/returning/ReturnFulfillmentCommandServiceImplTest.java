package cn.iocoder.yudao.module.cloudmold.fulfillment.service.returning;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.fulfillment.api.*;
import cn.iocoder.yudao.module.cloudmold.fulfillment.dal.dataobject.returning.*;
import cn.iocoder.yudao.module.cloudmold.fulfillment.dal.mysql.returning.*;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReturnFulfillmentCommandServiceImplTest {
    private final ReturnFulfillmentOperationMapper operationMapper = mock(ReturnFulfillmentOperationMapper.class);
    private final ReturnFulfillmentMapper fulfillmentMapper = mock(ReturnFulfillmentMapper.class);
    private final ReturnFulfillmentItemMapper itemMapper = mock(ReturnFulfillmentItemMapper.class);
    private final ReturnFulfillmentHistoryMapper historyMapper = mock(ReturnFulfillmentHistoryMapper.class);
    private final ReturnShipmentMapper shipmentMapper = mock(ReturnShipmentMapper.class);
    private final ReturnShipmentItemMapper shipmentItemMapper = mock(ReturnShipmentItemMapper.class);
    private final ReturnTrackingEventMapper trackingMapper = mock(ReturnTrackingEventMapper.class);
    private final ReturnInspectionMapper inspectionMapper = mock(ReturnInspectionMapper.class);
    private final OutboxAppender outboxAppender = mock(OutboxAppender.class);
    private final ReturnFulfillmentCommandServiceImpl service = new ReturnFulfillmentCommandServiceImpl(
            operationMapper, fulfillmentMapper, itemMapper, historyMapper, shipmentMapper, shipmentItemMapper,
            trackingMapper, inspectionMapper, outboxAppender);

    private final Map<String, ReturnFulfillmentOperationDO> operations = new HashMap<>();
    private final AtomicLong operationSequence = new AtomicLong();
    private final AtomicReference<Long> lastOperationId = new AtomicReference<>();
    private ReturnFulfillmentDO fulfillment;
    private ReturnFulfillmentItemDO item;
    private ReturnShipmentDO shipment;
    private ReturnShipmentItemDO shipmentItem;
    private ReturnInspectionDO inspection;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        wireOperationStore();
        when(fulfillmentMapper.insert(any(ReturnFulfillmentDO.class))).thenAnswer(invocation -> {
            fulfillment = invocation.getArgument(0);
            return 1;
        });
        when(itemMapper.insert(any(ReturnFulfillmentItemDO.class))).thenAnswer(invocation -> {
            item = invocation.getArgument(0);
            return 1;
        });
        when(fulfillmentMapper.selectForUpdate(anyLong(), anyString())).thenAnswer(invocation ->
                Objects.equals(invocation.getArgument(0), fulfillment == null ? null : fulfillment.getTenantId())
                        ? fulfillment : null);
        when(fulfillmentMapper.selectTenant(anyLong(), anyString())).thenAnswer(invocation ->
                fulfillment != null && Objects.equals(invocation.getArgument(0), fulfillment.getTenantId())
                        && Objects.equals(invocation.getArgument(1), fulfillment.getReturnFulfillmentId())
                        ? fulfillment : null);
        when(fulfillmentMapper.selectByAfterSale(anyLong(), anyString())).thenAnswer(invocation ->
                fulfillment != null && Objects.equals(invocation.getArgument(0), fulfillment.getTenantId())
                        && Objects.equals(invocation.getArgument(1), fulfillment.getAfterSaleId())
                        ? fulfillment : null);
        when(itemMapper.selectByFulfillment(anyLong(), anyString())).thenAnswer(invocation ->
                item != null && Objects.equals(invocation.getArgument(0), item.getTenantId())
                        && Objects.equals(invocation.getArgument(1), item.getReturnFulfillmentId())
                        ? List.of(item) : List.of());
        when(shipmentMapper.selectByFulfillment(anyLong(), anyString())).thenAnswer(invocation ->
                shipment != null && Objects.equals(invocation.getArgument(0), shipment.getTenantId())
                        && Objects.equals(invocation.getArgument(1), shipment.getReturnFulfillmentId())
                        ? shipment : null);
        when(shipmentMapper.insert(any(ReturnShipmentDO.class))).thenAnswer(invocation -> {
            shipment = invocation.getArgument(0);
            return 1;
        });
        when(shipmentItemMapper.insert(any(ReturnShipmentItemDO.class))).thenAnswer(invocation -> {
            shipmentItem = invocation.getArgument(0);
            return 1;
        });
        when(shipmentItemMapper.selectByShipment(anyLong(), anyString())).thenAnswer(invocation ->
                shipmentItem != null && shipment != null
                        && Objects.equals(invocation.getArgument(0), shipment.getTenantId())
                        && Objects.equals(invocation.getArgument(1), shipment.getReturnShipmentId())
                        ? List.of(shipmentItem) : List.of());
        when(inspectionMapper.selectByFulfillment(anyLong(), anyString())).thenAnswer(invocation -> inspection);
        when(inspectionMapper.insert(any(ReturnInspectionDO.class))).thenAnswer(invocation -> {
            inspection = invocation.getArgument(0);
            return 1;
        });
        when(fulfillmentMapper.transition(anyLong(), anyString(), anyLong(), anyString(), anyString(), any()))
                .thenReturn(1);
        when(shipmentMapper.transition(anyLong(), anyString(), anyString(), anyString(), any(), any(), any(), any()))
                .thenReturn(1);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldTraverseFiveStatesAndReplayImmutableResult() {
        ReturnFulfillmentCommand create = createCommand("return-create-001");
        ReturnFulfillmentView created = service.execute(create);
        assertThat(created.getCurrentStatus()).isEqualTo("CREATED");
        assertThat(created.getAggregateVersion()).isEqualTo(1L);

        ReturnFulfillmentView replay = service.execute(create);
        assertThat(replay.getReturnFulfillmentId()).isEqualTo(created.getReturnFulfillmentId());
        assertThat(replay.getDuplicate()).isTrue();
        ReturnFulfillmentCommand conflicting = createCommand("return-create-001");
        conflicting.setQuantity(new BigDecimal("2"));
        assertThatThrownBy(() -> service.execute(conflicting))
                .hasMessage("idempotency key conflicts with different return fulfillment payload");

        ReturnFulfillmentView handedOver = service.execute(transition(ReturnFulfillmentOperation.HAND_OVER,
                "return-hand-over-001", 1L).carrierCode("SF").waybillNo("SF10001").build());
        ReturnFulfillmentView inTransit = service.execute(transition(ReturnFulfillmentOperation.MARK_IN_TRANSIT,
                "return-in-transit-001", 2L).build());
        ReturnFulfillmentView received = service.execute(transition(ReturnFulfillmentOperation.RECEIVE,
                "return-receive-001", 3L).operatorId("receiver-1").build());
        ReturnFulfillmentView accepted = service.execute(transition(ReturnFulfillmentOperation.ACCEPT_INSPECTION,
                "return-inspection-001", 4L).operatorId("inspector-1").qualityStatus("QUALIFIED")
                .dispositionAssessmentId("70000000-0000-4000-8000-000000000010")
                .dispositionCode("RESTOCK").conditionGrade("A")
                .inspectionEvidenceRef("evidence:return-001").build());

        assertThat(List.of(handedOver.getCurrentStatus(), inTransit.getCurrentStatus(), received.getCurrentStatus(),
                accepted.getCurrentStatus())).containsExactly("HANDED_OVER", "IN_TRANSIT", "RECEIVED",
                "INSPECTION_ACCEPTED");
        assertThat(accepted.getAggregateVersion()).isEqualTo(5L);
        assertThat(accepted.getQualityStatus()).isEqualTo("QUALIFIED");
    }

    @Test
    void shouldRejectIllegalOrderWaybillConflictAndMismatchedInspectionDisposition() {
        service.execute(createCommand("return-create-002"));
        assertThatThrownBy(() -> service.execute(transition(ReturnFulfillmentOperation.RECEIVE,
                "return-receive-too-early", 1L).operatorId("receiver-1").build()))
                .hasMessage("RECEIVED requires IN_TRANSIT");

        ReturnShipmentDO existing = new ReturnShipmentDO().setReturnShipmentId("other-shipment")
                .setTenantId(1L).setCarrierCode("SF").setWaybillNo("SF-CONFLICT");
        when(shipmentMapper.selectByWaybill(1L, "SF", "SF-CONFLICT")).thenReturn(existing);
        assertThatThrownBy(() -> service.execute(transition(ReturnFulfillmentOperation.HAND_OVER,
                "return-waybill-conflict", 1L).carrierCode("SF").waybillNo("SF-CONFLICT").build()))
                .hasMessage("return waybill already belongs to another shipment");

        when(shipmentMapper.selectByWaybill(1L, "SF", "SF10002")).thenReturn(null);
        service.execute(transition(ReturnFulfillmentOperation.HAND_OVER,
                "return-hand-over-002", 1L).carrierCode("SF").waybillNo("SF10002").build());
        service.execute(transition(ReturnFulfillmentOperation.MARK_IN_TRANSIT,
                "return-in-transit-002", 2L).build());
        service.execute(transition(ReturnFulfillmentOperation.RECEIVE,
                "return-receive-002", 3L).operatorId("receiver-1").build());
        assertThatThrownBy(() -> service.execute(transition(ReturnFulfillmentOperation.ACCEPT_INSPECTION,
                "return-inspection-rejected", 4L).operatorId("inspector-1").qualityStatus("DAMAGED")
                .dispositionAssessmentId("70000000-0000-4000-8000-000000000011")
                .dispositionCode("RESTOCK").conditionGrade("D")
                .inspectionEvidenceRef("evidence:return-002").build()))
                .hasMessage("inspection quality and disposition do not match");
    }

    @Test
    void shouldKeepReturnAggregateInsideTenantBoundary() {
        ReturnFulfillmentView created = service.execute(createCommand("return-create-003"));
        TenantContextHolder.setTenantId(2L);

        assertThatThrownBy(() -> service.getForAfterSale("after-sale-1", created.getReturnFulfillmentId()))
                .hasMessage("return Fulfillment does not belong to after sale");
        verify(fulfillmentMapper).selectTenant(2L, created.getReturnFulfillmentId());
    }

    private ReturnFulfillmentCommand createCommand(String key) {
        return ReturnFulfillmentCommand.builder().operation(ReturnFulfillmentOperation.CREATE)
                .idempotencyKey(key).runId("run-1").afterSaleId("after-sale-1")
                .afterSaleItemId("after-sale-item-1").orderId("order-1").orderItemId("order-item-1")
                .canonicalSkuId("sku-1").quantity(BigDecimal.ONE).ownerId("owner-1")
                .warehouseId("warehouse-1").uomCode("PCS")
                .correlationId("70000000-0000-4000-8000-000000000001")
                .occurredAt(Instant.parse("2026-07-15T01:00:00Z")).build();
    }

    private ReturnFulfillmentCommand.ReturnFulfillmentCommandBuilder transition(ReturnFulfillmentOperation operation,
                                                                                  String key, Long version) {
        return ReturnFulfillmentCommand.builder().operation(operation).idempotencyKey(key).runId("run-1")
                .returnFulfillmentId(fulfillment.getReturnFulfillmentId()).expectedVersion(version)
                .correlationId("70000000-0000-4000-8000-000000000001")
                .occurredAt(Instant.parse("2026-07-15T01:00:01Z").plusSeconds(version));
    }

    private void wireOperationStore() {
        doAnswer(invocation -> {
            Long tenantId = invocation.getArgument(0);
            String key = invocation.getArgument(1);
            String type = invocation.getArgument(2);
            String hash = invocation.getArgument(3);
            String attemptToken = invocation.getArgument(4);
            String storeKey = tenantId + ":" + key;
            ReturnFulfillmentOperationDO operation = operations.get(storeKey);
            if (operation == null) {
                operation = new ReturnFulfillmentOperationDO().setOperationId(operationSequence.incrementAndGet())
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
            ReturnFulfillmentOperationDO operation = operations.values().stream()
                    .filter(value -> Objects.equals(value.getTenantId(), invocation.getArgument(0))
                            && Objects.equals(value.getOperationId(), invocation.getArgument(1))).findFirst().orElseThrow();
            operation.setStatus(10).setReturnFulfillmentId(invocation.getArgument(2))
                    .setResultJson(invocation.getArgument(3));
            return 1;
        });
    }
}
