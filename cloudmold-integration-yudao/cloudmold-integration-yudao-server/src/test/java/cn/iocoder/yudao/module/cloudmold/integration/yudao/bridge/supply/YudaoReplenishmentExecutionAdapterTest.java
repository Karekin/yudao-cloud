package cn.iocoder.yudao.module.cloudmold.integration.yudao.bridge.supply;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.module.cloudmold.catalog.api.CatalogSkuProjectionApi;
import cn.iocoder.yudao.module.cloudmold.catalog.api.CatalogSkuProjectionView;
import cn.iocoder.yudao.module.cloudmold.integration.yudao.api.YudaoErpCommandApi;
import cn.iocoder.yudao.module.cloudmold.integration.yudao.api.YudaoLegacyMasterDataQueryApi;
import cn.iocoder.yudao.module.cloudmold.integration.yudao.api.YudaoWmsCommandApi;
import cn.iocoder.yudao.module.cloudmold.supplyplanning.api.ReplenishmentExecutionPort;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.WarehouseNetworkView;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.WarehouseSourceMappingQueryApi;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.WarehouseSourceReference;
import cn.iocoder.yudao.module.erp.dal.dataobject.product.ErpProductDO;
import cn.iocoder.yudao.module.erp.dal.dataobject.product.ErpProductUnitDO;
import cn.iocoder.yudao.module.erp.dal.dataobject.purchase.ErpPurchaseOrderDO;
import cn.iocoder.yudao.module.erp.dal.dataobject.purchase.ErpPurchaseOrderItemDO;
import cn.iocoder.yudao.module.erp.enums.ErpAuditStatus;
import cn.iocoder.yudao.module.erp.service.product.ErpProductService;
import cn.iocoder.yudao.module.erp.service.product.ErpProductUnitService;
import cn.iocoder.yudao.module.erp.service.purchase.ErpPurchaseOrderService;
import cn.iocoder.yudao.module.wms.dal.dataobject.md.item.WmsItemDO;
import cn.iocoder.yudao.module.wms.dal.dataobject.md.item.WmsItemSkuDO;
import cn.iocoder.yudao.module.wms.dal.dataobject.order.movement.WmsMovementOrderDO;
import cn.iocoder.yudao.module.wms.dal.dataobject.order.movement.WmsMovementOrderDetailDO;
import cn.iocoder.yudao.module.wms.enums.order.WmsOrderStatusEnum;
import cn.iocoder.yudao.module.wms.service.md.item.WmsItemService;
import cn.iocoder.yudao.module.wms.service.md.item.WmsItemSkuService;
import cn.iocoder.yudao.module.wms.service.order.movement.WmsMovementOrderDetailService;
import cn.iocoder.yudao.module.wms.service.order.movement.WmsMovementOrderService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class YudaoReplenishmentExecutionAdapterTest {
    private final YudaoErpCommandApi erp = mock(YudaoErpCommandApi.class);
    private final YudaoWmsCommandApi wms = mock(YudaoWmsCommandApi.class);
    private final CatalogSkuProjectionApi catalog = mock(CatalogSkuProjectionApi.class);
    private final WarehouseSourceMappingQueryApi warehouseMappings = mock(WarehouseSourceMappingQueryApi.class);
    private final YudaoLegacyMasterDataQueryApi legacyMasterData = mock(YudaoLegacyMasterDataQueryApi.class);
    private final ErpProductService erpProductService = mock(ErpProductService.class);
    private final ErpProductUnitService erpProductUnitService = mock(ErpProductUnitService.class);
    private final ErpPurchaseOrderService erpPurchaseOrderService = mock(ErpPurchaseOrderService.class);
    private final WmsItemService wmsItemService = mock(WmsItemService.class);
    private final WmsItemSkuService wmsItemSkuService = mock(WmsItemSkuService.class);
    private final WmsMovementOrderService wmsMovementOrderService = mock(WmsMovementOrderService.class);
    private final WmsMovementOrderDetailService wmsMovementOrderDetailService =
            mock(WmsMovementOrderDetailService.class);
    private final YudaoReplenishmentExecutionAdapter adapter =
            new YudaoReplenishmentExecutionAdapter(
                    erp, wms, catalog, warehouseMappings, legacyMasterData,
                    erpProductService, erpProductUnitService, erpPurchaseOrderService,
                    wmsItemService, wmsItemSkuService, wmsMovementOrderService,
                    wmsMovementOrderDetailService);

    @Test
    void createsPreparePurchaseOrderOnlyAfterLiveMappingAndReadbackMatch() {
        CatalogSkuProjectionView sku = catalogSku("sku-01", "JACKET-BLK-42", "6901234567890", "EA");
        when(catalog.getActiveSku("sku-01")).thenReturn(sku);
        when(erpProductService.getProduct(13L)).thenReturn(ErpProductDO.builder()
                .id(13L).name("Classic Jacket").barCode("6901234567890")
                .unitId(14L).status(CommonStatusEnum.ENABLE.getStatus()).build());
        when(erpProductUnitService.getProductUnit(14L)).thenReturn(ErpProductUnitDO.builder()
                .id(14L).name("EA").status(CommonStatusEnum.ENABLE.getStatus()).build());
        when(erp.createPurchaseOrder(any())).thenReturn(781L);
        when(erpPurchaseOrderService.getPurchaseOrder(781L)).thenReturn(ErpPurchaseOrderDO.builder()
                .id(781L).no("PO-20260725-01").status(ErpAuditStatus.PROCESS.getStatus())
                .supplierId(11L).accountId(12L)
                .remark(governedRemark("conversion-01", LocalDate.of(2026, 8, 10), "EA",
                        purchaseEvidenceSha("sku-01", "warehouse-01", "JACKET-BLK-42",
                                "6901234567890", "EA", 13L, "6901234567890",
                                "Classic Jacket", 14L, "EA", "warehouse-ref:canonical-only")))
                .build());
        when(erpPurchaseOrderService.getPurchaseOrderItemListByOrderId(781L)).thenReturn(List.of(
                ErpPurchaseOrderItemDO.builder().productId(13L).productUnitId(14L)
                        .count(new BigDecimal("30")).productPrice(new BigDecimal("12.99"))
                        .taxPercent(new BigDecimal("13")).build()));

        ReplenishmentExecutionPort.ExecutionResult result = adapter.createDraft(purchaseCommand());

        assertThat(result.status()).isEqualTo("PREPARE");
        assertThat(result.externalDocumentId()).isEqualTo("781");
        assertThat(result.externalDocumentNo()).isEqualTo("PO-20260725-01");
        verify(erp).createPurchaseOrder(argThat(command ->
                command.supplierId().equals(11L)
                        && command.items().get(0).productPrice()
                        .compareTo(new BigDecimal("12.99")) == 0
                        && command.remark().contains("need-by:2026-08-10")
                        && command.remark().contains("uom:EA")));
        verify(erp, never()).setPurchaseOrderStatus(any());
        verifyNoInteractions(wms);
    }

    @Test
    void createsPrepareMovementOrderOnlyAfterLiveMappingAndReadbackMatch() {
        CatalogSkuProjectionView sku = catalogSku("sku-01", "JACKET-BLK-42", "6901234567890", "EA");
        when(catalog.getActiveSku("sku-01")).thenReturn(sku);
        when(wmsItemSkuService.getItemSkuListByIds(List.of(41L))).thenReturn(List.of(WmsItemSkuDO.builder()
                .id(41L).itemId(51L).code("JACKET-BLK-42").barCode("6901234567890").name("42").build()));
        when(wmsItemService.getItem(51L)).thenReturn(WmsItemDO.builder()
                .id(51L).code("ITEM-51").name("Classic Jacket").unit("EA").build());
        when(warehouseMappings.resolveReadyNetwork(
                argThat((WarehouseSourceReference reference) ->
                        "WMS".equals(reference.getSourceSystem())
                                && "WAREHOUSE".equals(reference.getSourceType())
                                && "32".equals(reference.getSourceId())),
                eq(Instant.parse("2026-07-25T00:00:00Z"))))
                .thenReturn(WarehouseNetworkView.builder()
                        .mappingId("mapping-32").warehouseId("warehouse-02")
                        .zoneId("zone-01").locationId("location-01").build());
        when(wms.createMovementOrder(any())).thenReturn(991L);
        when(wmsMovementOrderService.getMovementOrder(991L)).thenReturn(WmsMovementOrderDO.builder()
                .id(991L).no("CM-TR-conversion-02").status(WmsOrderStatusEnum.PREPARE.getStatus())
                .sourceWarehouseId(31L).targetWarehouseId(32L)
                .remark(governedRemark("conversion-02", LocalDate.of(2026, 8, 10), "EA",
                        transferEvidenceSha("sku-01", "warehouse-02", "JACKET-BLK-42",
                                "6901234567890", "EA", 41L, 51L, "JACKET-BLK-42",
                                "6901234567890", "EA", 31L, 32L, "mapping-32")))
                .build());
        when(wmsMovementOrderDetailService.getMovementOrderDetailList(991L)).thenReturn(List.of(
                WmsMovementOrderDetailDO.builder().skuId(41L).sourceWarehouseId(31L).targetWarehouseId(32L)
                        .quantity(new BigDecimal("8")).price(new BigDecimal("5.00"))
                        .totalPrice(new BigDecimal("40.00")).build()));

        ReplenishmentExecutionPort.ExecutionResult result = adapter.createDraft(transferCommand());

        assertThat(result.status()).isEqualTo("PREPARE");
        assertThat(result.externalDocumentNo()).isEqualTo("CM-TR-conversion-02");
        verify(wms).createMovementOrder(argThat(request ->
                request.sourceWarehouseId().equals(31L)
                        && request.targetWarehouseId().equals(32L)
                        && request.details().get(0).quantity().compareTo(new BigDecimal("8")) == 0
                        && request.remark().contains("need-by:2026-08-10")
                        && request.remark().contains("uom:EA")));
        verify(wms, never()).completeMovementOrder(any());
        verifyNoInteractions(erp);
    }

    @Test
    void failsClosedBeforeCallingErpWhenLiveMappingEvidenceDrifts() {
        CatalogSkuProjectionView sku = catalogSku("sku-01", "JACKET-BLK-42", "6901234567890", "EA");
        when(catalog.getActiveSku("sku-01")).thenReturn(sku);
        when(erpProductService.getProduct(13L)).thenReturn(ErpProductDO.builder()
                .id(13L).name("Classic Jacket").barCode("DIFFERENT")
                .unitId(14L).status(CommonStatusEnum.ENABLE.getStatus()).build());
        when(erpProductUnitService.getProductUnit(14L)).thenReturn(ErpProductUnitDO.builder()
                .id(14L).name("EA").status(CommonStatusEnum.ENABLE.getStatus()).build());

        assertThatThrownBy(() -> adapter.createDraft(purchaseCommand()))
                .hasMessage("ERP product no longer matches the canonical SKU identity");
        verifyNoInteractions(erp, wms);
    }

    @Test
    void failsClosedWhenPurchaseReadbackQuantityDoesNotMatch() {
        CatalogSkuProjectionView sku = catalogSku("sku-01", "JACKET-BLK-42", "6901234567890", "EA");
        when(catalog.getActiveSku("sku-01")).thenReturn(sku);
        when(erpProductService.getProduct(13L)).thenReturn(ErpProductDO.builder()
                .id(13L).name("Classic Jacket").barCode("6901234567890")
                .unitId(14L).status(CommonStatusEnum.ENABLE.getStatus()).build());
        when(erpProductUnitService.getProductUnit(14L)).thenReturn(ErpProductUnitDO.builder()
                .id(14L).name("EA").status(CommonStatusEnum.ENABLE.getStatus()).build());
        when(erp.createPurchaseOrder(any())).thenReturn(781L);
        when(erpPurchaseOrderService.getPurchaseOrder(781L)).thenReturn(ErpPurchaseOrderDO.builder()
                .id(781L).no("PO-20260725-01").status(ErpAuditStatus.PROCESS.getStatus())
                .supplierId(11L).accountId(12L)
                .remark(governedRemark("conversion-01", LocalDate.of(2026, 8, 10), "EA",
                        purchaseEvidenceSha("sku-01", "warehouse-01", "JACKET-BLK-42",
                                "6901234567890", "EA", 13L, "6901234567890",
                                "Classic Jacket", 14L, "EA", "warehouse-ref:canonical-only")))
                .build());
        when(erpPurchaseOrderService.getPurchaseOrderItemListByOrderId(781L)).thenReturn(List.of(
                ErpPurchaseOrderItemDO.builder().productId(13L).productUnitId(14L)
                        .count(new BigDecimal("31")).productPrice(new BigDecimal("12.99"))
                        .taxPercent(new BigDecimal("13")).build()));

        assertThatThrownBy(() -> adapter.createDraft(purchaseCommand()))
                .hasMessage("ERP purchase order quantity drifted during readback");
    }

    private static CatalogSkuProjectionView catalogSku(String canonicalSkuId, String skuCode,
                                                       String barcode, String baseUomCode) {
        CatalogSkuProjectionView view = new CatalogSkuProjectionView();
        view.setCanonicalSkuId(canonicalSkuId);
        view.setSkuCode(skuCode);
        view.setPrimaryBarcode(barcode);
        view.setBaseUomCode(baseUomCode);
        view.setProductName("Classic Jacket");
        view.setCatalogStatus("ACTIVE");
        return view;
    }

    private static ReplenishmentExecutionPort.ExecutionCommand purchaseCommand() {
        return new ReplenishmentExecutionPort.ExecutionCommand(
                "supply-conversion:conversion-01", "conversion-01", "PURCHASE_REQUEST",
                "sku-01", "warehouse-01", new BigDecimal("30"), "EA",
                LocalDate.of(2026, 8, 10),
                purchaseEvidenceSha("sku-01", "warehouse-01", "JACKET-BLK-42",
                        "6901234567890", "EA", 13L, "6901234567890",
                        "Classic Jacket", 14L, "EA", "warehouse-ref:canonical-only"),
                11L, 12L, 13L, 14L, 1299L, new BigDecimal("13"),
                null, null, null, Instant.parse("2026-07-25T00:00:00Z"));
    }

    private static ReplenishmentExecutionPort.ExecutionCommand transferCommand() {
        return new ReplenishmentExecutionPort.ExecutionCommand(
                "supply-conversion:conversion-02", "conversion-02", "TRANSFER_REQUEST",
                "sku-01", "warehouse-02", new BigDecimal("8"), "EA",
                LocalDate.of(2026, 8, 10),
                transferEvidenceSha("sku-01", "warehouse-02", "JACKET-BLK-42",
                        "6901234567890", "EA", 41L, 51L, "JACKET-BLK-42",
                        "6901234567890", "EA", 31L, 32L, "mapping-32"),
                null, null, null, null, 500L, null,
                31L, 32L, 41L, Instant.parse("2026-07-25T00:00:00Z"));
    }

    private static String purchaseEvidenceSha(String canonicalSkuId, String canonicalWarehouseId,
                                              String skuCode, String barcode, String baseUomCode,
                                              Long erpProductId, String productBarcode,
                                              String productName, Long unitId, String unitName,
                                              String warehouseEvidence) {
        return DigestUtil.sha256Hex(String.join("\u001f",
                "PURCHASE_REQUEST", canonicalSkuId, canonicalWarehouseId,
                skuCode, barcode, normalizeUnit(baseUomCode),
                String.valueOf(erpProductId), productBarcode, productName,
                String.valueOf(unitId), normalizeUnit(unitName), warehouseEvidence));
    }

    private static String transferEvidenceSha(String canonicalSkuId, String canonicalWarehouseId,
                                              String skuCode, String barcode, String baseUomCode,
                                              Long wmsSkuId, Long itemId, String wmsSkuCode,
                                              String wmsSkuBarcode, String itemUnit,
                                              Long sourceWarehouseId, Long targetWarehouseId,
                                              String mappingId) {
        return DigestUtil.sha256Hex(String.join("\u001f",
                "TRANSFER_REQUEST", canonicalSkuId, canonicalWarehouseId,
                skuCode, barcode, normalizeUnit(baseUomCode),
                String.valueOf(wmsSkuId), String.valueOf(itemId),
                wmsSkuCode, wmsSkuBarcode, normalizeUnit(itemUnit),
                String.valueOf(sourceWarehouseId), String.valueOf(targetWarehouseId), mappingId));
    }

    private static String governedRemark(String conversionId, LocalDate needByDate,
                                         String uomCode, String mappingEvidenceSha256) {
        return "CloudMold replenishment " + conversionId
                + " need-by:" + needByDate
                + " uom:" + uomCode
                + " mapping-sha256:" + mappingEvidenceSha256;
    }

    private static String normalizeUnit(String value) {
        return value.trim().replace('-', '_').replace(' ', '_').toUpperCase();
    }
}
