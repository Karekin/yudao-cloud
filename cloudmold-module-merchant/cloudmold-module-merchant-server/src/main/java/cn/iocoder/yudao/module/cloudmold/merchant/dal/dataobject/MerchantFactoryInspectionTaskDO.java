package cn.iocoder.yudao.module.cloudmold.merchant.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@TableName("cloudmold_merchant_factory_inspection_task")
@Data
@Accessors(chain = true)
public class MerchantFactoryInspectionTaskDO {
    @TableId(type = IdType.INPUT)
    private String inspectionTaskId;
    private Long tenantId;
    private String admissionId;
    private String diagnosticId;
    private String status;
    private String actorPrincipalId;
    private LocalDateTime scheduledAt;
    private String evidenceRef;
    private String note;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
