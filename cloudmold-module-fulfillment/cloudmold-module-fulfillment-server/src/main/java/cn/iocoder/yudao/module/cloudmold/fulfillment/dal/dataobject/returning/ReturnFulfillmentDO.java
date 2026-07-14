package cn.iocoder.yudao.module.cloudmold.fulfillment.dal.dataobject.returning;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_return_fulfillment")
public class ReturnFulfillmentDO {
    @TableId(type = IdType.INPUT)
    private String returnFulfillmentId;
    private Long tenantId;
    private String returnFulfillmentNo;
    private String runId;
    private String afterSaleId;
    private String orderId;
    private String ownerId;
    private String warehouseId;
    private String uomCode;
    private String status;
    private Long version;
    private String correlationId;
    private String causationId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
