package cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("cloudmold_warehouse_source_mapping")
public class WarehouseSourceMappingDO {
    @TableId(type = IdType.INPUT)
    private String mappingId;
    private Long tenantId;
    private String sourceSystem;
    private String sourceType;
    private String sourceId;
    private String canonicalType;
    private String canonicalId;
    private String warehouseId;
    private String zoneId;
    private String locationId;
    private LocalDateTime validFrom;
    private LocalDateTime validTo;
    private String verificationRef;
    private String status;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
