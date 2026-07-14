package cn.iocoder.yudao.module.cloudmold.fulfillment.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_shipment")
public class ShipmentDO {
    @TableId(type = IdType.INPUT)
    private String shipmentId;
    private Long tenantId;
    private String fulfillmentId;
    private String carrierCode;
    private String waybillNo;
    private String status;
    private LocalDateTime shippedAt;
    private LocalDateTime inTransitAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
