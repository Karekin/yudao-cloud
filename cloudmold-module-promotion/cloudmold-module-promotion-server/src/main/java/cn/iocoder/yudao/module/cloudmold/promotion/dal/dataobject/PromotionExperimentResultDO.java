package cn.iocoder.yudao.module.cloudmold.promotion.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_promotion_experiment_result")
public class PromotionExperimentResultDO {
    @TableId(type = IdType.INPUT)
    private String experimentId;
    private Long tenantId;
    private String experimentCode;
    private String campaignId;
    private String merchantId;
    private LocalDateTime measuredFrom;
    private LocalDateTime measuredTo;
    private Long baselineContributionProfitMinor;
    private Long treatmentContributionProfitMinor;
    private Long incrementalContributionProfitMinor;
    private Long promotionCostMinor;
    private Integer eligiblePopulationCount;
    private Integer treatmentPopulationCount;
    private Integer controlPopulationCount;
    private String currencyCode;
    private String methodologyRef;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
