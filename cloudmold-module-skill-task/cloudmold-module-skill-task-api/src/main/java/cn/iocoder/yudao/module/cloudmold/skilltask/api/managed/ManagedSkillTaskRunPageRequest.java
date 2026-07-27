package cn.iocoder.yudao.module.cloudmold.skilltask.api.managed;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.io.Serializable;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ManagedSkillTaskRunPageRequest extends PageParam implements Serializable {

    private String taskId;
    private String runId;
    private String skillId;
    private String status;
    private String riskLevel;
}
