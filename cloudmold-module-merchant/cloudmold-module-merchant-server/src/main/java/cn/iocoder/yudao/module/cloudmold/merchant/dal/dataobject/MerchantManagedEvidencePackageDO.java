package cn.iocoder.yudao.module.cloudmold.merchant.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@TableName("cloudmold_merchant_managed_evidence_package")
@Data
@Accessors(chain = true)
public class MerchantManagedEvidencePackageDO {
    @TableId(type = IdType.INPUT)
    private String evidencePackageId;
    private Long tenantId;
    private String admissionId;
    private String packageRef;
    private String itemsJson;
    private String status;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
