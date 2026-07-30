package cn.iocoder.yudao.module.cloudmold.catalog.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@TableName("cloudmold_assortment_candidate")
@Data
@Accessors(chain = true)
public class AssortmentCandidateDO {
    @TableId(type = IdType.INPUT)
    private String candidateId;
    private Long tenantId;
    private String waveId;
    private String candidateCode;
    private String productConcept;
    private String sourceSignalType;
    private String sourceSignalRef;
    private String priceBandCode;
    private Long targetPriceMinor;
    private Long expectedUnitCostMinor;
    private Integer expectedGrossMarginBps;
    private Integer trendScore;
    private Integer demandScore;
    private Integer audienceFitScore;
    private Integer supplyRiskScore;
    private Integer predictedReturnRateBps;
    private Integer weightedScore;
    private String evidenceSha256;
    private String rationale;
    private String status;
    private Long version;
    private LocalDateTime evaluatedAt;
    private LocalDateTime selectedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
