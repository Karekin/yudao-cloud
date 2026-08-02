package cn.iocoder.yudao.module.cloudmold.merchant.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@TableName("cloudmold_merchant_probation_assessment")
@Data
@Accessors(chain = true)
public class MerchantProbationAssessmentDO {
    @TableId(type = IdType.INPUT)
    private String probationAssessmentId;
    private Long tenantId;
    private String admissionId;
    private String merchantId;
    private String shopId;
    private String assessmentStatus;
    private String thresholdsConfigRef;
    private String gatesJson;
    private Integer gateCount;
    private String evidenceRef;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
