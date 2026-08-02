package cn.iocoder.yudao.module.cloudmold.inventory.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.catalog.api.CatalogSkuValidationApi;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.AppendDomainEventCommand;
import cn.iocoder.yudao.module.cloudmold.datacontract.api.outbox.OutboxAppender;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryStockTransferApi;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryStockTransferCommand;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryStockTransferOperation;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryStockTransferResult;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.InventoryLotDO;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.InventoryStockTransferDO;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.InventoryStockTransferOperationDO;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.InventoryV3BalanceDO;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.InventoryV3LedgerEntryDO;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.InventoryV3LedgerTransactionDO;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql.InventoryLotMapper;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql.InventoryStockTransferMapper;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql.InventoryStockTransferOperationMapper;
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
import java.time.LocalDate;
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
public class InventoryStockTransferServiceImpl implements InventoryStockTransferApi {

    private static final int OPERATION_SUCCEEDED = 10;
    private static final BigDecimal ZERO = new BigDecimal("0.000000");
    private static final Set<String> QUALITY_STATUSES = Set.of("PENDING_QC", "QUALIFIED", "DAMAGED", "REJECTED");
    private static final Set<String> STOCK_STATUSES = Set.of("SELLABLE", "NON_SELLABLE");

    private final InventoryStockTransferOperationMapper operationMapper;
    private final InventoryStockTransferMapper transferMapper;
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
    public InventoryStockTransferResult execute(InventoryStockTransferCommand rawCommand) {
        NormalizedCommand command = normalize(rawCommand);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String requestHash = fingerprint(tenantId, command);
        String attemptToken = UUID.randomUUID().toString();

        operationMapper.insertOrResolve(tenantId, command.idempotencyKey(), command.sourceEventId(),
                command.movementGroupId(), command.operation().name(), requestHash, attemptToken, now);
        Long operationId = operationMapper.selectLastInsertId();
        require(operationId != null && operationId > 0, "failed to resolve inventory stock transfer operation");
        InventoryStockTransferOperationDO operation = operationMapper.selectForUpdate(tenantId, operationId);
        require(operation != null, "inventory stock transfer operation disappeared");
        if (!attemptToken.equals(operation.getAttemptToken())) {
            require(Objects.equals(requestHash, operation.getRequestHash()),
                    "idempotency key or source event conflicts with different transfer payload");
            require(operation.getStatus() == OPERATION_SUCCEEDED && operation.getLedgerTransactionId() != null,
                    "existing inventory stock transfer operation is not complete");
            return replay(operation);
        }

        validateOwner(command.ownerType(), command.ownerId());
        catalogSkuValidationApi.requireActiveSku(command.canonicalSkuId());
        warehouseValidationApi.requireActiveLocation(command.sourceWarehouseId(), command.sourceLocationId());
        warehouseValidationApi.requireActiveLocation(command.targetWarehouseId(), command.targetLocationId());
        InventoryLotDO lot = requireLot(tenantId, command);

        transferMapper.insertOrResolve(command.movementGroupId(), tenantId, command.ownerType(), command.ownerId(),
                command.canonicalSkuId(), command.sourceWarehouseId(), command.sourceLocationId(),
                command.targetWarehouseId(), command.targetLocationId(), command.lotId(), command.stockStatus(),
                command.qualityStatus(), command.baseUomCode(), operationId, now);
        InventoryStockTransferDO transfer = transferMapper.selectForUpdate(tenantId, command.movementGroupId());
        require(transfer != null, "inventory stock transfer movement group disappeared");
        requireSameIdentity(transfer, command);

        balanceMapper.insertOrResolve(UUID.randomUUID().toString(), tenantId, command.ownerType(), command.ownerId(),
                command.canonicalSkuId(), command.targetWarehouseId(), command.targetLocationId(), command.lotId(),
                command.stockStatus(), command.qualityStatus(), command.baseUomCode(), now);
        InventoryV3BalanceDO sourceSnapshot = balanceMapper.selectDimension(tenantId, command.ownerType(),
                command.ownerId(), command.canonicalSkuId(), command.sourceWarehouseId(),
                command.sourceLocationId(), command.lotId(), command.stockStatus(), command.qualityStatus());
        InventoryV3BalanceDO targetSnapshot = balanceMapper.selectDimension(tenantId, command.ownerType(),
                command.ownerId(), command.canonicalSkuId(), command.targetWarehouseId(),
                command.targetLocationId(), command.lotId(), command.stockStatus(), command.qualityStatus());
        require(sourceSnapshot != null, "inventory stock transfer source balance does not exist");
        require(targetSnapshot != null, "inventory stock transfer target balance does not exist");

        LockedBalances balances = lockBalancesInStableOrder(tenantId, sourceSnapshot, targetSnapshot);
        InventoryV3BalanceDO source = balances.source();
        InventoryV3BalanceDO target = balances.target();
        requireBalanceIdentity(source, command, true);
        requireBalanceIdentity(target, command, false);
        require(Objects.equals(source.getBaseUomCode(), command.baseUomCode())
                        && Objects.equals(target.getBaseUomCode(), command.baseUomCode()),
                "base UOM does not match stock transfer balances");

        BigDecimal sourceBeforeOnHand = scaled(source.getOnHandQuantity());
        BigDecimal sourceBeforeReserved = scaled(source.getReservedQuantity());
        BigDecimal sourceBeforeTransit = scaled(source.getInTransitQuantity());
        BigDecimal targetBeforeOnHand = scaled(target.getOnHandQuantity());
        BigDecimal targetBeforeReserved = scaled(target.getReservedQuantity());
        BigDecimal targetBeforeTransit = scaled(target.getInTransitQuantity());
        BigDecimal dispatchedBefore = scaled(transfer.getDispatchedQuantity());
        BigDecimal receivedBefore = scaled(transfer.getReceivedQuantity());

        BigDecimal sourceAfterOnHand = sourceBeforeOnHand;
        BigDecimal sourceAfterTransit = sourceBeforeTransit;
        BigDecimal targetAfterOnHand = targetBeforeOnHand;
        BigDecimal dispatchedAfter = dispatchedBefore;
        BigDecimal receivedAfter = receivedBefore;
        long sourceVersion = source.getVersion();
        long targetVersion = target.getVersion();

        if (command.operation() == InventoryStockTransferOperation.DISPATCH) {
            require(sourceBeforeOnHand.subtract(sourceBeforeReserved).compareTo(command.quantity()) >= 0,
                    "insufficient unreserved source stock for transfer dispatch");
            sourceAfterOnHand = sourceBeforeOnHand.subtract(command.quantity());
            sourceAfterTransit = sourceBeforeTransit.add(command.quantity());
            require(balanceMapper.updateBalanceCas(tenantId, source.getBalanceId(), source.getVersion(),
                    sourceAfterOnHand, sourceBeforeReserved, sourceAfterTransit, now) == 1,
                    "inventory stock transfer source balance version conflict");
            require(transferMapper.addDispatched(tenantId, command.movementGroupId(), transfer.getVersion(),
                    command.quantity(), operationId, now) == 1, "inventory stock transfer version conflict");
            dispatchedAfter = dispatchedBefore.add(command.quantity());
            sourceVersion++;
        } else {
            require(dispatchedBefore.subtract(receivedBefore).compareTo(command.quantity()) >= 0,
                    "stock transfer receipt exceeds dispatched quantity");
            require(sourceBeforeTransit.compareTo(command.quantity()) >= 0,
                    "insufficient source in-transit stock for transfer receipt");
            sourceAfterTransit = sourceBeforeTransit.subtract(command.quantity());
            targetAfterOnHand = targetBeforeOnHand.add(command.quantity());
            require(balanceMapper.updateBalanceCas(tenantId, source.getBalanceId(), source.getVersion(),
                    sourceAfterOnHand, sourceBeforeReserved, sourceAfterTransit, now) == 1,
                    "inventory stock transfer source balance version conflict");
            require(balanceMapper.updateBalanceCas(tenantId, target.getBalanceId(), target.getVersion(),
                    targetAfterOnHand, targetBeforeReserved, targetBeforeTransit, now) == 1,
                    "inventory stock transfer target balance version conflict");
            require(transferMapper.addReceived(tenantId, command.movementGroupId(), transfer.getVersion(),
                    command.quantity(), operationId, now) == 1,
                    "stock transfer receipt exceeds dispatched quantity or transfer version changed");
            receivedAfter = receivedBefore.add(command.quantity());
            sourceVersion++;
            targetVersion++;
        }

        InventoryV3LedgerTransactionDO ledgerTransaction = new InventoryV3LedgerTransactionDO()
                .setTenantId(tenantId).setStockTransferOperationId(operationId)
                .setMovementGroupId(command.movementGroupId())
                .setCommandType("STOCK_TRANSFER_" + command.operation().name())
                .setBusinessType(command.businessType()).setBusinessId(command.businessId())
                .setBusinessItemId(command.businessItemId()).setBusinessNo(command.businessNo())
                .setOccurredAt(LocalDateTime.ofInstant(command.occurredAt(), ZoneOffset.UTC)).setCreatedAt(now);
        ledgerTransactionMapper.insert(ledgerTransaction);
        require(ledgerTransaction.getLedgerTransactionId() != null,
                "inventory stock transfer ledger transaction id was not generated");

        if (command.operation() == InventoryStockTransferOperation.DISPATCH) {
            ledgerEntryMapper.insert(entry(tenantId, ledgerTransaction.getLedgerTransactionId(), command,
                    "SINGLE", null, source.getBalanceId(), sourceVersion,
                    sourceBeforeOnHand, command.quantity().negate(), sourceAfterOnHand,
                    sourceBeforeReserved, ZERO, sourceBeforeReserved,
                    sourceBeforeTransit, command.quantity(), sourceAfterTransit, now));
        } else {
            ledgerEntryMapper.insert(entry(tenantId, ledgerTransaction.getLedgerTransactionId(), command,
                    "OUT", target.getBalanceId(), source.getBalanceId(), sourceVersion,
                    sourceBeforeOnHand, ZERO, sourceAfterOnHand,
                    sourceBeforeReserved, ZERO, sourceBeforeReserved,
                    sourceBeforeTransit, command.quantity().negate(), sourceAfterTransit, now));
            ledgerEntryMapper.insert(entry(tenantId, ledgerTransaction.getLedgerTransactionId(), command,
                    "IN", source.getBalanceId(), target.getBalanceId(), targetVersion,
                    targetBeforeOnHand, command.quantity(), targetAfterOnHand,
                    targetBeforeReserved, ZERO, targetBeforeReserved,
                    targetBeforeTransit, ZERO, targetBeforeTransit, now));
        }

        BigDecimal outstanding = dispatchedAfter.subtract(receivedAfter);
        InventoryStockTransferResult result = InventoryStockTransferResult.builder()
                .operationId(operationId).ledgerTransactionId(ledgerTransaction.getLedgerTransactionId())
                .movementGroupId(command.movementGroupId()).sourceBalanceId(source.getBalanceId())
                .sourceAggregateVersion(sourceVersion).sourceOnHandQuantity(sourceAfterOnHand)
                .sourceInTransitQuantity(sourceAfterTransit).targetBalanceId(target.getBalanceId())
                .targetAggregateVersion(targetVersion).targetOnHandQuantity(targetAfterOnHand)
                .targetInTransitQuantity(targetBeforeTransit).cumulativeDispatchedQuantity(dispatchedAfter)
                .cumulativeReceivedQuantity(receivedAfter).outstandingQuantity(outstanding).duplicate(false).build();

        appendEvents(tenantId, command, lot, ledgerTransaction.getLedgerTransactionId(), source, target,
                sourceVersion, targetVersion, sourceBeforeOnHand, sourceAfterOnHand, sourceBeforeTransit,
                sourceAfterTransit, targetBeforeOnHand, targetAfterOnHand, targetBeforeTransit,
                dispatchedAfter, receivedAfter, outstanding);
        require(operationMapper.markSucceeded(tenantId, operationId, result, now) == 1,
                "inventory stock transfer operation completion conflict");
        return result;
    }

    private LockedBalances lockBalancesInStableOrder(Long tenantId, InventoryV3BalanceDO source,
                                                       InventoryV3BalanceDO target) {
        boolean sourceFirst = source.getBalanceId().compareTo(target.getBalanceId()) < 0;
        InventoryV3BalanceDO first = balanceMapper.selectByIdForUpdate(tenantId,
                sourceFirst ? source.getBalanceId() : target.getBalanceId());
        InventoryV3BalanceDO second = balanceMapper.selectByIdForUpdate(tenantId,
                sourceFirst ? target.getBalanceId() : source.getBalanceId());
        require(first != null && second != null, "inventory stock transfer balance disappeared");
        return sourceFirst ? new LockedBalances(first, second) : new LockedBalances(second, first);
    }

    private InventoryLotDO requireLot(Long tenantId, NormalizedCommand command) {
        if (command.lotId() == null) return null;
        InventoryLotDO lot = lotMapper.selectCurrent(tenantId, command.lotId());
        require(lot != null, "inventory lot does not exist");
        require(Objects.equals(lot.getOwnerType(), command.ownerType())
                        && Objects.equals(lot.getOwnerId(), command.ownerId())
                        && Objects.equals(lot.getCanonicalSkuId(), command.canonicalSkuId()),
                "inventory lot does not belong to transfer owner and SKU");
        require(!"CLOSED".equals(lot.getStatus()), "closed inventory lot cannot be transferred");
        if (command.operation() == InventoryStockTransferOperation.DISPATCH) {
            require("ACTIVE".equals(lot.getStatus()), "only ACTIVE inventory lot can be dispatched");
            LocalDate occurredOn = command.occurredAt().atZone(ZoneOffset.UTC).toLocalDate();
            require(lot.getExpiresOn() == null || !lot.getExpiresOn().isBefore(occurredOn),
                    "expired inventory lot cannot be dispatched");
        }
        return lot;
    }

    private void appendEvents(Long tenantId, NormalizedCommand command, InventoryLotDO lot,
                              Long transactionId, InventoryV3BalanceDO source, InventoryV3BalanceDO target,
                              long sourceVersion, long targetVersion, BigDecimal sourceBeforeOnHand,
                              BigDecimal sourceAfterOnHand, BigDecimal sourceBeforeTransit,
                              BigDecimal sourceAfterTransit, BigDecimal targetBeforeOnHand,
                              BigDecimal targetAfterOnHand, BigDecimal targetBeforeTransit,
                              BigDecimal dispatched, BigDecimal received, BigDecimal outstanding) {
        appendEvent(tenantId, command, lot, transactionId, source, sourceVersion,
                sourceAfterOnHand.subtract(sourceBeforeOnHand), sourceAfterTransit.subtract(sourceBeforeTransit),
                sourceAfterOnHand, source.getReservedQuantity(), sourceAfterTransit,
                command.operation() == InventoryStockTransferOperation.DISPATCH ? "SINGLE" : "OUT",
                command.operation() == InventoryStockTransferOperation.DISPATCH ? null : target.getBalanceId(),
                dispatched, received, outstanding, "source");
        if (command.operation() == InventoryStockTransferOperation.RECEIVE) {
            appendEvent(tenantId, command, lot, transactionId, target, targetVersion,
                    targetAfterOnHand.subtract(targetBeforeOnHand), ZERO, targetAfterOnHand,
                    target.getReservedQuantity(), targetBeforeTransit, "IN", source.getBalanceId(),
                    dispatched, received, outstanding, "target");
        }
    }

    private void appendEvent(Long tenantId, NormalizedCommand command, InventoryLotDO lot,
                             Long transactionId, InventoryV3BalanceDO balance, long aggregateVersion,
                             BigDecimal deltaOnHand, BigDecimal deltaTransit, BigDecimal afterOnHand,
                             BigDecimal afterReserved, BigDecimal afterTransit, String entryRole,
                             String counterpartyBalanceId, BigDecimal dispatched, BigDecimal received,
                             BigDecimal outstanding, String side) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("movement_group_id", command.movementGroupId());
        payload.put("transfer_stage", command.operation().name());
        payload.put("transfer_side", side.toUpperCase(Locale.ROOT));
        payload.put("owner_type", command.ownerType());
        payload.put("owner_id", command.ownerId());
        payload.put("canonical_sku_id", command.canonicalSkuId());
        payload.put("source_warehouse_id", command.sourceWarehouseId());
        payload.put("source_location_id", command.sourceLocationId());
        payload.put("target_warehouse_id", command.targetWarehouseId());
        payload.put("target_location_id", command.targetLocationId());
        payload.put("warehouse_id", balance.getWarehouseId());
        payload.put("location_id", balance.getLocationId());
        payload.put("lot_id", command.lotId());
        payload.put("lot_code", lot == null ? null : lot.getLotCode());
        payload.put("stock_status", command.stockStatus());
        payload.put("quality_status", command.qualityStatus());
        payload.put("base_uom_code", command.baseUomCode());
        payload.put("delta_on_hand_quantity", decimal(deltaOnHand));
        payload.put("delta_reserved_quantity", decimal(ZERO));
        payload.put("delta_in_transit_quantity", decimal(deltaTransit));
        payload.put("after_on_hand_quantity", decimal(afterOnHand));
        payload.put("after_reserved_quantity", decimal(afterReserved));
        payload.put("after_in_transit_quantity", decimal(afterTransit));
        payload.put("cumulative_dispatched_quantity", decimal(dispatched));
        payload.put("cumulative_received_quantity", decimal(received));
        payload.put("outstanding_quantity", decimal(outstanding));
        payload.put("movement_type", "STOCK_TRANSFER_" + command.operation().name());
        payload.put("ledger_transaction_id", transactionId);
        payload.put("entry_role", entryRole);
        payload.put("counterparty_balance_id", counterpartyBalanceId);
        payload.put("business_type", command.businessType());
        payload.put("business_id", command.businessId());
        payload.put("business_item_id", command.businessItemId());
        payload.put("business_no", command.businessNo());
        outboxAppender.append(AppendDomainEventCommand.builder().eventType("inventory.stock.changed")
                .schemaVersion(6).sourceSystem("cloudmold-inventory").tenantId(tenantId)
                .aggregateType("inventory_balance_v3").aggregateId(balance.getBalanceId())
                .aggregateVersion(aggregateVersion).eventSequence((short) 1).occurredAt(command.occurredAt())
                .correlationId(command.correlationId()).causationId(command.causationId())
                .idempotencyKey(command.idempotencyKey() + ":event:v6:" + side).payload(payload)
                .headers(Map.of("ledger_transaction_id", transactionId,
                        "movement_group_id", command.movementGroupId(), "transfer_side", side))
                .destination("lakehouse").build());
    }

    private static InventoryV3LedgerEntryDO entry(Long tenantId, Long transactionId, NormalizedCommand command,
                                                   String role, String counterpartyBalanceId, String balanceId,
                                                   long aggregateVersion, BigDecimal beforeOnHand,
                                                   BigDecimal deltaOnHand, BigDecimal afterOnHand,
                                                   BigDecimal beforeReserved, BigDecimal deltaReserved,
                                                   BigDecimal afterReserved, BigDecimal beforeTransit,
                                                   BigDecimal deltaTransit, BigDecimal afterTransit,
                                                   LocalDateTime now) {
        return new InventoryV3LedgerEntryDO().setTenantId(tenantId).setLedgerTransactionId(transactionId)
                .setMovementGroupId(command.movementGroupId()).setEntryRole(role)
                .setCounterpartyBalanceId(counterpartyBalanceId).setBalanceId(balanceId)
                .setAggregateVersion(aggregateVersion).setBaseUomCode(command.baseUomCode())
                .setBeforeOnHandQuantity(beforeOnHand).setDeltaOnHandQuantity(deltaOnHand)
                .setAfterOnHandQuantity(afterOnHand).setBeforeReservedQuantity(beforeReserved)
                .setDeltaReservedQuantity(deltaReserved).setAfterReservedQuantity(afterReserved)
                .setBeforeInTransitQuantity(beforeTransit).setDeltaInTransitQuantity(deltaTransit)
                .setAfterInTransitQuantity(afterTransit).setCreatedAt(now);
    }

    private static InventoryStockTransferResult replay(InventoryStockTransferOperationDO operation) {
        return InventoryStockTransferResult.builder().operationId(operation.getOperationId())
                .ledgerTransactionId(operation.getLedgerTransactionId())
                .movementGroupId(operation.getMovementGroupId()).sourceBalanceId(operation.getSourceBalanceId())
                .sourceAggregateVersion(operation.getSourceAggregateVersion())
                .sourceOnHandQuantity(operation.getSourceOnHandQuantity())
                .sourceInTransitQuantity(operation.getSourceInTransitQuantity())
                .targetBalanceId(operation.getTargetBalanceId())
                .targetAggregateVersion(operation.getTargetAggregateVersion())
                .targetOnHandQuantity(operation.getTargetOnHandQuantity())
                .targetInTransitQuantity(operation.getTargetInTransitQuantity())
                .cumulativeDispatchedQuantity(operation.getCumulativeDispatchedQuantity())
                .cumulativeReceivedQuantity(operation.getCumulativeReceivedQuantity())
                .outstandingQuantity(operation.getOutstandingQuantity()).duplicate(true).build();
    }

    private static void requireSameIdentity(InventoryStockTransferDO transfer, NormalizedCommand command) {
        require(Objects.equals(transfer.getOwnerType(), command.ownerType())
                        && Objects.equals(transfer.getOwnerId(), command.ownerId())
                        && Objects.equals(transfer.getCanonicalSkuId(), command.canonicalSkuId())
                        && Objects.equals(transfer.getSourceWarehouseId(), command.sourceWarehouseId())
                        && Objects.equals(transfer.getSourceLocationId(), command.sourceLocationId())
                        && Objects.equals(transfer.getTargetWarehouseId(), command.targetWarehouseId())
                        && Objects.equals(transfer.getTargetLocationId(), command.targetLocationId())
                        && Objects.equals(transfer.getLotId(), command.lotId())
                        && Objects.equals(transfer.getStockStatus(), command.stockStatus())
                        && Objects.equals(transfer.getQualityStatus(), command.qualityStatus())
                        && Objects.equals(transfer.getBaseUomCode(), command.baseUomCode()),
                "movement group conflicts with different transfer identity");
    }

    private static void requireBalanceIdentity(InventoryV3BalanceDO balance, NormalizedCommand command,
                                               boolean source) {
        require(Objects.equals(balance.getOwnerType(), command.ownerType())
                        && Objects.equals(balance.getOwnerId(), command.ownerId())
                        && Objects.equals(balance.getCanonicalSkuId(), command.canonicalSkuId())
                        && Objects.equals(balance.getWarehouseId(),
                        source ? command.sourceWarehouseId() : command.targetWarehouseId())
                        && Objects.equals(balance.getLocationId(),
                        source ? command.sourceLocationId() : command.targetLocationId())
                        && Objects.equals(balance.getLotId(), command.lotId())
                        && Objects.equals(balance.getStockStatus(), command.stockStatus())
                        && Objects.equals(balance.getQualityStatus(), command.qualityStatus()),
                "locked balance does not match stock transfer identity");
    }

    static String fingerprint(Long tenantId, NormalizedCommand command) {
        return DigestUtil.sha256Hex(String.join("\u001f", tenantId.toString(), command.operation().name(),
                command.idempotencyKey(), Objects.toString(command.sourceEventId(), ""),
                command.movementGroupId(), command.ownerType(), command.ownerId(), command.canonicalSkuId(),
                command.sourceWarehouseId(), command.sourceLocationId(), command.targetWarehouseId(),
                command.targetLocationId(), Objects.toString(command.lotId(), ""), command.stockStatus(),
                command.qualityStatus(), command.baseUomCode(), decimal(command.quantity()),
                command.businessType(), command.businessId(), command.businessItemId(), command.businessNo(),
                command.correlationId(), Objects.toString(command.causationId(), ""),
                command.occurredAt().toString()));
    }

    private static NormalizedCommand normalize(InventoryStockTransferCommand command) {
        require(command != null && command.getOperation() != null, "stock transfer operation is required");
        requireText(command.getIdempotencyKey(), "idempotencyKey", 128);
        String sourceEventId = command.getSourceEventId() == null ? null
                : requireUuid(command.getSourceEventId(), "sourceEventId");
        String movementGroupId = requireUuid(command.getMovementGroupId(), "movementGroupId");
        String ownerType = upper(command.getOwnerType());
        require(Set.of("MERCHANT", "PLATFORM", "MEMBER").contains(ownerType), "unsupported stock transfer ownerType");
        String ownerId = requireUuid(command.getOwnerId(), "ownerId");
        String skuId = requireUuid(command.getCanonicalSkuId(), "canonicalSkuId");
        String sourceWarehouseId = requireUuid(command.getSourceWarehouseId(), "sourceWarehouseId");
        String sourceLocationId = requireUuid(command.getSourceLocationId(), "sourceLocationId");
        String targetWarehouseId = requireUuid(command.getTargetWarehouseId(), "targetWarehouseId");
        String targetLocationId = requireUuid(command.getTargetLocationId(), "targetLocationId");
        require(!Objects.equals(sourceWarehouseId, targetWarehouseId)
                        || !Objects.equals(sourceLocationId, targetLocationId),
                "stock transfer source and target must differ");
        String lotId = command.getLotId() == null ? null : requireUuid(command.getLotId(), "lotId");
        String stockStatus = upper(command.getStockStatus());
        String qualityStatus = upper(command.getQualityStatus());
        require(STOCK_STATUSES.contains(stockStatus), "unsupported stockStatus");
        require(QUALITY_STATUSES.contains(qualityStatus), "unsupported qualityStatus");
        require(!"SELLABLE".equals(stockStatus) || "QUALIFIED".equals(qualityStatus),
                "SELLABLE inventory must be QUALIFIED");
        String uom = upper(command.getBaseUomCode());
        requireText(uom, "baseUomCode", 32);
        BigDecimal quantity = scaled(command.getQuantity());
        require(quantity.signum() > 0 && quantity.precision() - quantity.scale() <= 18,
                "quantity must be positive with at most 18 integer and 6 fractional digits");
        requireText(command.getBusinessType(), "businessType", 32);
        requireText(command.getBusinessId(), "businessId", 128);
        requireText(command.getBusinessItemId(), "businessItemId", 128);
        requireText(command.getBusinessNo(), "businessNo", 128);
        String correlationId = requireUuid(command.getCorrelationId(), "correlationId");
        String causationId = command.getCausationId() == null ? null
                : requireUuid(command.getCausationId(), "causationId");
        require(command.getOccurredAt() != null, "occurredAt is required");
        return new NormalizedCommand(command.getOperation(), command.getIdempotencyKey().trim(), sourceEventId,
                movementGroupId, ownerType, ownerId, skuId, sourceWarehouseId, sourceLocationId,
                targetWarehouseId, targetLocationId, lotId, stockStatus, qualityStatus, uom, quantity,
                command.getBusinessType().trim(), command.getBusinessId().trim(), command.getBusinessItemId().trim(),
                command.getBusinessNo().trim(), correlationId, causationId, command.getOccurredAt());
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

    private void validateOwner(String ownerType, String ownerId) {
        if ("MERCHANT".equals(ownerType)) merchantOwnerValidationApi.requireActiveMerchant(ownerId);
    }

    record NormalizedCommand(InventoryStockTransferOperation operation, String idempotencyKey,
                             String sourceEventId, String movementGroupId, String ownerType, String ownerId,
                             String canonicalSkuId, String sourceWarehouseId, String sourceLocationId,
                             String targetWarehouseId, String targetLocationId, String lotId, String stockStatus,
                             String qualityStatus, String baseUomCode, BigDecimal quantity, String businessType,
                             String businessId, String businessItemId, String businessNo, String correlationId,
                             String causationId, Instant occurredAt) {
    }

    private record LockedBalances(InventoryV3BalanceDO source, InventoryV3BalanceDO target) {
    }
}
