package cn.iocoder.yudao.module.cloudmold.aftersale.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_after_sale_benefit_funding_reversal")
public class AfterSaleBenefitFundingReversalDO {
    @TableId(type = IdType.INPUT)
    private String fundingReversalId;
    private Long tenantId;
    private String benefitReversalId;
    private String reversalBatchId;
    private String afterSaleId;
    private String benefitFundingId;
    private String funderType;
    private String funderId;
    private Long amountMinor;
    private String currencyCode;
    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;
}
