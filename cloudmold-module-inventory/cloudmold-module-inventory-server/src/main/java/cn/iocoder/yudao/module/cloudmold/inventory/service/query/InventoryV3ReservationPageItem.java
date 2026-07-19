package cn.iocoder.yudao.module.cloudmold.inventory.service.query;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - CloudMold 规范 Inventory v3 预占分页项")
@Data
public class InventoryV3ReservationPageItem {

    private String reservationId;
    private String allocationId;
    private String businessType;
    private String businessId;
    private String businessItemId;
    private Integer status;
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal quantity;
    private Integer allocationStatus;
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal allocationQuantity;
    private Long allocationVersion;
    private String canonicalSkuId;
    private String skuCode;
    private String warehouseId;
    private String warehouseCode;
    private String locationId;
    private String locationCode;
    private String lotId;
    private String lotCode;
    private String ownerType;
    private String ownerId;
    private String stockStatus;
    private String qualityStatus;
    private String baseUomCode;
    private Long createdOperationId;
    private Long closedOperationId;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
