package cn.iocoder.yudao.module.cloudmold.order.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("cloudmold_order_return_settlement")
public class OrderReturnSettlementDO {
    @TableId
    private String orderId;
    private Long tenantId;
    private String status;
    private Integer totalItemCount;
    private Integer returnedItemCount;
    private BigDecimal returnedQuantity;
    private Long refundedNetAmountMinor;
    private Long reversedBenefitAmountMinor;
    private Long version;
    private String lastAfterSaleId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
