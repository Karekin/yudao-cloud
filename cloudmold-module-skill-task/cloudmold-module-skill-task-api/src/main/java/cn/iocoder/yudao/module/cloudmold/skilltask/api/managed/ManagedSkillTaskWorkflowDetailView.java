package cn.iocoder.yudao.module.cloudmold.skilltask.api.managed;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 可审计的 Skill 编排定义。它描述批准前后将执行的确定性步骤，不能替代运行态证据。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManagedSkillTaskWorkflowDetailView implements Serializable {

    private ManagedSkillTaskWorkflowView workflow;
    private List<ManagedSkillTaskWorkflowStepView> steps;
}
