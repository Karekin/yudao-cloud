package cn.iocoder.yudao.module.cloudmold.inventory.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryReservationView {
    private String reservationId;
    private String businessType;
    private String businessId;
    private String businessItemId;
    private BigDecimal quantity;
    private String status;
    private Long version;
    private String ownerId;
    private String canonicalSkuId;
    private String warehouseId;
    private String stockStatus;
    private String qualityStatus;
    private String uomCode;
}
