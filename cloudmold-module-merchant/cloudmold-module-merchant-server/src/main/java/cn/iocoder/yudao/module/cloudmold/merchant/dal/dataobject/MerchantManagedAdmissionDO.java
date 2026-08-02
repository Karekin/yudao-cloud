package cn.iocoder.yudao.module.cloudmold.merchant.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@TableName("cloudmold_merchant_managed_admission")
@Data
@Accessors(chain = true)
public class MerchantManagedAdmissionDO {
    @TableId(type = IdType.INPUT)
    private String admissionId;
    private Long tenantId;
    private String applicationId;
    private String merchantId;
    private String shopId;
    private String status;
    private String attributionChannelCode;
    private String attributionSourceSystem;
    private String attributionSourceType;
    private String attributionSourceId;
    private String attributionReference;
    private String attributionEvidenceRef;
    private String diagnosticId;
    private String inspectionTaskId;
    private String finalReviewId;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
