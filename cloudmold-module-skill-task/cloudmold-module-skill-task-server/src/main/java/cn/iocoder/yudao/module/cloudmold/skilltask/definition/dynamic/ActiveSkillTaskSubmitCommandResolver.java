package cn.iocoder.yudao.module.cloudmold.skilltask.definition.dynamic;

import cn.iocoder.yudao.module.cloudmold.skilltask.api.SkillTaskSubmitCommand;
import cn.iocoder.yudao.module.cloudmold.skilltask.definition.SkillTaskDefinition;
import cn.iocoder.yudao.module.cloudmold.skilltask.definition.SkillTaskDefinitionRegistry;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class ActiveSkillTaskSubmitCommandResolver {

    private final SkillTaskDefinitionRegistry definitionRegistry;
    private final DynamicSkillTaskDefinitionSource dynamicDefinitionSource;

    public ActiveSkillTaskSubmitCommandResolver(SkillTaskDefinitionRegistry definitionRegistry,
                                                DynamicSkillTaskDefinitionSource dynamicDefinitionSource) {
        this.definitionRegistry = definitionRegistry;
        this.dynamicDefinitionSource = dynamicDefinitionSource;
    }

    public SkillTaskSubmitCommand resolve(SkillTaskSubmitCommand command) {
        Objects.requireNonNull(command, "command");
        if (!dynamicDefinitionSource.isActiveAlias(command.getSkillVersion())) {
            return command;
        }
        SkillTaskDefinition definition = definitionRegistry.require(command.getSkillId(), command.getSkillVersion());
        return SkillTaskSubmitCommand.builder()
                .skillId(command.getSkillId())
                .skillVersion(definition.getSkillVersion())
                .runId(command.getRunId())
                .clientRequestKey(command.getClientRequestKey())
                .inputJson(command.getInputJson())
                .riskLevel(command.getRiskLevel())
                .approvalRef(command.getApprovalRef())
                .build();
    }
}
