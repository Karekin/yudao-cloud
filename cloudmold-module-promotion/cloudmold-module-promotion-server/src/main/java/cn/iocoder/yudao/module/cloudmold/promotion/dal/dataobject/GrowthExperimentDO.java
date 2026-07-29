package cn.iocoder.yudao.module.cloudmold.promotion.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_promotion_growth_experiment")
public class GrowthExperimentDO {
    @TableId(type = IdType.INPUT)
    private String experimentId;
    private Long tenantId;
    private String experimentCode;
    private String campaignId;
    private String name;
    private String hypothesis;
    private String primaryMetricCode;
    private Integer minimumSampleSizePerVariant;
    private String status;
    private LocalDateTime startsAt;
    private LocalDateTime endsAt;
    private LocalDateTime startedAt;
    private LocalDateTime concludedAt;
    private String decision;
    private Integer confidenceBasisPoints;
    private String guardrailStatus;
    private String conclusionEvidenceRef;
    private String conclusionReason;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
