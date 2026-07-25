package cn.iocoder.yudao.module.cloudmold.inventory.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.inventory.api.*;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.InventoryV3BalanceDO;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql.InventoryV3BalanceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InventoryCheckoutReservationService implements InventoryCheckoutReservationApi {

    private final InventoryV3BalanceMapper balanceMapper;
    private final InventoryV3CommandApi commandApi;

    @Override
    public InventoryCheckoutReservationResult reserve(InventoryCheckoutReservationCommand command) {
        require(command != null, "checkout reservation command is required");
        require(command.getQuantity() != null && command.getQuantity().signum() > 0,
                "quantity must be positive");
        require(command.getOccurredAt() != null, "occurredAt is required");
        InventoryV3BalanceDO balance = balanceMapper.selectReservationCandidate(
                TenantContextHolder.getRequiredTenantId(), command.getCanonicalSkuId(), command.getQuantity(),
                command.getOccurredAt().atZone(ZoneOffset.UTC).toLocalDate());
        require(balance != null, "canonical SKU has no allocatable inventory");
        InventoryV3CommandResult reserved = commandApi.execute(InventoryV3Command.builder()
                .operation(InventoryV3Operation.RESERVE)
                .idempotencyKey(command.getIdempotencyKey())
                .sourceEventId(sourceEventId(command.getIdempotencyKey()))
                .ownerType(balance.getOwnerType()).ownerId(balance.getOwnerId())
                .canonicalSkuId(balance.getCanonicalSkuId()).warehouseId(balance.getWarehouseId())
                .locationId(balance.getLocationId()).lotId(balance.getLotId())
                .stockStatus(balance.getStockStatus()).qualityStatus(balance.getQualityStatus())
                .baseUomCode(balance.getBaseUomCode()).quantity(command.getQuantity())
                .businessType("TRADE_ORDER").businessId(command.getOrderId())
                .businessItemId(command.getOrderItemId()).businessNo(command.getOrderNo())
                .correlationId(command.getCorrelationId()).causationId(command.getCausationId())
                .occurredAt(command.getOccurredAt()).build());
        return InventoryCheckoutReservationResult.builder()
                .reservationId(reserved.getReservationId()).allocationId(reserved.getAllocationId())
                .ownerId(balance.getOwnerId()).warehouseId(balance.getWarehouseId())
                .locationId(balance.getLocationId()).lotId(balance.getLotId())
                .aggregateVersion(reserved.getAggregateVersion()).duplicate(reserved.isDuplicate()).build();
    }

    static String sourceEventId(String idempotencyKey) {
        require(idempotencyKey != null && !idempotencyKey.isBlank(), "idempotencyKey is required");
        return UUID.nameUUIDFromBytes(
                ("checkout-reservation:" + idempotencyKey).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
