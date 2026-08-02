package cn.iocoder.yudao.module.cloudmold.inventory.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.catalog.api.CatalogSkuValidationApi;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryStockCountAdjustmentApi;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryStockCountAdjustmentCommand;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryStockCountAdjustmentResult;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.InventoryLotDO;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.InventoryStockCountAdjustmentDO;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.InventoryStockCountAdjustmentOperationDO;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.InventoryV3BalanceDO;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.InventoryV3LedgerEntryDO;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.InventoryV3LedgerTransactionDO;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql.InventoryLotMapper;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql.InventoryStockCountAdjustmentMapper;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql.InventoryStockCountAdjustmentOperationMapper;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql.InventoryV3BalanceMapper;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql.InventoryV3LedgerEntryMapper;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql.InventoryV3LedgerTransactionMapper;
import cn.iocoder.yudao.module.cloudmold.merchant.api.MerchantOwnerValidationApi;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.WarehouseReferenceValidationApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InventoryStockCountAdjustmentServiceImpl implements InventoryStockCountAdjustmentApi {

    private static final int OPERATION_SUCCEEDED = 10;
    private static final BigDecimal ZERO = new BigDecimal("0.000000");
    private static final Set<String> QUALITY_STATUSES = Set.of("PENDING_QC", "QUALIFIED", "DAMAGED", "REJECTED");
    private static final Set<String> STOCK_STATUSES = Set.of("SELLABLE", "NON_SELLABLE");

    private final InventoryStockCountAdjustmentOperationMapper operationMapper;
    private final InventoryStockCountAdjustmentMapper adjustmentMapper;
    private final InventoryV3BalanceMapper balanceMapper;
    private final InventoryLotMapper lotMapper;
    private final InventoryV3LedgerTransactionMapper ledgerTransactionMapper;
    private final InventoryV3LedgerEntryMapper ledgerEntryMapper;
    private final OutboxAppender outboxAppender;
    private final CatalogSkuValidationApi catalogSkuValidationApi;
    private final WarehouseReferenceValidationApi warehouseValidationApi;
    private final MerchantOwnerValidationApi merchantOwnerValidationApi;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InventoryStockCountAdjustmentResult execute(InventoryStockCountAdjustmentCommand rawCommand) {
        NormalizedCommand command = normalize(rawCommand);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String requestHash = fingerprint(tenantId, command);
        String attemptToken = UUID.randomUUID().toString();

        operationMapper.insertOrResolve(tenantId, command.idempotencyKey(), command.sourceEventId(),
                command.stockCountLineId(), requestHash, attemptToken, now);
        Long operationId = operationMapper.selectLastInsertId();
        require(operationId != null && operationId > 0, "failed to resolve stock count inventory adjustment operation");
        InventoryStockCountAdjustmentOperationDO operation = operationMapper.selectForUpdate(tenantId, operationId);
        require(operation != null, "stock count inventory adjustment operation disappeared");
        if (!attemptToken.equals(operation.getAttemptToken())) {
            require(Objects.equals(requestHash, operation.getRequestHash()),
                    "idempotency key or source event conflicts with different stock count adjustment payload");
            require(operation.getStatus() == OPERATION_SUCCEEDED && operation.getResultJson() != null,
                    "existing stock count inventory adjustment operation is not complete");
            InventoryStockCountAdjustmentResult replay = JsonUtils.parseObject(operation.getResultJson(),
                    InventoryStockCountAdjustmentResult.class);
            replay.setDuplicate(true);
            return replay;
        }

        require(adjustmentMapper.selectByStockCountLineId(tenantId, command.stockCountLineId()) == null,
                "stock count line already adjusted");
        merchantOwnerValidationApi.requireActiveMerchant(command.ownerId());
        catalogSkuValidationApi.requireActiveSku(command.canonicalSkuId());
        warehouseValidationApi.requireActiveLocation(command.warehouseId(), command.locationId());
        InventoryLotDO lot = requireLot(tenantId, command);

        balanceMapper.insertOrResolve(UUID.randomUUID().toString(), tenantId, command.ownerType(), command.ownerId(),
                command.canonicalSkuId(), command.warehouseId(), command.locationId(), command.lotId(),
                command.stockStatus(), command.qualityStatus(), command.baseUomCode(), now);
        InventoryV3BalanceDO balance = balanceMapper.selectDimensionForUpdate(tenantId, command.ownerType(),
                command.ownerId(), command.canonicalSkuId(), command.warehouseId(), command.locationId(),
                command.lotId(), command.stockStatus(), command.qualityStatus());
        require(balance != null, "stock count inventory balance does not exist");
        require(Objects.equals(balance.getBaseUomCode(), command.baseUomCode()),
                "base UOM does not match stock count inventory balance");

        BigDecimal beforeOnHand = scaled(balance.getOnHandQuantity());
        BigDecimal beforeReserved = scaled(balance.getReservedQuantity());
        BigDecimal beforeTransit = scaled(balance.getInTransitQuantity());
        require(beforeReserved.compareTo(ZERO) == 0, "stock count adjustment requires zero reserved quantity");
        require(beforeTransit.compareTo(ZERO) == 0, "stock count adjustment requires zero in-transit quantity");
        require(beforeOnHand.compareTo(command.bookOnHandQuantity()) == 0,
                "book on-hand quantity no longer matches frozen stock count snapshot");

        BigDecimal afterOnHand = command.countedOnHandQuantity();
        require(afterOnHand.signum() >= 0, "counted on-hand quantity cannot be negative");
        require(balanceMapper.updateBalanceCas(tenantId, balance.getBalanceId(), balance.getVersion(),
                afterOnHand, beforeReserved, beforeTransit, now) == 1,
                "stock count inventory balance version conflict");
        long aggregateVersion = balance.getVersion() + 1;

        InventoryV3LedgerTransactionDO transaction = new InventoryV3LedgerTransactionDO()
                .setTenantId(tenantId)
                .setOperationId(operationId)
                .setMovementGroupId(UUID.randomUUID().toString())
                .setCommandType("STOCK_COUNT_ADJUST")
                .setBusinessType(command.businessType())
                .setBusinessId(command.businessId())
                .setBusinessItemId(command.businessItemId())
                .setBusinessNo(command.businessNo())
                .setOccurredAt(LocalDateTime.ofInstant(command.occurredAt(), ZoneOffset.UTC))
                .setCreatedAt(now);
        ledgerTransactionMapper.insert(transaction);
        require(transaction.getLedgerTransactionId() != null,
                "stock count inventory ledger transaction id was not generated");

        ledgerEntryMapper.insert(new InventoryV3LedgerEntryDO()
                .setTenantId(tenantId)
                .setLedgerTransactionId(transaction.getLedgerTransactionId())
                .setMovementGroupId(transaction.getMovementGroupId())
                .setEntryRole("SINGLE")
                .setCounterpartyBalanceId(null)
                .setBalanceId(balance.getBalanceId())
                .setAggregateVersion(aggregateVersion)
                .setBaseUomCode(command.baseUomCode())
                .setBeforeOnHandQuantity(beforeOnHand)
                .setDeltaOnHandQuantity(command.adjustmentQuantity())
                .setAfterOnHandQuantity(afterOnHand)
                .setBeforeReservedQuantity(beforeReserved)
                .setDeltaReservedQuantity(ZERO)
                .setAfterReservedQuantity(beforeReserved)
                .setBeforeInTransitQuantity(beforeTransit)
                .setDeltaInTransitQuantity(ZERO)
                .setAfterInTransitQuantity(beforeTransit)
                .setCreatedAt(now));

        String adjustmentId = command.adjustmentId() == null || command.adjustmentId().isBlank()
                ? UUID.randomUUID().toString() : command.adjustmentId();
        adjustmentMapper.insert(new InventoryStockCountAdjustmentDO()
                .setAdjustmentId(adjustmentId)
                .setTenantId(tenantId)
                .setStockCountId(command.stockCountId())
                .setStockCountLineId(command.stockCountLineId())
                .setOwnerType(command.ownerType())
                .setOwnerId(command.ownerId())
                .setCanonicalSkuId(command.canonicalSkuId())
                .setWarehouseId(command.warehouseId())
                .setLocationId(command.locationId())
                .setLotId(command.lotId())
                .setStockStatus(command.stockStatus())
                .setQualityStatus(command.qualityStatus())
                .setBaseUomCode(command.baseUomCode())
                .setBalanceId(balance.getBalanceId())
                .setBookOnHandQuantity(command.bookOnHandQuantity())
                .setCountedOnHandQuantity(command.countedOnHandQuantity())
                .setAdjustmentQuantity(command.adjustmentQuantity())
                .setLedgerTransactionId(transaction.getLedgerTransactionId())
                .setAggregateVersion(aggregateVersion)
                .setStatus("APPLIED")
                .setOccurredAt(LocalDateTime.ofInstant(command.occurredAt(), ZoneOffset.UTC))
                .setCreatedAt(now)
                .setUpdatedAt(now));

        appendEvent(tenantId, command, lot, balance, transaction.getLedgerTransactionId(), transaction.getMovementGroupId(),
                aggregateVersion, beforeOnHand, afterOnHand, beforeReserved, beforeTransit);

        InventoryStockCountAdjustmentResult result = InventoryStockCountAdjustmentResult.builder()
                .operationId(operationId)
                .adjustmentId(adjustmentId)
                .ledgerTransactionId(transaction.getLedgerTransactionId())
                .balanceId(balance.getBalanceId())
                .aggregateVersion(aggregateVersion)
                .onHandQuantity(afterOnHand)
                .adjustmentQuantity(command.adjustmentQuantity())
                .duplicate(false)
                .build();
        require(operationMapper.markSucceeded(tenantId, operationId, result, now) == 1,
                "stock count inventory adjustment operation completion conflict");
        return result;
    }

    private void appendEvent(Long tenantId, NormalizedCommand command, InventoryLotDO lot, InventoryV3BalanceDO balance,
                             Long ledgerTransactionId, String movementGroupId, long aggregateVersion,
                             BigDecimal beforeOnHand, BigDecimal afterOnHand, BigDecimal reserved,
                             BigDecimal transit) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("movement_type", "STOCK_COUNT_ADJUSTMENT");
        payload.put("movement_group_id", movementGroupId);
        payload.put("stock_count_id", command.stockCountId());
        payload.put("stock_count_line_id", command.stockCountLineId());
        payload.put("owner_type", command.ownerType());
        payload.put("owner_id", command.ownerId());
        payload.put("canonical_sku_id", command.canonicalSkuId());
        payload.put("warehouse_id", command.warehouseId());
        payload.put("location_id", command.locationId());
        payload.put("lot_id", command.lotId());
        payload.put("lot_code", lot == null ? null : lot.getLotCode());
        payload.put("stock_status", command.stockStatus());
        payload.put("quality_status", command.qualityStatus());
        payload.put("base_uom_code", command.baseUomCode());
        payload.put("book_on_hand_quantity", decimal(command.bookOnHandQuantity()));
        payload.put("counted_on_hand_quantity", decimal(command.countedOnHandQuantity()));
        payload.put("delta_on_hand_quantity", decimal(command.adjustmentQuantity()));
        payload.put("delta_reserved_quantity", decimal(ZERO));
        payload.put("delta_in_transit_quantity", decimal(ZERO));
        payload.put("after_on_hand_quantity", decimal(afterOnHand));
        payload.put("after_reserved_quantity", decimal(reserved));
        payload.put("after_in_transit_quantity", decimal(transit));
        payload.put("ledger_transaction_id", ledgerTransactionId);
        payload.put("entry_role", "SINGLE");
        payload.put("business_type", command.businessType());
        payload.put("business_id", command.businessId());
        payload.put("business_item_id", command.businessItemId());
        payload.put("business_no", command.businessNo());
        outboxAppender.append(AppendDomainEventCommand.builder()
                .eventType("inventory.stock.changed")
                .schemaVersion(7)
                .sourceSystem("cloudmold-inventory")
                .tenantId(tenantId)
                .aggregateType("inventory_balance_v3")
                .aggregateId(balance.getBalanceId())
                .aggregateVersion(aggregateVersion)
                .eventSequence((short) 1)
                .occurredAt(command.occurredAt())
                .correlationId(command.correlationId())
                .causationId(command.causationId())
                .idempotencyKey(command.idempotencyKey() + ":event:v7")
                .payload(payload)
                .headers(Map.of(
                        "ledger_transaction_id", ledgerTransactionId,
                        "movement_group_id", movementGroupId,
                        "movement_type", "STOCK_COUNT_ADJUSTMENT"))
                .destination("lakehouse")
                .build());
    }

    private InventoryLotDO requireLot(Long tenantId, NormalizedCommand command) {
        if (command.lotId() == null) {
            return null;
        }
        InventoryLotDO lot = lotMapper.selectCurrent(tenantId, command.lotId());
        require(lot != null, "inventory lot does not exist");
        require(Objects.equals(lot.getOwnerType(), command.ownerType())
                        && Objects.equals(lot.getOwnerId(), command.ownerId())
                        && Objects.equals(lot.getCanonicalSkuId(), command.canonicalSkuId()),
                "inventory lot does not belong to stock count owner and SKU");
        require(!"CLOSED".equals(lot.getStatus()), "closed inventory lot cannot be adjusted by stock count");
        return lot;
    }

    private static NormalizedCommand normalize(InventoryStockCountAdjustmentCommand command) {
        require(command != null, "stock count inventory adjustment command is required");
        String ownerType = requireUpper(command.getOwnerType(), "ownerType");
        String ownerId = requireText(command.getOwnerId(), "ownerId");
        String canonicalSkuId = requireText(command.getCanonicalSkuId(), "canonicalSkuId");
        String warehouseId = requireText(command.getWarehouseId(), "warehouseId");
        String locationId = requireText(command.getLocationId(), "locationId");
        String stockStatus = requireUpper(command.getStockStatus(), "stockStatus");
        String qualityStatus = requireUpper(command.getQualityStatus(), "qualityStatus");
        require(STOCK_STATUSES.contains(stockStatus), "unsupported stockStatus");
        require(QUALITY_STATUSES.contains(qualityStatus), "unsupported qualityStatus");
        String baseUomCode = requireUpper(command.getBaseUomCode(), "baseUomCode");
        BigDecimal bookOnHand = scaled(requireNonNull(command.getBookOnHandQuantity(), "bookOnHandQuantity is required"));
        BigDecimal countedOnHand = scaled(requireNonNull(command.getCountedOnHandQuantity(), "countedOnHandQuantity is required"));
        BigDecimal adjustmentQuantity = scaled(requireNonNull(command.getAdjustmentQuantity(), "adjustmentQuantity is required"));
        require(countedOnHand.signum() >= 0, "countedOnHandQuantity cannot be negative");
        require(countedOnHand.subtract(bookOnHand).compareTo(adjustmentQuantity) == 0,
                "adjustmentQuantity must equal countedOnHandQuantity - bookOnHandQuantity");
        return new NormalizedCommand(
                requireText(command.getIdempotencyKey(), "idempotencyKey"),
                command.getSourceEventId() == null || command.getSourceEventId().isBlank()
                        ? null : command.getSourceEventId().trim(),
                requireText(command.getStockCountId(), "stockCountId"),
                requireText(command.getStockCountLineId(), "stockCountLineId"),
                command.getAdjustmentId() == null || command.getAdjustmentId().isBlank()
                        ? null : command.getAdjustmentId().trim(),
                ownerType,
                ownerId,
                canonicalSkuId,
                warehouseId,
                locationId,
                command.getLotId() == null || command.getLotId().isBlank() ? null : command.getLotId().trim(),
                stockStatus,
                qualityStatus,
                baseUomCode,
                bookOnHand,
                countedOnHand,
                adjustmentQuantity,
                requireUpper(command.getBusinessType(), "businessType"),
                requireText(command.getBusinessId(), "businessId"),
                requireText(command.getBusinessItemId(), "businessItemId"),
                requireText(command.getBusinessNo(), "businessNo"),
                command.getCorrelationId() == null || command.getCorrelationId().isBlank()
                        ? requireText(command.getStockCountId(), "stockCountId") : command.getCorrelationId().trim(),
                command.getCausationId() == null || command.getCausationId().isBlank()
                        ? requireText(command.getStockCountLineId(), "stockCountLineId") : command.getCausationId().trim(),
                command.getOccurredAt() == null ? Instant.now() : command.getOccurredAt());
    }

    private static String fingerprint(Long tenantId, NormalizedCommand command) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", tenantId);
        payload.put("stockCountId", command.stockCountId());
        payload.put("stockCountLineId", command.stockCountLineId());
        payload.put("ownerType", command.ownerType());
        payload.put("ownerId", command.ownerId());
        payload.put("canonicalSkuId", command.canonicalSkuId());
        payload.put("warehouseId", command.warehouseId());
        payload.put("locationId", command.locationId());
        payload.put("lotId", command.lotId());
        payload.put("stockStatus", command.stockStatus());
        payload.put("qualityStatus", command.qualityStatus());
        payload.put("baseUomCode", command.baseUomCode());
        payload.put("bookOnHandQuantity", decimal(command.bookOnHandQuantity()));
        payload.put("countedOnHandQuantity", decimal(command.countedOnHandQuantity()));
        payload.put("adjustmentQuantity", decimal(command.adjustmentQuantity()));
        payload.put("businessType", command.businessType());
        payload.put("businessId", command.businessId());
        payload.put("businessItemId", command.businessItemId());
        payload.put("businessNo", command.businessNo());
        return DigestUtil.sha256Hex(JsonUtils.toJsonString(payload));
    }

    private static BigDecimal scaled(BigDecimal value) {
        return value.setScale(6, RoundingMode.HALF_UP);
    }

    private static String decimal(BigDecimal value) {
        return scaled(value).toPlainString();
    }

    private static String requireText(String value, String field) {
        require(value != null && !value.isBlank(), field + " is required");
        return value.trim();
    }

    private static String requireUpper(String value, String field) {
        return requireText(value, field).toUpperCase(Locale.ROOT);
    }

    private static <T> T requireNonNull(T value, String message) {
        require(value != null, message);
        return value;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    private record NormalizedCommand(String idempotencyKey,
                                     String sourceEventId,
                                     String stockCountId,
                                     String stockCountLineId,
                                     String adjustmentId,
                                     String ownerType,
                                     String ownerId,
                                     String canonicalSkuId,
                                     String warehouseId,
                                     String locationId,
                                     String lotId,
                                     String stockStatus,
                                     String qualityStatus,
                                     String baseUomCode,
                                     BigDecimal bookOnHandQuantity,
                                     BigDecimal countedOnHandQuantity,
                                     BigDecimal adjustmentQuantity,
                                     String businessType,
                                     String businessId,
                                     String businessItemId,
                                     String businessNo,
                                     String correlationId,
                                     String causationId,
                                     Instant occurredAt) {
    }
}
