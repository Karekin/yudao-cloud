package cn.iocoder.yudao.module.cloudmold.skilltask.api.managed;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** 可审计 Skill 步骤的幂等参数绑定。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManagedSkillTaskWorkflowIdempotencyBindingView implements Serializable {

    private Integer argumentIndex;
    private String jsonPointer;
}
