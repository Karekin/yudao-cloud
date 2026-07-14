package cn.iocoder.yudao.module.cloudmold.inventory.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.catalog.api.CatalogSkuValidationApi;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryCommand;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryCommandApi;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryCommandResult;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryOperation;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.*;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InventoryCommandServiceImpl implements InventoryCommandApi {

    static final int OPERATION_PROCESSING = 0;
    static final int OPERATION_SUCCEEDED = 10;
    static final int RESERVATION_ACTIVE = 10;
    static final int RESERVATION_COMMITTED = 20;
    static final int RESERVATION_RELEASED = 30;
    private static final BigDecimal ZERO = new BigDecimal("0.000000");

    private final InventoryOperationMapper operationMapper;
    private final InventoryBalanceMapper balanceMapper;
    private final InventoryReservationMapper reservationMapper;
    private final InventoryLedgerTransactionMapper ledgerTransactionMapper;
    private final InventoryLedgerEntryMapper ledgerEntryMapper;
    private final OutboxAppender outboxAppender;
    private final CatalogSkuValidationApi catalogSkuValidationApi;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InventoryCommandResult execute(InventoryCommand rawCommand) {
        NormalizedCommand command = normalize(rawCommand);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        catalogSkuValidationApi.requireActiveSku(command.canonicalSkuId());
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String requestHash = fingerprint(tenantId, command);
        String attemptToken = UUID.randomUUID().toString();

        operationMapper.insertOrResolve(tenantId, command.idempotencyKey(), command.sourceEventId(),
                command.operation().name(), requestHash, attemptToken, now);
        Long operationId = operationMapper.selectLastInsertId();
        require(operationId != null && operationId > 0, "failed to resolve inventory operation");
        InventoryOperationDO operation = operationMapper.selectForUpdate(operationId, tenantId);
        require(operation != null, "inventory operation disappeared");
        if (!attemptToken.equals(operation.getAttemptToken())) {
            require(Objects.equals(operation.getRequestHash(), requestHash),
                    "idempotency key or source event conflicts with different payload");
            require(operation.getStatus() == OPERATION_SUCCEEDED && operation.getResultJson() != null,
                    "existing inventory operation is not complete");
            InventoryCommandResult replay = JsonUtils.parseObject(operation.getResultJson(), InventoryCommandResult.class);
            replay.setDuplicate(true);
            return replay;
        }

        LockedAggregate aggregate = lockAggregate(tenantId, command, operationId, now);
        InventoryBalanceDO balance = aggregate.balance();
        require(balance.getBaseUomCode().equals(command.uomCode()), "base UOM does not match inventory balance");

        BigDecimal beforeOnHand = scaled(balance.getOnHandQuantity());
        BigDecimal beforeReserved = scaled(balance.getReservedQuantity());
        BigDecimal afterOnHand = beforeOnHand;
        BigDecimal afterReserved = beforeReserved;
        BigDecimal deltaOnHand = ZERO;
        BigDecimal deltaReserved = ZERO;
        String reservationId = aggregate.reservation() != null ? aggregate.reservation().getReservationId() : null;

        switch (command.operation()) {
            case RECEIVE, RETURN -> {
                deltaOnHand = command.quantity();
                afterOnHand = beforeOnHand.add(command.quantity());
            }
            case RESERVE -> {
                require(available(balance.getStockStatus(), beforeOnHand, beforeReserved)
                        .compareTo(command.quantity()) >= 0, "insufficient available inventory");
                deltaReserved = command.quantity();
                afterReserved = beforeReserved.add(command.quantity());
                reservationId = UUID.randomUUID().toString();
            }
            case SHIP -> {
                requireActiveReservation(aggregate.reservation(), command);
                deltaOnHand = command.quantity().negate();
                deltaReserved = command.quantity().negate();
                afterOnHand = beforeOnHand.subtract(command.quantity());
                afterReserved = beforeReserved.subtract(command.quantity());
            }
            case RELEASE -> {
                requireActiveReservation(aggregate.reservation(), command);
                deltaReserved = command.quantity().negate();
                afterReserved = beforeReserved.subtract(command.quantity());
            }
        }
        require(afterOnHand.signum() >= 0 && afterReserved.signum() >= 0,
                "inventory balance cannot become negative");
        require(!"SELLABLE".equals(balance.getStockStatus()) || afterReserved.compareTo(afterOnHand) <= 0,
                "reserved inventory cannot exceed on-hand inventory");

        long aggregateVersion = balance.getVersion() + 1;
        require(balanceMapper.updateBalanceCas(tenantId, balance.getBalanceId(), balance.getVersion(),
                afterOnHand, afterReserved, now) == 1, "inventory balance version conflict");

        if (command.operation() == InventoryOperation.RESERVE) {
            InventoryReservationDO reservation = new InventoryReservationDO()
                    .setReservationId(reservationId).setTenantId(tenantId).setBalanceId(balance.getBalanceId())
                    .setBusinessType(command.businessType()).setBusinessId(command.businessId())
                    .setBusinessItemId(command.businessItemId()).setQuantity(command.quantity())
                    .setStatus(RESERVATION_ACTIVE).setVersion(1L).setCreatedOperationId(operationId)
                    .setCreatedAt(now).setUpdatedAt(now);
            reservationMapper.insert(reservation);
        } else if (command.operation() == InventoryOperation.SHIP || command.operation() == InventoryOperation.RELEASE) {
            int terminalStatus = command.operation() == InventoryOperation.SHIP
                    ? RESERVATION_COMMITTED : RESERVATION_RELEASED;
            require(reservationMapper.close(tenantId, reservationId, terminalStatus, operationId, now) == 1,
                    "reservation was already closed");
        }

        InventoryLedgerTransactionDO ledgerTransaction = new InventoryLedgerTransactionDO()
                .setTenantId(tenantId).setOperationId(operationId).setCommandType(command.operation().name())
                .setBusinessType(command.businessType()).setBusinessId(command.businessId())
                .setBusinessItemId(command.businessItemId()).setBusinessNo(command.businessNo())
                .setOccurredAt(LocalDateTime.ofInstant(command.occurredAt(), ZoneOffset.UTC)).setCreatedAt(now);
        ledgerTransactionMapper.insert(ledgerTransaction);
        require(ledgerTransaction.getLedgerTransactionId() != null, "ledger transaction id was not generated");

        InventoryLedgerEntryDO entry = new InventoryLedgerEntryDO()
                .setTenantId(tenantId).setLedgerTransactionId(ledgerTransaction.getLedgerTransactionId())
                .setBalanceId(balance.getBalanceId()).setAggregateVersion(aggregateVersion)
                .setBaseUomCode(command.uomCode())
                .setBeforeOnHandQuantity(beforeOnHand).setDeltaOnHandQuantity(deltaOnHand)
                .setAfterOnHandQuantity(afterOnHand)
                .setBeforeReservedQuantity(beforeReserved).setDeltaReservedQuantity(deltaReserved)
                .setAfterReservedQuantity(afterReserved).setCreatedAt(now);
        ledgerEntryMapper.insert(entry);

        BigDecimal afterAvailable = available(balance.getStockStatus(), afterOnHand, afterReserved);
        appendEvent(tenantId, command, balance, ledgerTransaction.getLedgerTransactionId(), reservationId,
                aggregateVersion, deltaOnHand, afterOnHand, afterReserved, afterAvailable);

        InventoryCommandResult result = InventoryCommandResult.builder()
                .operationId(operationId).ledgerTransactionId(ledgerTransaction.getLedgerTransactionId())
                .balanceId(balance.getBalanceId()).reservationId(reservationId).aggregateVersion(aggregateVersion)
                .onHandQuantity(afterOnHand).reservedQuantity(afterReserved).availableQuantity(afterAvailable)
                .duplicate(false).build();
        require(operationMapper.markSucceeded(operationId, tenantId, ledgerTransaction.getLedgerTransactionId(),
                JsonUtils.toJsonString(result), now) == 1, "inventory operation completion conflict");
        return result;
    }

    private LockedAggregate lockAggregate(Long tenantId, NormalizedCommand command,
                                           Long operationId, LocalDateTime now) {
        if (command.operation() == InventoryOperation.SHIP || command.operation() == InventoryOperation.RELEASE) {
            InventoryReservationDO hint = reservationMapper.selectHint(tenantId, command.reservationId());
            require(hint != null, "reservation does not exist");
            InventoryBalanceDO balance = balanceMapper.selectByIdForUpdate(tenantId, hint.getBalanceId());
            require(balance != null, "reservation balance does not exist");
            InventoryReservationDO reservation = reservationMapper.selectForUpdate(tenantId, command.reservationId());
            require(reservation != null && reservation.getBalanceId().equals(balance.getBalanceId()),
                    "reservation changed while locking");
            requireMatches(balance, command);
            return new LockedAggregate(balance, reservation);
        }
        if (command.operation() == InventoryOperation.RECEIVE) {
            balanceMapper.insertOrResolve(UUID.randomUUID().toString(), tenantId, command.ownerId(),
                    command.canonicalSkuId(), command.warehouseId(), command.stockStatus(),
                    command.qualityStatus(), command.uomCode(), now);
        }
        InventoryBalanceDO balance = balanceMapper.selectDimensionForUpdate(tenantId, command.ownerId(),
                command.canonicalSkuId(), command.warehouseId(), command.stockStatus(), command.qualityStatus());
        require(balance != null, "inventory balance does not exist");
        return new LockedAggregate(balance, null);
    }

    private void appendEvent(Long tenantId, NormalizedCommand command, InventoryBalanceDO balance,
                             Long ledgerTransactionId, String reservationId, long version,
                             BigDecimal deltaOnHand, BigDecimal afterOnHand,
                             BigDecimal afterReserved, BigDecimal afterAvailable) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("canonical_sku_id", command.canonicalSkuId());
        payload.put("warehouse_id", command.warehouseId());
        payload.put("owner_id", command.ownerId());
        payload.put("location_id", null);
        payload.put("lot_no", null);
        payload.put("quality_status", command.qualityStatus());
        payload.put("delta_quantity", decimal(deltaOnHand));
        payload.put("after_on_hand_quantity", decimal(afterOnHand));
        payload.put("after_reserved_quantity", decimal(afterReserved));
        payload.put("after_available_quantity", decimal(afterAvailable));
        payload.put("uom_code", command.uomCode());
        payload.put("movement_type", movementType(command.operation()));
        payload.put("business_type", command.businessType());
        payload.put("business_id", command.businessId());
        payload.put("business_item_id", command.businessItemId());
        payload.put("business_no", command.businessNo());
        payload.put("reservation_id", reservationId);
        payload.put("cancellation_saga_id", command.cancellationSagaId());
        payload.put("step_ordinal", command.cancellationStepOrdinal());

        outboxAppender.append(AppendDomainEventCommand.builder()
                .eventType("inventory.stock.changed")
                .schemaVersion(command.cancellationSagaId() == null ? 1 : 2).sourceSystem("cloudmold-inventory")
                .tenantId(tenantId).aggregateType("inventory_balance").aggregateId(balance.getBalanceId())
                .aggregateVersion(version).eventSequence((short) 1).occurredAt(command.occurredAt())
                .correlationId(command.correlationId()).causationId(command.causationId())
                .idempotencyKey(command.idempotencyKey()).payload(payload)
                .headers(Map.of("ledger_transaction_id", ledgerTransactionId,
                        "reservation_id", Objects.toString(reservationId, "")))
                .destination("lakehouse").build());
    }

    private static NormalizedCommand normalize(InventoryCommand command) {
        require(command != null && command.getOperation() != null, "inventory operation is required");
        requireText(command.getIdempotencyKey(), "idempotencyKey", 128);
        requireText(command.getOwnerId(), "ownerId", 128);
        requireText(command.getCanonicalSkuId(), "canonicalSkuId", 128);
        requireText(command.getWarehouseId(), "warehouseId", 128);
        require("SELLABLE".equals(command.getStockStatus()), "first slice supports SELLABLE stock only");
        require("QUALIFIED".equals(command.getQualityStatus()), "first slice supports QUALIFIED stock only");
        requireText(command.getUomCode(), "uomCode", 32);
        requireText(command.getBusinessType(), "businessType", 32);
        requireText(command.getBusinessId(), "businessId", 128);
        requireText(command.getBusinessItemId(), "businessItemId", 128);
        requireText(command.getBusinessNo(), "businessNo", 128);
        require(command.getOccurredAt() != null, "occurredAt is required");
        requireUuid(command.getCorrelationId(), "correlationId");
        if (command.getCausationId() != null) {
            requireUuid(command.getCausationId(), "causationId");
        }
        BigDecimal quantity = scaled(command.getQuantity());
        require(quantity.signum() > 0 && quantity.precision() - quantity.scale() <= 18,
                "quantity must be positive with at most 18 integer and 6 fractional digits");
        boolean closesReservation = command.getOperation() == InventoryOperation.SHIP
                || command.getOperation() == InventoryOperation.RELEASE;
        require(!closesReservation || notBlank(command.getReservationId()),
                "reservationId is required for SHIP and RELEASE");
        require(closesReservation || command.getReservationId() == null,
                "reservationId is only accepted for SHIP and RELEASE");
        return new NormalizedCommand(command.getOperation(), command.getIdempotencyKey(), command.getOwnerId(),
                command.getCanonicalSkuId(), command.getWarehouseId(), command.getStockStatus(),
                command.getQualityStatus(), command.getUomCode(), quantity, command.getReservationId(),
                command.getBusinessType(), command.getBusinessId(), command.getBusinessItemId(),
                command.getBusinessNo(), command.getSourceEventId(), command.getCorrelationId(),
                command.getCausationId(), command.getOccurredAt(), command.getCancellationSagaId(),
                command.getCancellationStepOrdinal());
    }

    private static void requireActiveReservation(InventoryReservationDO reservation, NormalizedCommand command) {
        require(reservation != null && reservation.getStatus() == RESERVATION_ACTIVE,
                "reservation is not active");
        require(scaled(reservation.getQuantity()).compareTo(command.quantity()) == 0,
                "first slice only supports full reservation close");
    }

    private static void requireMatches(InventoryBalanceDO balance, NormalizedCommand command) {
        require(balance.getOwnerId().equals(command.ownerId())
                        && balance.getCanonicalSkuId().equals(command.canonicalSkuId())
                        && balance.getWarehouseId().equals(command.warehouseId())
                        && balance.getStockStatus().equals(command.stockStatus())
                        && balance.getQualityStatus().equals(command.qualityStatus()),
                "reservation does not belong to the requested inventory dimension");
    }

    private static String fingerprint(Long tenantId, NormalizedCommand command) {
        String canonical = String.join("\u001f",
                tenantId.toString(), command.operation().name(), command.ownerId(), command.canonicalSkuId(),
                command.warehouseId(), command.stockStatus(), command.qualityStatus(), command.uomCode(),
                decimal(command.quantity()), Objects.toString(command.reservationId(), ""),
                command.businessType(), command.businessId(), command.businessItemId(), command.businessNo(),
                Objects.toString(command.sourceEventId(), ""), command.occurredAt().toString());
        // Keep the v1 fingerprint byte-for-byte stable. Saga metadata only belongs to the
        // v2 command identity when it is actually present, otherwise historical retries
        // would conflict with their already-persisted request hash.
        if (command.cancellationSagaId() != null || command.cancellationStepOrdinal() != null) {
            canonical = canonical + "\u001f" + Objects.toString(command.cancellationSagaId(), "")
                    + "\u001f" + Objects.toString(command.cancellationStepOrdinal(), "");
        }
        return DigestUtil.sha256Hex(canonical);
    }

    private static BigDecimal available(String stockStatus, BigDecimal onHand, BigDecimal reserved) {
        return "SELLABLE".equals(stockStatus) ? onHand.subtract(reserved) : ZERO;
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

    private static String movementType(InventoryOperation operation) {
        return switch (operation) {
            case RECEIVE -> "PURCHASE_RECEIPT";
            case RESERVE -> "RESERVATION";
            case SHIP -> "SALE_SHIPMENT";
            case RETURN -> "SALE_RETURN";
            case RELEASE -> "RESERVATION_RELEASE";
        };
    }

    private static void requireUuid(String value, String field) {
        require(notBlank(value), field + " is required");
        try {
            UUID.fromString(value);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException(field + " must be a UUID", error);
        }
    }

    private static void requireText(String value, String field, int maxLength) {
        require(notBlank(value) && value.length() <= maxLength, field + " is required and too long");
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    private record NormalizedCommand(InventoryOperation operation, String idempotencyKey, String ownerId,
                                     String canonicalSkuId, String warehouseId, String stockStatus,
                                     String qualityStatus, String uomCode, BigDecimal quantity,
                                     String reservationId, String businessType, String businessId,
                                     String businessItemId, String businessNo, String sourceEventId,
                                     String correlationId, String causationId, java.time.Instant occurredAt,
                                     String cancellationSagaId, Integer cancellationStepOrdinal) {
    }

    private record LockedAggregate(InventoryBalanceDO balance, InventoryReservationDO reservation) {
    }

}
