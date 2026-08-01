package cn.iocoder.yudao.module.cloudmold.skilltask.definition.dynamic;

import cn.iocoder.yudao.module.cloudmold.skilltask.api.SkillTaskCommandApi;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.SkillTaskRetryCommand;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.SkillTaskSubmitCommand;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.SkillTaskView;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SkillTaskCommandApiBridgePostProcessorTest {

    @Test
    void rewritesActiveSubmissionsWithoutBreakingConcreteBeanType() {
        ActiveSkillTaskSubmitCommandResolver resolver = mock(ActiveSkillTaskSubmitCommandResolver.class);
        SkillTaskCommandApiBridgePostProcessor postProcessor = new SkillTaskCommandApiBridgePostProcessor(resolver);
        TestCommandApi target = new TestCommandApi();
        SkillTaskSubmitCommand original = SkillTaskSubmitCommand.builder()
                .skillId("skill.test")
                .skillVersion("ACTIVE")
                .clientRequestKey("request-1")
                .build();
        SkillTaskSubmitCommand rewritten = SkillTaskSubmitCommand.builder()
                .skillId("skill.test")
                .skillVersion("2.0.0")
                .clientRequestKey("request-1")
                .build();
        when(resolver.resolve(original)).thenReturn(rewritten);

        Object proxiedBean = postProcessor.postProcessAfterInitialization(target, "skillTaskApiService");
        SkillTaskView result = ((SkillTaskCommandApi) proxiedBean).submit(original);

        assertThat(proxiedBean).isInstanceOf(TestCommandApi.class);
        assertThat(target.lastCommand.getSkillVersion()).isEqualTo("2.0.0");
        assertThat(result.getSkillVersion()).isEqualTo("2.0.0");
    }

    public static class TestCommandApi implements SkillTaskCommandApi {

        private SkillTaskSubmitCommand lastCommand;

        @Override
        public SkillTaskView submit(SkillTaskSubmitCommand command) {
            this.lastCommand = command;
            return SkillTaskView.builder().skillVersion(command.getSkillVersion()).build();
        }

        @Override
        public SkillTaskView retry(SkillTaskRetryCommand command) {
            return null;
        }
    }
}
