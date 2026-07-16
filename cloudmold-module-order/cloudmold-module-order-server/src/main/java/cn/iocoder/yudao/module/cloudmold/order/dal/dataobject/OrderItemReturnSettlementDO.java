package cn.iocoder.yudao.module.cloudmold.order.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("cloudmold_order_item_return_settlement")
public class OrderItemReturnSettlementDO {
    @TableId
    private String orderItemId;
    private Long tenantId;
    private String orderId;
    private BigDecimal orderedQuantity;
    private BigDecimal returnedQuantity;
    private Long returnedGrossAmountMinor;
    private Long reversedBenefitAmountMinor;
    private Long refundedNetAmountMinor;
    private Long version;
    private String lastAfterSaleId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
