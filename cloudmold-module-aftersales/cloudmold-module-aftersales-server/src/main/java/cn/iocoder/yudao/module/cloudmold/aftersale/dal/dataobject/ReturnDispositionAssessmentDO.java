package cn.iocoder.yudao.module.cloudmold.aftersale.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cloudmold_return_disposition_assessment")
public class ReturnDispositionAssessmentDO {
    @TableId(type = IdType.INPUT)
    private String assessmentId;
    private Long tenantId;
    private String afterSaleId;
    private String returnFulfillmentId;
    private String assessorId;
    private Integer packagingScore;
    private Integer appearanceScore;
    private Integer functionScore;
    private Boolean safetyRisk;
    private Boolean counterfeitRisk;
    private Long estimatedResaleValueMinor;
    private Long estimatedRecoveryCostMinor;
    private String inspectionEvidenceRef;
    private String recommendedDisposition;
    private String qualityStatus;
    private String conditionGrade;
    private Integer confidenceScore;
    private String rationaleCode;
    private LocalDateTime createdAt;
}
