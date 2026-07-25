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
import cn.iocoder.yudao.module.cloudmold.merchant.api.SourceReference;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.WarehouseNetworkView;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.WarehouseSourceMappingQueryApi;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.WarehouseSourceReference;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class YudaoWmsReceiptInventoryBridgeService {

    private final YudaoWmsReceiptInventoryBridgeMapper bridgeMapper;
    private final LegacyCatalogProjectionMapper legacyCatalogProjectionMapper;
    private final CatalogSkuProjectionApi catalogSkuProjectionApi;
    private final SourceMappingQueryApi sourceMappingQueryApi;
    private final WarehouseSourceMappingQueryApi warehouseSourceMappingQueryApi;
    private final InventoryV3CommandApi inventoryV3CommandApi;
    private final LegacyWmsSkuReadPort wmsSkuReadPort;

    public ReceiptBridgeReplay resolveReplay(Long receiptOrderId,
                                             LegacyWmsPhysicalOperationsPort.ReceiptOrderContext context) {
        List<YudaoWmsReceiptInventoryBridgeRow> rows =
                bridgeMapper.selectByReceiptOrderId(TenantContextHolder.getRequiredTenantId(), receiptOrderId);
        if (rows == null || rows.isEmpty()) {
            return ReceiptBridgeReplay.none();
        }
        require(context != null && context.lines() != null && context.lines().size() == 1,
                "finished receipt order does not match the canonical inventory evidence shape");
        require(rows.size() == 1, "finished receipt order has ambiguous canonical inventory evidence");
        YudaoWmsReceiptInventoryBridgeRow row = rows.get(0);
        LegacyWmsPhysicalOperationsPort.ReceiptLineSnapshot line = context.lines().get(0);
        require(Objects.equals(row.getReceiptOrderLineId(), line.receiptLineId()),
                "finished receipt line does not match canonical inventory evidence");
        return new ReceiptBridgeReplay(true, row);
    }

    public PreparedReceipt prepare(LegacyWmsPhysicalOperationsPort.ReceiptOrderContext context) {
        require(context != null && context.order() != null, "receipt order context is required");
        require(context.lines() != null && context.lines().size() == 1,
                "WMS receipt bridging supports exactly one receipt line");
        LegacyWmsPhysicalOperationsPort.ReceiptLineSnapshot line = context.lines().get(0);
        require(line.receiptLineId() != null && line.receiptLineId() > 0, "receipt line id is required");
        require(line.warehouseId() != null && Objects.equals(line.warehouseId(), context.order().warehouseId()),
                "receipt line warehouse must match the receipt order warehouse");
        require(line.quantity() != null && line.quantity().signum() > 0, "receipt quantity must be positive");
        requireDecimal(context.order().quantity(), line.quantity(),
                "receipt order total quantity must equal its single line quantity");
        requireDecimal(context.order().amount(), line.totalPrice(),
                "receipt order total amount must equal its single line total");
        require(context.merchantId() != null && context.merchantId() > 0,
                "receipt order merchant mapping is required");
        require(context.order().warehouseId() != null && context.order().warehouseId() > 0,
                "receipt order warehouse mapping is required");

        Instant effectiveAt = parseOccurredAt(context.order().businessTime());
        var ownerMapping = sourceMappingQueryApi.resolveActive(new SourceReference()
                .setSourceSystem("WMS")
                .setSourceType("MERCHANT")
                .setSourceId(String.valueOf(context.merchantId()))
                .setEffectiveAt(effectiveAt));
        require("MERCHANT".equals(ownerMapping.getTargetType()),
                "receipt merchant mapping must resolve to a canonical Merchant");
        String ownerId = ownerMapping.getTargetId();
        WarehouseNetworkView network = warehouseSourceMappingQueryApi.resolveReadyNetwork(
                new WarehouseSourceReference("WMS", "WAREHOUSE",
                        String.valueOf(context.order().warehouseId())),
                effectiveAt);
        CatalogSkuProjectionView sku = resolveCanonicalSku(line.skuId());
        require(normalizeCode(sku.getBaseUomCode()) != null, "canonical base UOM is required");
        return new PreparedReceipt(
                context.order().documentId(),
                context.order().documentNo(),
                context.merchantId(),
                context.order().warehouseId(),
                line,
                ownerId,
                sku,
                network,
                effectiveAt);
    }

    public void apply(PreparedReceipt prepared) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        List<YudaoWmsReceiptInventoryBridgeRow> existing =
                bridgeMapper.selectByReceiptOrderId(tenantId, prepared.receiptOrderId());
        if (existing != null && !existing.isEmpty()) {
            require(existing.size() == 1
                            && Objects.equals(existing.get(0).getReceiptOrderLineId(),
                            prepared.line().receiptLineId()),
                    "receipt order already has conflicting canonical inventory evidence");
            return;
        }
        InventoryV3CommandResult result = inventoryV3CommandApi.execute(InventoryV3Command.builder()
                .operation(cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryV3Operation.RECEIVE)
                .idempotencyKey(inventoryIdempotencyKey(prepared))
                .sourceEventId(sourceEventId(prepared))
                .ownerType("MERCHANT")
                .ownerId(prepared.canonicalOwnerId())
                .canonicalSkuId(prepared.catalogSku().getCanonicalSkuId())
                .warehouseId(prepared.network().getWarehouseId())
                .locationId(prepared.network().getLocationId())
                .lotId(null)
                .stockStatus("SELLABLE")
                .qualityStatus("QUALIFIED")
                .baseUomCode(normalizeCode(prepared.catalogSku().getBaseUomCode()))
                .quantity(prepared.line().quantity())
                .businessType("WMS_RECEIPT_ORDER")
                .businessId(businessId(prepared))
                .businessItemId(businessItemId(prepared))
                .businessNo(prepared.receiptOrderNo())
                .correlationId(correlationId(prepared))
                .causationId(sourceEventId(prepared))
                .occurredAt(prepared.effectiveAt())
                .build());
        require(result != null, "inventory v3 receive returned no result");
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        require(bridgeMapper.insert(new YudaoWmsReceiptInventoryBridgeRow()
                .setTenantId(tenantId)
                .setReceiptOrderId(prepared.receiptOrderId())
                .setReceiptOrderNo(prepared.receiptOrderNo())
                .setReceiptOrderLineId(prepared.line().receiptLineId())
                .setWmsMerchantId(prepared.wmsMerchantId())
                .setWmsWarehouseId(prepared.wmsWarehouseId())
                .setWmsSkuId(prepared.line().skuId())
                .setCanonicalOwnerId(prepared.canonicalOwnerId())
                .setCanonicalSkuId(prepared.catalogSku().getCanonicalSkuId())
                .setWarehouseMappingId(prepared.network().getMappingId())
                .setCanonicalWarehouseId(prepared.network().getWarehouseId())
                .setCanonicalZoneId(prepared.network().getZoneId())
                .setCanonicalLocationId(prepared.network().getLocationId())
                .setLotMappingStatus("NOT_TRACKED")
                .setCanonicalLotId(null)
                .setReceiptQuantity(prepared.line().quantity())
                .setBaseUomCode(normalizeCode(prepared.catalogSku().getBaseUomCode()))
                .setInventoryIdempotencyKey(inventoryIdempotencyKey(prepared))
                .setInventorySourceEventId(sourceEventId(prepared))
                .setInventoryBusinessId(businessId(prepared))
                .setInventoryBusinessItemId(businessItemId(prepared))
                .setInventoryOperationId(result.getOperationId())
                .setInventoryLedgerTransactionId(result.getLedgerTransactionId())
                .setInventoryBalanceId(result.getBalanceId())
                .setInventoryAggregateVersion(result.getAggregateVersion())
                .setCreatedAt(now)
                .setUpdatedAt(now)) == 1,
                "failed to persist receipt-to-inventory evidence");
    }

    private CatalogSkuProjectionView resolveCanonicalSku(Long wmsSkuId) {
        require(wmsSkuId != null && wmsSkuId > 0, "receipt line SKU mapping is required");
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LegacyWmsSkuReadPort.WmsSkuSnapshot wmsSku = wmsSkuReadPort.getSku(wmsSkuId);
        require(wmsSku != null, "receipt line SKU does not resolve uniquely in WMS");
        String skuCode = hasText(wmsSku.skuCode()) ? wmsSku.skuCode().trim() : null;
        String barcode = hasText(wmsSku.primaryBarcode()) ? wmsSku.primaryBarcode().trim() : null;
        require(skuCode != null || barcode != null,
                "receipt line WMS SKU must expose either a code or a primary barcode");
        List<String> candidates = legacyCatalogProjectionMapper.selectWmsCanonicalSkuCandidates(
                tenantId, skuCode, barcode);
        require(candidates != null && candidates.size() == 1,
                candidates == null || candidates.isEmpty()
                        ? "receipt line canonical SKU mapping does not exist"
                        : "receipt line canonical SKU mapping is ambiguous");
        CatalogSkuProjectionView view = catalogSkuProjectionApi.getActiveSku(candidates.get(0));
        require(view != null && "ACTIVE".equals(view.getCatalogStatus()),
                "receipt line canonical SKU is not active");
        if (barcode != null && hasText(view.getPrimaryBarcode())) {
            require(barcode.equalsIgnoreCase(view.getPrimaryBarcode()),
                    "receipt line barcode no longer matches the canonical SKU");
        }
        if (skuCode != null && hasText(view.getSkuCode())) {
            require(skuCode.equalsIgnoreCase(view.getSkuCode()),
                    "receipt line SKU code no longer matches the canonical SKU");
        }
        require(normalizeCode(wmsSku.baseUomCode()).equals(normalizeCode(view.getBaseUomCode())),
                "receipt line WMS unit no longer matches the canonical base UOM");
        return view;
    }

    static String inventoryIdempotencyKey(PreparedReceipt prepared) {
        return "wms-receipt:" + prepared.receiptOrderId() + ":line:" + prepared.line().receiptLineId() + ":receive";
    }

    static String sourceEventId(PreparedReceipt prepared) {
        return stableUuid("wms-receipt-source:" + prepared.receiptOrderId() + ":" + prepared.line().receiptLineId());
    }

    static String correlationId(PreparedReceipt prepared) {
        return stableUuid("wms-receipt-correlation:" + prepared.receiptOrderId());
    }

    static String businessId(PreparedReceipt prepared) {
        return "wms-receipt-order:" + prepared.receiptOrderId();
    }

    static String businessItemId(PreparedReceipt prepared) {
        return "wms-receipt-line:" + prepared.line().receiptLineId();
    }

    static String stableUuid(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static Instant parseOccurredAt(String businessTime) {
        require(businessTime != null && !businessTime.isBlank(), "receipt businessTime is required");
        try {
            return LocalDateTime.parse(businessTime).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("receipt businessTime must be ISO-8601 local date-time", ex);
        }
    }

    private static void requireDecimal(BigDecimal actual, BigDecimal expected, String message) {
        require(actual != null && expected != null && actual.compareTo(expected) == 0, message);
    }

    private static String normalizeCode(String value) {
        return value == null ? null : value.trim().replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    public record PreparedReceipt(Long receiptOrderId,
                                  String receiptOrderNo,
                                  Long wmsMerchantId,
                                  Long wmsWarehouseId,
                                  LegacyWmsPhysicalOperationsPort.ReceiptLineSnapshot line,
                                  String canonicalOwnerId,
                                  CatalogSkuProjectionView catalogSku,
                                  WarehouseNetworkView network,
                                  Instant effectiveAt) {
    }

    public record ReceiptBridgeReplay(boolean exists, YudaoWmsReceiptInventoryBridgeRow row) {
        static ReceiptBridgeReplay none() {
            return new ReceiptBridgeReplay(false, null);
        }
    }
}
