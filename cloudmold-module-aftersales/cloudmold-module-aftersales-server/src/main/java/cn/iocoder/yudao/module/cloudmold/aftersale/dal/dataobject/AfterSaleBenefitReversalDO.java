package cn.iocoder.yudao.module.cloudmold.aftersale.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_after_sale_benefit_reversal")
public class AfterSaleBenefitReversalDO {
    @TableId(type = IdType.INPUT)
    private String benefitReversalId;
    private Long tenantId;
    private String reversalBatchId;
    private String afterSaleId;
    private String afterSaleItemId;
    private String orderId;
    private String orderItemId;
    private String benefitApplicationId;
    private String benefitAllocationId;
    private String benefitType;
    private String benefitSourceType;
    private String benefitSourceId;
    private Long benefitSourceVersion;
    private String entitlementId;
    private Long amountMinor;
    private String currencyCode;
    private String entitlementEffectStatus;
    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;
}
