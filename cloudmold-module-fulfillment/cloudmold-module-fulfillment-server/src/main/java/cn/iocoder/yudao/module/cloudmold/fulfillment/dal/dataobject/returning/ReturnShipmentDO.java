package cn.iocoder.yudao.module.cloudmold.fulfillment.dal.dataobject.returning;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_return_shipment")
public class ReturnShipmentDO {
    @TableId(type = IdType.INPUT)
    private String returnShipmentId;
    private Long tenantId;
    private String returnFulfillmentId;
    private String carrierCode;
    private String waybillNo;
    private String status;
    private LocalDateTime handedOverAt;
    private LocalDateTime inTransitAt;
    private LocalDateTime receivedAt;
    private String receiverId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
