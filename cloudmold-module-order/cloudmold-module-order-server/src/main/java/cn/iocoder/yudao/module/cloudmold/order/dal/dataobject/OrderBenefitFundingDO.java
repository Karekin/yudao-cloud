package cn.iocoder.yudao.module.cloudmold.order.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_order_benefit_funding")
public class OrderBenefitFundingDO {
    @TableId(type = IdType.INPUT)
    private String benefitFundingId;
    private Long tenantId;
    private String orderId;
    private String benefitApplicationId;
    private String benefitAllocationId;
    private String fundingKey;
    private String funderType;
    private String funderId;
    private Long amountMinor;
    private String currencyCode;
    private LocalDateTime createdAt;
}
