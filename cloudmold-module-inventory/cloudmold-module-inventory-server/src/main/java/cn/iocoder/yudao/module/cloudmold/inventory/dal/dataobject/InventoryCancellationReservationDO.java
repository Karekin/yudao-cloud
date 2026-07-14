package cn.iocoder.yudao.module.cloudmold.inventory.dal.dataobject;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class InventoryCancellationReservationDO {
    private String reservationId;
    private String businessType;
    private String businessId;
    private String businessItemId;
    private BigDecimal quantity;
    private Integer status;
    private Long version;
    private String ownerId;
    private String canonicalSkuId;
    private String warehouseId;
    private String stockStatus;
    private String qualityStatus;
    private String uomCode;
}
