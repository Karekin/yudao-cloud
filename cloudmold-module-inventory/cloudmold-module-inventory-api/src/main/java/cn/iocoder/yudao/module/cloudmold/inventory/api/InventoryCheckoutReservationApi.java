package cn.iocoder.yudao.module.cloudmold.inventory.api;

public interface InventoryCheckoutReservationApi {
    InventoryCheckoutReservationResult reserve(InventoryCheckoutReservationCommand command);
}
