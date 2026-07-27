package cn.iocoder.yudao.module.cloudmold.aioperations.service.command;

import cn.iocoder.yudao.module.cloudmold.aioperations.service.query.AiOperationsManagedRunQueryService;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.managed.ManagedSkillTaskWorkflowView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AiOperationsManagedRunQueryServiceFacade {

    public static final String CONSOLE_SKILL_ID = "ai-operations.console";

    private final AiOperationsManagedRunQueryService queryService;

    public ManagedSkillTaskWorkflowView requireWorkflow(String skillId, String skillVersion) {
        return requireWorkflow(queryService.listManagedWorkflows(), skillId, skillVersion);
    }

    public ManagedSkillTaskWorkflowView requireWorkflowAs(String skillId, String skillVersion,
                                                           Long operatorUserId, Integer operatorUserType) {
        return requireWorkflow(queryService.listManagedWorkflowsAs(operatorUserId, operatorUserType),
                skillId, skillVersion);
    }

    private ManagedSkillTaskWorkflowView requireWorkflow(
            java.util.List<ManagedSkillTaskWorkflowView> workflows,
            String skillId, String skillVersion) {
        return workflows.stream()
                .filter(item -> item.getSkillId().equals(skillId) && item.getSkillVersion().equals(skillVersion))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Managed workflow is not registered"));
    }
}
