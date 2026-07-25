package cn.iocoder.yudao.module.cloudmold.inventory.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryCheckoutReservationResult {
    private String reservationId;
    private String allocationId;
    private String ownerId;
    private String warehouseId;
    private String locationId;
    private String lotId;
    private Long aggregateVersion;
    private Boolean duplicate;
}
