package cn.iocoder.yudao.module.cloudmold.aioperations.controller.admin;

import cn.iocoder.yudao.module.cloudmold.aioperations.controller.admin.vo.ManagedSkillTaskTriggerReqVO;
import cn.iocoder.yudao.module.cloudmold.aioperations.service.command.AiOperationsManagedRunCommandService;
import cn.iocoder.yudao.module.cloudmold.aioperations.service.command.ManagedSkillTaskTriggerResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiOperationsManagedRunCommandControllerTest {

    private final AiOperationsManagedRunCommandService commandService =
            mock(AiOperationsManagedRunCommandService.class);
    private final AiOperationsManagedRunCommandController controller =
            new AiOperationsManagedRunCommandController(commandService);

    @Test
    void shouldTriggerManagedRunThroughAdminConsoleService() {
        ManagedSkillTaskTriggerReqVO request = new ManagedSkillTaskTriggerReqVO();
        ManagedSkillTaskTriggerResult trigger = ManagedSkillTaskTriggerResult.builder()
                .taskId("task-1").runId("run-1").status("QUEUED").build();
        when(commandService.trigger(request)).thenReturn(trigger);

        var result = controller.triggerManagedRun(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isSameAs(trigger);
        verify(commandService).trigger(request);
    }
}
