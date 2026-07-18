package cn.iocoder.yudao.module.cloudmold.promotion.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_promotion_advertising_ledger")
public class AdvertisingLedgerEntryDO {
    @TableId(type = IdType.INPUT)
    private String ledgerEntryId;
    private Long tenantId;
    private String ledgerEntryCode;
    private String campaignId;
    private String placementId;
    private String merchantId;
    private String entryType;
    private String chargeModel;
    private String revenueType;
    private String sourceInteractionId;
    private String orderRef;
    private Long amountMinor;
    private String currencyCode;
    private LocalDateTime occurredAt;
    private LocalDateTime createdAt;
}
