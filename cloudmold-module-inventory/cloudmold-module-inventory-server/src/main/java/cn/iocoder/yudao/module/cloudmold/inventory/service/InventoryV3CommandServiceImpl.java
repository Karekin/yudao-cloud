package cn.iocoder.yudao.module.cloudmold.inventory.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.catalog.api.CatalogSkuValidationApi;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.inventory.api.*;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.*;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql.*;
import cn.iocoder.yudao.module.cloudmold.merchant.api.MerchantOwnerValidationApi;
import cn.iocoder.yudao.module.cloudmold.warehouse.api.WarehouseReferenceValidationApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class InventoryV3CommandServiceImpl implements InventoryV3CommandApi {

    static final int OPERATION_SUCCEEDED = 10;
    static final int RESERVATION_ACTIVE = 10;
    static final int RESERVATION_COMMITTED = 20;
    static final int RESERVATION_RELEASED = 30;
    private static final BigDecimal ZERO = new BigDecimal("0.000000");
    private static final Set<String> QUALITY_STATUSES = Set.of("PENDING_QC", "QUALIFIED", "DAMAGED", "REJECTED");
    private static final Set<String> STOCK_STATUSES = Set.of("SELLABLE", "NON_SELLABLE");

    private final InventoryV3OperationMapper operationMapper;
    private final InventoryV3BalanceMapper balanceMapper;
    private final InventoryLotMapper lotMapper;
    private final InventoryV3ReservationMapper reservationMapper;
    private final InventoryV3LedgerTransactionMapper ledgerTransactionMapper;
    private final InventoryV3LedgerEntryMapper ledgerEntryMapper;
    private final OutboxAppender outboxAppender;
    private final CatalogSkuValidationApi catalogSkuValidationApi;
    private final WarehouseReferenceValidationApi warehouseValidationApi;
    private final MerchantOwnerValidationApi merchantOwnerValidationApi;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InventoryV3CommandResult execute(InventoryV3Command rawCommand) {
        NormalizedCommand command = normalize(rawCommand);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String requestHash = fingerprint(tenantId, command);
        String attemptToken = UUID.randomUUID().toString();
        operationMapper.insertOrResolve(tenantId, command.idempotencyKey(), command.sourceEventId(),
                command.operation().name(), requestHash, attemptToken, now);
        Long operationId = operationMapper.selectLastInsertId();
        require(operationId != null && operationId > 0, "failed to resolve inventory v3 operation");
        InventoryV3OperationDO operation = operationMapper.selectForUpdate(operationId, tenantId);
        require(operation != null, "inventory v3 operation disappeared");
        if (!attemptToken.equals(operation.getAttemptToken())) {
            require(Objects.equals(requestHash, operation.getRequestHash()),
                    "idempotency key or source event conflicts with different payload");
            require(operation.getStatus() == OPERATION_SUCCEEDED && operation.getResultJson() != null,
                    "existing inventory v3 operation is not complete");
            InventoryV3CommandResult replay = JsonUtils.parseObject(operation.getResultJson(),
                    InventoryV3CommandResult.class);
            replay.setDuplicate(true);
            return replay;
        }

        merchantOwnerValidationApi.requireActiveMerchant(command.ownerId());
        catalogSkuValidationApi.requireActiveSku(command.canonicalSkuId());
        warehouseValidationApi.requireActiveLocation(command.warehouseId(), command.locationId());
        InventoryLotDO lot = requireLot(tenantId, command);

        LockedAggregate aggregate = lockAggregate(tenantId, command, operationId, now);
        InventoryV3BalanceDO balance = aggregate.balance();
        require(Objects.equals(balance.getBaseUomCode(), command.baseUomCode()),
                "base UOM does not match inventory v3 balance");

        BigDecimal beforeOnHand = scaled(balance.getOnHandQuantity());
        BigDecimal beforeReserved = scaled(balance.getReservedQuantity());
        BigDecimal beforeInTransit = scaled(balance.getInTransitQuantity());
        BigDecimal deltaOnHand = ZERO;
        BigDecimal deltaReserved = ZERO;
        BigDecimal deltaInTransit = ZERO;
        String reservationId = aggregate.reservation() == null ? null : aggregate.reservation().getReservationId();
        String allocationId = aggregate.allocation() == null ? null : aggregate.allocation().getAllocationId();

        switch (command.operation()) {
            case RECEIVE, RETURN -> deltaOnHand = command.quantity();
            case RESERVE -> {
                require(isSellableQualified(balance), "only SELLABLE QUALIFIED inventory can be reserved");
                require(available(balance, beforeOnHand, beforeReserved).compareTo(command.quantity()) >= 0,
                        "insufficient available inventory");
                deltaReserved = command.quantity();
                reservationId = UUID.randomUUID().toString();
                allocationId = UUID.randomUUID().toString();
            }
            case SHIP -> {
                requireActiveReservation(aggregate, command);
                deltaOnHand = command.quantity().negate();
                deltaReserved = command.quantity().negate();
            }
            case RELEASE -> {
                requireActiveReservation(aggregate, command);
                deltaReserved = command.quantity().negate();
            }
        }

        BigDecimal afterOnHand = beforeOnHand.add(deltaOnHand);
        BigDecimal afterReserved = beforeReserved.add(deltaReserved);
        BigDecimal afterInTransit = beforeInTransit.add(deltaInTransit);
        require(afterOnHand.signum() >= 0 && afterReserved.signum() >= 0 && afterInTransit.signum() >= 0,
                "inventory v3 balance cannot become negative");
        require(!isSellableQualified(balance) || afterReserved.compareTo(afterOnHand) <= 0,
                "reserved inventory cannot exceed on-hand inventory");

        long aggregateVersion = balance.getVersion() + 1;
        require(balanceMapper.updateBalanceCas(tenantId, balance.getBalanceId(), balance.getVersion(),
                afterOnHand, afterReserved, afterInTransit, now) == 1, "inventory v3 balance version conflict");

        if (command.operation() == InventoryV3Operation.RESERVE) {
            reservationMapper.insert(new InventoryV3ReservationDO().setReservationId(reservationId).setTenantId(tenantId)
                    .setBusinessType(command.businessType()).setBusinessId(command.businessId())
                    .setBusinessItemId(command.businessItemId()).setQuantity(command.quantity())
                    .setStatus(RESERVATION_ACTIVE).setVersion(1L).setCreatedOperationId(operationId)
                    .setCreatedAt(now).setUpdatedAt(now));
            reservationMapper.insertAllocation(new InventoryV3ReservationAllocationDO().setAllocationId(allocationId)
                    .setTenantId(tenantId).setReservationId(reservationId).setBalanceId(balance.getBalanceId())
                    .setQuantity(command.quantity()).setStatus(RESERVATION_ACTIVE).setVersion(1L)
                    .setCreatedOperationId(operationId).setCreatedAt(now).setUpdatedAt(now));
        } else if (command.operation() == InventoryV3Operation.SHIP
                || command.operation() == InventoryV3Operation.RELEASE) {
            int terminal = command.operation() == InventoryV3Operation.SHIP
                    ? RESERVATION_COMMITTED : RESERVATION_RELEASED;
            require(reservationMapper.closeAllocation(tenantId, allocationId, terminal, operationId, now) == 1,
                    "inventory v3 allocation was already closed");
            require(reservationMapper.closeReservation(tenantId, reservationId, terminal, operationId, now) == 1,
                    "inventory v3 reservation was already closed");
        }

        String movementGroupId = UUID.randomUUID().toString();
        InventoryV3LedgerTransactionDO transaction = new InventoryV3LedgerTransactionDO()
                .setTenantId(tenantId).setOperationId(operationId).setMovementGroupId(movementGroupId)
                .setCommandType(command.operation().name()).setBusinessType(command.businessType())
                .setBusinessId(command.businessId()).setBusinessItemId(command.businessItemId())
                .setBusinessNo(command.businessNo())
                .setOccurredAt(LocalDateTime.ofInstant(command.occurredAt(), ZoneOffset.UTC)).setCreatedAt(now);
        ledgerTransactionMapper.insert(transaction);
        require(transaction.getLedgerTransactionId() != null, "inventory v3 ledger transaction id was not generated");

        ledgerEntryMapper.insert(new InventoryV3LedgerEntryDO().setTenantId(tenantId)
                .setLedgerTransactionId(transaction.getLedgerTransactionId()).setMovementGroupId(movementGroupId)
                .setEntryRole("SINGLE").setBalanceId(balance.getBalanceId()).setAggregateVersion(aggregateVersion)
                .setBaseUomCode(command.baseUomCode())
                .setBeforeOnHandQuantity(beforeOnHand).setDeltaOnHandQuantity(deltaOnHand)
                .setAfterOnHandQuantity(afterOnHand).setBeforeReservedQuantity(beforeReserved)
                .setDeltaReservedQuantity(deltaReserved).setAfterReservedQuantity(afterReserved)
                .setBeforeInTransitQuantity(beforeInTransit).setDeltaInTransitQuantity(deltaInTransit)
                .setAfterInTransitQuantity(afterInTransit).setCreatedAt(now));

        BigDecimal afterAvailable = available(balance, afterOnHand, afterReserved);
        appendEvent(tenantId, command, balance, lot, transaction.getLedgerTransactionId(), movementGroupId,
                reservationId, allocationId, aggregateVersion, deltaOnHand, deltaReserved, deltaInTransit,
                afterOnHand, afterReserved, afterInTransit, afterAvailable);

        InventoryV3CommandResult result = InventoryV3CommandResult.builder().operationId(operationId)
                .ledgerTransactionId(transaction.getLedgerTransactionId()).movementGroupId(movementGroupId)
                .balanceId(balance.getBalanceId()).reservationId(reservationId).allocationId(allocationId)
                .aggregateVersion(aggregateVersion).onHandQuantity(afterOnHand).reservedQuantity(afterReserved)
                .inTransitQuantity(afterInTransit).availableQuantity(afterAvailable).duplicate(false).build();
        require(operationMapper.markSucceeded(operationId, tenantId, transaction.getLedgerTransactionId(),
                JsonUtils.toJsonString(result), now) == 1, "inventory v3 operation completion conflict");
        return result;
    }

    private LockedAggregate lockAggregate(Long tenantId, NormalizedCommand command, Long operationId,
                                           LocalDateTime now) {
        if (command.operation() == InventoryV3Operation.SHIP || command.operation() == InventoryV3Operation.RELEASE) {
            List<InventoryV3ReservationAllocationDO> hints = reservationMapper.selectAllocationHints(tenantId,
                    command.reservationId());
            require(hints != null && hints.size() == 1,
                    "inventory v3 first slice requires exactly one reservation allocation");
            InventoryV3ReservationAllocationDO hint = hints.get(0);
            InventoryV3BalanceDO balance = balanceMapper.selectByIdForUpdate(tenantId, hint.getBalanceId());
            require(balance != null, "inventory v3 reservation balance does not exist");
            InventoryV3ReservationDO reservation = reservationMapper.selectForUpdate(tenantId, command.reservationId());
            InventoryV3ReservationAllocationDO allocation = reservationMapper.selectAllocationForUpdate(tenantId,
                    command.reservationId(), hint.getAllocationId());
            require(reservation != null && allocation != null
                            && Objects.equals(allocation.getBalanceId(), balance.getBalanceId()),
                    "inventory v3 reservation allocation changed while locking");
            requireMatches(balance, command);
            return new LockedAggregate(balance, reservation, allocation);
        }
        if (command.operation() == InventoryV3Operation.RECEIVE || command.operation() == InventoryV3Operation.RETURN) {
            balanceMapper.insertOrResolve(UUID.randomUUID().toString(), tenantId, command.ownerType(), command.ownerId(),
                    command.canonicalSkuId(), command.warehouseId(), command.locationId(), command.lotId(),
                    command.stockStatus(), command.qualityStatus(), command.baseUomCode(), now);
        }
        InventoryV3BalanceDO balance = balanceMapper.selectDimensionForUpdate(tenantId, command.ownerType(),
                command.ownerId(), command.canonicalSkuId(), command.warehouseId(), command.locationId(),
                command.lotId(), command.stockStatus(), command.qualityStatus());
        require(balance != null, "inventory v3 balance does not exist");
        return new LockedAggregate(balance, null, null);
    }

    private InventoryLotDO requireLot(Long tenantId, NormalizedCommand command) {
        if (command.lotId() == null) return null;
        InventoryLotDO lot = lotMapper.selectCurrent(tenantId, command.lotId());
        require(lot != null, "inventory lot does not exist");
        require(Objects.equals(lot.getOwnerType(), command.ownerType())
                        && Objects.equals(lot.getOwnerId(), command.ownerId())
                        && Objects.equals(lot.getCanonicalSkuId(), command.canonicalSkuId()),
                "inventory lot does not belong to requested owner and SKU");
        if (command.operation() != InventoryV3Operation.RELEASE) {
            require("ACTIVE".equals(lot.getStatus()), "inventory lot is not ACTIVE");
        }
        if (command.operation() == InventoryV3Operation.RESERVE || command.operation() == InventoryV3Operation.SHIP) {
            LocalDate effectiveDate = command.occurredAt().atZone(ZoneOffset.UTC).toLocalDate();
            require(lot.getExpiresOn() == null || !lot.getExpiresOn().isBefore(effectiveDate),
                    "expired inventory lot cannot be reserved or shipped");
        }
        return lot;
    }

    private void appendEvent(Long tenantId, NormalizedCommand command, InventoryV3BalanceDO balance,
                             InventoryLotDO lot, Long ledgerTransactionId, String movementGroupId,
                             String reservationId, String allocationId, long version,
                             BigDecimal deltaOnHand, BigDecimal deltaReserved, BigDecimal deltaInTransit,
                             BigDecimal afterOnHand, BigDecimal afterReserved, BigDecimal afterInTransit,
                             BigDecimal afterAvailable) {
        Map<String, Object> payload = new LinkedHashMap<>();
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
        payload.put("uom_code", command.baseUomCode());
        payload.put("delta_quantity", decimal(deltaOnHand));
        payload.put("delta_on_hand_quantity", decimal(deltaOnHand));
        payload.put("delta_reserved_quantity", decimal(deltaReserved));
        payload.put("delta_in_transit_quantity", decimal(deltaInTransit));
        payload.put("after_on_hand_quantity", decimal(afterOnHand));
        payload.put("after_reserved_quantity", decimal(afterReserved));
        payload.put("after_in_transit_quantity", decimal(afterInTransit));
        payload.put("after_available_quantity", decimal(afterAvailable));
        payload.put("movement_type", movementType(command.operation()));
        payload.put("ledger_transaction_id", ledgerTransactionId);
        payload.put("movement_group_id", movementGroupId);
        payload.put("entry_role", "SINGLE");
        payload.put("counterparty_balance_id", null);
        payload.put("business_type", command.businessType());
        payload.put("business_id", command.businessId());
        payload.put("business_item_id", command.businessItemId());
        payload.put("business_no", command.businessNo());
        payload.put("reservation_id", reservationId);
        payload.put("allocation_id", allocationId);
        if (command.costEvidence() != null) {
            payload.put("unit_cost_amount_minor", command.costEvidence().unitCostAmountMinor());
            payload.put("movement_cost_amount_minor", command.costEvidence().movementCostAmountMinor());
            payload.put("currency_code", command.costEvidence().currencyCode());
            payload.put("cost_source_system", command.costEvidence().costSourceSystem());
            payload.put("cost_source_ref", command.costEvidence().costSourceRef());
            payload.put("cost_policy_version", command.costEvidence().costPolicyVersion());
        }
        int schemaVersion = command.costEvidence() == null ? 3 : 5;
        outboxAppender.append(AppendDomainEventCommand.builder().eventType("inventory.stock.changed")
                .schemaVersion(schemaVersion).sourceSystem("cloudmold-inventory").tenantId(tenantId)
                .aggregateType("inventory_balance_v3").aggregateId(balance.getBalanceId())
                .aggregateVersion(version).eventSequence((short) 1).occurredAt(command.occurredAt())
                .correlationId(command.correlationId()).causationId(command.causationId())
                .idempotencyKey(command.idempotencyKey() + ":event:v" + schemaVersion).payload(payload)
                .headers(Map.of("ledger_transaction_id", ledgerTransactionId,
                        "movement_group_id", movementGroupId)).destination("lakehouse").build());
    }

    static String fingerprint(Long tenantId, NormalizedCommand command) {
        return DigestUtil.sha256Hex(String.join("\u001f", tenantId.toString(), command.operation().name(),
                command.ownerType(), command.ownerId(), command.canonicalSkuId(), command.warehouseId(),
                command.locationId(), Objects.toString(command.lotId(), ""), command.stockStatus(),
                command.qualityStatus(), command.baseUomCode(), decimal(command.quantity()),
                command.costEvidence() == null ? "" : command.costEvidence().fingerprintPart(),
                Objects.toString(command.reservationId(), ""), command.businessType(), command.businessId(),
                command.businessItemId(), command.businessNo(), Objects.toString(command.sourceEventId(), ""),
                command.occurredAt().toString()));
    }

    private static NormalizedCommand normalize(InventoryV3Command command) {
        require(command != null && command.getOperation() != null, "inventory v3 operation is required");
        String ownerType = upper(command.getOwnerType());
        require("MERCHANT".equals(ownerType), "inventory v3 first slice supports MERCHANT owner only");
        String ownerId = requireUuid(command.getOwnerId(), "ownerId");
        String skuId = requireUuid(command.getCanonicalSkuId(), "canonicalSkuId");
        String warehouseId = requireUuid(command.getWarehouseId(), "warehouseId");
        String locationId = requireUuid(command.getLocationId(), "locationId");
        String lotId = command.getLotId() == null ? null : requireUuid(command.getLotId(), "lotId");
        String stockStatus = upper(command.getStockStatus());
        String qualityStatus = upper(command.getQualityStatus());
        require(STOCK_STATUSES.contains(stockStatus), "unsupported stockStatus");
        require(QUALITY_STATUSES.contains(qualityStatus), "unsupported qualityStatus");
        require(!"SELLABLE".equals(stockStatus) || "QUALIFIED".equals(qualityStatus),
                "SELLABLE inventory must be QUALIFIED");
        String uom = upper(command.getBaseUomCode());
        requireText(uom, "baseUomCode", 32);
        requireText(command.getIdempotencyKey(), "idempotencyKey", 128);
        requireText(command.getBusinessType(), "businessType", 32);
        requireText(command.getBusinessId(), "businessId", 128);
        requireText(command.getBusinessItemId(), "businessItemId", 128);
        requireText(command.getBusinessNo(), "businessNo", 128);
        require(command.getOccurredAt() != null, "occurredAt is required");
        String correlationId = requireUuid(command.getCorrelationId(), "correlationId");
        String causationId = command.getCausationId() == null ? null
                : requireUuid(command.getCausationId(), "causationId");
        BigDecimal quantity = scaled(command.getQuantity());
        require(quantity.signum() > 0 && quantity.precision() - quantity.scale() <= 18,
                "quantity must be positive with at most 18 integer and 6 fractional digits");
        CostEvidence costEvidence = normalizeCostEvidence(command, quantity);
        boolean closes = command.getOperation() == InventoryV3Operation.SHIP
                || command.getOperation() == InventoryV3Operation.RELEASE;
        String reservationId = command.getReservationId();
        if (closes) reservationId = requireUuid(reservationId, "reservationId");
        require(closes || reservationId == null, "reservationId is only accepted for SHIP and RELEASE");
        return new NormalizedCommand(command.getOperation(), command.getIdempotencyKey(), command.getSourceEventId(),
                ownerType, ownerId, skuId, warehouseId, locationId, lotId, stockStatus, qualityStatus, uom,
                quantity, reservationId, command.getBusinessType(), command.getBusinessId(),
                command.getBusinessItemId(), command.getBusinessNo(), correlationId, causationId,
                command.getOccurredAt(), costEvidence);
    }

    private static CostEvidence normalizeCostEvidence(InventoryV3Command command, BigDecimal quantity) {
        boolean any = command.getUnitCostAmountMinor() != null || command.getMovementCostAmountMinor() != null
                || command.getCurrencyCode() != null || command.getCostSourceSystem() != null
                || command.getCostSourceRef() != null || command.getCostPolicyVersion() != null;
        if (!any) return null;
        require(command.getOperation() == InventoryV3Operation.RECEIVE
                        || command.getOperation() == InventoryV3Operation.RETURN
                        || command.getOperation() == InventoryV3Operation.SHIP,
                "cost evidence is accepted only for RECEIVE, RETURN, and SHIP");
        require(command.getUnitCostAmountMinor() != null && command.getUnitCostAmountMinor() >= 0,
                "unitCostAmountMinor is required and cannot be negative");
        require(command.getMovementCostAmountMinor() != null && command.getMovementCostAmountMinor() >= 0,
                "movementCostAmountMinor is required and cannot be negative");
        String currencyCode = upper(command.getCurrencyCode());
        require(currencyCode != null && currencyCode.matches("[A-Z]{3}"),
                "currencyCode must be a three-letter ISO currency code");
        requireText(command.getCostSourceSystem(), "costSourceSystem", 64);
        requireText(command.getCostSourceRef(), "costSourceRef", 128);
        requireText(command.getCostPolicyVersion(), "costPolicyVersion", 64);
        BigDecimal expected = quantity.multiply(BigDecimal.valueOf(command.getUnitCostAmountMinor()));
        require(expected.scale() <= 6 && expected.stripTrailingZeros().scale() <= 0,
                "quantity multiplied by unit cost must resolve to whole minor currency units");
        require(expected.longValueExact() == command.getMovementCostAmountMinor(),
                "movementCostAmountMinor must equal quantity multiplied by unitCostAmountMinor");
        return new CostEvidence(command.getUnitCostAmountMinor(), command.getMovementCostAmountMinor(), currencyCode,
                command.getCostSourceSystem().trim(), command.getCostSourceRef().trim(),
                command.getCostPolicyVersion().trim());
    }

    private static void requireMatches(InventoryV3BalanceDO balance, NormalizedCommand command) {
        require(Objects.equals(balance.getOwnerType(), command.ownerType())
                        && Objects.equals(balance.getOwnerId(), command.ownerId())
                        && Objects.equals(balance.getCanonicalSkuId(), command.canonicalSkuId())
                        && Objects.equals(balance.getWarehouseId(), command.warehouseId())
                        && Objects.equals(balance.getLocationId(), command.locationId())
                        && Objects.equals(balance.getLotId(), command.lotId())
                        && Objects.equals(balance.getStockStatus(), command.stockStatus())
                        && Objects.equals(balance.getQualityStatus(), command.qualityStatus()),
                "reservation does not belong to requested inventory v3 dimension");
    }

    private static void requireActiveReservation(LockedAggregate aggregate, NormalizedCommand command) {
        require(aggregate.reservation() != null && aggregate.reservation().getStatus() == RESERVATION_ACTIVE,
                "inventory v3 reservation is not active");
        require(aggregate.allocation() != null && aggregate.allocation().getStatus() == RESERVATION_ACTIVE,
                "inventory v3 allocation is not active");
        require(scaled(aggregate.reservation().getQuantity()).compareTo(command.quantity()) == 0
                        && scaled(aggregate.allocation().getQuantity()).compareTo(command.quantity()) == 0,
                "inventory v3 first slice only supports full single-allocation close");
    }

    private static boolean isSellableQualified(InventoryV3BalanceDO balance) {
        return "SELLABLE".equals(balance.getStockStatus()) && "QUALIFIED".equals(balance.getQualityStatus());
    }

    private static BigDecimal available(InventoryV3BalanceDO balance, BigDecimal onHand, BigDecimal reserved) {
        return isSellableQualified(balance) ? onHand.subtract(reserved) : ZERO;
    }

    private static String movementType(InventoryV3Operation operation) {
        return switch (operation) {
            case RECEIVE -> "PURCHASE_RECEIPT";
            case RESERVE -> "RESERVATION";
            case SHIP -> "SALE_SHIPMENT";
            case RETURN -> "SALE_RETURN";
            case RELEASE -> "RESERVATION_RELEASE";
        };
    }

    private static String upper(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String requireUuid(String value, String field) {
        requireText(value, field, 36);
        try {
            return UUID.fromString(value).toString();
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException(field + " must be a UUID", error);
        }
    }

    private static BigDecimal scaled(BigDecimal value) {
        require(value != null, "quantity is required");
        try {
            return value.setScale(6, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException error) {
            throw new IllegalArgumentException("quantity supports at most 6 fractional digits", error);
        }
    }

    private static String decimal(BigDecimal value) {
        return scaled(value).toPlainString();
    }

    private static void requireText(String value, String field, int maxLength) {
        require(value != null && !value.isBlank() && value.length() <= maxLength,
                field + " is required and too long");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    record NormalizedCommand(InventoryV3Operation operation, String idempotencyKey, String sourceEventId,
                             String ownerType, String ownerId, String canonicalSkuId, String warehouseId,
                             String locationId, String lotId, String stockStatus, String qualityStatus,
                             String baseUomCode, BigDecimal quantity, String reservationId, String businessType,
                             String businessId, String businessItemId, String businessNo, String correlationId,
                             String causationId, Instant occurredAt, CostEvidence costEvidence) {
    }

    private record CostEvidence(Long unitCostAmountMinor, Long movementCostAmountMinor, String currencyCode,
                                String costSourceSystem, String costSourceRef, String costPolicyVersion) {
        String fingerprintPart() {
            return String.join("|", unitCostAmountMinor.toString(), movementCostAmountMinor.toString(), currencyCode,
                    costSourceSystem, costSourceRef, costPolicyVersion);
        }
    }

    private record LockedAggregate(InventoryV3BalanceDO balance, InventoryV3ReservationDO reservation,
                                   InventoryV3ReservationAllocationDO allocation) {
    }
}
