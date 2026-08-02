package cn.iocoder.yudao.module.cloudmold.merchant.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@TableName("cloudmold_merchant_ai_diagnostic")
@Data
@Accessors(chain = true)
public class MerchantAiDiagnosticDO {
    @TableId(type = IdType.INPUT)
    private String diagnosticId;
    private Long tenantId;
    private String admissionId;
    private String evidencePackageId;
    private String recommendationCode;
    private String recommendationSummary;
    private String evidenceRef;
    private String status;
    private String reviewerPrincipalId;
    private String reviewNote;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
