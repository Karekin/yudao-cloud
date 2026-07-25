package cn.iocoder.yudao.module.cloudmold.integration.yudao.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.catalog.api.CatalogSkuProjectionView;
import cn.iocoder.yudao.module.cloudmold.integration.yudao.api.YudaoWmsCommandApi;
import cn.iocoder.yudao.module.cloudmold.integration.yudao.wms.LegacyWmsMasterDataPort;
import cn.iocoder.yudao.module.cloudmold.integration.yudao.wms.LegacyWmsPhysicalOperationsPort;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.WarehouseNetworkView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class YudaoWmsPhysicalOperationsServiceTest {

    private final LegacyWmsMasterDataPort masterDataPort = mock(LegacyWmsMasterDataPort.class);
    private final LegacyWmsPhysicalOperationsPort physicalOperationsPort = mock(LegacyWmsPhysicalOperationsPort.class);
    private final YudaoWmsReceiptInventoryBridgeService receiptInventoryBridgeService =
            mock(YudaoWmsReceiptInventoryBridgeService.class);
    private final YudaoCommandOperationService operationService = mock(YudaoCommandOperationService.class);
    private final YudaoWmsPhysicalOperationsService service = new YudaoWmsPhysicalOperationsService(
            masterDataPort, physicalOperationsPort, receiptInventoryBridgeService, operationService);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        when(operationService.executeTyped(anyString(), anyString(), any(), eq(YudaoWmsCommandApi.PhysicalOperationResult.class), any()))
                .thenAnswer(invocation -> ((Supplier<YudaoWmsCommandApi.PhysicalOperationResult>) invocation.getArgument(4)).get());
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldDelegateMasterDataCommandsToTheLegacyBoundary() {
        var merchant = new YudaoWmsCommandApi.MerchantCommand(
                "merchant-create-001", "M1", "Merchant", 1, null,
                null, null, null, null, null);
        var warehouse = new YudaoWmsCommandApi.WarehouseCommand(
                "warehouse-create-001", "W1", "Warehouse", 1, null);
        var category = new YudaoWmsCommandApi.ItemCategoryCommand(
                "category-create-001", 0L, "C1", "Category", 1, 0);
        var item = new YudaoWmsCommandApi.ItemCommand(
                "item-create-001", "I1", "Item", 1L, "EA", null, List.of());
        when(masterDataPort.createMerchant(merchant)).thenReturn(11L);
        when(masterDataPort.createWarehouse(warehouse)).thenReturn(12L);
        when(masterDataPort.createItemCategory(category)).thenReturn(13L);
        when(masterDataPort.createItem(item)).thenReturn(14L);

        assertThat(service.createMerchant(merchant)).isEqualTo(11L);
        assertThat(service.createWarehouse(warehouse)).isEqualTo(12L);
        assertThat(service.createItemCategory(category)).isEqualTo(13L);
        assertThat(service.createItem(item)).isEqualTo(14L);

        verify(masterDataPort).createMerchant(merchant);
        verify(masterDataPort).createWarehouse(warehouse);
        verify(masterDataPort).createItemCategory(category);
        verify(masterDataPort).createItem(item);
    }

    @Test
    void shouldCompleteReceiptOrderThroughAnIdempotentTenantAwareBoundary() {
        AtomicLong seenTenant = new AtomicLong(-1L);
        when(physicalOperationsPort.getReceiptOrderContext(41L)).thenReturn(receiptContext(0));
        when(receiptInventoryBridgeService.prepare(any())).thenReturn(preparedReceipt());
        when(physicalOperationsPort.completeReceiptOrder(any())).thenAnswer(invocation -> {
            seenTenant.set(TenantContextHolder.getRequiredTenantId());
            return new LegacyWmsPhysicalOperationsPort.PhysicalOrderSnapshot(
                    "WMS", "RECEIPT_ORDER", 41L, "RK-41", 4,
                    "2026-07-25T11:00:00", 100L, BigDecimal.ONE,
                    BigDecimal.TEN, "done");
        });
        when(physicalOperationsPort.getReceiptOrder(41L)).thenReturn(new LegacyWmsPhysicalOperationsPort.PhysicalOrderSnapshot(
                "WMS", "RECEIPT_ORDER", 41L, "RK-41", 0,
                "2026-07-25T10:00:00", 100L, BigDecimal.ONE, BigDecimal.TEN, "draft"));

        var result = service.completeReceiptOrder(new YudaoWmsCommandApi.DocumentActionCommand(
                "receipt-complete-001", 41L, 0, null));

        assertThat(result.success()).isTrue();
        assertThat(result.supported()).isTrue();
        assertThat(result.snapshot().status()).isEqualTo(4);
        assertThat(seenTenant.get()).isEqualTo(1L);
        verify(receiptInventoryBridgeService).apply(any());
        ArgumentCaptor<String> operationCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(operationService).executeTyped(operationCaptor.capture(), keyCaptor.capture(), any(),
                eq(YudaoWmsCommandApi.PhysicalOperationResult.class), any());
        assertThat(operationCaptor.getValue()).isEqualTo("COMPLETE_WMS_RECEIPT_ORDER");
        assertThat(keyCaptor.getValue()).isEqualTo("receipt-complete-001");
    }

    @Test
    void shouldReturnStateConflictWhenThePhysicalDocumentIsNotFinished() {
        when(physicalOperationsPort.getReceiptOrderContext(41L)).thenReturn(receiptContext(0));
        when(receiptInventoryBridgeService.prepare(any())).thenReturn(preparedReceipt());
        when(physicalOperationsPort.completeReceiptOrder(any())).thenReturn(
                new LegacyWmsPhysicalOperationsPort.PhysicalOrderSnapshot(
                        "WMS", "RECEIPT_ORDER", 41L, "RK-41", 0,
                        "2026-07-25T11:00:00", 100L, BigDecimal.ONE,
                        BigDecimal.TEN, "draft"));

        var result = service.completeReceiptOrder(new YudaoWmsCommandApi.DocumentActionCommand(
                "receipt-complete-002", 41L, 0, null));

        assertThat(result.success()).isFalse();
        assertThat(result.supported()).isTrue();
        assertThat(result.failureCode()).isEqualTo("STATE_CONFLICT");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldRollBackTheOperationBoundarySoAFailedPhysicalActionCanRetry() {
        AtomicBoolean failureCrossedOperationBoundary = new AtomicBoolean();
        when(operationService.executeTyped(anyString(), anyString(), any(),
                eq(YudaoWmsCommandApi.PhysicalOperationResult.class), any()))
                .thenAnswer(invocation -> {
                    try {
                        return ((Supplier<YudaoWmsCommandApi.PhysicalOperationResult>)
                                invocation.getArgument(4)).get();
                    } catch (RuntimeException failure) {
                        failureCrossedOperationBoundary.set(true);
                        throw failure;
                    }
                });
        var draft = new LegacyWmsPhysicalOperationsPort.PhysicalOrderSnapshot(
                "WMS", "RECEIPT_ORDER", 41L, "RK-41", 0,
                "2026-07-25T10:00:00", 100L, BigDecimal.ONE,
                BigDecimal.TEN, "draft");
        var completed = new LegacyWmsPhysicalOperationsPort.PhysicalOrderSnapshot(
                "WMS", "RECEIPT_ORDER", 41L, "RK-41", 4,
                "2026-07-25T11:00:00", 100L, BigDecimal.ONE,
                BigDecimal.TEN, "done");
        when(physicalOperationsPort.getReceiptOrderContext(41L)).thenReturn(new LegacyWmsPhysicalOperationsPort.ReceiptOrderContext(
                draft, 7L, List.of(new LegacyWmsPhysicalOperationsPort.ReceiptLineSnapshot(
                4101L, 5101L, 100L, BigDecimal.ONE, BigDecimal.TEN, BigDecimal.TEN))));
        when(receiptInventoryBridgeService.prepare(any())).thenReturn(preparedReceipt());
        when(physicalOperationsPort.completeReceiptOrder(any()))
                .thenThrow(new IllegalStateException("temporary lock conflict"))
                .thenReturn(completed);
        var command = new YudaoWmsCommandApi.DocumentActionCommand(
                "receipt-complete-retry-001", 41L, 0, null);

        var failed = service.completeReceiptOrder(command);
        var retried = service.completeReceiptOrder(command);

        assertThat(failed.success()).isFalse();
        assertThat(failed.failureCode()).isEqualTo("STATE_CONFLICT");
        assertThat(failureCrossedOperationBoundary).isTrue();
        assertThat(retried.success()).isTrue();
        verify(physicalOperationsPort, times(2)).completeReceiptOrder(any());
    }

    @Test
    void shouldFailClosedWhenTheBridgeCannotReadBackTheCompletedDocument() {
        when(physicalOperationsPort.getReceiptOrderContext(41L)).thenReturn(receiptContext(0));
        when(receiptInventoryBridgeService.prepare(any())).thenReturn(preparedReceipt());
        when(physicalOperationsPort.completeReceiptOrder(any())).thenReturn(null);

        var result = service.completeReceiptOrder(new YudaoWmsCommandApi.DocumentActionCommand(
                "receipt-complete-003", 41L, 0, null));

        assertThat(result.success()).isFalse();
        assertThat(result.failureCode()).isEqualTo("INVALID_ARGUMENT");
    }

    @Test
    void shouldReturnUnsupportedWhenExpectedVersionIsRequested() {
        var result = service.completeReceiptOrder(new YudaoWmsCommandApi.DocumentActionCommand(
                "receipt-complete-004", 41L, 0, 3L));

        assertThat(result.success()).isFalse();
        assertThat(result.supported()).isFalse();
        assertThat(result.failureCode()).isEqualTo("UNSUPPORTED_VERSION_CHECK");
        verifyNoInteractions(physicalOperationsPort);
    }

    @Test
    void shouldReturnStatusMismatchWithoutMutatingWhenCurrentStatusDiffers() {
        when(physicalOperationsPort.getReceiptOrderContext(41L)).thenReturn(receiptContext(5));

        var result = service.completeReceiptOrder(new YudaoWmsCommandApi.DocumentActionCommand(
                "receipt-complete-005", 41L, 0, null));

        assertThat(result.success()).isFalse();
        assertThat(result.failureCode()).isEqualTo("UNEXPECTED_CURRENT_STATUS");
        verify(physicalOperationsPort, never()).completeReceiptOrder(any());
    }

    @Test
    void shouldReplayFinishedReceiptWithoutMutatingWhenCanonicalEvidenceAlreadyExists() {
        var finished = new LegacyWmsPhysicalOperationsPort.PhysicalOrderSnapshot(
                "WMS", "RECEIPT_ORDER", 41L, "RK-41", 4,
                "2026-07-25T11:00:00", 100L, BigDecimal.ONE, BigDecimal.TEN, "done");
        when(physicalOperationsPort.getReceiptOrderContext(41L)).thenReturn(
                new LegacyWmsPhysicalOperationsPort.ReceiptOrderContext(
                        finished, 7L, List.of(new LegacyWmsPhysicalOperationsPort.ReceiptLineSnapshot(
                        4101L, 5101L, 100L, BigDecimal.ONE, BigDecimal.TEN, BigDecimal.TEN))));
        when(receiptInventoryBridgeService.resolveReplay(eq(41L), any()))
                .thenReturn(new YudaoWmsReceiptInventoryBridgeService.ReceiptBridgeReplay(true, null));

        var result = service.completeReceiptOrder(new YudaoWmsCommandApi.DocumentActionCommand(
                "receipt-complete-replay-001", 41L, 0, null));

        assertThat(result.success()).isTrue();
        verify(physicalOperationsPort, never()).completeReceiptOrder(any());
        verify(receiptInventoryBridgeService, never()).apply(any());
    }

    @Test
    void shouldReportPutawayAsExplicitlyUnsupported() {
        var result = service.completePutaway(new YudaoWmsCommandApi.DocumentActionCommand(
                "putaway-complete-001", 41L, 0, null));

        assertThat(result.success()).isFalse();
        assertThat(result.supported()).isFalse();
        assertThat(result.failureCode()).isEqualTo("UNSUPPORTED_CAPABILITY");
        assertThat(result.operationType()).isEqualTo("COMPLETE_WMS_PUTAWAY");
    }

    @Test
    void shouldReportPickingAsExplicitlyUnsupported() {
        var result = service.completePicking(new YudaoWmsCommandApi.DocumentActionCommand(
                "picking-complete-001", 41L, 0, null));

        assertThat(result.success()).isFalse();
        assertThat(result.supported()).isFalse();
        assertThat(result.failureCode()).isEqualTo("UNSUPPORTED_CAPABILITY");
        assertThat(result.operationType()).isEqualTo("COMPLETE_WMS_PICKING");
    }

    private static LegacyWmsPhysicalOperationsPort.ReceiptOrderContext receiptContext(int status) {
        return new LegacyWmsPhysicalOperationsPort.ReceiptOrderContext(
                new LegacyWmsPhysicalOperationsPort.PhysicalOrderSnapshot(
                        "WMS", "RECEIPT_ORDER", 41L, "RK-41", status,
                        "2026-07-25T10:00:00", 100L, BigDecimal.ONE, BigDecimal.TEN, "draft"),
                7L,
                List.of(new LegacyWmsPhysicalOperationsPort.ReceiptLineSnapshot(
                        4101L, 5101L, 100L, BigDecimal.ONE, BigDecimal.TEN, BigDecimal.TEN)));
    }

    private static YudaoWmsReceiptInventoryBridgeService.PreparedReceipt preparedReceipt() {
        CatalogSkuProjectionView sku = new CatalogSkuProjectionView();
        sku.setCanonicalSkuId("7e932174-170c-4c13-b7fd-4a966b3d01f8");
        sku.setBaseUomCode("EA");
        WarehouseNetworkView network = WarehouseNetworkView.builder()
                .mappingId("mapping-1")
                .warehouseId("655f0df2-9d4d-46f3-946e-bdbfbc3a294f")
                .zoneId("df43b6f8-d496-4103-8767-44bb3fd652bc")
                .locationId("866dc968-ee12-4971-a9ce-c0137dddf2a6")
                .build();
        return new YudaoWmsReceiptInventoryBridgeService.PreparedReceipt(
                41L, "RK-41", 7L, 100L,
                new LegacyWmsPhysicalOperationsPort.ReceiptLineSnapshot(
                        4101L, 5101L, 100L, BigDecimal.ONE, BigDecimal.TEN, BigDecimal.TEN),
                "bf446dd5-934b-4a53-a683-4a6b8854b1a0",
                sku,
                network,
                java.time.Instant.parse("2026-07-25T10:00:00Z"));
    }
}
