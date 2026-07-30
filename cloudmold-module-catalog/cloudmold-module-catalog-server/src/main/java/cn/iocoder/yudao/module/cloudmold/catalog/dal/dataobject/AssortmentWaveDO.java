package cn.iocoder.yudao.module.cloudmold.catalog.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("cloudmold_assortment_wave")
@Data
@Accessors(chain = true)
public class AssortmentWaveDO {
    @TableId(type = IdType.INPUT)
    private String waveId;
    private Long tenantId;
    private String waveCode;
    private String runId;
    private Integer planningYear;
    private String seasonCode;
    private String categoryCode;
    private String trendBrief;
    private String targetAudience;
    private Integer targetStyleCount;
    private Long targetPriceFloorMinor;
    private Long targetPriceCeilingMinor;
    private Integer targetGrossMarginBps;
    private Integer maxReturnRateBps;
    private LocalDate launchStartDate;
    private LocalDate launchEndDate;
    private String currencyCode;
    private Integer candidateCount;
    private Integer evaluatedCandidateCount;
    private Integer selectedStyleCount;
    private String decisionPolicyVersion;
    private String decisionEvidenceSha256;
    private String decisionSummary;
    private String approvedByPrincipalId;
    private String approvalEvidenceSha256;
    private String launchCalendarRef;
    private String downstreamHandoffRef;
    private String publicationEvidenceSha256;
    private String status;
    private String reasonCode;
    private Long version;
    private LocalDateTime selectedAt;
    private LocalDateTime approvedAt;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
