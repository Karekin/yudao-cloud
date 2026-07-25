package cn.iocoder.yudao.module.cloudmold.integration.yudao.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.catalog.api.CatalogSkuProjectionApi;
import cn.iocoder.yudao.module.cloudmold.catalog.api.CatalogSkuProjectionView;
import cn.iocoder.yudao.module.cloudmold.integration.yudao.dal.YudaoWmsReceiptInventoryBridgeMapper;
import cn.iocoder.yudao.module.cloudmold.integration.yudao.dal.YudaoWmsReceiptInventoryBridgeRow;
import cn.iocoder.yudao.module.cloudmold.integration.yudao.dal.mysql.LegacyCatalogProjectionMapper;
import cn.iocoder.yudao.module.cloudmold.integration.yudao.wms.LegacyWmsPhysicalOperationsPort;
import cn.iocoder.yudao.module.cloudmold.integration.yudao.wms.LegacyWmsSkuReadPort;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryV3Command;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryV3CommandApi;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryV3CommandResult;
import cn.iocoder.yudao.module.cloudmold.merchant.api.SourceMappingQueryApi;
import cn.iocoder.yudao.module.cloudmold.merchant.api.SourceMappingView;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.WarehouseNetworkView;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.WarehouseSourceMappingQueryApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class YudaoWmsReceiptInventoryBridgeServiceTest {

    private final YudaoWmsReceiptInventoryBridgeMapper bridgeMapper = mock(YudaoWmsReceiptInventoryBridgeMapper.class);
    private final LegacyCatalogProjectionMapper projectionMapper = mock(LegacyCatalogProjectionMapper.class);
    private final CatalogSkuProjectionApi catalogSkuProjectionApi = mock(CatalogSkuProjectionApi.class);
    private final SourceMappingQueryApi sourceMappingQueryApi = mock(SourceMappingQueryApi.class);
    private final WarehouseSourceMappingQueryApi warehouseSourceMappingQueryApi = mock(WarehouseSourceMappingQueryApi.class);
    private final InventoryV3CommandApi inventoryV3CommandApi = mock(InventoryV3CommandApi.class);
    private final LegacyWmsSkuReadPort wmsSkuReadPort = mock(LegacyWmsSkuReadPort.class);
    private final YudaoWmsReceiptInventoryBridgeService service =
            new YudaoWmsReceiptInventoryBridgeService(
                    bridgeMapper, projectionMapper, catalogSkuProjectionApi, sourceMappingQueryApi,
                    warehouseSourceMappingQueryApi, inventoryV3CommandApi, wmsSkuReadPort);

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldPrepareAndApplySingleReceiptLineExactlyOnce() {
        when(sourceMappingQueryApi.resolveActive(any())).thenReturn(new SourceMappingView()
                .setTargetType("MERCHANT").setTargetId("f518dcaa-4695-4db0-a7bc-7d3aa29f649e"));
        when(warehouseSourceMappingQueryApi.resolveReadyNetwork(any(), any())).thenReturn(WarehouseNetworkView.builder()
                .mappingId("mapping-warehouse-32")
                .warehouseId("87d569d4-4f90-4b01-a491-9f7549126d8e")
                .zoneId("6f917318-1a95-4eaf-ac11-6a5e8df940b5")
                .locationId("95d0b2a1-8d32-4475-aecc-aa79eea2d9c7")
                .build());
        when(wmsSkuReadPort.getSku(5101L)).thenReturn(new LegacyWmsSkuReadPort.WmsSkuSnapshot(
                5101L, 7101L, "SKU-BLK-42", "6901234567890", "EA"));
        when(projectionMapper.selectWmsCanonicalSkuCandidates(1L, "SKU-BLK-42", "6901234567890"))
                .thenReturn(List.of("847914dd-55a2-4c0e-bad0-962b8f4ee3de"));
        CatalogSkuProjectionView sku = new CatalogSkuProjectionView();
        sku.setCanonicalSkuId("847914dd-55a2-4c0e-bad0-962b8f4ee3de");
        sku.setSkuCode("SKU-BLK-42");
        sku.setPrimaryBarcode("6901234567890");
        sku.setBaseUomCode("EA");
        sku.setCatalogStatus("ACTIVE");
        when(catalogSkuProjectionApi.getActiveSku("847914dd-55a2-4c0e-bad0-962b8f4ee3de")).thenReturn(sku);
        YudaoWmsReceiptInventoryBridgeRow applied = new YudaoWmsReceiptInventoryBridgeRow()
                .setReceiptOrderLineId(4101L);
        when(bridgeMapper.selectByReceiptOrderId(1L, 41L)).thenReturn(List.of(), List.of(applied));
        when(inventoryV3CommandApi.execute(any())).thenReturn(InventoryV3CommandResult.builder()
                .operationId(801L).ledgerTransactionId(901L).balanceId("balance-1")
                .aggregateVersion(4L).build());
        when(bridgeMapper.insert(any())).thenReturn(1);

        var prepared = service.prepare(receiptContext());
        service.apply(prepared);
        service.apply(prepared);

        verify(inventoryV3CommandApi, times(1)).execute(argThat((InventoryV3Command command) ->
                "wms-receipt:41:line:4101:receive".equals(command.getIdempotencyKey())
                        && "WMS_RECEIPT_ORDER".equals(command.getBusinessType())
                        && "wms-receipt-order:41".equals(command.getBusinessId())
                        && "wms-receipt-line:4101".equals(command.getBusinessItemId())
                        && "87d569d4-4f90-4b01-a491-9f7549126d8e".equals(command.getWarehouseId())
                        && "95d0b2a1-8d32-4475-aecc-aa79eea2d9c7".equals(command.getLocationId())
                        && "847914dd-55a2-4c0e-bad0-962b8f4ee3de".equals(command.getCanonicalSkuId())
                        && new BigDecimal("2.000000").compareTo(command.getQuantity()) == 0));
        verify(bridgeMapper, times(1)).insert(argThat(row ->
                row.getReceiptOrderId().equals(41L)
                        && row.getReceiptOrderLineId().equals(4101L)
                        && row.getInventoryLedgerTransactionId().equals(901L)
                        && "NOT_TRACKED".equals(row.getLotMappingStatus())));
    }

    @Test
    void shouldFailClosedWhenReceiptHasMultipleLines() {
        LegacyWmsPhysicalOperationsPort.ReceiptOrderContext multiLine =
                new LegacyWmsPhysicalOperationsPort.ReceiptOrderContext(
                        receiptContext().order(),
                        7L,
                        List.of(
                                new LegacyWmsPhysicalOperationsPort.ReceiptLineSnapshot(
                                        4101L, 5101L, 100L, BigDecimal.ONE, BigDecimal.TEN, BigDecimal.TEN),
                                new LegacyWmsPhysicalOperationsPort.ReceiptLineSnapshot(
                                        4102L, 5102L, 100L, BigDecimal.ONE, BigDecimal.TEN, BigDecimal.TEN)));

        assertThatThrownBy(() -> service.prepare(multiLine))
                .hasMessage("WMS receipt bridging supports exactly one receipt line");
        verifyNoInteractions(inventoryV3CommandApi);
    }

    @Test
    void shouldFailClosedWhenCanonicalSkuMappingIsAmbiguous() {
        when(sourceMappingQueryApi.resolveActive(any())).thenReturn(new SourceMappingView()
                .setTargetType("MERCHANT").setTargetId("f518dcaa-4695-4db0-a7bc-7d3aa29f649e"));
        when(warehouseSourceMappingQueryApi.resolveReadyNetwork(any(), any())).thenReturn(WarehouseNetworkView.builder()
                .mappingId("mapping-warehouse-32")
                .warehouseId("87d569d4-4f90-4b01-a491-9f7549126d8e")
                .zoneId("6f917318-1a95-4eaf-ac11-6a5e8df940b5")
                .locationId("95d0b2a1-8d32-4475-aecc-aa79eea2d9c7")
                .build());
        when(wmsSkuReadPort.getSku(5101L)).thenReturn(new LegacyWmsSkuReadPort.WmsSkuSnapshot(
                5101L, 7101L, "SKU-BLK-42", "6901234567890", "EA"));
        when(projectionMapper.selectWmsCanonicalSkuCandidates(1L, "SKU-BLK-42", "6901234567890"))
                .thenReturn(List.of("sku-1", "sku-2"));

        assertThatThrownBy(() -> service.prepare(receiptContext()))
                .hasMessage("receipt line canonical SKU mapping is ambiguous");
        verifyNoInteractions(inventoryV3CommandApi);
    }

    private static LegacyWmsPhysicalOperationsPort.ReceiptOrderContext receiptContext() {
        return new LegacyWmsPhysicalOperationsPort.ReceiptOrderContext(
                new LegacyWmsPhysicalOperationsPort.PhysicalOrderSnapshot(
                        "WMS", "RECEIPT_ORDER", 41L, "RK-41", 0,
                        "2026-07-25T10:00:00", 100L,
                        new BigDecimal("2.000000"), new BigDecimal("20.00"), "demo"),
                7L,
                List.of(new LegacyWmsPhysicalOperationsPort.ReceiptLineSnapshot(
                        4101L, 5101L, 100L, new BigDecimal("2.000000"),
                        new BigDecimal("10.00"), new BigDecimal("20.00"))));
    }
}
