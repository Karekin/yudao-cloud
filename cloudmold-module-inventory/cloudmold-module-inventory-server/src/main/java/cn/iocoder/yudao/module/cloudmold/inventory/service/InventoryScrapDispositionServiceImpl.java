package cn.iocoder.yudao.module.cloudmold.inventory.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.catalog.api.CatalogSkuValidationApi;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryScrapDispositionApi;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryScrapDispositionCommand;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryScrapDispositionResult;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.InventoryLotDO;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.InventoryScrapDispositionOperationDO;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.InventoryV3BalanceDO;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.InventoryV3LedgerEntryDO;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.InventoryV3LedgerTransactionDO;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql.InventoryLotMapper;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql.InventoryScrapDispositionOperationMapper;
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
public class InventoryScrapDispositionServiceImpl implements InventoryScrapDispositionApi {

    private static final int OPERATION_SUCCEEDED = 10;
    private static final BigDecimal ZERO = new BigDecimal("0.000000");
    private static final Set<String> STOCK_STATUSES = Set.of("SELLABLE", "NON_SELLABLE");
    private static final Set<String> QUALITY_STATUSES = Set.of("PENDING_QC", "QUALIFIED", "DAMAGED", "REJECTED");

    private final InventoryScrapDispositionOperationMapper operationMapper;
    private final InventoryV3BalanceMapper balanceMapper;
    private final InventoryLotMapper lotMapper;
    private final InventoryV3LedgerTransactionMapper ledgerTransactionMapper;
    private final InventoryV3LedgerEntryMapper ledgerEntryMapper;
    private final OutboxAppender outboxAppender;
    private final CatalogSkuValidationApi catalogSkuValidationApi;
    private final WarehouseReferenceValidationApi warehouseReferenceValidationApi;
    private final MerchantOwnerValidationApi merchantOwnerValidationApi;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InventoryScrapDispositionResult execute(InventoryScrapDispositionCommand rawCommand) {
        NormalizedCommand command = normalize(rawCommand);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String requestHash = fingerprint(tenantId, command);
        String attemptToken = UUID.randomUUID().toString();
        operationMapper.insertOrResolve(tenantId, command.idempotencyKey(), command.sourceEventId(),
                command.dispositionLineId(), command.scrapDocumentId(), command.scrapLineId(),
                command.dispositionBatchId(), requestHash, attemptToken, now);
        Long operationId = operationMapper.selectLastInsertId();
        require(operationId != null && operationId > 0, "failed to resolve inventory scrap operation");
        InventoryScrapDispositionOperationDO operation = operationMapper.selectForUpdate(tenantId, operationId);
        require(operation != null, "inventory scrap operation disappeared");
        if (!attemptToken.equals(operation.getAttemptToken())) {
            require(Objects.equals(requestHash, operation.getRequestHash()),
                    "idempotency key or source event conflicts with different inventory scrap payload");
            require(operation.getStatus() == OPERATION_SUCCEEDED && operation.getLedgerTransactionId() != null,
                    "existing inventory scrap operation is not complete");
            return InventoryScrapDispositionResult.builder()
                    .operationId(operation.getOperationId())
                    .ledgerTransactionId(operation.getLedgerTransactionId())
                    .balanceId(operation.getBalanceId())
                    .aggregateVersion(operation.getAggregateVersion())
                    .onHandQuantity(operation.getOnHandQuantity())
                    .disposedQuantity(operation.getDisposedQuantity())
                    .duplicate(true)
                    .build();
        }

        validateOwner(command.ownerType(), command.ownerId());
        catalogSkuValidationApi.requireActiveSku(command.canonicalSkuId());
        warehouseReferenceValidationApi.requireActiveLocation(command.warehouseId(), command.locationId());
        requireLot(tenantId, command);

        InventoryV3BalanceDO balance = balanceMapper.selectDimensionForUpdate(tenantId, command.ownerType(),
                command.ownerId(), command.canonicalSkuId(), command.warehouseId(), command.locationId(),
                command.lotId(), command.stockStatus(), command.qualityStatus());
        require(balance != null, "inventory balance does not exist for scrap disposition");
        require(Objects.equals(balance.getBaseUomCode(), command.baseUomCode()),
                "base UOM does not match inventory balance");

        BigDecimal beforeOnHand = scaled(balance.getOnHandQuantity());
        BigDecimal beforeReserved = scaled(balance.getReservedQuantity());
        BigDecimal beforeInTransit = scaled(balance.getInTransitQuantity());
        require(beforeOnHand.subtract(beforeReserved).compareTo(command.quantity()) >= 0,
                "insufficient unreserved inventory for scrap disposition");
        BigDecimal afterOnHand = beforeOnHand.subtract(command.quantity());
        require(balanceMapper.updateBalanceCas(tenantId, balance.getBalanceId(), balance.getVersion(),
                afterOnHand, beforeReserved, beforeInTransit, now) == 1,
                "inventory scrap balance version conflict");

        InventoryV3LedgerTransactionDO transaction = new InventoryV3LedgerTransactionDO()
                .setTenantId(tenantId)
                .setOperationId(operationId)
                .setMovementGroupId(command.dispositionLineId())
                .setCommandType("SCRAP_DISPOSITION")
                .setBusinessType("INVENTORY_SCRAP")
                .setBusinessId(command.scrapDocumentId())
                .setBusinessItemId(command.scrapLineId())
                .setBusinessNo(command.businessNo())
                .setOccurredAt(command.occurredAt())
                .setCreatedAt(now);
        ledgerTransactionMapper.insert(transaction);
        require(transaction.getLedgerTransactionId() != null,
                "inventory scrap ledger transaction id was not generated");

        long aggregateVersion = balance.getVersion() + 1;
        ledgerEntryMapper.insert(new InventoryV3LedgerEntryDO()
                .setTenantId(tenantId)
                .setLedgerTransactionId(transaction.getLedgerTransactionId())
                .setMovementGroupId(command.dispositionLineId())
                .setEntryRole("SINGLE")
                .setCounterpartyBalanceId(null)
                .setBalanceId(balance.getBalanceId())
                .setAggregateVersion(aggregateVersion)
                .setBaseUomCode(command.baseUomCode())
                .setBeforeOnHandQuantity(beforeOnHand)
                .setDeltaOnHandQuantity(command.quantity().negate())
                .setAfterOnHandQuantity(afterOnHand)
                .setBeforeReservedQuantity(beforeReserved)
                .setDeltaReservedQuantity(ZERO)
                .setAfterReservedQuantity(beforeReserved)
                .setBeforeInTransitQuantity(beforeInTransit)
                .setDeltaInTransitQuantity(ZERO)
                .setAfterInTransitQuantity(beforeInTransit)
                .setCreatedAt(now));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("scrap_document_id", command.scrapDocumentId());
        payload.put("scrap_line_id", command.scrapLineId());
        payload.put("disposition_batch_id", command.dispositionBatchId());
        payload.put("disposition_line_id", command.dispositionLineId());
        payload.put("canonical_sku_id", command.canonicalSkuId());
        payload.put("warehouse_id", command.warehouseId());
        payload.put("location_id", command.locationId());
        payload.put("lot_id", command.lotId());
        payload.put("stock_status", command.stockStatus());
        payload.put("quality_status", command.qualityStatus());
        payload.put("disposed_quantity", command.quantity());
        payload.put("balance_id", balance.getBalanceId());
        payload.put("aggregate_version", aggregateVersion);
        payload.put("ledger_transaction_id", transaction.getLedgerTransactionId());
        payload.put("occurred_at", command.occurredAt().toInstant(ZoneOffset.UTC).toString());
        outboxAppender.append(AppendDomainEventCommand.builder()
                .eventType("inventory.scrap.disposed")
                .schemaVersion(1)
                .sourceSystem("cloudmold-inventory")
                .tenantId(tenantId)
                .aggregateType("inventory_balance_v3")
                .aggregateId(balance.getBalanceId())
                .aggregateVersion(aggregateVersion)
                .eventSequence((short) 1)
                .occurredAt(command.occurredAt().toInstant(ZoneOffset.UTC))
                .correlationId(command.scrapDocumentId())
                .causationId(command.scrapLineId())
                .idempotencyKey(command.idempotencyKey() + ":event")
                .payload(payload)
                .headers(Map.of(
                        "ledger_transaction_id", transaction.getLedgerTransactionId(),
                        "disposition_batch_id", command.dispositionBatchId(),
                        "disposition_line_id", command.dispositionLineId()))
                .destination("lakehouse")
                .build());

        InventoryScrapDispositionResult result = InventoryScrapDispositionResult.builder()
                .operationId(operationId)
                .ledgerTransactionId(transaction.getLedgerTransactionId())
                .balanceId(balance.getBalanceId())
                .aggregateVersion(aggregateVersion)
                .onHandQuantity(afterOnHand)
                .disposedQuantity(command.quantity())
                .duplicate(false)
                .build();
        require(operationMapper.markSucceeded(tenantId, operationId, result, now) == 1,
                "inventory scrap operation completion conflict");
        return result;
    }

    private void validateOwner(String ownerType, String ownerId) {
        if ("MERCHANT".equals(ownerType)) {
            merchantOwnerValidationApi.requireActiveMerchant(ownerId);
        }
    }

    private void requireLot(Long tenantId, NormalizedCommand command) {
        if (command.lotId() == null) {
            return;
        }
        InventoryLotDO lot = lotMapper.selectCurrent(tenantId, command.lotId());
        require(lot != null, "inventory lot does not exist");
        require(Objects.equals(lot.getOwnerType(), command.ownerType())
                        && Objects.equals(lot.getOwnerId(), command.ownerId())
                        && Objects.equals(lot.getCanonicalSkuId(), command.canonicalSkuId()),
                "inventory lot does not belong to scrap owner and SKU");
        require(!"CLOSED".equals(lot.getStatus()), "closed inventory lot cannot be scrapped");
    }

    private static NormalizedCommand normalize(InventoryScrapDispositionCommand command) {
        require(command != null, "inventory scrap command is required");
        return new NormalizedCommand(
                requireText(command.getIdempotencyKey(), "idempotencyKey", 128),
                normalizeText(command.getSourceEventId(), 128),
                requireText(command.getDispositionLineId(), "dispositionLineId", 128),
                requireText(command.getScrapDocumentId(), "scrapDocumentId", 128),
                requireText(command.getScrapLineId(), "scrapLineId", 128),
                requireText(command.getDispositionBatchId(), "dispositionBatchId", 128),
                normalizeUpper(command.getOwnerType(), "ownerType"),
                requireText(command.getOwnerId(), "ownerId", 128),
                requireText(command.getCanonicalSkuId(), "canonicalSkuId", 128),
                requireText(command.getWarehouseId(), "warehouseId", 128),
                requireText(command.getLocationId(), "locationId", 128),
                normalizeText(command.getLotId(), 128),
                validateStatus(command.getStockStatus(), STOCK_STATUSES, "stockStatus"),
                validateStatus(command.getQualityStatus(), QUALITY_STATUSES, "qualityStatus"),
                normalizeUpper(command.getBaseUomCode(), "baseUomCode"),
                scaled(requireNonNull(command.getQuantity(), "quantity is required")),
                requireText(command.getBusinessNo(), "businessNo", 128),
                requireNonNull(command.getOccurredAt(), "occurredAt is required").atOffset(ZoneOffset.UTC)
                        .toLocalDateTime());
    }

    private static String fingerprint(Long tenantId, NormalizedCommand command) {
        return DigestUtil.sha256Hex(String.join("\u001f",
                tenantId.toString(), command.idempotencyKey(), nullToEmpty(command.sourceEventId()),
                command.dispositionLineId(), command.scrapDocumentId(), command.scrapLineId(),
                command.dispositionBatchId(), command.ownerType(), command.ownerId(), command.canonicalSkuId(),
                command.warehouseId(), command.locationId(), nullToEmpty(command.lotId()),
                command.stockStatus(), command.qualityStatus(), command.baseUomCode(),
                command.quantity().toPlainString(), command.businessNo(), command.occurredAt().toString()));
    }

    private static String requireText(String value, String field, int maxLength) {
        String normalized = normalizeText(value, maxLength);
        require(normalized != null, field + " is required");
        return normalized;
    }

    private static String normalizeText(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        require(normalized.length() <= maxLength, "field exceeds max length: " + maxLength);
        return normalized;
    }

    private static String normalizeUpper(String value, String field) {
        String normalized = normalizeText(value, 64);
        require(normalized != null, field + " is required");
        return normalized.toUpperCase(Locale.ROOT);
    }

    private static String validateStatus(String value, Set<String> allowed, String field) {
        String normalized = normalizeUpper(value, field);
        require(allowed.contains(normalized), field + " is not allowed");
        return normalized;
    }

    private static BigDecimal scaled(BigDecimal value) {
        try {
            return value.setScale(6, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("quantity must use scale 6", ex);
        }
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

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record NormalizedCommand(
            String idempotencyKey,
            String sourceEventId,
            String dispositionLineId,
            String scrapDocumentId,
            String scrapLineId,
            String dispositionBatchId,
            String ownerType,
            String ownerId,
            String canonicalSkuId,
            String warehouseId,
            String locationId,
            String lotId,
            String stockStatus,
            String qualityStatus,
            String baseUomCode,
            BigDecimal quantity,
            String businessNo,
            LocalDateTime occurredAt) {
    }
}
