package cn.iocoder.yudao.module.cloudmold.inventory.api;

public interface InventoryReservationQueryApi {
    InventoryReservationView requireForCancellation(String reservationId, String businessType,
                                                     String businessId, String businessItemId);
    InventoryReservationView requireReleased(String reservationId, String businessType,
                                              String businessId, String businessItemId);

    InventoryReservationView requireCommitted(String reservationId, String businessId,
                                               String businessItemId);
}
