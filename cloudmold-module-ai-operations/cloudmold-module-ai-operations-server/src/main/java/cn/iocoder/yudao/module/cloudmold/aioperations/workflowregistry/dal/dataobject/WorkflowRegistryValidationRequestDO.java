package cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("cloudmold_ai_ops_workflow_registry_validation_request")
public class WorkflowRegistryValidationRequestDO {

    @TableId(type = IdType.INPUT)
    private String validationRequestId;
    private Long tenantId;
    private String skillId;
    private String registryVersionId;
    private Long pointerVersion;
    private String challenge;
    private String requestStatus;
    private String requestedBySubject;
    private String idempotencyKey;
    private LocalDateTime expiresAt;
    private LocalDateTime consumedAt;
    private LocalDateTime createdAt;
}
