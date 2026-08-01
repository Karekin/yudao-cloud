package cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("cloudmold_ai_ops_workflow_registry_version")
public class WorkflowRegistryVersionDO {
    @TableId(type = IdType.INPUT)
    private String registryVersionId;
    private Long tenantId;
    private String skillId;
    private String skillSemanticVersion;
    private String parentRegistryVersionId;
    private String proposalId;
    private String baseDefinitionSha256;
    private String definitionSha256;
    private String canonicalDefinitionJson;
    private String proposalSha256;
    private String proposalJson;
    private String validationJson;
    private String riskLevel;
    private String registryStatus;
    private String proposedBy;
    private LocalDateTime createdAt;
}
