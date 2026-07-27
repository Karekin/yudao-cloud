package cn.iocoder.yudao.module.cloudmold.aioperations.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.cloudmold.aioperations.service.query.AiOperationsConsoleQueryService;
import cn.iocoder.yudao.module.cloudmold.aioperations.service.query.AiOperationsManagedRunQueryService;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.managed.ManagedSkillTaskDetailView;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.managed.ManagedSkillTaskRunPageRequest;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.managed.ManagedSkillTaskRunView;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.managed.ManagedSkillTaskWorkflowView;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiOperationsConsoleQueryControllerManagedTest {

    private final AiOperationsManagedRunQueryService managedRunQueryService = mock(AiOperationsManagedRunQueryService.class);
    private final AiOperationsConsoleQueryService consoleQueryService = mock(AiOperationsConsoleQueryService.class);
    private final AiOperationsConsoleQueryController controller = new AiOperationsConsoleQueryController();

    AiOperationsConsoleQueryControllerManagedTest() throws Exception {
        inject("managedRunQueryService", managedRunQueryService);
        inject("consoleQueryService", consoleQueryService);
    }

    @Test
    void shouldReturnManagedWorkflowList() {
        List<ManagedSkillTaskWorkflowView> workflows = List.of(ManagedSkillTaskWorkflowView.builder()
                .skillId("skill-a")
                .build());
        when(managedRunQueryService.listManagedWorkflows()).thenReturn(workflows);

        var result = controller.listManagedWorkflows();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo(workflows);
        verify(managedRunQueryService).listManagedWorkflows();
    }

    @Test
    void shouldReturnManagedRunPage() {
        ManagedSkillTaskRunPageRequest request = new ManagedSkillTaskRunPageRequest();
        request.setPageNo(1);
        request.setPageSize(10);
        PageResult<ManagedSkillTaskRunView> page = new PageResult<>(List.of(
                ManagedSkillTaskRunView.builder().taskId("task-1").build()), 1L);
        when(managedRunQueryService.getManagedRunPage(request)).thenReturn(page);

        var result = controller.getManagedRunPage(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo(page);
        verify(managedRunQueryService).getManagedRunPage(request);
    }

    @Test
    void shouldReturnManagedRunDetail() {
        ManagedSkillTaskDetailView detail = ManagedSkillTaskDetailView.builder().build();
        when(managedRunQueryService.getManagedRun("task-1")).thenReturn(detail);

        var result = controller.getManagedRun("task-1");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isSameAs(detail);
        verify(managedRunQueryService).getManagedRun("task-1");
    }

    private void inject(String fieldName, Object value) throws Exception {
        var field = AiOperationsConsoleQueryController.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(controller, value);
    }
}
