package cn.iocoder.yudao.module.cloudmold.fulfillment.dal.dataobject.returning;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("cloudmold_return_inspection")
public class ReturnInspectionDO {
    @TableId(type = IdType.INPUT)
    private String inspectionId;
    private Long tenantId;
    private String returnFulfillmentId;
    private String returnShipmentId;
    private String returnFulfillmentItemId;
    private String returnShipmentItemId;
    private String warehouseId;
    private BigDecimal receivedQuantity;
    private BigDecimal acceptedQuantity;
    private String qualityStatus;
    private String inspectorId;
    private LocalDateTime decidedAt;
    private LocalDateTime createdAt;
}
