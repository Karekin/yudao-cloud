package cn.iocoder.yudao.module.cloudmold.order.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_order_benefit_application")
public class OrderBenefitApplicationDO {
    @TableId(type = IdType.INPUT)
    private String benefitApplicationId;
    private Long tenantId;
    private String orderId;
    private String applicationKey;
    private String benefitType;
    private String benefitSourceType;
    private String benefitSourceId;
    private Long benefitSourceVersion;
    private String entitlementId;
    private Long amountMinor;
    private String currencyCode;
    private String calculationDigest;
    private Long operationId;
    private Long version;
    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;
}
