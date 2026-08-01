package cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("cloudmold_ai_ops_workflow_registry_pointer")
public class WorkflowRegistryPointerDO {
    @TableId(type = IdType.INPUT)
    private String pointerId;
    private Long tenantId;
    private String skillId;
    private String stableVersionId;
    private String candidateVersionId;
    private Long pointerVersion;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
