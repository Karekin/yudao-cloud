package cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("cloudmold_warehouse_operator_assignment")
public class WarehouseOperatorAssignmentDO {
    @TableId(type = IdType.INPUT)
    private String assignmentId;
    private Long tenantId;
    private String warehouseId;
    private String zoneId;
    private String locationId;
    private String principalId;
    private String roleCode;
    private String shiftCode;
    private LocalDateTime validFrom;
    private LocalDateTime validTo;
    private String status;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
