package cn.iocoder.yudao.module.cloudmold.integration.yudao.bridge;

import cn.iocoder.yudao.module.cloudmold.integration.yudao.api.YudaoErpCommandApi;
import cn.iocoder.yudao.module.cloudmold.integration.yudao.api.YudaoLegacyOperationsQueryApi;
import cn.iocoder.yudao.module.cloudmold.integration.yudao.api.YudaoLegacyMasterDataQueryApi;
import cn.iocoder.yudao.module.cloudmold.integration.yudao.api.YudaoMesCommandApi;
import cn.iocoder.yudao.module.cloudmold.integration.yudao.api.YudaoWmsCommandApi;
import cn.iocoder.yudao.module.cloudmold.integration.yudao.service.YudaoCommandOperationService;
import cn.iocoder.yudao.module.cloudmold.integration.yudao.wms.LegacyWmsMasterDataPort;
import cn.iocoder.yudao.module.cloudmold.integration.yudao.wms.LegacyWmsPhysicalOperationsPort;
import cn.iocoder.yudao.module.erp.controller.admin.finance.vo.payment.ErpFinancePaymentSaveReqVO;
import cn.iocoder.yudao.module.erp.controller.admin.finance.vo.receipt.ErpFinanceReceiptSaveReqVO;
import cn.iocoder.yudao.module.erp.controller.admin.purchase.vo.in.ErpPurchaseInSaveReqVO;
import cn.iocoder.yudao.module.erp.controller.admin.sale.vo.customer.ErpCustomerSaveReqVO;
import cn.iocoder.yudao.module.erp.controller.admin.sale.vo.order.ErpSaleOrderSaveReqVO;
import cn.iocoder.yudao.module.erp.controller.admin.sale.vo.out.ErpSaleOutSaveReqVO;
import cn.iocoder.yudao.module.erp.controller.admin.stock.vo.check.ErpStockCheckSaveReqVO;
import cn.iocoder.yudao.module.erp.controller.admin.stock.vo.move.ErpStockMoveSaveReqVO;
import cn.iocoder.yudao.module.erp.controller.admin.stock.vo.warehouse.ErpWarehouseSaveReqVO;
import cn.iocoder.yudao.module.erp.dal.dataobject.finance.ErpFinancePaymentDO;
import cn.iocoder.yudao.module.erp.dal.dataobject.finance.ErpFinanceReceiptDO;
import cn.iocoder.yudao.module.erp.dal.dataobject.purchase.ErpPurchaseOrderDO;
import cn.iocoder.yudao.module.erp.dal.dataobject.purchase.ErpPurchaseInDO;
import cn.iocoder.yudao.module.erp.dal.dataobject.sale.ErpSaleOrderDO;
import cn.iocoder.yudao.module.erp.dal.dataobject.sale.ErpSaleOutDO;
import cn.iocoder.yudao.module.erp.dal.dataobject.stock.ErpStockCheckDO;
import cn.iocoder.yudao.module.erp.dal.dataobject.stock.ErpStockMoveDO;
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
import cn.iocoder.yudao.module.mes.controller.admin.pro.workorder.vo.MesProWorkOrderSaveReqVO;
import cn.iocoder.yudao.module.mes.controller.admin.md.item.vo.MesMdItemSaveReqVO;
import cn.iocoder.yudao.module.mes.controller.admin.md.item.vo.type.MesMdItemTypeSaveReqVO;
import cn.iocoder.yudao.module.mes.controller.admin.md.unitmeasure.vo.MesMdUnitMeasureSaveReqVO;
import cn.iocoder.yudao.module.mes.controller.admin.md.workstation.vo.MesMdWorkstationSaveReqVO;
import cn.iocoder.yudao.module.mes.controller.admin.md.workstation.vo.workshop.MesMdWorkshopSaveReqVO;
import cn.iocoder.yudao.module.mes.controller.admin.pro.feedback.vo.MesProFeedbackSaveReqVO;
import cn.iocoder.yudao.module.mes.controller.admin.pro.process.vo.MesProProcessSaveReqVO;
import cn.iocoder.yudao.module.mes.controller.admin.pro.route.vo.MesProRouteSaveReqVO;
import cn.iocoder.yudao.module.mes.controller.admin.pro.route.vo.process.MesProRouteProcessSaveReqVO;
import cn.iocoder.yudao.module.mes.controller.admin.pro.task.vo.MesProTaskSaveReqVO;
import cn.iocoder.yudao.module.mes.dal.dataobject.pro.feedback.MesProFeedbackDO;
import cn.iocoder.yudao.module.mes.dal.dataobject.pro.task.MesProTaskDO;
import cn.iocoder.yudao.module.mes.dal.dataobject.pro.workorder.MesProWorkOrderDO;
import cn.iocoder.yudao.module.mes.dal.dataobject.wm.productproduce.MesWmProductProduceDO;
import cn.iocoder.yudao.module.mes.dal.dataobject.wm.productproduce.MesWmProductProduceLineDO;
import cn.iocoder.yudao.module.mes.enums.pro.MesProFeedbackStatusEnum;
import cn.iocoder.yudao.module.mes.enums.pro.MesProTaskStatusEnum;
import cn.iocoder.yudao.module.mes.enums.pro.MesProWorkOrderStatusEnum;
import cn.iocoder.yudao.module.mes.enums.wm.MesWmProductProduceStatusEnum;
import cn.iocoder.yudao.module.mes.enums.wm.MesWmQualityStatusEnum;
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
import cn.iocoder.yudao.module.mes.service.pro.workorder.MesProWorkOrderService;
import cn.iocoder.yudao.module.mes.service.wm.productproduce.MesWmProductProduceLineService;
import cn.iocoder.yudao.module.mes.service.wm.productproduce.MesWmProductProduceService;
import cn.iocoder.yudao.module.wms.controller.admin.md.item.vo.category.WmsItemCategorySaveReqVO;
import cn.iocoder.yudao.module.wms.controller.admin.md.item.vo.item.WmsItemSaveReqVO;
import cn.iocoder.yudao.module.wms.controller.admin.md.item.vo.sku.WmsItemSkuSaveReqVO;
import cn.iocoder.yudao.module.wms.controller.admin.md.merchant.vo.WmsMerchantSaveReqVO;
import cn.iocoder.yudao.module.wms.controller.admin.md.warehouse.vo.WmsWarehouseSaveReqVO;
import cn.iocoder.yudao.module.wms.service.md.item.WmsItemCategoryService;
import cn.iocoder.yudao.module.wms.service.md.item.WmsItemService;
import cn.iocoder.yudao.module.wms.service.md.item.WmsItemSkuService;
import cn.iocoder.yudao.module.wms.service.md.merchant.WmsMerchantService;
import cn.iocoder.yudao.module.wms.service.md.warehouse.WmsWarehouseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Anti-corruption layer between governed CloudMold capabilities and upstream
 * yudao ERP/WMS/MES services. Upstream VOs and DOs never cross the RPC boundary.
 */
@Service
@RequiredArgsConstructor
public class YudaoLegacyOperationsAdapter implements YudaoErpCommandApi,
        YudaoMesCommandApi, YudaoLegacyOperationsQueryApi, YudaoLegacyMasterDataQueryApi,
        LegacyWmsMasterDataPort {

    private final ErpPurchaseOrderService purchaseOrderService;
    private final ErpPurchaseInService purchaseInService;
    private final ErpCustomerService customerService;
    private final ErpSaleOrderService saleOrderService;
    private final ErpSaleOutService saleOutService;
    private final ErpStockMoveService stockMoveService;
    private final ErpStockCheckService stockCheckService;
    private final ErpWarehouseService erpWarehouseService;
    private final ErpFinancePaymentService financePaymentService;
    private final ErpFinanceReceiptService financeReceiptService;
    private final WmsMerchantService wmsMerchantService;
    private final WmsWarehouseService wmsWarehouseService;
    private final WmsItemCategoryService wmsItemCategoryService;
    private final WmsItemService wmsItemService;
    private final WmsItemSkuService wmsItemSkuService;
    private final LegacyWmsPhysicalOperationsPort wmsPhysicalOperationsPort;
    private final MesMdUnitMeasureService mesUnitMeasureService;
    private final MesMdItemTypeService mesItemTypeService;
    private final MesMdItemService mesItemService;
    private final MesMdWorkshopService mesWorkshopService;
    private final MesProProcessService mesProcessService;
    private final MesMdWorkstationService mesWorkstationService;
    private final MesProRouteService mesRouteService;
    private final MesProRouteProcessService mesRouteProcessService;
    private final MesProWorkOrderService workOrderService;
    private final MesProTaskService mesTaskService;
    private final MesProFeedbackService mesFeedbackService;
    private final MesWmProductProduceService mesProductProduceService;
    private final MesWmProductProduceLineService mesProductProduceLineService;
    private final YudaoCommandOperationService operationService;

    @Override
    public LegacyWarehouseView getErpWarehouse(Long warehouseId) {
        ErpWarehouseDO warehouse = erpWarehouseService.getWarehouse(warehouseId);
        if (warehouse == null) {
            throw new IllegalArgumentException("ERP warehouse does not exist: " + warehouseId);
        }
        return new LegacyWarehouseView(warehouse.getId(), warehouse.getName(), warehouse.getAddress(), warehouse.getStatus());
    }

    @Override
    public Long createCustomer(CustomerCommand command) {
        ErpCustomerSaveReqVO request = new ErpCustomerSaveReqVO();
        request.setName(command.name());
        request.setContact(command.contact());
        request.setMobile(command.mobile());
        request.setRemark(command.remark());
        request.setStatus(command.status());
        request.setSort(command.sort());
        return operationService.executeLong("CREATE_ERP_CUSTOMER", command.idempotencyKey(), command,
                () -> customerService.createCustomer(request));
    }

    @Override
    public Long createWarehouse(YudaoErpCommandApi.WarehouseCommand command) {
        ErpWarehouseSaveReqVO request = new ErpWarehouseSaveReqVO();
        request.setName(command.name());
        request.setAddress(command.address());
        request.setSort(command.sort());
        request.setRemark(command.remark());
        request.setPrincipal(command.principal());
        request.setWarehousePrice(command.warehousePrice());
        request.setTruckagePrice(command.truckagePrice());
        request.setStatus(command.status());
        return operationService.executeLong("CREATE_ERP_WAREHOUSE", command.idempotencyKey(), command,
                () -> erpWarehouseService.createWarehouse(request));
    }

    @Override
    public Boolean setPurchaseOrderStatus(DocumentStatusCommand command) {
        return operationService.executeBoolean("SET_PURCHASE_ORDER_STATUS", command.idempotencyKey(), command, () -> {
            purchaseOrderService.updatePurchaseOrderStatus(command.documentId(), command.targetStatus());
            return true;
        });
    }

    @Override
    public Long createPurchaseInFromOrder(PurchaseInFromOrderCommand command) {
        return operationService.executeLong("CREATE_PURCHASE_IN_FROM_ORDER", command.idempotencyKey(), command,
                () -> {
                    var order = purchaseOrderService.validatePurchaseOrder(command.orderId());
                    var orderItems = purchaseOrderService.getPurchaseOrderItemListByOrderId(command.orderId());
                    ErpPurchaseInSaveReqVO request = new ErpPurchaseInSaveReqVO();
                    request.setAccountId(command.accountId());
                    request.setInTime(parseBusinessTime(command.inTime(), "inTime"));
                    request.setOrderId(command.orderId());
                    request.setDiscountPercent(order.getDiscountPercent());
                    request.setOtherPrice(BigDecimal.ZERO);
                    request.setRemark(command.remark());
                    request.setItems(orderItems.stream().map(line -> {
                        ErpPurchaseInSaveReqVO.Item item = new ErpPurchaseInSaveReqVO.Item();
                        item.setOrderItemId(line.getId());
                        item.setWarehouseId(command.warehouseId());
                        item.setProductId(line.getProductId());
                        item.setProductUnitId(line.getProductUnitId());
                        item.setProductPrice(line.getProductPrice());
                        item.setCount(line.getCount().subtract(line.getInCount() == null ? BigDecimal.ZERO : line.getInCount()));
                        item.setTaxPercent(line.getTaxPercent());
                        item.setRemark(line.getRemark());
                        return item;
                    }).toList());
                    return purchaseInService.createPurchaseIn(request);
                });
    }

    @Override
    public Boolean setPurchaseInStatus(DocumentStatusCommand command) {
        return operationService.executeBoolean("SET_PURCHASE_IN_STATUS", command.idempotencyKey(), command, () -> {
            purchaseInService.updatePurchaseInStatus(command.documentId(), command.targetStatus());
            return true;
        });
    }

    @Override
    public Long createSaleOrder(SaleOrderCommand command) {
        ErpSaleOrderSaveReqVO request = new ErpSaleOrderSaveReqVO();
        request.setCustomerId(command.customerId());
        request.setSaleUserId(command.saleUserId());
        request.setAccountId(command.accountId());
        request.setOrderTime(parseBusinessTime(command.orderTime(), "orderTime"));
        request.setDiscountPercent(command.discountPercent());
        request.setDepositPrice(command.depositPrice());
        request.setRemark(command.remark());
        request.setItems(command.items().stream().map(line -> {
            ErpSaleOrderSaveReqVO.Item item = new ErpSaleOrderSaveReqVO.Item();
            item.setProductId(line.productId());
            item.setProductUnitId(line.productUnitId());
            item.setProductPrice(line.productPrice());
            item.setCount(line.count());
            item.setTaxPercent(line.taxPercent());
            item.setRemark(line.remark());
            return item;
        }).toList());
        return operationService.executeLong("CREATE_SALE_ORDER", command.idempotencyKey(), command,
                () -> saleOrderService.createSaleOrder(request));
    }

    @Override
    public Boolean setSaleOrderStatus(DocumentStatusCommand command) {
        return operationService.executeBoolean("SET_SALE_ORDER_STATUS", command.idempotencyKey(), command, () -> {
            saleOrderService.updateSaleOrderStatus(command.documentId(), command.targetStatus());
            return true;
        });
    }

    @Override
    public Long createSaleOutFromOrder(SaleOutFromOrderCommand command) {
        return operationService.executeLong("CREATE_SALE_OUT_FROM_ORDER", command.idempotencyKey(), command,
                () -> {
                    var order = saleOrderService.validateSaleOrder(command.orderId());
                    var orderItems = saleOrderService.getSaleOrderItemListByOrderId(command.orderId());
                    ErpSaleOutSaveReqVO request = new ErpSaleOutSaveReqVO();
                    request.setAccountId(command.accountId());
                    request.setSaleUserId(command.saleUserId());
                    request.setOutTime(parseBusinessTime(command.outTime(), "outTime"));
                    request.setOrderId(command.orderId());
                    request.setDiscountPercent(order.getDiscountPercent());
                    request.setOtherPrice(BigDecimal.ZERO);
                    request.setRemark(command.remark());
                    request.setItems(orderItems.stream().map(line -> {
                        ErpSaleOutSaveReqVO.Item item = new ErpSaleOutSaveReqVO.Item();
                        item.setOrderItemId(line.getId());
                        item.setWarehouseId(command.warehouseId());
                        item.setProductId(line.getProductId());
                        item.setProductUnitId(line.getProductUnitId());
                        item.setProductPrice(line.getProductPrice());
                        item.setCount(line.getCount().subtract(line.getOutCount() == null ? BigDecimal.ZERO : line.getOutCount()));
                        item.setTaxPercent(line.getTaxPercent());
                        item.setRemark(line.getRemark());
                        return item;
                    }).toList());
                    return saleOutService.createSaleOut(request);
                });
    }

    @Override
    public Boolean setSaleOutStatus(DocumentStatusCommand command) {
        return operationService.executeBoolean("SET_SALE_OUT_STATUS", command.idempotencyKey(), command, () -> {
            saleOutService.updateSaleOutStatus(command.documentId(), command.targetStatus());
            return true;
        });
    }

    @Override
    public Long createStockMove(StockMoveCommand command) {
        ErpStockMoveSaveReqVO request = new ErpStockMoveSaveReqVO();
        request.setCustomerId(command.customerId());
        request.setMoveTime(parseBusinessTime(command.moveTime(), "moveTime"));
        request.setRemark(command.remark());
        request.setItems(command.items().stream().map(line -> {
            ErpStockMoveSaveReqVO.Item item = new ErpStockMoveSaveReqVO.Item();
            item.setFromWarehouseId(line.fromWarehouseId());
            item.setToWarehouseId(line.toWarehouseId());
            item.setProductId(line.productId());
            item.setProductPrice(line.productPrice());
            item.setCount(line.count());
            item.setRemark(line.remark());
            return item;
        }).toList());
        return operationService.executeLong("CREATE_STOCK_MOVE", command.idempotencyKey(), command,
                () -> stockMoveService.createStockMove(request));
    }

    @Override
    public Boolean setStockMoveStatus(DocumentStatusCommand command) {
        return operationService.executeBoolean("SET_STOCK_MOVE_STATUS", command.idempotencyKey(), command, () -> {
            stockMoveService.updateStockMoveStatus(command.documentId(), command.targetStatus());
            return true;
        });
    }

    @Override
    public Long createStockCheck(StockCheckCommand command) {
        ErpStockCheckSaveReqVO request = new ErpStockCheckSaveReqVO();
        request.setCheckTime(parseBusinessTime(command.checkTime(), "checkTime"));
        request.setRemark(command.remark());
        request.setItems(command.items().stream().map(line -> {
            ErpStockCheckSaveReqVO.Item item = new ErpStockCheckSaveReqVO.Item();
            item.setWarehouseId(line.warehouseId());
            item.setProductId(line.productId());
            item.setProductPrice(line.productPrice());
            item.setStockCount(line.stockCount());
            item.setActualCount(line.actualCount());
            item.setCount(line.count());
            item.setRemark(line.remark());
            return item;
        }).toList());
        return operationService.executeLong("CREATE_STOCK_CHECK", command.idempotencyKey(), command,
                () -> stockCheckService.createStockCheck(request));
    }

    @Override
    public Boolean setStockCheckStatus(DocumentStatusCommand command) {
        return operationService.executeBoolean("SET_STOCK_CHECK_STATUS", command.idempotencyKey(), command, () -> {
            stockCheckService.updateStockCheckStatus(command.documentId(), command.targetStatus());
            return true;
        });
    }

    @Override
    public Long createFinancePayment(FinancePaymentCommand command) {
        ErpFinancePaymentSaveReqVO request = new ErpFinancePaymentSaveReqVO();
        request.setPaymentTime(parseBusinessTime(command.paymentTime(), "paymentTime"));
        request.setFinanceUserId(command.financeUserId());
        request.setSupplierId(command.supplierId());
        request.setAccountId(command.accountId());
        request.setDiscountPrice(command.discountPrice());
        request.setRemark(command.remark());
        request.setItems(command.items().stream().map(line -> {
            ErpFinancePaymentSaveReqVO.Item item = new ErpFinancePaymentSaveReqVO.Item();
            item.setBizType(line.bizType());
            item.setBizId(line.bizId());
            item.setPaidPrice(line.paidPrice());
            item.setPaymentPrice(line.paymentPrice());
            item.setRemark(line.remark());
            return item;
        }).toList());
        return operationService.executeLong("CREATE_FINANCE_PAYMENT", command.idempotencyKey(), command,
                () -> financePaymentService.createFinancePayment(request));
    }

    @Override
    public Boolean setFinancePaymentStatus(DocumentStatusCommand command) {
        return operationService.executeBoolean("SET_FINANCE_PAYMENT_STATUS", command.idempotencyKey(), command, () -> {
            financePaymentService.updateFinancePaymentStatus(command.documentId(), command.targetStatus());
            return true;
        });
    }

    @Override
    public Long createFinanceReceipt(FinanceReceiptCommand command) {
        ErpFinanceReceiptSaveReqVO request = new ErpFinanceReceiptSaveReqVO();
        request.setReceiptTime(parseBusinessTime(command.receiptTime(), "receiptTime"));
        request.setFinanceUserId(command.financeUserId());
        request.setCustomerId(command.customerId());
        request.setAccountId(command.accountId());
        request.setDiscountPrice(command.discountPrice());
        request.setRemark(command.remark());
        request.setItems(command.items().stream().map(line -> {
            ErpFinanceReceiptSaveReqVO.Item item = new ErpFinanceReceiptSaveReqVO.Item();
            item.setBizType(line.bizType());
            item.setBizId(line.bizId());
            item.setReceiptedPrice(line.receiptedPrice());
            item.setReceiptPrice(line.receiptPrice());
            item.setRemark(line.remark());
            return item;
        }).toList());
        return operationService.executeLong("CREATE_FINANCE_RECEIPT", command.idempotencyKey(), command,
                () -> financeReceiptService.createFinanceReceipt(request));
    }

    @Override
    public Boolean setFinanceReceiptStatus(DocumentStatusCommand command) {
        return operationService.executeBoolean("SET_FINANCE_RECEIPT_STATUS", command.idempotencyKey(), command, () -> {
            financeReceiptService.updateFinanceReceiptStatus(command.documentId(), command.targetStatus());
            return true;
        });
    }

    @Override
    public Long createMerchant(YudaoWmsCommandApi.MerchantCommand command) {
        WmsMerchantSaveReqVO request = new WmsMerchantSaveReqVO();
        request.setCode(command.code());
        request.setName(command.name());
        request.setType(command.type());
        request.setLevel(command.level());
        request.setAddress(command.address());
        request.setMobile(command.mobile());
        request.setContact(command.contact());
        request.setEmail(command.email());
        request.setRemark(command.remark());
        return operationService.executeLong("CREATE_WMS_MERCHANT", command.idempotencyKey(), command,
                () -> wmsMerchantService.createMerchant(request));
    }

    @Override
    public Long createWarehouse(YudaoWmsCommandApi.WarehouseCommand command) {
        WmsWarehouseSaveReqVO request = new WmsWarehouseSaveReqVO();
        request.setCode(command.code());
        request.setName(command.name());
        request.setSort(command.sort());
        request.setRemark(command.remark());
        return operationService.executeLong("CREATE_WMS_WAREHOUSE", command.idempotencyKey(), command,
                () -> wmsWarehouseService.createWarehouse(request));
    }

    @Override
    public Long createItemCategory(YudaoWmsCommandApi.ItemCategoryCommand command) {
        WmsItemCategorySaveReqVO request = new WmsItemCategorySaveReqVO();
        request.setParentId(command.parentId());
        request.setCode(command.code());
        request.setName(command.name());
        request.setSort(command.sort());
        request.setStatus(command.status());
        return operationService.executeLong("CREATE_WMS_ITEM_CATEGORY", command.idempotencyKey(), command,
                () -> wmsItemCategoryService.createItemCategory(request));
    }

    @Override
    public Long createItem(YudaoWmsCommandApi.ItemCommand command) {
        WmsItemSaveReqVO request = new WmsItemSaveReqVO();
        request.setCode(command.code());
        request.setName(command.name());
        request.setCategoryId(command.categoryId());
        request.setUnit(command.unit());
        request.setRemark(command.remark());
        request.setSkus(command.skus().stream().map(line -> {
            WmsItemSkuSaveReqVO sku = new WmsItemSkuSaveReqVO();
            sku.setName(line.name());
            sku.setBarCode(line.barCode());
            sku.setCode(line.code());
            sku.setLength(line.length());
            sku.setWidth(line.width());
            sku.setHeight(line.height());
            sku.setGrossWeight(line.grossWeight());
            sku.setNetWeight(line.netWeight());
            sku.setCostPrice(line.costPrice());
            sku.setSellingPrice(line.sellingPrice());
            return sku;
        }).toList());
        return operationService.executeLong("CREATE_WMS_ITEM", command.idempotencyKey(), command,
                () -> wmsItemService.createItem(request));
    }

    @Override
    public Long createUnitMeasure(YudaoMesCommandApi.UnitMeasureCommand command) {
        MesMdUnitMeasureSaveReqVO request = new MesMdUnitMeasureSaveReqVO();
        request.setCode(command.code());
        request.setName(command.name());
        request.setPrimaryFlag(command.primaryFlag());
        request.setPrimaryId(command.primaryId());
        request.setChangeRate(command.changeRate());
        request.setStatus(command.status());
        request.setRemark(command.remark());
        return operationService.executeLong("CREATE_MES_UNIT_MEASURE", command.idempotencyKey(), command,
                () -> mesUnitMeasureService.createUnitMeasure(request));
    }

    @Override
    public Long createItemType(YudaoMesCommandApi.ItemTypeCommand command) {
        MesMdItemTypeSaveReqVO request = new MesMdItemTypeSaveReqVO();
        request.setParentId(command.parentId());
        request.setCode(command.code());
        request.setName(command.name());
        request.setItemOrProduct(command.itemOrProduct());
        request.setSort(command.sort());
        request.setStatus(command.status());
        request.setRemark(command.remark());
        return operationService.executeLong("CREATE_MES_ITEM_TYPE", command.idempotencyKey(), command,
                () -> mesItemTypeService.createItemType(request));
    }

    @Override
    public Long createItem(YudaoMesCommandApi.ItemCommand command) {
        MesMdItemSaveReqVO request = new MesMdItemSaveReqVO();
        request.setCode(command.code());
        request.setName(command.name());
        request.setSpecification(command.specification());
        request.setUnitMeasureId(command.unitMeasureId());
        request.setItemTypeId(command.itemTypeId());
        request.setSafeStockFlag(command.safeStockFlag());
        request.setMinStock(command.minStock());
        request.setMaxStock(command.maxStock());
        request.setHighValue(command.highValue());
        request.setBatchFlag(command.batchFlag());
        request.setRemark(command.remark());
        return operationService.executeLong("CREATE_MES_ITEM", command.idempotencyKey(), command,
                () -> mesItemService.createItem(request));
    }

    @Override
    public Long createWorkshop(YudaoMesCommandApi.WorkshopCommand command) {
        MesMdWorkshopSaveReqVO request = new MesMdWorkshopSaveReqVO();
        request.setCode(command.code());
        request.setName(command.name());
        request.setArea(command.area());
        request.setChargeUserId(command.chargeUserId());
        request.setStatus(command.status());
        request.setRemark(command.remark());
        return operationService.executeLong("CREATE_MES_WORKSHOP", command.idempotencyKey(), command,
                () -> mesWorkshopService.createWorkshop(request));
    }

    @Override
    public Long createProcess(YudaoMesCommandApi.ProcessCommand command) {
        MesProProcessSaveReqVO request = new MesProProcessSaveReqVO();
        request.setCode(command.code());
        request.setName(command.name());
        request.setAttention(command.attention());
        request.setStatus(command.status());
        request.setRemark(command.remark());
        return operationService.executeLong("CREATE_MES_PROCESS", command.idempotencyKey(), command,
                () -> mesProcessService.createProcess(request));
    }

    @Override
    public Long createWorkstation(YudaoMesCommandApi.WorkstationCommand command) {
        MesMdWorkstationSaveReqVO request = new MesMdWorkstationSaveReqVO();
        request.setCode(command.code());
        request.setName(command.name());
        request.setAddress(command.address());
        request.setWorkshopId(command.workshopId());
        request.setProcessId(command.processId());
        request.setWarehouseId(command.warehouseId());
        request.setLocationId(command.locationId());
        request.setAreaId(command.areaId());
        request.setStatus(command.status());
        request.setRemark(command.remark());
        return operationService.executeLong("CREATE_MES_WORKSTATION", command.idempotencyKey(), command,
                () -> mesWorkstationService.createWorkstation(request));
    }

    @Override
    public Long createRoute(YudaoMesCommandApi.RouteCommand command) {
        MesProRouteSaveReqVO request = new MesProRouteSaveReqVO();
        request.setCode(command.code());
        request.setName(command.name());
        request.setDescription(command.description());
        request.setRemark(command.remark());
        return operationService.executeLong("CREATE_MES_ROUTE", command.idempotencyKey(), command,
                () -> mesRouteService.createRoute(request));
    }

    @Override
    public Long createRouteProcess(YudaoMesCommandApi.RouteProcessCommand command) {
        MesProRouteProcessSaveReqVO request = new MesProRouteProcessSaveReqVO();
        request.setRouteId(command.routeId());
        request.setProcessId(command.processId());
        request.setSort(command.sort());
        request.setLinkType(command.linkType());
        request.setPrepareTime(command.prepareTime());
        request.setWaitTime(command.waitTime());
        request.setColorCode(command.colorCode());
        request.setKeyFlag(command.keyFlag());
        request.setCheckFlag(command.checkFlag());
        request.setRemark(command.remark());
        return operationService.executeLong("CREATE_MES_ROUTE_PROCESS", command.idempotencyKey(), command,
                () -> mesRouteProcessService.createRouteProcess(request));
    }

    @Override
    public Boolean setRouteStatus(YudaoMesCommandApi.RouteStatusCommand command) {
        return operationService.executeBoolean("SET_MES_ROUTE_STATUS", command.idempotencyKey(), command, () -> {
            mesRouteService.updateRouteStatus(command.routeId(), command.status());
            return true;
        });
    }

    @Override
    public Long createWorkOrder(WorkOrderCommand command) {
        MesProWorkOrderSaveReqVO request = new MesProWorkOrderSaveReqVO();
        request.setCode(command.code());
        request.setName(command.name());
        request.setType(command.type());
        request.setOrderSourceType(command.orderSourceType());
        request.setOrderSourceCode(command.orderSourceCode());
        request.setProductId(command.productId());
        request.setQuantity(command.quantity());
        request.setClientId(command.clientId());
        request.setVendorId(command.vendorId());
        request.setBatchCode(command.batchCode());
        request.setRequestDate(parseBusinessTime(command.requestDate(), "requestDate"));
        request.setParentId(command.parentId());
        request.setRemark(command.remark());
        return operationService.executeLong("CREATE_MES_WORK_ORDER", command.idempotencyKey(), command,
                () -> workOrderService.createWorkOrder(request));
    }

    @Override
    public Boolean confirmWorkOrder(YudaoMesCommandApi.DocumentActionCommand command) {
        return operationService.executeBoolean("CONFIRM_MES_WORK_ORDER", command.idempotencyKey(), command, () -> {
            workOrderService.confirmWorkOrder(command.documentId());
            return true;
        });
    }

    @Override
    public Long createTask(YudaoMesCommandApi.TaskCommand command) {
        MesProTaskSaveReqVO request = new MesProTaskSaveReqVO();
        request.setWorkOrderId(command.workOrderId());
        request.setWorkstationId(command.workstationId());
        request.setRouteId(command.routeId());
        request.setProcessId(command.processId());
        request.setItemId(command.itemId());
        request.setQuantity(command.quantity());
        request.setStartTime(parseBusinessTime(command.startTime(), "startTime"));
        request.setDuration(command.duration());
        request.setEndTime(parseBusinessTime(command.endTime(), "endTime"));
        request.setColorCode(command.colorCode());
        request.setRemark(command.remark());
        return operationService.executeLong("CREATE_MES_TASK", command.idempotencyKey(), command,
                () -> mesTaskService.createTask(request));
    }

    @Override
    public Long createFeedback(YudaoMesCommandApi.FeedbackCommand command) {
        MesProFeedbackSaveReqVO request = new MesProFeedbackSaveReqVO();
        request.setCode(command.code());
        request.setType(command.type());
        request.setWorkstationId(command.workstationId());
        request.setRouteId(command.routeId());
        request.setProcessId(command.processId());
        request.setWorkOrderId(command.workOrderId());
        request.setTaskId(command.taskId());
        request.setItemId(command.itemId());
        request.setExpireDate(parseOptionalBusinessTime(command.expireDate(), "expireDate"));
        request.setLotNumber(command.lotNumber());
        request.setScheduledQuantity(command.scheduledQuantity());
        request.setFeedbackQuantity(command.feedbackQuantity());
        request.setQualifiedQuantity(command.qualifiedQuantity());
        request.setUnqualifiedQuantity(command.unqualifiedQuantity());
        request.setUncheckQuantity(command.uncheckQuantity());
        request.setLaborScrapQuantity(command.laborScrapQuantity());
        request.setMaterialScrapQuantity(command.materialScrapQuantity());
        request.setOtherScrapQuantity(command.otherScrapQuantity());
        request.setFeedbackUserId(command.feedbackUserId());
        request.setFeedbackTime(parseBusinessTime(command.feedbackTime(), "feedbackTime"));
        request.setApproveUserId(command.approveUserId());
        request.setRemark(command.remark());
        return operationService.executeLong("CREATE_MES_FEEDBACK", command.idempotencyKey(), command,
                () -> mesFeedbackService.createFeedback(request));
    }

    @Override
    public Boolean submitFeedback(YudaoMesCommandApi.DocumentActionCommand command) {
        return operationService.executeBoolean("SUBMIT_MES_FEEDBACK", command.idempotencyKey(), command, () -> {
            mesFeedbackService.submitFeedback(command.documentId());
            return true;
        });
    }

    @Override
    public Boolean approveFeedback(YudaoMesCommandApi.DocumentActionCommand command) {
        return operationService.executeBoolean("APPROVE_MES_FEEDBACK", command.idempotencyKey(), command,
                () -> mesFeedbackService.approveFeedback(command.documentId()));
    }

    @Override
    public Boolean finishWorkOrder(YudaoMesCommandApi.DocumentActionCommand command) {
        return operationService.executeBoolean("FINISH_MES_WORK_ORDER", command.idempotencyKey(), command, () -> {
            workOrderService.finishWorkOrder(command.documentId());
            return true;
        });
    }

    @Override
    public LegacyDocumentView getPurchaseOrder(Long documentId) {
        ErpPurchaseOrderDO value = purchaseOrderService.getPurchaseOrder(documentId);
        return value == null ? null : view("ERP", "PURCHASE_ORDER", value.getId(), value.getNo(),
                value.getStatus(), value.getOrderTime(), value.getTotalCount(), value.getTotalPrice(), value.getRemark());
    }

    @Override
    public LegacyDocumentView getPurchaseIn(Long documentId) {
        ErpPurchaseInDO value = purchaseInService.getPurchaseIn(documentId);
        return value == null ? null : view("ERP", "PURCHASE_IN", value.getId(), value.getNo(),
                value.getStatus(), value.getInTime(), value.getTotalCount(), value.getTotalPrice(), value.getRemark());
    }

    @Override
    public LegacyDocumentView getSaleOrder(Long documentId) {
        ErpSaleOrderDO value = saleOrderService.getSaleOrder(documentId);
        return value == null ? null : view("ERP", "SALE_ORDER", value.getId(), value.getNo(),
                value.getStatus(), value.getOrderTime(), value.getTotalCount(), value.getTotalPrice(), value.getRemark());
    }

    @Override
    public LegacyDocumentView getSaleOut(Long documentId) {
        ErpSaleOutDO value = saleOutService.getSaleOut(documentId);
        return value == null ? null : view("ERP", "SALE_OUT", value.getId(), value.getNo(),
                value.getStatus(), value.getOutTime(), value.getTotalCount(), value.getTotalPrice(), value.getRemark());
    }

    @Override
    public LegacyDocumentView getStockMove(Long documentId) {
        ErpStockMoveDO value = stockMoveService.getStockMove(documentId);
        return value == null ? null : view("ERP", "STOCK_MOVE", value.getId(), value.getNo(),
                value.getStatus(), value.getMoveTime(), value.getTotalCount(), value.getTotalPrice(), value.getRemark());
    }

    @Override
    public LegacyDocumentView getStockCheck(Long documentId) {
        ErpStockCheckDO value = stockCheckService.getStockCheck(documentId);
        return value == null ? null : view("ERP", "STOCK_CHECK", value.getId(), value.getNo(),
                value.getStatus(), value.getCheckTime(), value.getTotalCount(), value.getTotalPrice(), value.getRemark());
    }

    @Override
    public LegacyDocumentView getFinancePayment(Long documentId) {
        ErpFinancePaymentDO value = financePaymentService.getFinancePayment(documentId);
        return value == null ? null : view("ERP", "FINANCE_PAYMENT", value.getId(), value.getNo(),
                value.getStatus(), value.getPaymentTime(), null, value.getPaymentPrice(), value.getRemark());
    }

    @Override
    public LegacyDocumentView getFinanceReceipt(Long documentId) {
        ErpFinanceReceiptDO value = financeReceiptService.getFinanceReceipt(documentId);
        return value == null ? null : view("ERP", "FINANCE_RECEIPT", value.getId(), value.getNo(),
                value.getStatus(), value.getReceiptTime(), null, value.getReceiptPrice(), value.getRemark());
    }

    @Override
    public LegacyDocumentView getReceiptOrder(Long documentId) {
        return toLegacyDocument(wmsPhysicalOperationsPort.getReceiptOrder(documentId));
    }

    @Override
    public LegacyDocumentView getShipmentOrder(Long documentId) {
        return toLegacyDocument(wmsPhysicalOperationsPort.getShipmentOrder(documentId));
    }

    @Override
    public LegacyDocumentView getMovementOrder(Long documentId) {
        return toLegacyDocument(wmsPhysicalOperationsPort.getMovementOrder(documentId));
    }

    @Override
    public LegacyDocumentView getCheckOrder(Long documentId) {
        return toLegacyDocument(wmsPhysicalOperationsPort.getCheckOrder(documentId));
    }

    @Override
    public LegacyDocumentView getWorkOrder(Long documentId) {
        MesProWorkOrderDO value = workOrderService.getWorkOrder(documentId);
        return value == null ? null : view("MES", "WORK_ORDER", value.getId(), value.getCode(),
                value.getStatus(), value.getRequestDate(), value.getQuantity(), null, value.getRemark());
    }

    @Override
    public MesProductionExecutionView getMesProductionExecution(Long workOrderId, Long taskId, Long feedbackId) {
        MesProWorkOrderDO workOrder = workOrderService.getWorkOrder(workOrderId);
        MesProTaskDO task = mesTaskService.getTask(taskId);
        MesProFeedbackDO feedback = mesFeedbackService.getFeedback(feedbackId);
        if (workOrder == null || task == null || feedback == null) {
            return null;
        }
        MesWmProductProduceDO produce = mesProductProduceService.getProductProduceByFeedbackId(feedbackId);
        List<MesWmProductProduceLineDO> outputLines = produce == null
                ? List.of()
                : mesProductProduceLineService.getProductProduceLineListByProduceId(produce.getId());
        BigDecimal outputQuantity = sumQuantities(outputLines);
        BigDecimal passedOutputQuantity = sumQuantities(outputLines.stream()
                .filter(line -> Objects.equals(line.getQualityStatus(), MesWmQualityStatusEnum.PASS.getStatus())).toList());
        BigDecimal failedOutputQuantity = sumQuantities(outputLines.stream()
                .filter(line -> Objects.equals(line.getQualityStatus(), MesWmQualityStatusEnum.FAIL.getStatus())).toList());
        boolean closedLoop = Objects.equals(workOrder.getStatus(), MesProWorkOrderStatusEnum.FINISHED.getStatus())
                && Objects.equals(task.getStatus(), MesProTaskStatusEnum.FINISHED.getStatus())
                && Objects.equals(feedback.getStatus(), MesProFeedbackStatusEnum.FINISHED.getStatus())
                && produce != null
                && Objects.equals(produce.getStatus(), MesWmProductProduceStatusEnum.FINISHED.getStatus())
                && outputQuantity.compareTo(BigDecimal.ZERO) > 0
                && workOrder.getQuantityProduced() != null
                && workOrder.getQuantityProduced().compareTo(feedback.getFeedbackQuantity()) == 0
                && task.getProducedQuantity() != null
                && task.getProducedQuantity().compareTo(feedback.getFeedbackQuantity()) == 0;
        return new MesProductionExecutionView(
                workOrder.getId(), workOrder.getStatus(), workOrder.getQuantity(),
                workOrder.getQuantityScheduled(), workOrder.getQuantityProduced(),
                task.getId(), task.getStatus(), task.getQuantity(), task.getProducedQuantity(),
                task.getQualifyQuantity(), task.getUnqualifyQuantity(),
                feedback.getId(), feedback.getStatus(), feedback.getFeedbackQuantity(),
                feedback.getQualifiedQuantity(), feedback.getUnqualifiedQuantity(),
                produce == null ? null : produce.getId(), produce == null ? null : produce.getStatus(),
                outputQuantity, passedOutputQuantity, failedOutputQuantity,
                outputLines.stream().map(MesWmProductProduceLineDO::getBatchCode)
                        .filter(Objects::nonNull).distinct().toList(),
                closedLoop);
    }

    @Override
    public WmsSkuView getWmsItemSku(Long itemId) {
        var item = wmsItemService.getItem(itemId);
        if (item == null) {
            throw new IllegalArgumentException("WMS item does not exist: " + itemId);
        }
        var skus = wmsItemSkuService.getItemSkuList(itemId);
        if (skus.size() != 1) {
            throw new IllegalStateException("WMS item must have exactly one SKU for this atomic lookup: " + itemId);
        }
        var sku = skus.get(0);
        return new WmsSkuView(item.getId(), sku.getId(), item.getCode(), sku.getCode(), sku.getName());
    }

    @Override
    public WmsInventoryView getWmsInventory(Long warehouseId, Long skuId) {
        LegacyWmsPhysicalOperationsPort.InventorySnapshot snapshot = wmsPhysicalOperationsPort.getWmsInventory(warehouseId, skuId);
        return new WmsInventoryView(snapshot.inventoryId(), snapshot.warehouseId(), snapshot.skuId(), snapshot.quantity());
    }

    private static LegacyDocumentView view(String sourceSystem, String documentType, Long documentId,
                                           String documentNo, Integer status,
                                           java.time.LocalDateTime businessTime, BigDecimal quantity,
                                           BigDecimal amount, String remark) {
        return new LegacyDocumentView(sourceSystem, documentType, documentId, documentNo, status,
                businessTime == null ? null : businessTime.toString(), quantity, amount, remark);
    }

    private static LegacyDocumentView toLegacyDocument(LegacyWmsPhysicalOperationsPort.PhysicalOrderSnapshot snapshot) {
        return snapshot == null ? null : new LegacyDocumentView(
                snapshot.sourceSystem(),
                snapshot.documentType(),
                snapshot.documentId(),
                snapshot.documentNo(),
                snapshot.status(),
                snapshot.businessTime(),
                snapshot.quantity(),
                snapshot.amount(),
                snapshot.remark());
    }

    private static java.time.LocalDateTime parseBusinessTime(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required as ISO-8601 local date-time");
        }
        try {
            return java.time.LocalDateTime.parse(value);
        } catch (java.time.format.DateTimeParseException ex) {
            throw new IllegalArgumentException(field + " must be an ISO-8601 local date-time", ex);
        }
    }

    private static java.time.LocalDateTime parseOptionalBusinessTime(String value, String field) {
        return value == null || value.isBlank() ? null : parseBusinessTime(value, field);
    }

    private static BigDecimal sumQuantities(List<MesWmProductProduceLineDO> lines) {
        return lines.stream().map(MesWmProductProduceLineDO::getQuantity)
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
