package cn.iocoder.yudao.module.cloudmold.fulfillment.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("cloudmold_shipment_item")
public class ShipmentItemDO {
    @TableId(type = IdType.INPUT)
    private String shipmentItemId;
    private Long tenantId;
    private String shipmentId;
    private String fulfillmentItemId;
    private BigDecimal quantity;
    private LocalDateTime createdAt;
}
