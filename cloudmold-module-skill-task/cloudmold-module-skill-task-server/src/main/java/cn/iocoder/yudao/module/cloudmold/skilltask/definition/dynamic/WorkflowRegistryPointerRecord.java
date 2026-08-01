package cn.iocoder.yudao.module.cloudmold.skilltask.definition.dynamic;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class WorkflowRegistryPointerRecord {

    private String pointerId;
    private Long tenantId;
    private String skillId;
    private String stableVersionId;
    private String candidateVersionId;
    private Long pointerVersion;
    private Boolean killSwitchEnabled;
}
