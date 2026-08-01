package cn.iocoder.yudao.module.cloudmold.skilltask.definition.dynamic;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class WorkflowRegistryVersionRecord {

    private String registryVersionId;
    private Long tenantId;
    private String skillId;
    private String skillSemanticVersion;
    private String definitionSha256;
    private String canonicalDefinitionJson;
    private String registryStatus;
}
