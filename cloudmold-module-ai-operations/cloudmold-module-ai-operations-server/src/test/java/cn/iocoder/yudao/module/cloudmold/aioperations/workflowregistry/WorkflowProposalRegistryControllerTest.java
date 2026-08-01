package cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry;

import cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry.request.WorkflowProposalRegistryRequest;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry.view.WorkflowProposalRegistryStatusView;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowProposalRegistryControllerTest {

    private final WorkflowProposalRegistryService service = mock(WorkflowProposalRegistryService.class);
    private final WorkflowProposalRegistryController controller = new WorkflowProposalRegistryController(service);

    @Test
    void delegatesSubmissionAndStatusQueries() {
        WorkflowProposalRegistryRequest request = new WorkflowProposalRegistryRequest();
        WorkflowProposalRegistryStatusView submitted = WorkflowProposalRegistryStatusView.builder()
                .workflowId("skill.test").proposalId("proposal-aabbccddeeff0011")
                .candidateStatus("SUBMITTED").pointerVersion(1L).build();
        when(service.submit(request)).thenReturn(submitted);

        var submitResult = controller.submit(request);

        assertThat(submitResult.getData()).isSameAs(submitted);
        verify(service).submit(request);

        when(service.getStatus("skill.test")).thenReturn(submitted);
        var statusResult = controller.getStatus("skill.test");
        assertThat(statusResult.getData()).isSameAs(submitted);
        verify(service).getStatus("skill.test");
    }
}
