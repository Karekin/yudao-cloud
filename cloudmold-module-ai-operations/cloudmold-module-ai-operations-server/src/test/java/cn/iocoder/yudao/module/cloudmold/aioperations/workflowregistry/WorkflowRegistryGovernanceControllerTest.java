package cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry;

import cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry.request.WorkflowRegistryApprovalRequest;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry.request.WorkflowRegistryEvaluationRequest;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry.request.WorkflowRegistryRetirementRequest;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry.request.WorkflowRegistryValidationStartRequest;
import cn.iocoder.yudao.module.cloudmold.aioperations.workflowregistry.view.WorkflowRegistryGovernanceStatusView;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowRegistryGovernanceControllerTest {

    private final WorkflowRegistryGovernanceService service = mock(WorkflowRegistryGovernanceService.class);
    private final WorkflowRegistryGovernanceController controller = new WorkflowRegistryGovernanceController(service);

    @Test
    void delegatesGovernanceEndpoints() {
        WorkflowRegistryEvaluationRequest evaluation = new WorkflowRegistryEvaluationRequest();
        WorkflowRegistryApprovalRequest approval = new WorkflowRegistryApprovalRequest();
        WorkflowRegistryRetirementRequest retirement = new WorkflowRegistryRetirementRequest();
        WorkflowRegistryValidationStartRequest validation = new WorkflowRegistryValidationStartRequest();
        WorkflowRegistryGovernanceStatusView view = WorkflowRegistryGovernanceStatusView.builder()
                .workflowId("skill.cloudmold.inventory.stockout-diagnosis.v1")
                .currentStatus("CANARY")
                .pointerVersion(3L)
                .build();

        when(service.recordEvaluation(evaluation)).thenReturn(view);
        when(service.startValidation(validation)).thenReturn(view);
        when(service.recordApproval(approval)).thenReturn(view);
        when(service.retire(retirement)).thenReturn(view);
        when(service.getStatus("skill.cloudmold.inventory.stockout-diagnosis.v1")).thenReturn(view);
        when(service.listStatuses(100)).thenReturn(List.of(view));

        assertThat(controller.startValidation(validation).getData()).isSameAs(view);
        assertThat(controller.recordEvaluation(evaluation).getData()).isSameAs(view);
        assertThat(controller.recordApproval(approval).getData()).isSameAs(view);
        assertThat(controller.retire(retirement).getData()).isSameAs(view);
        assertThat(controller.getStatus("skill.cloudmold.inventory.stockout-diagnosis.v1").getData()).isSameAs(view);
        assertThat(controller.listStatuses(100).getData()).containsExactly(view);

        verify(service).startValidation(validation);
        verify(service).recordEvaluation(evaluation);
        verify(service).recordApproval(approval);
        verify(service).retire(retirement);
        verify(service).getStatus("skill.cloudmold.inventory.stockout-diagnosis.v1");
        verify(service).listStatuses(100);
    }
}
