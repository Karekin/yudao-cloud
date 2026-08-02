package cn.iocoder.yudao.module.cloudmold.integration.yudao.service;

import cn.iocoder.yudao.module.cloudmold.integration.yudao.api.YudaoErpCommandApi;
import cn.iocoder.yudao.module.cloudmold.integration.yudao.api.YudaoLegacyOperationsQueryApi;
import cn.iocoder.yudao.module.cloudmold.integration.yudao.bridge.YudaoLegacyOperationsAdapter;
import cn.iocoder.yudao.module.cloudmold.integration.yudao.wms.LegacyWmsPhysicalOperationsPort;
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
import cn.iocoder.yudao.module.mes.controller.admin.pro.feedback.vo.MesProFeedbackSaveReqVO;
import cn.iocoder.yudao.module.mes.controller.admin.pro.task.vo.MesProTaskSaveReqVO;
import cn.iocoder.yudao.module.mes.dal.dataobject.pro.feedback.MesProFeedbackDO;
import cn.iocoder.yudao.module.mes.dal.dataobject.pro.task.MesProTaskDO;
import cn.iocoder.yudao.module.mes.dal.dataobject.pro.workorder.MesProWorkOrderDO;
import cn.iocoder.yudao.module.mes.dal.dataobject.wm.productproduce.MesWmProductProduceDO;
import cn.iocoder.yudao.module.mes.dal.dataobject.wm.productproduce.MesWmProductProduceLineDO;
import cn.iocoder.yudao.module.mes.service.pro.workorder.MesProWorkOrderService;
import cn.iocoder.yudao.module.mes.service.md.item.MesMdItemService;
import cn.iocoder.yudao.module.mes.service.md.item.MesMdItemTypeService;
import cn.iocoder.yudao.module.mes.service.md.unitmeasure.MesMdUnitMeasureService;
import cn.iocoder.yudao.module.mes.service.md.workstation.MesMdWorkshopService;
import cn.iocoder.yudao.module.mes.service.md.workstation.MesMdWorkstationService;
import cn.iocoder.yudao.module.mes.service.pro.feedback.MesProFeedbackService;
import cn.iocoder.yudao.module.mes.service.pro.process.MesProProcessService;
import cn.iocoder.yudao.module.mes.service.pro.route.MesProRouteProcessService;
import cn.iocoder.yudao.module.mes.service.pro.route.MesProRouteService;
import cn.iocoder.yudao.module.mes.service.pro.task.MesProTaskService;
import cn.iocoder.yudao.module.mes.service.wm.productproduce.MesWmProductProduceLineService;
import cn.iocoder.yudao.module.mes.service.wm.productproduce.MesWmProductProduceService;
import cn.iocoder.yudao.module.wms.service.md.item.WmsItemCategoryService;
import cn.iocoder.yudao.module.wms.service.md.item.WmsItemService;
import cn.iocoder.yudao.module.wms.service.md.item.WmsItemSkuService;
import cn.iocoder.yudao.module.wms.service.md.merchant.WmsMerchantService;
import cn.iocoder.yudao.module.wms.service.md.warehouse.WmsWarehouseService;
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
    private final LegacyWmsPhysicalOperationsPort wmsPhysicalOperationsPort = mock(LegacyWmsPhysicalOperationsPort.class);
    private final MesMdUnitMeasureService mesUnitMeasureService = mock(MesMdUnitMeasureService.class);
    private final MesMdItemTypeService mesItemTypeService = mock(MesMdItemTypeService.class);
    private final MesMdItemService mesItemService = mock(MesMdItemService.class);
    private final MesMdWorkshopService mesWorkshopService = mock(MesMdWorkshopService.class);
    private final MesProProcessService mesProcessService = mock(MesProProcessService.class);
    private final MesMdWorkstationService mesWorkstationService = mock(MesMdWorkstationService.class);
    private final MesProRouteService mesRouteService = mock(MesProRouteService.class);
    private final MesProRouteProcessService mesRouteProcessService = mock(MesProRouteProcessService.class);
    private final MesProWorkOrderService workOrderService = mock(MesProWorkOrderService.class);
    private final MesProTaskService mesTaskService = mock(MesProTaskService.class);
    private final MesProFeedbackService mesFeedbackService = mock(MesProFeedbackService.class);
    private final MesWmProductProduceService mesProductProduceService = mock(MesWmProductProduceService.class);
    private final MesWmProductProduceLineService mesProductProduceLineService = mock(MesWmProductProduceLineService.class);
    private final YudaoCommandOperationService operationService = mock(YudaoCommandOperationService.class);
    private final YudaoLegacyOperationsAdapter adapter = new YudaoLegacyOperationsAdapter(
            purchaseOrderService, purchaseInService, customerService, saleOrderService, saleOutService,
            stockMoveService, stockCheckService, erpWarehouseService,
            financePaymentService, financeReceiptService, wmsMerchantService, wmsWarehouseService,
            wmsItemCategoryService, wmsItemService, wmsItemSkuService, wmsPhysicalOperationsPort,
            mesUnitMeasureService, mesItemTypeService,
            mesItemService, mesWorkshopService, mesProcessService, mesWorkstationService,
            mesRouteService, mesRouteProcessService, workOrderService, mesTaskService,
            mesFeedbackService, mesProductProduceService, mesProductProduceLineService, operationService);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void executeIdempotentActionsInAdapterTests() {
        when(operationService.executeLong(anyString(), anyString(), any(), any()))
                .thenAnswer(invocation -> ((Supplier<Long>) invocation.getArgument(3)).get());
        when(operationService.executeBoolean(anyString(), anyString(), any(), any()))
                .thenAnswer(invocation -> ((Supplier<Boolean>) invocation.getArgument(3)).get());
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
    void shouldTranslateMesTaskAndFeedbackWithoutLeakingUpstreamVos() {
        when(mesTaskService.createTask(any())).thenReturn(201L);
        when(mesFeedbackService.createFeedback(any())).thenReturn(202L);
        var taskCommand =
                new cn.iocoder.yudao.module.cloudmold.integration.yudao.api.YudaoMesCommandApi.TaskCommand(
                        "mes-task-001", 11L, 12L, 13L, 14L, 15L,
                        new BigDecimal("12"), "2026-07-30T09:00:00", 1,
                        "2026-07-30T17:00:00", "#1677FF", "dispatch");
        var feedbackCommand =
                new cn.iocoder.yudao.module.cloudmold.integration.yudao.api.YudaoMesCommandApi.FeedbackCommand(
                        "mes-feedback-001", "FB-001", 1, 12L, 13L, 14L,
                        11L, 201L, 15L, "2027-07-30T23:59:59",
                        "LOT-001", new BigDecimal("12"), new BigDecimal("12"),
                        new BigDecimal("12"), BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        226L, "2026-07-30T17:00:00", 227L, "qualified");

        assertThat(adapter.createTask(taskCommand)).isEqualTo(201L);
        assertThat(adapter.createFeedback(feedbackCommand)).isEqualTo(202L);

        ArgumentCaptor<MesProTaskSaveReqVO> taskCaptor =
                ArgumentCaptor.forClass(MesProTaskSaveReqVO.class);
        verify(mesTaskService).createTask(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getWorkOrderId()).isEqualTo(11L);
        assertThat(taskCaptor.getValue().getQuantity()).isEqualByComparingTo("12");
        assertThat(taskCaptor.getValue().getStartTime())
                .isEqualTo(LocalDateTime.of(2026, 7, 30, 9, 0));

        ArgumentCaptor<MesProFeedbackSaveReqVO> feedbackCaptor =
                ArgumentCaptor.forClass(MesProFeedbackSaveReqVO.class);
        verify(mesFeedbackService).createFeedback(feedbackCaptor.capture());
        assertThat(feedbackCaptor.getValue().getTaskId()).isEqualTo(201L);
        assertThat(feedbackCaptor.getValue().getQualifiedQuantity()).isEqualByComparingTo("12");
        assertThat(feedbackCaptor.getValue().getFeedbackUserId()).isEqualTo(226L);
        assertThat(feedbackCaptor.getValue().getApproveUserId()).isEqualTo(227L);
    }

    @Test
    void shouldExposeMesProductionTerminalQuantityAndReceiptEvidence() {
        when(workOrderService.getWorkOrder(301L)).thenReturn(MesProWorkOrderDO.builder()
                .id(301L).status(2).quantity(new BigDecimal("12"))
                .quantityScheduled(new BigDecimal("12")).quantityProduced(new BigDecimal("12")).build());
        when(mesTaskService.getTask(302L)).thenReturn(MesProTaskDO.builder()
                .id(302L).status(4).quantity(new BigDecimal("12"))
                .producedQuantity(new BigDecimal("12")).qualifyQuantity(new BigDecimal("12"))
                .unqualifyQuantity(BigDecimal.ZERO).build());
        when(mesFeedbackService.getFeedback(303L)).thenReturn(MesProFeedbackDO.builder()
                .id(303L).status(4).feedbackQuantity(new BigDecimal("12"))
                .qualifiedQuantity(new BigDecimal("12")).unqualifiedQuantity(BigDecimal.ZERO).build());
        when(mesProductProduceService.getProductProduceByFeedbackId(303L))
                .thenReturn(MesWmProductProduceDO.builder().id(304L).feedbackId(303L).status(4).build());
        when(mesProductProduceLineService.getProductProduceLineListByProduceId(304L))
                .thenReturn(List.of(MesWmProductProduceLineDO.builder()
                        .produceId(304L).quantity(new BigDecimal("12"))
                        .qualityStatus(1).batchCode("BATCH-001").build()));

        YudaoLegacyOperationsQueryApi.MesProductionExecutionView result =
                adapter.getMesProductionExecution(301L, 302L, 303L);

        assertThat(result.closedLoop()).isTrue();
        assertThat(result.outputQuantity()).isEqualByComparingTo("12");
        assertThat(result.passedOutputQuantity()).isEqualByComparingTo("12");
        assertThat(result.failedOutputQuantity()).isZero();
        assertThat(result.outputBatchCodes()).containsExactly("BATCH-001");
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

    @Test
    void shouldDelegateWmsOperationalReadModelsThroughTheDedicatedPhysicalPort() {
        when(wmsPhysicalOperationsPort.getReceiptOrder(41L)).thenReturn(
                new LegacyWmsPhysicalOperationsPort.PhysicalOrderSnapshot(
                        "WMS", "RECEIPT_ORDER", 41L, "RK-41", 4,
                        "2026-07-25T11:00:00", 100L, new BigDecimal("2"),
                        new BigDecimal("40"), "done"));
        when(wmsPhysicalOperationsPort.getWmsInventory(100L, 200L)).thenReturn(
                new LegacyWmsPhysicalOperationsPort.InventorySnapshot(301L, 100L, 200L, new BigDecimal("8")));

        var receipt = adapter.getReceiptOrder(41L);
        var inventory = adapter.getWmsInventory(100L, 200L);

        assertThat(receipt.documentNo()).isEqualTo("RK-41");
        assertThat(receipt.status()).isEqualTo(4);
        assertThat(inventory.inventoryId()).isEqualTo(301L);
        assertThat(inventory.quantity()).isEqualByComparingTo("8");
    }
}
