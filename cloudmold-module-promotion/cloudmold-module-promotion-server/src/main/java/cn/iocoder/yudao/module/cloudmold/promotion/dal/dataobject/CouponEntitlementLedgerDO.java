package cn.iocoder.yudao.module.cloudmold.promotion.dal.dataobject;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_promotion_coupon_entitlement_ledger")
public class CouponEntitlementLedgerDO {
    @TableId(type = IdType.INPUT)
    private String ledgerEntryId;
    private Long tenantId;
    private String entitlementId;
    private Long entitlementVersion;
    private Long operationId;
    private String operationType;
    private String previousStatus;
    private String currentStatus;
    private String orderRef;
    private String reason;
    private Long faceAmountMinor;
    private Long thresholdMinor;
    private String currencyCode;
    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;
}
