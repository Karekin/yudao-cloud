package cn.iocoder.yudao.module.cloudmold.inventory.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryReservationQueryApi;
import cn.iocoder.yudao.module.cloudmold.inventory.api.InventoryReservationView;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.InventoryCancellationReservationDO;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.InventoryReservationDO;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject.InventoryV3ReservationDO;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql.InventoryReservationMapper;
import cn.iocoder.yudao.module.cloudmold.inventory.dal.mysql.InventoryV3ReservationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class InventoryReservationQueryServiceImpl implements InventoryReservationQueryApi {

    private static final int RESERVATION_ACTIVE = 10;
    private static final int RESERVATION_COMMITTED = 20;
    private static final int RESERVATION_RELEASED = 30;
    private final InventoryReservationMapper reservationMapper;
    private final InventoryV3ReservationMapper reservationV3Mapper;

    @Override
    public InventoryReservationView requireForCancellation(String reservationId, String businessType,
                                                            String businessId, String businessItemId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        requireText(reservationId, "reservationId");
        InventoryCancellationReservationDO reservation = reservationMapper.selectCancellationView(tenantId,
                reservationId);
        require(reservation != null, "inventory reservation does not exist");
        require(reservation.getStatus() == RESERVATION_ACTIVE || reservation.getStatus() == RESERVATION_RELEASED,
                "inventory reservation is not cancellable");
        requireIdentity(reservation.getBusinessType(), reservation.getBusinessId(), reservation.getBusinessItemId(),
                businessType, businessId, businessItemId);
        return InventoryReservationView.builder().reservationId(reservation.getReservationId())
                .businessType(reservation.getBusinessType()).businessId(reservation.getBusinessId())
                .businessItemId(reservation.getBusinessItemId()).quantity(reservation.getQuantity())
                .status(reservation.getStatus() == RESERVATION_ACTIVE ? "ACTIVE" : "RELEASED")
                .version(reservation.getVersion()).ownerId(reservation.getOwnerId())
                .canonicalSkuId(reservation.getCanonicalSkuId()).warehouseId(reservation.getWarehouseId())
                .stockStatus(reservation.getStockStatus()).qualityStatus(reservation.getQualityStatus())
                .uomCode(reservation.getUomCode()).build();
    }

    @Override
    public InventoryReservationView requireReleased(String reservationId, String businessType,
                                                     String businessId, String businessItemId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        requireText(reservationId, "reservationId");
        InventoryReservationDO reservation = reservationMapper.selectHint(tenantId, reservationId);
        require(reservation != null, "inventory reservation does not exist");
        require(reservation.getStatus() == RESERVATION_RELEASED, "inventory reservation is not released");
        requireIdentity(reservation.getBusinessType(), reservation.getBusinessId(), reservation.getBusinessItemId(),
                businessType, businessId, businessItemId);
        return InventoryReservationView.builder().reservationId(reservation.getReservationId())
                .businessType(reservation.getBusinessType()).businessId(reservation.getBusinessId())
                .businessItemId(reservation.getBusinessItemId()).quantity(reservation.getQuantity())
                .status("RELEASED").version(reservation.getVersion()).build();
    }

    @Override
    public InventoryReservationView requireCommitted(String reservationId, String businessId,
                                                      String businessItemId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        requireText(reservationId, "reservationId");
        InventoryReservationDO reservation = reservationMapper.selectHint(tenantId, reservationId);
        if (reservation != null) {
            require(reservation.getStatus() == RESERVATION_COMMITTED,
                    "inventory reservation is not committed for shipment");
            requireShipmentIdentity(reservation.getBusinessType(), reservation.getBusinessId(),
                    reservation.getBusinessItemId(), businessId, businessItemId);
            return InventoryReservationView.builder().reservationId(reservation.getReservationId())
                    .businessType(reservation.getBusinessType()).businessId(reservation.getBusinessId())
                    .businessItemId(reservation.getBusinessItemId()).quantity(reservation.getQuantity())
                    .status("COMMITTED").version(reservation.getVersion()).build();
        }
        InventoryV3ReservationDO reservationV3 = reservationV3Mapper.selectHint(tenantId, reservationId);
        require(reservationV3 != null, "inventory reservation does not exist");
        require(reservationV3.getStatus() == RESERVATION_COMMITTED,
                "inventory reservation is not committed for shipment");
        requireShipmentIdentity(reservationV3.getBusinessType(), reservationV3.getBusinessId(),
                reservationV3.getBusinessItemId(), businessId, businessItemId);
        return InventoryReservationView.builder().reservationId(reservationV3.getReservationId())
                .businessType(reservationV3.getBusinessType()).businessId(reservationV3.getBusinessId())
                .businessItemId(reservationV3.getBusinessItemId()).quantity(reservationV3.getQuantity())
                .status("COMMITTED").version(reservationV3.getVersion()).build();
    }

    private static void requireIdentity(String actualType, String actualId, String actualItemId,
                                        String expectedType, String expectedId, String expectedItemId) {
        require(Objects.equals(actualType, expectedType) && Objects.equals(actualId, expectedId)
                        && Objects.equals(actualItemId, expectedItemId),
                "inventory reservation does not belong to cancellation item");
    }

    private static void requireShipmentIdentity(String actualType, String actualId, String actualItemId,
                                                String expectedId, String expectedItemId) {
        require("TRADE_ORDER".equals(actualType)
                        && Objects.equals(actualId, expectedId) && Objects.equals(actualItemId, expectedItemId),
                "inventory reservation does not belong to fulfillment item");
    }

    private static void requireText(String value, String field) {
        require(value != null && !value.isBlank(), field + " is required");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
