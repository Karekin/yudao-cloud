package cn.iocoder.yudao.module.cloudmold.merchant.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@TableName("cloudmold_merchant_monthly_scorecard")
@Data
@Accessors(chain = true)
public class MerchantMonthlyScorecardDO {
    @TableId(type = IdType.INPUT)
    private String scorecardId;
    private Long tenantId;
    private String merchantId;
    private String shopId;
    private String scorecardMonth;
    private String scorecardStatus;
    private String transitionRecommendation;
    private String thresholdsConfigRef;
    private String itemsJson;
    private Integer itemCount;
    private Integer redLineCount;
    private Integer remediationFailureCount;
    private String evidenceRef;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
