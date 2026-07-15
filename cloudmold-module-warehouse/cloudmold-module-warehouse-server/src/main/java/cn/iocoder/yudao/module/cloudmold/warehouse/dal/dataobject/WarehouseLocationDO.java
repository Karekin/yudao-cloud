package cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("cloudmold_warehouse_location")
public class WarehouseLocationDO {
    @TableId(type = IdType.INPUT)
    private String locationId;
    private Long tenantId;
    private String warehouseId;
    private String zoneId;
    private String locationCode;
    private String name;
    private String locationType;
    private String aisleCode;
    private String rackCode;
    private String bayCode;
    private String levelCode;
    private Boolean allowItemMixing;
    private Boolean allowLotMixing;
    private BigDecimal capacityQuantity;
    private String capacityUomCode;
    private String status;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
