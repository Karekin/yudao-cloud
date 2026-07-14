package cn.iocoder.yudao.module.cloudmold.fulfillment.dal.dataobject.returning;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("cloudmold_return_fulfillment_item")
public class ReturnFulfillmentItemDO {
    @TableId(type = IdType.INPUT)
    private String returnFulfillmentItemId;
    private Long tenantId;
    private String returnFulfillmentId;
    private String afterSaleItemId;
    private String orderItemId;
    private String canonicalSkuId;
    private BigDecimal quantity;
    private LocalDateTime createdAt;
}
