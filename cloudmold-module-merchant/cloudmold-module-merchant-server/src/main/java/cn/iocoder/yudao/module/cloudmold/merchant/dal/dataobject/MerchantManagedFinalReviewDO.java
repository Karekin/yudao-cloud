package cn.iocoder.yudao.module.cloudmold.merchant.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@TableName("cloudmold_merchant_managed_final_review")
@Data
@Accessors(chain = true)
public class MerchantManagedFinalReviewDO {
    @TableId(type = IdType.INPUT)
    private String finalReviewId;
    private Long tenantId;
    private String admissionId;
    private String inspectionTaskId;
    private String decision;
    private String evidenceRef;
    private String reviewerPrincipalId;
    private String reviewNote;
    private String status;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
