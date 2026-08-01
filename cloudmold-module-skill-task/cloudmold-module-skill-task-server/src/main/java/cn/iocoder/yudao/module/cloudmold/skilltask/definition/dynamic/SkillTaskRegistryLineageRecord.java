package cn.iocoder.yudao.module.cloudmold.skilltask.definition.dynamic;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
public class SkillTaskRegistryLineageRecord {

    private String lineageId;
    private Long tenantId;
    private String skillId;
    private String skillVersion;
    private String registryVersionId;
    private Long pointerVersion;
    private String sourceKind;
    private String canonicalDefinitionJson;
    private String registryPayloadSha256;
    private String definitionSha256;
    private String definitionClosureSha256;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
