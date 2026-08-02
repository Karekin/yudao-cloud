package cn.iocoder.yudao.module.cloudmold.inventory.service;

import cn.hutool.crypto.digest.DigestUtil;
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
public class InventoryProcurementReceiptServiceImpl implements InventoryProcurementReceiptApi {
    private static final BigDecimal ZERO = new BigDecimal("0.000000");
    private static final int SUCCEEDED = 10;

    private final InventoryProcurementReceiptOperationMapper operationMapper;
    private final InventoryProcurementReceiptMapper receiptMapper;
    private final InventoryV3BalanceMapper balanceMapper;
    private final InventoryLotMapper lotMapper;
    private final InventoryV3LedgerTransactionMapper transactionMapper;
    private final InventoryV3LedgerEntryMapper entryMapper;
    private final OutboxAppender outboxAppender;
    private final CatalogSkuValidationApi catalogApi;
    private final WarehouseReferenceValidationApi warehouseApi;
    private final MerchantOwnerValidationApi merchantApi;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InventoryProcurementReceiptResult execute(InventoryProcurementReceiptCommand raw) {
        Command c = normalize(raw);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String hash = fingerprint(tenantId, c);
        String attempt = UUID.randomUUID().toString();
        operationMapper.insertOrResolve(tenantId, c.idempotencyKey(), c.sourceEventId(), c.receiptLineId(), c.receiptId(),
                c.decisionVersion(), c.disposition().name(), c.operation().name(), hash, attempt, now);
        Long operationId = operationMapper.selectLastInsertId();
        require(operationId != null && operationId > 0, "failed to resolve procurement receipt operation");
        InventoryProcurementReceiptOperationDO op = operationMapper.selectForUpdate(tenantId, operationId);
        require(op != null, "procurement receipt operation disappeared");
        if (!attempt.equals(op.getAttemptToken())) {
            require(Objects.equals(hash, op.getRequestHash()),
                    "idempotency key or receipt effect conflicts with different payload");
            require(op.getStatus() == SUCCEEDED && op.getLedgerTransactionId() != null,
                    "existing procurement receipt operation is not complete");
            return replay(op, c);
        }

        validateOwner(c.ownerType(), c.ownerId());
        catalogApi.requireActiveSku(c.skuId());
        warehouseApi.requireActiveLocation(c.warehouseId(), c.locationId());
        InventoryLotDO lot = requireLot(tenantId, c);

        receiptMapper.insertOrResolve(tenantId, c.receiptId(), c.receiptLineId(), c.poId(), c.poItemId(),
                c.scheduleId(), c.supplierId(), c.ownerType(), c.ownerId(), c.skuId(), c.warehouseId(),
                c.locationId(), c.lotId(), c.uom(), c.valuationPolicy(), c.unitCost(),
                c.valuationPolicyVersion(), c.valuationPolicyHash(), c.currency(),
                operationId, now);
        InventoryProcurementReceiptDO receipt = receiptMapper.selectForUpdate(tenantId, c.receiptLineId());
        require(receipt != null, "procurement receipt inventory aggregate disappeared");
        requireIdentity(receipt, c);

        Quantities before = Quantities.from(receipt);
        Quantities after = transition(before, c);
        require(receiptMapper.updateQuantitiesCas(tenantId, c.receiptLineId(), receipt.getVersion(),
                after.received(), after.pending(), after.accepted(), after.rejected(), after.quarantined(),
                after.returned(), operationId, now) == 1, "procurement receipt quantity version conflict");

        Dimension sourceDimension = sourceDimension(c);
        Dimension targetDimension = targetDimension(c);
        if (targetDimension != null) ensureBalance(tenantId, c, targetDimension, now);
        InventoryV3BalanceDO sourceSnapshot = sourceDimension == null ? null : findBalance(tenantId, c, sourceDimension);
        InventoryV3BalanceDO targetSnapshot = targetDimension == null ? null : findBalance(tenantId, c, targetDimension);
        if (sourceDimension != null) require(sourceSnapshot != null, "procurement receipt source balance does not exist");
        if (targetDimension != null) require(targetSnapshot != null, "procurement receipt target balance does not exist");

        Locked locked = lockBalances(tenantId, sourceSnapshot, targetSnapshot);
        InventoryV3BalanceDO source = locked.source();
        InventoryV3BalanceDO target = locked.target();
        BigDecimal sourceBefore = source == null ? ZERO : scaled(source.getOnHandQuantity());
        BigDecimal targetBefore = target == null ? ZERO : scaled(target.getOnHandQuantity());
        BigDecimal sourceAfter = sourceBefore;
        BigDecimal targetAfter = targetBefore;
        long sourceVersion = source == null ? 0 : source.getVersion();
        long targetVersion = target == null ? 0 : target.getVersion();

        if (c.operation() == InventoryProcurementReceiptOperation.RECEIVE_PENDING_QUALITY) {
            targetAfter = targetBefore.add(c.quantity());
            updateBalance(tenantId, target, targetAfter, now);
            targetVersion++;
        } else if (c.operation() == InventoryProcurementReceiptOperation.RETURN_TO_SUPPLIER) {
            require(sourceBefore.compareTo(c.quantity()) >= 0, "insufficient disposition stock for supplier return");
            sourceAfter = sourceBefore.subtract(c.quantity());
            updateBalance(tenantId, source, sourceAfter, now);
            sourceVersion++;
            target = source;
            targetBefore = sourceBefore;
            targetAfter = sourceAfter;
            targetVersion = sourceVersion;
        } else {
            require(sourceBefore.compareTo(c.quantity()) >= 0, "insufficient pending quality stock");
            sourceAfter = sourceBefore.subtract(c.quantity());
            targetAfter = targetBefore.add(c.quantity());
            updateBalancesInLockOrder(tenantId, source, sourceAfter, target, targetAfter, now);
            sourceVersion++;
            targetVersion++;
        }

        InventoryV3LedgerTransactionDO tx = new InventoryV3LedgerTransactionDO().setTenantId(tenantId)
                .setProcurementReceiptOperationId(operationId).setMovementGroupId(c.receiptLineId())
                .setCommandType(c.operation().name()).setBusinessType("PROCUREMENT_RECEIPT")
                .setBusinessId(c.receiptId()).setBusinessItemId(c.receiptLineId()).setBusinessNo(c.businessNo())
                .setOccurredAt(LocalDateTime.ofInstant(c.occurredAt(), ZoneOffset.UTC)).setCreatedAt(now);
        transactionMapper.insert(tx);
        require(tx.getLedgerTransactionId() != null, "procurement receipt ledger transaction id was not generated");

        if (c.operation() == InventoryProcurementReceiptOperation.RECEIVE_PENDING_QUALITY) {
            entryMapper.insert(entry(tenantId, tx.getLedgerTransactionId(), c, "SINGLE", null, target,
                    targetVersion, targetBefore, c.quantity(), targetAfter, now));
        } else if (c.operation() == InventoryProcurementReceiptOperation.RETURN_TO_SUPPLIER) {
            entryMapper.insert(entry(tenantId, tx.getLedgerTransactionId(), c, "SINGLE", null, source,
                    sourceVersion, sourceBefore, c.quantity().negate(), sourceAfter, now));
        } else {
            entryMapper.insert(entry(tenantId, tx.getLedgerTransactionId(), c, "OUT", target.getBalanceId(), source,
                    sourceVersion, sourceBefore, c.quantity().negate(), sourceAfter, now));
            entryMapper.insert(entry(tenantId, tx.getLedgerTransactionId(), c, "IN", source.getBalanceId(), target,
                    targetVersion, targetBefore, c.quantity(), targetAfter, now));
        }

        InventoryProcurementReceiptResult result = InventoryProcurementReceiptResult.builder()
                .operationId(operationId).ledgerTransactionId(tx.getLedgerTransactionId())
                .receiptId(c.receiptId()).receiptLineId(c.receiptLineId())
                .sourceBalanceId(source == null ? null : source.getBalanceId())
                .sourceAggregateVersion(source == null ? null : sourceVersion)
                .targetBalanceId(target.getBalanceId()).targetAggregateVersion(targetVersion)
                .unitCostAmountMinor(c.unitCost()).movementCostAmountMinor(c.movementCost())
                .currencyCode(c.currency()).valuationPolicyId(c.valuationPolicy())
                .valuationPolicyVersion(c.valuationPolicyVersion()).valuationPolicyHash(c.valuationPolicyHash())
                .receivedQuantity(after.received()).pendingQuantity(after.pending())
                .acceptedQuantity(after.accepted()).rejectedQuantity(after.rejected())
                .quarantinedQuantity(after.quarantined()).returnedQuantity(after.returned()).duplicate(false).build();
        appendEvents(tenantId, c, lot, tx.getLedgerTransactionId(), source, target, sourceVersion, targetVersion,
                sourceBefore, sourceAfter, targetBefore, targetAfter, after);
        require(operationMapper.markSucceeded(tenantId, operationId, result, now) == 1,
                "procurement receipt operation completion conflict");
        return result;
    }

    private static Quantities transition(Quantities q, Command c) {
        BigDecimal n = c.quantity();
        return switch (c.operation()) {
            case RECEIVE_PENDING_QUALITY -> new Quantities(q.received().add(n), q.pending().add(n), q.accepted(),
                    q.rejected(), q.quarantined(), q.returned());
            case ACCEPT_QUALITY -> {
                require(q.pending().compareTo(n) >= 0, "quality decision exceeds pending quantity");
                yield new Quantities(q.received(), q.pending().subtract(n), q.accepted().add(n), q.rejected(),
                        q.quarantined(), q.returned());
            }
            case REJECT_QUALITY -> {
                require(q.pending().compareTo(n) >= 0, "quality decision exceeds pending quantity");
                yield new Quantities(q.received(), q.pending().subtract(n), q.accepted(), q.rejected().add(n),
                        q.quarantined(), q.returned());
            }
            case QUARANTINE_QUALITY -> {
                require(q.pending().compareTo(n) >= 0, "quality decision exceeds pending quantity");
                yield new Quantities(q.received(), q.pending().subtract(n), q.accepted(), q.rejected(),
                        q.quarantined().add(n), q.returned());
            }
            case RETURN_TO_SUPPLIER -> {
                BigDecimal accepted = q.accepted(), rejected = q.rejected(), quarantined = q.quarantined();
                switch (c.disposition()) {
                    case ACCEPTED -> { require(accepted.compareTo(n) >= 0, "supplier return exceeds accepted quantity"); accepted = accepted.subtract(n); }
                    case REJECTED -> { require(rejected.compareTo(n) >= 0, "supplier return exceeds rejected quantity"); rejected = rejected.subtract(n); }
                    case QUARANTINED -> { require(quarantined.compareTo(n) >= 0, "supplier return exceeds quarantined quantity"); quarantined = quarantined.subtract(n); }
                    default -> throw new IllegalArgumentException("pending quality stock cannot be returned to supplier");
                }
                yield new Quantities(q.received(), q.pending(), accepted, rejected, quarantined, q.returned().add(n));
            }
        };
    }

    private Locked lockBalances(Long tenantId, InventoryV3BalanceDO source, InventoryV3BalanceDO target) {
        if (source == null) return new Locked(null, balanceMapper.selectByIdForUpdate(tenantId, target.getBalanceId()));
        if (target == null || Objects.equals(source.getBalanceId(), target.getBalanceId()))
            return new Locked(balanceMapper.selectByIdForUpdate(tenantId, source.getBalanceId()), null);
        boolean sourceFirst = source.getBalanceId().compareTo(target.getBalanceId()) < 0;
        InventoryV3BalanceDO first = balanceMapper.selectByIdForUpdate(tenantId,
                sourceFirst ? source.getBalanceId() : target.getBalanceId());
        InventoryV3BalanceDO second = balanceMapper.selectByIdForUpdate(tenantId,
                sourceFirst ? target.getBalanceId() : source.getBalanceId());
        return sourceFirst ? new Locked(first, second) : new Locked(second, first);
    }

    private void updateBalancesInLockOrder(Long tenantId, InventoryV3BalanceDO source, BigDecimal sourceAfter,
                                           InventoryV3BalanceDO target, BigDecimal targetAfter, LocalDateTime now) {
        if (source.getBalanceId().compareTo(target.getBalanceId()) < 0) {
            updateBalance(tenantId, source, sourceAfter, now); updateBalance(tenantId, target, targetAfter, now);
        } else {
            updateBalance(tenantId, target, targetAfter, now); updateBalance(tenantId, source, sourceAfter, now);
        }
    }

    private void updateBalance(Long tenantId, InventoryV3BalanceDO b, BigDecimal onHand, LocalDateTime now) {
        require(balanceMapper.updateBalanceCas(tenantId, b.getBalanceId(), b.getVersion(), onHand,
                scaled(b.getReservedQuantity()), scaled(b.getInTransitQuantity()), now) == 1,
                "procurement receipt balance version conflict");
    }

    private void ensureBalance(Long tenantId, Command c, Dimension d, LocalDateTime now) {
        balanceMapper.insertOrResolve(UUID.randomUUID().toString(), tenantId, c.ownerType(), c.ownerId(), c.skuId(),
                c.warehouseId(), c.locationId(), c.lotId(), d.stock(), d.quality(), c.uom(), now);
    }

    private InventoryV3BalanceDO findBalance(Long tenantId, Command c, Dimension d) {
        return balanceMapper.selectDimension(tenantId, c.ownerType(), c.ownerId(), c.skuId(), c.warehouseId(),
                c.locationId(), c.lotId(), d.stock(), d.quality());
    }

    private InventoryLotDO requireLot(Long tenantId, Command c) {
        if (c.lotId() == null) return null;
        InventoryLotDO lot = lotMapper.selectCurrent(tenantId, c.lotId());
        require(lot != null && Objects.equals(lot.getOwnerId(), c.ownerId())
                        && Objects.equals(lot.getCanonicalSkuId(), c.skuId()),
                "inventory lot does not belong to procurement receipt owner and SKU");
        require(!"CLOSED".equals(lot.getStatus()), "closed inventory lot cannot receive procurement effects");
        if (c.operation() == InventoryProcurementReceiptOperation.RECEIVE_PENDING_QUALITY) {
            require("ACTIVE".equals(lot.getStatus()), "only ACTIVE inventory lot can be received");
            LocalDate date = c.occurredAt().atZone(ZoneOffset.UTC).toLocalDate();
            require(lot.getExpiresOn() == null || !lot.getExpiresOn().isBefore(date), "expired lot cannot be received");
        }
        return lot;
    }

    private void appendEvents(Long tenantId, Command c, InventoryLotDO lot, Long txId,
                              InventoryV3BalanceDO source, InventoryV3BalanceDO target, long sourceVersion,
                              long targetVersion, BigDecimal sourceBefore, BigDecimal sourceAfter,
                              BigDecimal targetBefore, BigDecimal targetAfter, Quantities q) {
        if (source != null && !Objects.equals(source.getBalanceId(), target.getBalanceId()))
            appendEvent(tenantId, c, lot, txId, source, sourceVersion, sourceAfter.subtract(sourceBefore), q, "source");
        appendEvent(tenantId, c, lot, txId, target, targetVersion, targetAfter.subtract(targetBefore), q, "target");
    }

    private void appendEvent(Long tenantId, Command c, InventoryLotDO lot, Long txId, InventoryV3BalanceDO b,
                             long version, BigDecimal delta, Quantities q, String side) {
        Map<String,Object> p = new LinkedHashMap<>();
        p.put("receipt_id", c.receiptId()); p.put("receipt_line_id", c.receiptLineId());
        p.put("purchase_order_id", c.poId()); p.put("purchase_order_item_id", c.poItemId());
        p.put("purchase_order_schedule_id", c.scheduleId()); p.put("supplier_id", c.supplierId());
        p.put("owner_id", c.ownerId()); p.put("canonical_sku_id", c.skuId());
        p.put("warehouse_id", c.warehouseId()); p.put("location_id", c.locationId());
        p.put("lot_id", c.lotId()); p.put("lot_code", lot == null ? null : lot.getLotCode());
        p.put("stock_status", b.getStockStatus()); p.put("quality_status", b.getQualityStatus());
        p.put("base_uom_code", c.uom()); p.put("delta_on_hand_quantity", decimal(delta));
        p.put("movement_type", c.operation().name()); p.put("disposition", c.disposition().name());
        p.put("quality_decision_id", c.qualityDecisionId()); p.put("decision_version", c.decisionVersion());
        p.put("quality_evidence_ref", c.evidence()); p.put("valuation_policy", c.valuationPolicy());
        p.put("valuation_policy_version", c.valuationPolicyVersion());
        p.put("valuation_policy_hash", c.valuationPolicyHash());
        p.put("unit_cost_amount_minor", c.unitCost()); p.put("movement_cost_amount_minor", c.movementCost());
        p.put("currency_code", c.currency()); p.put("received_quantity", decimal(q.received()));
        p.put("pending_quantity", decimal(q.pending())); p.put("accepted_quantity", decimal(q.accepted()));
        p.put("rejected_quantity", decimal(q.rejected())); p.put("quarantined_quantity", decimal(q.quarantined()));
        p.put("returned_quantity", decimal(q.returned())); p.put("ledger_transaction_id", txId);
        outboxAppender.append(AppendDomainEventCommand.builder().eventType("inventory.stock.changed").schemaVersion(7)
                .sourceSystem("cloudmold-inventory").tenantId(tenantId).aggregateType("inventory_balance_v3")
                .aggregateId(b.getBalanceId()).aggregateVersion(version).eventSequence((short)1)
                .occurredAt(c.occurredAt()).correlationId(c.correlationId()).causationId(c.causationId())
                .idempotencyKey(c.idempotencyKey()+":event:v7:"+side).payload(p)
                .headers(Map.of("receipt_line_id",c.receiptLineId(),"ledger_transaction_id",txId,"side",side))
                .destination("lakehouse").build());
    }

    private static InventoryV3LedgerEntryDO entry(Long tenantId, Long txId, Command c, String role,
                                                   String counterparty, InventoryV3BalanceDO b, long version,
                                                   BigDecimal before, BigDecimal delta, BigDecimal after,
                                                   LocalDateTime now) {
        return new InventoryV3LedgerEntryDO().setTenantId(tenantId).setLedgerTransactionId(txId)
                .setMovementGroupId(c.receiptLineId()).setEntryRole(role).setCounterpartyBalanceId(counterparty)
                .setBalanceId(b.getBalanceId()).setAggregateVersion(version).setBaseUomCode(c.uom())
                .setBeforeOnHandQuantity(before).setDeltaOnHandQuantity(delta).setAfterOnHandQuantity(after)
                .setBeforeReservedQuantity(scaled(b.getReservedQuantity())).setDeltaReservedQuantity(ZERO)
                .setAfterReservedQuantity(scaled(b.getReservedQuantity()))
                .setBeforeInTransitQuantity(scaled(b.getInTransitQuantity())).setDeltaInTransitQuantity(ZERO)
                .setAfterInTransitQuantity(scaled(b.getInTransitQuantity())).setCreatedAt(now);
    }

    private static Dimension sourceDimension(Command c) {
        if (c.operation() == InventoryProcurementReceiptOperation.RECEIVE_PENDING_QUALITY) return null;
        if (c.operation() != InventoryProcurementReceiptOperation.RETURN_TO_SUPPLIER)
            return dimension(InventoryProcurementReceiptDisposition.PENDING);
        return dimension(c.disposition());
    }
    private static Dimension targetDimension(Command c) {
        if (c.operation() == InventoryProcurementReceiptOperation.RETURN_TO_SUPPLIER) return null;
        return dimension(c.disposition());
    }
    private static Dimension dimension(InventoryProcurementReceiptDisposition d) {
        return switch (d) {
            case PENDING -> new Dimension("QA_HOLD","PENDING_QC");
            case ACCEPTED -> new Dimension("SELLABLE","QUALIFIED");
            case REJECTED -> new Dimension("NON_SELLABLE","REJECTED");
            case QUARANTINED -> new Dimension("NON_SELLABLE","QUARANTINED");
        };
    }

    private static InventoryProcurementReceiptResult replay(InventoryProcurementReceiptOperationDO o, Command c) {
        return InventoryProcurementReceiptResult.builder().operationId(o.getOperationId())
                .ledgerTransactionId(o.getLedgerTransactionId()).receiptId(o.getReceiptId()).receiptLineId(o.getReceiptLineId())
                .sourceBalanceId(o.getSourceBalanceId()).sourceAggregateVersion(o.getSourceAggregateVersion())
                .targetBalanceId(o.getTargetBalanceId()).targetAggregateVersion(o.getTargetAggregateVersion())
                .unitCostAmountMinor(c.unitCost()).movementCostAmountMinor(c.movementCost())
                .currencyCode(c.currency()).valuationPolicyId(c.valuationPolicy())
                .valuationPolicyVersion(c.valuationPolicyVersion()).valuationPolicyHash(c.valuationPolicyHash())
                .receivedQuantity(o.getReceivedQuantity()).pendingQuantity(o.getPendingQuantity())
                .acceptedQuantity(o.getAcceptedQuantity()).rejectedQuantity(o.getRejectedQuantity())
                .quarantinedQuantity(o.getQuarantinedQuantity()).returnedQuantity(o.getReturnedQuantity())
                .duplicate(true).build();
    }

    private static void requireIdentity(InventoryProcurementReceiptDO r, Command c) {
        require(Objects.equals(r.getReceiptId(),c.receiptId()) && Objects.equals(r.getPurchaseOrderId(),c.poId())
                && Objects.equals(r.getPurchaseOrderItemId(),c.poItemId())
                && Objects.equals(r.getPurchaseOrderScheduleId(),c.scheduleId())
                && Objects.equals(r.getSupplierId(),c.supplierId()) && Objects.equals(r.getOwnerId(),c.ownerId())
                && Objects.equals(r.getCanonicalSkuId(),c.skuId()) && Objects.equals(r.getWarehouseId(),c.warehouseId())
                && Objects.equals(r.getLocationId(),c.locationId()) && Objects.equals(r.getLotId(),c.lotId())
                && Objects.equals(r.getBaseUomCode(),c.uom()) && Objects.equals(r.getValuationPolicy(),c.valuationPolicy())
                && Objects.equals(r.getValuationPolicyVersion(),c.valuationPolicyVersion())
                && Objects.equals(r.getValuationPolicyHash(),c.valuationPolicyHash())
                && Objects.equals(r.getUnitCostAmountMinor(),c.unitCost()) && Objects.equals(r.getCurrencyCode(),c.currency()),
                "receipt line conflicts with different inventory or valuation identity");
    }

    static String fingerprint(Long tenantId, Command c) {
        return DigestUtil.sha256Hex(String.join("\u001f",tenantId.toString(),c.operation().name(),c.disposition().name(),
                c.idempotencyKey(),Objects.toString(c.sourceEventId(),""),c.receiptId(),c.receiptLineId(),c.poId(),
                c.poItemId(),c.scheduleId(),c.supplierId(),c.ownerType(),c.ownerId(),c.skuId(),c.warehouseId(),
                c.locationId(),Objects.toString(c.lotId(),""),c.uom(),decimal(c.quantity()),
                Objects.toString(c.qualityDecisionId(),""),c.decisionVersion().toString(),Objects.toString(c.evidence(),""),
                c.valuationPolicy(),c.valuationPolicyVersion(),c.valuationPolicyHash(),
                c.unitCost().toString(),c.movementCost().toString(),c.currency(),c.businessNo(),
                c.correlationId(),Objects.toString(c.causationId(),""),c.occurredAt().toString()));
    }

    private static Command normalize(InventoryProcurementReceiptCommand x) {
        require(x!=null && x.getOperation()!=null && x.getDisposition()!=null,"operation and disposition are required");
        InventoryProcurementReceiptDisposition expected = switch(x.getOperation()) {
            case RECEIVE_PENDING_QUALITY -> InventoryProcurementReceiptDisposition.PENDING;
            case ACCEPT_QUALITY -> InventoryProcurementReceiptDisposition.ACCEPTED;
            case REJECT_QUALITY -> InventoryProcurementReceiptDisposition.REJECTED;
            case QUARANTINE_QUALITY -> InventoryProcurementReceiptDisposition.QUARANTINED;
            case RETURN_TO_SUPPLIER -> x.getDisposition();
        };
        require(expected==x.getDisposition(),"operation does not match quality disposition");
        require(x.getOperation()!=InventoryProcurementReceiptOperation.RETURN_TO_SUPPLIER
                || x.getDisposition()!=InventoryProcurementReceiptDisposition.PENDING,"pending quality cannot be returned");
        text(x.getIdempotencyKey(),"idempotencyKey",128);
        String sourceEvent=x.getSourceEventId()==null?null:uuid(x.getSourceEventId(),"sourceEventId");
        String ownerType=upper(x.getOwnerType());
        require(Set.of("MERCHANT","PLATFORM","MEMBER").contains(ownerType),"unsupported ownerType");
        BigDecimal quantity=scaled(x.getQuantity()); require(quantity.signum()>0,"quantity must be positive");
        long decision=x.getOperation()==InventoryProcurementReceiptOperation.RECEIVE_PENDING_QUALITY?0:
                Objects.requireNonNull(x.getDecisionVersion(),"decisionVersion is required");
        require(decision >= (x.getOperation()==InventoryProcurementReceiptOperation.RECEIVE_PENDING_QUALITY?0:1),
                "decisionVersion must be positive for quality effects");
        String decisionId=null,evidence=null;
        if (decision>0) { decisionId=uuid(x.getQualityDecisionId(),"qualityDecisionId"); evidence=trim(x.getQualityEvidenceRef(),"qualityEvidenceRef",256); }
        else require(x.getQualityDecisionId()==null && x.getQualityEvidenceRef()==null,"receive cannot fabricate quality decision evidence");
        require(x.getUnitCostAmountMinor()!=null && x.getUnitCostAmountMinor()>=0,"unitCostAmountMinor is required");
        require(x.getMovementCostAmountMinor()!=null && x.getMovementCostAmountMinor()>=0,"movementCostAmountMinor is required");
        BigDecimal expectedCost=quantity.multiply(BigDecimal.valueOf(x.getUnitCostAmountMinor()));
        require(expectedCost.stripTrailingZeros().scale()<=0 && expectedCost.longValueExact()==x.getMovementCostAmountMinor(),
                "movement cost must equal quantity multiplied by unit cost");
        String currency=upper(x.getCurrencyCode()); require(currency!=null&&currency.matches("[A-Z]{3}"),"currencyCode must be ISO-4217");
        require(x.getOccurredAt()!=null,"occurredAt is required");
        return new Command(x.getOperation(),x.getDisposition(),x.getIdempotencyKey().trim(),sourceEvent,
                uuid(x.getReceiptId(),"receiptId"),uuid(x.getReceiptLineId(),"receiptLineId"),
                uuid(x.getPurchaseOrderId(),"purchaseOrderId"),uuid(x.getPurchaseOrderItemId(),"purchaseOrderItemId"),
                uuid(x.getPurchaseOrderScheduleId(),"purchaseOrderScheduleId"),uuid(x.getSupplierId(),"supplierId"),
                ownerType,uuid(x.getOwnerId(),"ownerId"),uuid(x.getCanonicalSkuId(),"canonicalSkuId"),
                uuid(x.getWarehouseId(),"warehouseId"),uuid(x.getLocationId(),"locationId"),
                x.getLotId()==null?null:uuid(x.getLotId(),"lotId"),upperRequired(x.getBaseUomCode(),"baseUomCode",32),quantity,decisionId,decision,
                evidence,policyId(x.getValuationPolicy()),policyVersion(x.getValuationPolicyVersion()),
                policyHash(x.getValuationPolicyHash()),x.getUnitCostAmountMinor(),
                x.getMovementCostAmountMinor(),currency,trim(x.getBusinessNo(),"businessNo",128),
                uuid(x.getCorrelationId(),"correlationId"),x.getCausationId()==null?null:uuid(x.getCausationId(),"causationId"),x.getOccurredAt());
    }

    private static String uuid(String v,String f){ text(v,f,36); try{return UUID.fromString(v).toString();}catch(Exception e){throw new IllegalArgumentException(f+" must be UUID",e);} }
    private static String upper(String v){return v==null?null:v.trim().toUpperCase(Locale.ROOT);}
    private static String upperRequired(String v,String f,int n){return upper(trim(v,f,n));}
    private static String policyId(String v){String p=upperRequired(v,"valuationPolicy",64);require(p.matches("[A-Z][A-Z0-9_]{1,63}"),"valuationPolicy must be a canonical policy id");return p;}
    private static String policyVersion(String v){String p=upperRequired(v,"valuationPolicyVersion",64);require(p.matches("[A-Z0-9][A-Z0-9._-]{0,63}"),"valuationPolicyVersion is invalid");return p;}
    private static String policyHash(String v){String p=trim(v,"valuationPolicyHash",64).toLowerCase(Locale.ROOT);require(p.matches("[0-9a-f]{64}"),"valuationPolicyHash must be SHA-256");return p;}
    private static String trim(String v,String f,int n){text(v,f,n);return v.trim();}
    private static void text(String v,String f,int n){require(v!=null&&!v.isBlank()&&v.length()<=n,f+" is required or too long");}
    private static BigDecimal scaled(BigDecimal v){require(v!=null,"quantity is required");try{return v.setScale(6,RoundingMode.UNNECESSARY);}catch(Exception e){throw new IllegalArgumentException("quantity supports 6 decimals",e);}}
    private static String decimal(BigDecimal v){return scaled(v).toPlainString();}
    private static void require(boolean v,String m){if(!v)throw new IllegalArgumentException(m);}

    record Command(InventoryProcurementReceiptOperation operation,InventoryProcurementReceiptDisposition disposition,
                   String idempotencyKey,String sourceEventId,String receiptId,String receiptLineId,String poId,
                   String poItemId,String scheduleId,String supplierId,String ownerType,String ownerId,String skuId,
                   String warehouseId,String locationId,String lotId,String uom,BigDecimal quantity,String qualityDecisionId,
                   Long decisionVersion,String evidence,String valuationPolicy,String valuationPolicyVersion,
                   String valuationPolicyHash,Long unitCost,Long movementCost,String currency,
                   String businessNo,String correlationId,String causationId,Instant occurredAt){}
    record Dimension(String stock,String quality){}
    record Locked(InventoryV3BalanceDO source,InventoryV3BalanceDO target){}
    record Quantities(BigDecimal received,BigDecimal pending,BigDecimal accepted,BigDecimal rejected,
                      BigDecimal quarantined,BigDecimal returned){
        static Quantities from(InventoryProcurementReceiptDO r){return new Quantities(scaled(r.getReceivedQuantity()),scaled(r.getPendingQuantity()),scaled(r.getAcceptedQuantity()),scaled(r.getRejectedQuantity()),scaled(r.getQuarantinedQuantity()),scaled(r.getReturnedQuantity()));}
    }

    private void validateOwner(String ownerType,String ownerId){if("MERCHANT".equals(ownerType))merchantApi.requireActiveMerchant(ownerId);}
}
