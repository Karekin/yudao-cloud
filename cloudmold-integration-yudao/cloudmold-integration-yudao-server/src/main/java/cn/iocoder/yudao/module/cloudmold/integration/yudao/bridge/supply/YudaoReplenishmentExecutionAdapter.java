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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Creates only PREPARE documents. Approval and physical completion remain explicit operator actions.
 */
@Service
@RequiredArgsConstructor
public class YudaoReplenishmentExecutionAdapter implements ReplenishmentExecutionPort {
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Set<String> TARGETS = Set.of("PURCHASE_REQUEST", "TRANSFER_REQUEST");

    private final YudaoErpCommandApi erpCommandApi;
    private final YudaoWmsCommandApi wmsCommandApi;
    private final CatalogSkuProjectionApi catalogSkuProjectionApi;
    private final WarehouseSourceMappingQueryApi warehouseSourceMappingQueryApi;
    private final YudaoLegacyMasterDataQueryApi legacyMasterDataQueryApi;
    private final ErpProductService erpProductService;
    private final ErpProductUnitService erpProductUnitService;
    private final ErpPurchaseOrderService erpPurchaseOrderService;
    private final WmsItemService wmsItemService;
    private final WmsItemSkuService wmsItemSkuService;
    private final WmsMovementOrderService wmsMovementOrderService;
    private final WmsMovementOrderDetailService wmsMovementOrderDetailService;

    @Override
    public ExecutionResult createDraft(ExecutionCommand command) {
        require(command != null, "replenishment execution command is required");
        require(TARGETS.contains(command.targetType()), "unsupported replenishment execution target");
        require(command.quantity() != null && command.quantity().signum() > 0,
                "replenishment quantity must be positive");
        require(command.occurredAt() != null, "replenishment execution occurredAt is required");
        require(command.needByDate() != null, "replenishment needByDate is required");
        require(command.uomCode() != null && !command.uomCode().isBlank(), "replenishment uomCode is required");
        require(command.mappingEvidenceSha256() != null
                        && SHA256.matcher(command.mappingEvidenceSha256()).matches(),
                "governed mapping evidence is required");
        return switch (command.targetType()) {
            case "PURCHASE_REQUEST" -> createPurchaseDraft(resolvePurchaseDraft(command));
            case "TRANSFER_REQUEST" -> createTransferDraft(resolveTransferDraft(command));
            default -> throw new IllegalArgumentException("unsupported replenishment execution target");
        };
    }

    private ExecutionResult createPurchaseDraft(PurchaseDraftContext context) {
        Long documentId = erpCommandApi.createPurchaseOrder(
                new YudaoErpCommandApi.PurchaseOrderCommand(
                        context.command().idempotencyKey(), context.command().supplierId(),
                        context.command().accountId(), businessTime(context.command()),
                        new BigDecimal("100"), BigDecimal.ZERO, context.remark(),
                        List.of(new YudaoErpCommandApi.PurchaseOrderLine(
                                context.command().erpProductId(), context.command().erpProductUnitId(),
                                minorToMajor(context.command().unitCostMinor()),
                                context.command().quantity(), context.command().taxPercent(), context.remark()))));
        requirePositive(documentId, "ERP purchase order id");
        ErpPurchaseOrderDO order = erpPurchaseOrderService.getPurchaseOrder(documentId);
        require(order != null, "ERP purchase order readback is missing");
        require(Objects.equals(order.getStatus(), ErpAuditStatus.PROCESS.getStatus()),
                "ERP purchase order must stay in PREPARE status");
        require(Objects.equals(order.getSupplierId(), context.command().supplierId()),
                "ERP purchase order supplier drifted during readback");
        require(Objects.equals(order.getAccountId(), context.command().accountId()),
                "ERP purchase order account drifted during readback");
        require(context.remark().equals(order.getRemark()),
                "ERP purchase order governed remark drifted during readback");
        List<ErpPurchaseOrderItemDO> items =
                erpPurchaseOrderService.getPurchaseOrderItemListByOrderId(documentId);
        require(items != null && items.size() == 1,
                "ERP purchase order must contain exactly one governed line");
        ErpPurchaseOrderItemDO item = items.get(0);
        require(Objects.equals(item.getProductId(), context.command().erpProductId()),
                "ERP purchase order product drifted during readback");
        require(Objects.equals(item.getProductUnitId(), context.command().erpProductUnitId()),
                "ERP purchase order unit drifted during readback");
        requireDecimal(item.getCount(), context.command().quantity(),
                "ERP purchase order quantity drifted during readback");
        requireDecimal(item.getProductPrice(), minorToMajor(context.command().unitCostMinor()),
                "ERP purchase order unit cost drifted during readback");
        requireDecimal(item.getTaxPercent(), context.command().taxPercent(),
                "ERP purchase order tax percent drifted during readback");
        return new ExecutionResult("YUDAO_ERP", "PURCHASE_ORDER",
                documentId.toString(), order.getNo(), "PREPARE");
    }

    private ExecutionResult createTransferDraft(TransferDraftContext context) {
        String documentNo = "CM-TR-" + context.command().conversionId();
        BigDecimal unitPrice = minorToMajor(context.command().unitCostMinor());
        Long documentId = wmsCommandApi.createMovementOrder(
                new YudaoWmsCommandApi.MovementOrderCommand(
                        context.command().idempotencyKey(), documentNo, businessTime(context.command()),
                        context.remark(), context.command().sourceWarehouseId(),
                        context.command().targetWarehouseId(),
                        List.of(new YudaoWmsCommandApi.QuantityLine(
                                context.command().wmsSkuId(), context.command().quantity(), unitPrice,
                                unitPrice.multiply(context.command().quantity())
                                        .setScale(2, RoundingMode.HALF_UP)))));
        requirePositive(documentId, "WMS movement order id");
        WmsMovementOrderDO order = wmsMovementOrderService.getMovementOrder(documentId);
        require(order != null, "WMS movement order readback is missing");
        require(Objects.equals(order.getStatus(), WmsOrderStatusEnum.PREPARE.getStatus()),
                "WMS movement order must stay in PREPARE status");
        require(Objects.equals(order.getSourceWarehouseId(), context.command().sourceWarehouseId()),
                "WMS movement order source warehouse drifted during readback");
        require(Objects.equals(order.getTargetWarehouseId(), context.command().targetWarehouseId()),
                "WMS movement order target warehouse drifted during readback");
        require(documentNo.equals(order.getNo()), "WMS movement order number drifted during readback");
        require(context.remark().equals(order.getRemark()),
                "WMS movement order governed remark drifted during readback");
        List<WmsMovementOrderDetailDO> details =
                wmsMovementOrderDetailService.getMovementOrderDetailList(documentId);
        require(details != null && details.size() == 1,
                "WMS movement order must contain exactly one governed line");
        WmsMovementOrderDetailDO detail = details.get(0);
        require(Objects.equals(detail.getSkuId(), context.command().wmsSkuId()),
                "WMS movement order SKU drifted during readback");
        require(Objects.equals(detail.getSourceWarehouseId(), context.command().sourceWarehouseId()),
                "WMS movement order detail source warehouse drifted during readback");
        require(Objects.equals(detail.getTargetWarehouseId(), context.command().targetWarehouseId()),
                "WMS movement order detail target warehouse drifted during readback");
        requireDecimal(detail.getQuantity(), context.command().quantity(),
                "WMS movement order quantity drifted during readback");
        requireDecimal(detail.getPrice(), unitPrice,
                "WMS movement order unit cost drifted during readback");
        requireDecimal(detail.getTotalPrice(),
                unitPrice.multiply(context.command().quantity()).setScale(2, RoundingMode.HALF_UP),
                "WMS movement order line total drifted during readback");
        return new ExecutionResult("YUDAO_WMS", "MOVEMENT_ORDER",
                documentId.toString(), order.getNo(), "PREPARE");
    }

    private PurchaseDraftContext resolvePurchaseDraft(ExecutionCommand command) {
        requirePositive(command.supplierId(), "supplierId");
        requirePositive(command.accountId(), "accountId");
        requirePositive(command.erpProductId(), "erpProductId");
        requirePositive(command.erpProductUnitId(), "erpProductUnitId");
        require(command.unitCostMinor() != null && command.unitCostMinor() >= 0,
                "unitCostMinor is required");
        require(command.taxPercent() != null
                        && command.taxPercent().signum() >= 0
                        && command.taxPercent().compareTo(new BigDecimal("100")) <= 0,
                "taxPercent must be between 0 and 100");
        CatalogSkuProjectionView sku = resolveCatalogSku(command);
        ErpProductDO product = erpProductService.getProduct(command.erpProductId());
        require(product != null, "ERP product does not exist");
        require(Objects.equals(product.getStatus(), CommonStatusEnum.ENABLE.getStatus()),
                "ERP product is not active");
        require(Objects.equals(product.getUnitId(), command.erpProductUnitId()),
                "ERP product unit does not match the requested mapping");
        ErpProductUnitDO unit = erpProductUnitService.getProductUnit(command.erpProductUnitId());
        require(unit != null, "ERP product unit does not exist");
        require(Objects.equals(unit.getStatus(), CommonStatusEnum.ENABLE.getStatus()),
                "ERP product unit is not active");
        require(matchesStableIdentity(sku, product.getBarCode(), product.getName()),
                "ERP product no longer matches the canonical SKU identity");
        require(unitCode(unit.getName()).equals(unitCode(command.uomCode())),
                "ERP product unit no longer matches the canonical UOM");
        String warehouseEvidence = "warehouse-ref:canonical-only";
        if (command.targetWarehouseId() != null) {
            YudaoLegacyMasterDataQueryApi.LegacyWarehouseView warehouse =
                    legacyMasterDataQueryApi.getErpWarehouse(command.targetWarehouseId());
            require(warehouse != null && Objects.equals(warehouse.status(), CommonStatusEnum.ENABLE.getStatus()),
                    "ERP warehouse is not active");
            WarehouseNetworkView network = warehouseSourceMappingQueryApi.resolveReadyNetwork(
                    new WarehouseSourceReference("ERP", "WAREHOUSE",
                            String.valueOf(command.targetWarehouseId())),
                    command.occurredAt());
            require(command.canonicalWarehouseId().equals(network.getWarehouseId()),
                    "ERP warehouse mapping no longer resolves to the canonical warehouse");
            warehouseEvidence = "warehouse-mapping:" + network.getMappingId();
        }
        String expectedEvidence = evidenceSha256(List.of(
                "PURCHASE_REQUEST", command.canonicalSkuId(), command.canonicalWarehouseId(),
                sku.getSkuCode(), nullToEmpty(sku.getPrimaryBarcode()), unitCode(sku.getBaseUomCode()),
                String.valueOf(command.erpProductId()), nullToEmpty(product.getBarCode()),
                nullToEmpty(product.getName()), String.valueOf(command.erpProductUnitId()),
                unitCode(unit.getName()), warehouseEvidence));
        require(expectedEvidence.equals(command.mappingEvidenceSha256()),
                "replenishment mapping evidence no longer matches live canonical-to-ERP mapping");
        return new PurchaseDraftContext(command, remark(command, expectedEvidence, sku.getBaseUomCode()));
    }

    private TransferDraftContext resolveTransferDraft(ExecutionCommand command) {
        requirePositive(command.sourceWarehouseId(), "sourceWarehouseId");
        requirePositive(command.targetWarehouseId(), "targetWarehouseId");
        require(!command.sourceWarehouseId().equals(command.targetWarehouseId()),
                "source and target warehouse must differ");
        requirePositive(command.wmsSkuId(), "wmsSkuId");
        require(command.unitCostMinor() != null && command.unitCostMinor() >= 0,
                "unitCostMinor is required");
        CatalogSkuProjectionView sku = resolveCatalogSku(command);
        List<WmsItemSkuDO> skuMatches = wmsItemSkuService.getItemSkuListByIds(List.of(command.wmsSkuId()));
        WmsItemSkuDO wmsSku = skuMatches == null || skuMatches.isEmpty() ? null : skuMatches.get(0);
        require(wmsSku != null, "WMS SKU does not exist");
        WmsItemDO item = wmsItemService.getItem(wmsSku.getItemId());
        require(item != null, "WMS item does not exist");
        require(matchesStableIdentity(sku, wmsSku.getBarCode(),
                firstNonBlank(wmsSku.getCode(), wmsSku.getName(), item.getCode(), item.getName())),
                "WMS SKU no longer matches the canonical SKU identity");
        require(unitCode(item.getUnit()).equals(unitCode(command.uomCode())),
                "WMS item unit no longer matches the canonical UOM");
        WarehouseNetworkView network = warehouseSourceMappingQueryApi.resolveReadyNetwork(
                new WarehouseSourceReference("WMS", "WAREHOUSE",
                        String.valueOf(command.targetWarehouseId())),
                command.occurredAt());
        require(command.canonicalWarehouseId().equals(network.getWarehouseId()),
                "WMS target warehouse mapping no longer resolves to the canonical warehouse");
        String expectedEvidence = evidenceSha256(List.of(
                "TRANSFER_REQUEST", command.canonicalSkuId(), command.canonicalWarehouseId(),
                sku.getSkuCode(), nullToEmpty(sku.getPrimaryBarcode()), unitCode(sku.getBaseUomCode()),
                String.valueOf(command.wmsSkuId()), String.valueOf(item.getId()),
                nullToEmpty(wmsSku.getCode()), nullToEmpty(wmsSku.getBarCode()),
                unitCode(item.getUnit()), String.valueOf(command.sourceWarehouseId()),
                String.valueOf(command.targetWarehouseId()), network.getMappingId()));
        require(expectedEvidence.equals(command.mappingEvidenceSha256()),
                "replenishment mapping evidence no longer matches live canonical-to-WMS mapping");
        return new TransferDraftContext(command, remark(command, expectedEvidence, sku.getBaseUomCode()));
    }

    private CatalogSkuProjectionView resolveCatalogSku(ExecutionCommand command) {
        require(command.canonicalSkuId() != null && !command.canonicalSkuId().isBlank(),
                "canonicalSkuId is required");
        require(command.canonicalWarehouseId() != null && !command.canonicalWarehouseId().isBlank(),
                "canonicalWarehouseId is required");
        CatalogSkuProjectionView sku = catalogSkuProjectionApi.getActiveSku(command.canonicalSkuId());
        require(sku != null, "canonical SKU does not exist");
        require("ACTIVE".equals(sku.getCatalogStatus()), "canonical SKU must be ACTIVE");
        require(unitCode(sku.getBaseUomCode()).equals(unitCode(command.uomCode())),
                "replenishment UOM must match the canonical base UOM");
        return sku;
    }

    private static boolean matchesStableIdentity(CatalogSkuProjectionView sku, String barcode, String nameOrCode) {
        if (hasText(sku.getPrimaryBarcode()) && hasText(barcode)) {
            return sku.getPrimaryBarcode().trim().equalsIgnoreCase(barcode.trim());
        }
        if (hasText(sku.getSkuCode()) && hasText(nameOrCode)) {
            return sku.getSkuCode().trim().equalsIgnoreCase(nameOrCode.trim())
                    || (hasText(sku.getProductName())
                    && sku.getProductName().trim().equalsIgnoreCase(nameOrCode.trim()));
        }
        return false;
    }

    private static String remark(ExecutionCommand command, String mappingEvidenceSha256, String canonicalUomCode) {
        return "CloudMold replenishment " + command.conversionId()
                + " need-by:" + command.needByDate()
                + " uom:" + canonicalUomCode
                + " mapping-sha256:" + mappingEvidenceSha256;
    }

    private static String businessTime(ExecutionCommand command) {
        return command.occurredAt().atZone(ZoneOffset.UTC).toLocalDateTime().toString();
    }

    private static BigDecimal minorToMajor(Long value) {
        return BigDecimal.valueOf(value, 2);
    }

    private static String evidenceSha256(List<String> values) {
        return DigestUtil.sha256Hex(String.join("\u001f", values));
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private static String unitCode(String value) {
        return nullToEmpty(value).trim().replace('-', '_').replace(' ', '_').toUpperCase();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static void requirePositive(Long value, String field) {
        require(value != null && value > 0, field + " must be a positive identifier");
    }

    private static void requireDecimal(BigDecimal actual, BigDecimal expected, String message) {
        require(actual != null && expected != null && actual.compareTo(expected) == 0, message);
    }

    private static void require(boolean valid, String message) {
        if (!valid) {
            throw new IllegalArgumentException(message);
        }
    }

    private record PurchaseDraftContext(ExecutionCommand command, String remark) {
    }

    private record TransferDraftContext(ExecutionCommand command, String remark) {
    }
}
