package cn.iocoder.yudao.module.cloudmold.skilltask.controller.admin;

import cn.iocoder.yudao.module.cloudmold.skilltask.workflowregistry.WorkflowRegistryAttestationView;
import cn.iocoder.yudao.module.cloudmold.skilltask.workflowregistry.WorkflowRegistryDefinitionView;
import cn.iocoder.yudao.module.cloudmold.skilltask.workflowregistry.WorkflowRegistryQueryService;
import cn.iocoder.yudao.module.cloudmold.skilltask.workflowregistry.WorkflowRegistrySubjectView;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowRegistryControllerTest {

    private final WorkflowRegistryQueryService queryService = mock(WorkflowRegistryQueryService.class);
    private final WorkflowRegistryController controller = new WorkflowRegistryController(queryService);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldReturnAuthenticatedSubjectFromQueryService() {
        WorkflowRegistrySubjectView view = new WorkflowRegistrySubjectView("42", 162L);
        when(queryService.getCurrentSubject()).thenReturn(view);

        var result = controller.getCurrentSubject();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo(view);
        verify(queryService).getCurrentSubject();
    }

    @Test
    void shouldReturnDefinitionAndAttestationFromQueryService() {
        WorkflowRegistryDefinitionView view = new WorkflowRegistryDefinitionView(
                objectMapper.createObjectNode().put("skill_id", "skill.test"),
                new WorkflowRegistryAttestationView(
                        "cloudmold-workflow-registry",
                        "skill.test",
                        "42",
                        "1.0.0",
                        "a".repeat(64),
                        1780000000L,
                        "b".repeat(64)));
        when(queryService.getDefinition("skill.test", "1.0.0")).thenReturn(view);

        var result = controller.getDefinition("skill.test", "1.0.0");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo(view);
        verify(queryService).getDefinition("skill.test", "1.0.0");
    }
}
