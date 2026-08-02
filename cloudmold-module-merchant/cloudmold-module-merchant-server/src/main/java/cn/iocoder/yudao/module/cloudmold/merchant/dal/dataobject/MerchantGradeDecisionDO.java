package cn.iocoder.yudao.module.cloudmold.merchant.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@TableName("cloudmold_merchant_grade_decision")
@Data
@Accessors(chain = true)
public class MerchantGradeDecisionDO {
    @TableId(type = IdType.INPUT)
    private String gradeDecisionId;
    private Long tenantId;
    private String merchantId;
    private String shopId;
    private String probationAssessmentId;
    private String scorecardId;
    private String gradeCode;
    private String transitionDecision;
    private String decisionStatus;
    private String thresholdsConfigRef;
    private String evidenceRef;
    private String entitlementsJson;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
