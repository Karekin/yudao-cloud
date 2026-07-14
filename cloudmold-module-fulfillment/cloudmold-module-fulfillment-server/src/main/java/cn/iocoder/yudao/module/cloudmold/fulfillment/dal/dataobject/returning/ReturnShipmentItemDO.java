package cn.iocoder.yudao.module.cloudmold.fulfillment.dal.dataobject.returning;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("cloudmold_return_shipment_item")
public class ReturnShipmentItemDO {
    @TableId(type = IdType.INPUT)
    private String returnShipmentItemId;
    private Long tenantId;
    private String returnShipmentId;
    private String returnFulfillmentItemId;
    private BigDecimal quantity;
    private LocalDateTime createdAt;
}
