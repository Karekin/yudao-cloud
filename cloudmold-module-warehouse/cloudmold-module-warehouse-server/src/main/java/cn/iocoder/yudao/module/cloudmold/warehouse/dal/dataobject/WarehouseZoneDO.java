package cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("cloudmold_warehouse_zone")
public class WarehouseZoneDO {
    @TableId(type = IdType.INPUT)
    private String zoneId;
    private Long tenantId;
    private String warehouseId;
    private String zoneCode;
    private String name;
    private String zoneType;
    private String status;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
