package cn.iocoder.yudao.module.cloudmold.skilltask.controller.admin;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.managed.ManagedSkillTaskDetailView;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.managed.ManagedSkillTaskRunPageRequest;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.managed.ManagedSkillTaskRunView;
import cn.iocoder.yudao.module.cloudmold.skilltask.api.managed.ManagedSkillTaskWorkflowView;
import cn.iocoder.yudao.module.cloudmold.skilltask.service.query.ManagedSkillTaskQueryService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkillTaskAiOperationsQueryControllerTest {

    private final ManagedSkillTaskQueryService queryService = mock(ManagedSkillTaskQueryService.class);
    private final SkillTaskAiOperationsQueryController controller =
            new SkillTaskAiOperationsQueryController(queryService);

    @Test
    void shouldReturnManagedWorkflowListFromQueryService() {
        List<ManagedSkillTaskWorkflowView> workflows = List.of(ManagedSkillTaskWorkflowView.builder()
                .skillId("skill-a")
                .skillVersion("1.0.0")
                .displayName("示例")
                .triggerSource("ADMIN_CONSOLE")
                .managementSurface("DEER_FLOW")
                .orchestrationSurface("ADMIN_CONSOLE")
                .durableAuthority("SKILL_TASK")
                .build());
        when(queryService.listManagedWorkflows()).thenReturn(workflows);

        var result = controller.listManagedWorkflows();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo(workflows);
        verify(queryService).listManagedWorkflows();
    }

    @Test
    void shouldReturnPagedManagedRunsFromQueryService() {
        ManagedSkillTaskRunPageRequest request = new ManagedSkillTaskRunPageRequest();
        request.setPageNo(1);
        request.setPageSize(20);
        PageResult<ManagedSkillTaskRunView> page = new PageResult<>(List.of(
                ManagedSkillTaskRunView.builder().taskId("task-1").build()), 1L);
        when(queryService.getManagedRunPage(request)).thenReturn(page);

        var result = controller.getManagedRunPage(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo(page);
        verify(queryService).getManagedRunPage(request);
    }

    @Test
    void shouldReturnManagedRunDetailFromQueryService() {
        ManagedSkillTaskDetailView detail = ManagedSkillTaskDetailView.builder().build();
        when(queryService.getManagedRun("task-1")).thenReturn(detail);

        var result = controller.getManagedRun("task-1");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isSameAs(detail);
        verify(queryService).getManagedRun("task-1");
    }
}
