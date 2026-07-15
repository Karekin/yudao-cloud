package cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("cloudmold_warehouse")
public class WarehouseDO {
    @TableId(type = IdType.INPUT)
    private String warehouseId;
    private Long tenantId;
    private String warehouseCode;
    private String name;
    private String warehouseType;
    private String timezone;
    private String status;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
