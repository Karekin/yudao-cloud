package cn.iocoder.yudao.module.cloudmold.integration.yudao.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventResult;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.integration.yudao.api.YudaoProcurementPromiseApi;
import cn.iocoder.yudao.module.cloudmold.integration.yudao.dal.YudaoPurchasePromiseMapper;
import cn.iocoder.yudao.module.cloudmold.integration.yudao.dal.YudaoPurchasePromiseRow;
import cn.iocoder.yudao.module.cloudmold.integration.yudao.procurement.LegacyProcurementReadPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class YudaoProcurementPromiseServiceTest {

    private final LegacyProcurementReadPort procurementReadPort = mock(LegacyProcurementReadPort.class);
    private final YudaoPurchasePromiseMapper promiseMapper = mock(YudaoPurchasePromiseMapper.class);
    private final YudaoCommandOperationService operationService = mock(YudaoCommandOperationService.class);
    private final OutboxAppender outboxAppender = mock(OutboxAppender.class);

    private final YudaoProcurementPromiseService service = new YudaoProcurementPromiseService(
            procurementReadPort, promiseMapper, operationService, outboxAppender);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        when(operationService.executeBoolean(anyString(), anyString(), any(), any()))
                .thenAnswer(invocation -> {
                    java.util.function.Supplier<Boolean> action = invocation.getArgument(3);
                    return action.get();
                });
        when(outboxAppender.append(any())).thenReturn(new AppendDomainEventResult(
                "evt-1", "hash", false));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldCreatePurchasePromiseAndAppendGovernedEvent() {
        LegacyProcurementReadPort.PurchaseOrderSnapshot order = approvedOrder();
        when(promiseMapper.selectForUpdateByLineId(1L, 31L)).thenReturn(null);
        when(promiseMapper.insert(any())).thenReturn(1);
        when(procurementReadPort.getPurchaseOrder(51L)).thenReturn(order);
        when(promiseMapper.selectByLineId(1L, 31L)).thenAnswer(invocation -> captureRow(invocation, order));

        YudaoProcurementPromiseApi.PurchasePromiseView result = service.savePurchasePromise(
                new YudaoProcurementPromiseApi.PurchasePromiseCommand(
                        "promise-op-001", "procurement-promise-run-001", 51L, 31L,
                        "2026-07-20T18:00:00+08:00", "Asia/Shanghai", 120, 30,
                        "ACTIVE", "supplier confirmed window"));

        assertThat(result.purchaseOrderLineId()).isEqualTo(31L);
        assertThat(result.graceMinutes()).isEqualTo(120);
        assertThat(result.pauseMinutes()).isEqualTo(30);
        assertThat(result.status()).isEqualTo("ACTIVE");
        ArgumentCaptor<AppendDomainEventCommand> eventCaptor = ArgumentCaptor.forClass(AppendDomainEventCommand.class);
        verify(outboxAppender).append(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo("procurement.purchase_promise.status_changed");
        assertThat(eventCaptor.getValue().getPayload()).containsEntry("purchase_order_line_id", 31L)
                .containsEntry("current_status", "ACTIVE")
                .containsEntry("promise_timezone", "Asia/Shanghai");
    }

    @Test
    void shouldBumpVersionWhenPromiseIsUpdated() {
        LegacyProcurementReadPort.PurchaseOrderSnapshot order = approvedOrder();
        YudaoPurchasePromiseRow existing = new YudaoPurchasePromiseRow()
                .setPromiseId("10000000-0000-4000-8000-000000000001")
                .setPromiseKey("ERP_PO_LINE:51:31")
                .setTenantId(1L)
                .setPurchaseOrderId(51L)
                .setPurchaseOrderNo("CG-51")
                .setPurchaseOrderLineId(31L)
                .setSupplierId(11L)
                .setProductId(21L)
                .setProductUnitId(22L)
                .setOrderedQuantity(new BigDecimal("10"))
                .setPromisedReceiptAt(LocalDateTime.of(2026, 7, 20, 10, 0))
                .setPromiseTimezone("Asia/Shanghai")
                .setGraceMinutes(0)
                .setPauseMinutes(0)
                .setPromiseFrozenAt(LocalDateTime.of(2026, 7, 18, 2, 0))
                .setStatus("ACTIVE")
                .setVersion(2L)
                .setRunId("old-run")
                .setCreatedAt(LocalDateTime.of(2026, 7, 18, 2, 0))
                .setUpdatedAt(LocalDateTime.of(2026, 7, 18, 2, 0));
        when(procurementReadPort.getPurchaseOrder(51L)).thenReturn(order);
        when(promiseMapper.selectForUpdateByLineId(1L, 31L)).thenReturn(existing);
        when(promiseMapper.update(any(), eq(2L))).thenReturn(1);
        when(promiseMapper.selectByLineId(1L, 31L)).thenAnswer(invocation -> updatedRow(existing));

        YudaoProcurementPromiseApi.PurchasePromiseView result = service.savePurchasePromise(
                new YudaoProcurementPromiseApi.PurchasePromiseCommand(
                        "promise-op-002", "procurement-promise-run-002", 51L, 31L,
                        "2026-07-21T18:00:00+08:00", "Asia/Shanghai", 60, 0,
                        "CANCELLED", "supplier missed slot"));

        assertThat(result.version()).isEqualTo(3L);
        assertThat(result.status()).isEqualTo("CANCELLED");
        verify(promiseMapper).update(any(), eq(2L));
    }

    @Test
    void shouldRejectLineOutsidePurchaseOrder() {
        when(procurementReadPort.getPurchaseOrder(51L)).thenReturn(new LegacyProcurementReadPort.PurchaseOrderSnapshot(
                51L, "CG-51", 11L, List.of(orderLine(99L, new BigDecimal("10")))));

        assertThatThrownBy(() -> service.savePurchasePromise(
                new YudaoProcurementPromiseApi.PurchasePromiseCommand(
                        "promise-op-003", "procurement-promise-run-003", 51L, 31L,
                        "2026-07-20T18:00:00+08:00", "Asia/Shanghai", 0, 0,
                        "ACTIVE", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("purchase order line does not belong");
    }

    @Test
    void shouldListTypedPurchaseOrderLinesForSkillComposition() {
        when(procurementReadPort.getPurchaseOrder(51L)).thenReturn(new LegacyProcurementReadPort.PurchaseOrderSnapshot(
                51L, "CG-51", 11L, List.of(orderLine(31L, new BigDecimal("10"), new BigDecimal("2"), BigDecimal.ONE))));

        List<YudaoProcurementPromiseApi.PurchaseOrderLineView> result =
                service.listPurchaseOrderLines(51L);

        assertThat(result).singleElement().satisfies(view -> {
            assertThat(view.purchaseOrderLineId()).isEqualTo(31L);
            assertThat(view.purchaseOrderId()).isEqualTo(51L);
            assertThat(view.productId()).isEqualTo(21L);
            assertThat(view.orderedQuantity()).isEqualByComparingTo("10");
            assertThat(view.receivedQuantity()).isEqualByComparingTo("2");
            assertThat(view.returnedQuantity()).isEqualByComparingTo("1");
        });
    }

    @Test
    void shouldFailClosedWhenProcurementLineViewIsMissing() {
        when(procurementReadPort.getPurchaseOrder(51L)).thenReturn(new LegacyProcurementReadPort.PurchaseOrderSnapshot(
                51L, "CG-51", 11L, List.of()));

        assertThatThrownBy(() -> service.savePurchasePromise(
                new YudaoProcurementPromiseApi.PurchasePromiseCommand(
                        "promise-op-004", "procurement-promise-run-004", 51L, 31L,
                        "2026-07-20T18:00:00+08:00", "Asia/Shanghai", 0, 0,
                        "ACTIVE", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("purchase order line does not belong");
    }

    private static LegacyProcurementReadPort.PurchaseOrderSnapshot approvedOrder() {
        return new LegacyProcurementReadPort.PurchaseOrderSnapshot(
                51L,
                "CG-51",
                11L,
                List.of(orderLine(31L, new BigDecimal("10"), BigDecimal.ZERO, BigDecimal.ZERO)));
    }

    private static LegacyProcurementReadPort.PurchaseOrderLineSnapshot orderLine(
            Long id, BigDecimal count, BigDecimal receivedQuantity, BigDecimal returnedQuantity) {
        return new LegacyProcurementReadPort.PurchaseOrderLineSnapshot(
                id, 51L, 21L, 22L, count, receivedQuantity, returnedQuantity);
    }

    private static LegacyProcurementReadPort.PurchaseOrderLineSnapshot orderLine(Long id, BigDecimal count) {
        return orderLine(id, count, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private static YudaoPurchasePromiseRow captureRow(org.mockito.invocation.InvocationOnMock ignored,
                                                      LegacyProcurementReadPort.PurchaseOrderSnapshot order) {
        LegacyProcurementReadPort.PurchaseOrderLineSnapshot line = order.lines().get(0);
        return new YudaoPurchasePromiseRow()
                .setPromiseId("10000000-0000-4000-8000-000000000010")
                .setPromiseKey("ERP_PO_LINE:51:31")
                .setTenantId(1L)
                .setPurchaseOrderId(order.purchaseOrderId())
                .setPurchaseOrderNo(order.purchaseOrderNo())
                .setPurchaseOrderLineId(line.purchaseOrderLineId())
                .setSupplierId(order.supplierId())
                .setProductId(line.productId())
                .setProductUnitId(line.productUnitId())
                .setOrderedQuantity(line.orderedQuantity())
                .setPromisedReceiptAt(LocalDateTime.of(2026, 7, 20, 10, 0))
                .setPromiseTimezone("Asia/Shanghai")
                .setGraceMinutes(120)
                .setPauseMinutes(30)
                .setPromiseFrozenAt(LocalDateTime.of(2026, 7, 18, 4, 0))
                .setStatus("ACTIVE")
                .setVersion(1L)
                .setRunId("procurement-promise-run-001")
                .setReason("supplier confirmed window");
    }

    private static YudaoPurchasePromiseRow updatedRow(YudaoPurchasePromiseRow existing) {
        return new YudaoPurchasePromiseRow()
                .setPromiseId(existing.getPromiseId())
                .setPromiseKey(existing.getPromiseKey())
                .setTenantId(existing.getTenantId())
                .setPurchaseOrderId(existing.getPurchaseOrderId())
                .setPurchaseOrderNo(existing.getPurchaseOrderNo())
                .setPurchaseOrderLineId(existing.getPurchaseOrderLineId())
                .setSupplierId(existing.getSupplierId())
                .setProductId(existing.getProductId())
                .setProductUnitId(existing.getProductUnitId())
                .setOrderedQuantity(existing.getOrderedQuantity())
                .setPromisedReceiptAt(LocalDateTime.of(2026, 7, 21, 10, 0))
                .setPromiseTimezone(existing.getPromiseTimezone())
                .setGraceMinutes(60)
                .setPauseMinutes(0)
                .setPromiseFrozenAt(LocalDateTime.of(2026, 7, 18, 5, 0))
                .setStatus("CANCELLED")
                .setVersion(3L)
                .setRunId("procurement-promise-run-002")
                .setReason("supplier missed slot");
    }
}
