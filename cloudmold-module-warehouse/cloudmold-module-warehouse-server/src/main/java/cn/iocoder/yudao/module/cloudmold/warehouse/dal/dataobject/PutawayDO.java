package cn.iocoder.yudao.module.cloudmold.warehouse.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_putaway")
public class PutawayDO {
    private String putawayId;
    private Long tenantId;
    private String receiptId;
    private String warehouseId;
    private String targetLocationId;
    private String status;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
