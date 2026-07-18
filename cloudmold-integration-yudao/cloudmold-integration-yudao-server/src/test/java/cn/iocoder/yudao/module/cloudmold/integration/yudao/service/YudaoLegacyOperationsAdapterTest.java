package cn.iocoder.yudao.module.cloudmold.integration.yudao.service;

import cn.iocoder.yudao.module.cloudmold.integration.yudao.api.YudaoErpCommandApi;
import cn.iocoder.yudao.module.cloudmold.integration.yudao.api.YudaoLegacyOperationsQueryApi;
import cn.iocoder.yudao.module.cloudmold.integration.yudao.api.YudaoWmsCommandApi;
import cn.iocoder.yudao.module.erp.controller.admin.purchase.vo.order.ErpPurchaseOrderSaveReqVO;
import cn.iocoder.yudao.module.erp.controller.admin.purchase.vo.in.ErpPurchaseInSaveReqVO;
import cn.iocoder.yudao.module.erp.controller.admin.sale.vo.customer.ErpCustomerSaveReqVO;
import cn.iocoder.yudao.module.erp.controller.admin.sale.vo.out.ErpSaleOutSaveReqVO;
import cn.iocoder.yudao.module.erp.controller.admin.stock.vo.warehouse.ErpWarehouseSaveReqVO;
import cn.iocoder.yudao.module.erp.dal.dataobject.purchase.ErpPurchaseOrderItemDO;
import cn.iocoder.yudao.module.erp.dal.dataobject.purchase.ErpPurchaseOrderDO;
import cn.iocoder.yudao.module.erp.dal.dataobject.sale.ErpSaleOrderDO;
import cn.iocoder.yudao.module.erp.dal.dataobject.sale.ErpSaleOrderItemDO;
import cn.iocoder.yudao.module.erp.dal.dataobject.stock.ErpWarehouseDO;
import cn.iocoder.yudao.module.erp.service.finance.ErpFinancePaymentService;
import cn.iocoder.yudao.module.erp.service.finance.ErpFinanceReceiptService;
import cn.iocoder.yudao.module.erp.service.purchase.ErpPurchaseOrderService;
import cn.iocoder.yudao.module.erp.service.purchase.ErpPurchaseInService;
import cn.iocoder.yudao.module.erp.service.sale.ErpCustomerService;
import cn.iocoder.yudao.module.erp.service.sale.ErpSaleOrderService;
import cn.iocoder.yudao.module.erp.service.sale.ErpSaleOutService;
import cn.iocoder.yudao.module.erp.service.stock.ErpStockCheckService;
import cn.iocoder.yudao.module.erp.service.stock.ErpStockMoveService;
import cn.iocoder.yudao.module.erp.service.stock.ErpWarehouseService;
import cn.iocoder.yudao.module.mes.service.pro.workorder.MesProWorkOrderService;
import cn.iocoder.yudao.module.mes.service.md.item.MesMdItemService;
import cn.iocoder.yudao.module.mes.service.md.item.MesMdItemTypeService;
import cn.iocoder.yudao.module.mes.service.md.unitmeasure.MesMdUnitMeasureService;
import cn.iocoder.yudao.module.wms.service.inventory.WmsInventoryService;
import cn.iocoder.yudao.module.wms.service.md.item.WmsItemCategoryService;
import cn.iocoder.yudao.module.wms.service.md.item.WmsItemService;
import cn.iocoder.yudao.module.wms.service.md.item.WmsItemSkuService;
import cn.iocoder.yudao.module.wms.service.md.merchant.WmsMerchantService;
import cn.iocoder.yudao.module.wms.service.md.warehouse.WmsWarehouseService;
import cn.iocoder.yudao.module.wms.service.order.check.WmsCheckOrderService;
import cn.iocoder.yudao.module.wms.service.order.movement.WmsMovementOrderService;
import cn.iocoder.yudao.module.wms.service.order.receipt.WmsReceiptOrderService;
import cn.iocoder.yudao.module.wms.service.order.shipment.WmsShipmentOrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

class YudaoLegacyOperationsAdapterTest {

    private final ErpPurchaseOrderService purchaseOrderService = mock(ErpPurchaseOrderService.class);
    private final ErpPurchaseInService purchaseInService = mock(ErpPurchaseInService.class);
    private final ErpCustomerService customerService = mock(ErpCustomerService.class);
    private final ErpSaleOrderService saleOrderService = mock(ErpSaleOrderService.class);
    private final ErpSaleOutService saleOutService = mock(ErpSaleOutService.class);
    private final ErpStockMoveService stockMoveService = mock(ErpStockMoveService.class);
    private final ErpStockCheckService stockCheckService = mock(ErpStockCheckService.class);
    private final ErpWarehouseService erpWarehouseService = mock(ErpWarehouseService.class);
    private final ErpFinancePaymentService financePaymentService = mock(ErpFinancePaymentService.class);
    private final ErpFinanceReceiptService financeReceiptService = mock(ErpFinanceReceiptService.class);
    private final WmsMerchantService wmsMerchantService = mock(WmsMerchantService.class);
    private final WmsWarehouseService wmsWarehouseService = mock(WmsWarehouseService.class);
    private final WmsItemCategoryService wmsItemCategoryService = mock(WmsItemCategoryService.class);
    private final WmsItemService wmsItemService = mock(WmsItemService.class);
    private final WmsItemSkuService wmsItemSkuService = mock(WmsItemSkuService.class);
    private final WmsInventoryService wmsInventoryService = mock(WmsInventoryService.class);
    private final WmsReceiptOrderService receiptOrderService = mock(WmsReceiptOrderService.class);
    private final WmsShipmentOrderService shipmentOrderService = mock(WmsShipmentOrderService.class);
    private final WmsMovementOrderService movementOrderService = mock(WmsMovementOrderService.class);
    private final WmsCheckOrderService checkOrderService = mock(WmsCheckOrderService.class);
    private final MesMdUnitMeasureService mesUnitMeasureService = mock(MesMdUnitMeasureService.class);
    private final MesMdItemTypeService mesItemTypeService = mock(MesMdItemTypeService.class);
    private final MesMdItemService mesItemService = mock(MesMdItemService.class);
    private final MesProWorkOrderService workOrderService = mock(MesProWorkOrderService.class);
    private final YudaoCommandOperationService operationService = mock(YudaoCommandOperationService.class);
    private final YudaoLegacyOperationsAdapter adapter = new YudaoLegacyOperationsAdapter(
            purchaseOrderService, purchaseInService, customerService, saleOrderService, saleOutService,
            stockMoveService, stockCheckService, erpWarehouseService,
            financePaymentService, financeReceiptService, wmsMerchantService, wmsWarehouseService,
            wmsItemCategoryService, wmsItemService, wmsItemSkuService, wmsInventoryService,
            receiptOrderService, shipmentOrderService,
            movementOrderService, checkOrderService, mesUnitMeasureService, mesItemTypeService,
            mesItemService, workOrderService, operationService);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void executeIdempotentActionsInAdapterTests() {
        when(operationService.executeLong(anyString(), anyString(), any(), any()))
                .thenAnswer(invocation -> ((Supplier<Long>) invocation.getArgument(3)).get());
        when(operationService.executeBoolean(anyString(), anyString(), any(), any()))
                .thenAnswer(invocation -> ((Supplier<Boolean>) invocation.getArgument(3)).get());
    }

    @Test
    void shouldTranslateProcurementCommandWithoutLeakingUpstreamVo() {
        LocalDateTime orderTime = LocalDateTime.of(2026, 7, 18, 9, 0);
        YudaoErpCommandApi.PurchaseOrderCommand command = new YudaoErpCommandApi.PurchaseOrderCommand(
                "purchase-create-001", 11L, 12L, orderTime.toString(), new BigDecimal("98.5"), new BigDecimal("10.00"), "skill-run",
                List.of(new YudaoErpCommandApi.PurchaseOrderLine(21L, 22L,
                        new BigDecimal("19.90"), new BigDecimal("2"), new BigDecimal("13"), "line")));
        when(purchaseOrderService.createPurchaseOrder(org.mockito.ArgumentMatchers.any())).thenReturn(31L);

        assertThat(adapter.createPurchaseOrder(command)).isEqualTo(31L);
        ArgumentCaptor<ErpPurchaseOrderSaveReqVO> captor = ArgumentCaptor.forClass(ErpPurchaseOrderSaveReqVO.class);
        verify(purchaseOrderService).createPurchaseOrder(captor.capture());
        assertThat(captor.getValue().getSupplierId()).isEqualTo(11L);
        assertThat(captor.getValue().getOrderTime()).isEqualTo(orderTime);
        assertThat(captor.getValue().getItems()).singleElement().satisfies(item -> {
            assertThat(item.getProductId()).isEqualTo(21L);
            assertThat(item.getCount()).isEqualByComparingTo("2");
        });
    }

    @Test
    void shouldCreateCustomerAsAnIdempotentRpcPrerequisite() {
        when(customerService.createCustomer(any())).thenReturn(71L);
        var command = new YudaoErpCommandApi.CustomerCommand(
                "customer-create-001", "CloudMold buyer", "Owner", "13800000000",
                "HSF prerequisite", 0, 1);

        assertThat(adapter.createCustomer(command)).isEqualTo(71L);
        ArgumentCaptor<ErpCustomerSaveReqVO> captor = ArgumentCaptor.forClass(ErpCustomerSaveReqVO.class);
        verify(customerService).createCustomer(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("CloudMold buyer");
        assertThat(captor.getValue().getStatus()).isZero();
    }

    @Test
    void shouldCreateWarehouseAsAnIdempotentRpcPrerequisite() {
        when(erpWarehouseService.createWarehouse(any())).thenReturn(72L);
        var command = new YudaoErpCommandApi.WarehouseCommand(
                "warehouse-create-001", "CloudMold transfer target", "Shanghai", 1L,
                "HSF prerequisite", "Owner", BigDecimal.ZERO, BigDecimal.ZERO, 0);

        assertThat(adapter.createWarehouse(command)).isEqualTo(72L);
        ArgumentCaptor<ErpWarehouseSaveReqVO> captor = ArgumentCaptor.forClass(ErpWarehouseSaveReqVO.class);
        verify(erpWarehouseService).createWarehouse(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("CloudMold transfer target");
        assertThat(captor.getValue().getStatus()).isZero();
    }

    @Test
    void shouldCreatePurchaseInFromTheOutstandingApprovedOrderQuantity() {
        LocalDateTime inTime = LocalDateTime.of(2026, 7, 18, 10, 30);
        when(purchaseOrderService.validatePurchaseOrder(81L)).thenReturn(
                ErpPurchaseOrderDO.builder().id(81L).discountPercent(new BigDecimal("100")).build());
        when(purchaseOrderService.getPurchaseOrderItemListByOrderId(81L)).thenReturn(List.of(
                ErpPurchaseOrderItemDO.builder().id(811L).productId(21L).productUnitId(22L)
                        .productPrice(new BigDecimal("19.90")).count(new BigDecimal("3"))
                        .inCount(BigDecimal.ONE).taxPercent(new BigDecimal("13")).remark("physical-in").build()));
        when(purchaseInService.createPurchaseIn(any())).thenReturn(82L);
        var command = new YudaoErpCommandApi.PurchaseInFromOrderCommand(
                "purchase-in-001", 81L, 31L, 12L, inTime.toString(), "skill-run");

        assertThat(adapter.createPurchaseInFromOrder(command)).isEqualTo(82L);
        ArgumentCaptor<ErpPurchaseInSaveReqVO> captor = ArgumentCaptor.forClass(ErpPurchaseInSaveReqVO.class);
        verify(purchaseInService).createPurchaseIn(captor.capture());
        assertThat(captor.getValue().getOrderId()).isEqualTo(81L);
        assertThat(captor.getValue().getItems()).singleElement().satisfies(item -> {
            assertThat(item.getOrderItemId()).isEqualTo(811L);
            assertThat(item.getWarehouseId()).isEqualTo(31L);
            assertThat(item.getCount()).isEqualByComparingTo("2");
        });
    }

    @Test
    void shouldCreateSaleOutFromTheOutstandingApprovedOrderQuantity() {
        LocalDateTime outTime = LocalDateTime.of(2026, 7, 18, 11, 0);
        when(saleOrderService.validateSaleOrder(91L)).thenReturn(
                ErpSaleOrderDO.builder().id(91L).discountPercent(new BigDecimal("100")).build());
        when(saleOrderService.getSaleOrderItemListByOrderId(91L)).thenReturn(List.of(
                ErpSaleOrderItemDO.builder().id(911L).productId(21L).productUnitId(22L)
                        .productPrice(new BigDecimal("19.90")).count(new BigDecimal("4"))
                        .outCount(new BigDecimal("1.5")).taxPercent(new BigDecimal("13")).remark("physical-out").build()));
        when(saleOutService.createSaleOut(any())).thenReturn(92L);
        var command = new YudaoErpCommandApi.SaleOutFromOrderCommand(
                "sale-out-001", 91L, 31L, 12L, 1L, outTime.toString(), "skill-run");

        assertThat(adapter.createSaleOutFromOrder(command)).isEqualTo(92L);
        ArgumentCaptor<ErpSaleOutSaveReqVO> captor = ArgumentCaptor.forClass(ErpSaleOutSaveReqVO.class);
        verify(saleOutService).createSaleOut(captor.capture());
        assertThat(captor.getValue().getOrderId()).isEqualTo(91L);
        assertThat(captor.getValue().getItems()).singleElement().satisfies(item -> {
            assertThat(item.getOrderItemId()).isEqualTo(911L);
            assertThat(item.getWarehouseId()).isEqualTo(31L);
            assertThat(item.getCount()).isEqualByComparingTo("2.5");
        });
    }

    @Test
    void shouldDelegatePhysicalWmsCompletionAsAnAtomicCapability() {
        assertThat(adapter.completeReceiptOrder(new YudaoWmsCommandApi.DocumentActionCommand(
                "receipt-complete-001", 41L))).isTrue();
        verify(receiptOrderService).completeReceiptOrder(41L);
    }

    @Test
    void shouldBuildMesMasterDataThroughTypedIdempotentCapabilities() {
        when(mesUnitMeasureService.createUnitMeasure(any())).thenReturn(101L);
        when(mesItemTypeService.createItemType(any())).thenReturn(102L);
        when(mesItemService.createItem(any())).thenReturn(103L);

        Long unitId = adapter.createUnitMeasure(new cn.iocoder.yudao.module.cloudmold.integration.yudao.api.YudaoMesCommandApi.UnitMeasureCommand(
                "mes-unit-001", "EA0718", "piece", true, null, null, 0, "HSF"));
        Long typeId = adapter.createItemType(new cn.iocoder.yudao.module.cloudmold.integration.yudao.api.YudaoMesCommandApi.ItemTypeCommand(
                "mes-type-001", 0L, "PRODUCT0718", "CloudMold product", "PRODUCT", 1, 0, "HSF"));
        Long itemId = adapter.createItem(new cn.iocoder.yudao.module.cloudmold.integration.yudao.api.YudaoMesCommandApi.ItemCommand(
                "mes-item-001", "MESITEM0718", "CloudMold product", "one product", unitId, typeId,
                false, BigDecimal.ZERO, BigDecimal.ZERO, false, false, "HSF"));

        assertThat(unitId).isEqualTo(101L);
        assertThat(typeId).isEqualTo(102L);
        assertThat(itemId).isEqualTo(103L);
        verify(mesUnitMeasureService).createUnitMeasure(any());
        verify(mesItemTypeService).createItemType(any());
        verify(mesItemService).createItem(any());
    }

    @Test
    void shouldReturnAStableReadModelForSkillAssertions() {
        ErpPurchaseOrderDO order = new ErpPurchaseOrderDO().setId(51L).setNo("PO-51").setStatus(20)
                .setOrderTime(LocalDateTime.of(2026, 7, 18, 10, 0))
                .setTotalCount(new BigDecimal("3")).setTotalPrice(new BigDecimal("59.70"))
                .setRemark("verified");
        when(purchaseOrderService.getPurchaseOrder(51L)).thenReturn(order);

        YudaoLegacyOperationsQueryApi.LegacyDocumentView result = adapter.getPurchaseOrder(51L);

        assertThat(result.sourceSystem()).isEqualTo("ERP");
        assertThat(result.documentType()).isEqualTo("PURCHASE_ORDER");
        assertThat(result.documentNo()).isEqualTo("PO-51");
        assertThat(result.quantity()).isEqualByComparingTo("3");
    }

    @Test
    void shouldExposeTypedErpWarehouseReferenceWithoutLeakingDo() {
        when(erpWarehouseService.getWarehouse(61L)).thenReturn(ErpWarehouseDO.builder()
                .id(61L).name("Shanghai").address("Pudong").status(0).build());

        var result = adapter.getErpWarehouse(61L);

        assertThat(result.warehouseId()).isEqualTo(61L);
        assertThat(result.name()).isEqualTo("Shanghai");
        assertThat(result.status()).isZero();
    }
}
