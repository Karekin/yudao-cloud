package cn.iocoder.yudao.module.cloudmold.order.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_order_benefit_allocation")
public class OrderBenefitAllocationDO {
    @TableId(type = IdType.INPUT)
    private String benefitAllocationId;
    private Long tenantId;
    private String orderId;
    private String benefitApplicationId;
    private String allocationKey;
    private String orderItemId;
    private String lineKey;
    private Long amountMinor;
    private String currencyCode;
    private LocalDateTime createdAt;
}
