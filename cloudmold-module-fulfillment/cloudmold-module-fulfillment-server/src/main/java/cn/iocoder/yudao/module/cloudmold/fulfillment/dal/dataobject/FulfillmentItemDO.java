package cn.iocoder.yudao.module.cloudmold.fulfillment.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("cloudmold_fulfillment_item")
public class FulfillmentItemDO {
    @TableId(type = IdType.INPUT)
    private String fulfillmentItemId;
    private Long tenantId;
    private String fulfillmentId;
    private String orderItemId;
    private String canonicalSkuId;
    private BigDecimal quantity;
    private String reservationId;
    private Long variableFulfillmentCostMinor;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
